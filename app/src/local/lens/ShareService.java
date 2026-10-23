package local.lens;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.graphics.*;
import android.hardware.Camera;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public class ShareService extends Service {
    public static volatile ShareService instance;
    public final java.util.concurrent.atomic.AtomicBoolean uninstallPending=new java.util.concurrent.atomic.AtomicBoolean(false);
    public void requestUninstall(){
        if(uninstallPending.compareAndSet(false,true)) main.post(()->{
            if(instance!=this)return;
            message="Se solicitó desinstalar desde el navegador. Abre la app para aceptar o cancelar.";
            foreground(projection!=null);
        });
    }
    public void cancelUninstallRequest(){
        uninstallPending.set(false);
        if(instance!=this)return;
        message="Solicitud de desinstalación cancelada en el teléfono.";
        foreground(projection!=null);
    }
    public volatile byte[] frame;
    public volatile boolean mic;
    public volatile String source="apagada",message="Activa una fuente desde este teléfono.";
    public int port;
    public String token;
    private LocalServer server;
    private Camera camera;
    private SurfaceTexture texture;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private AudioRecord recorder;
    private Thread audioThread;
    private HandlerThread captureThread;
    private Handler capture;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ArrayBlockingQueue<byte[]>> listeners=new CopyOnWriteArrayList<>();
    private long lastFrame;
    public IBinder onBind(Intent i) { return null; }
    public void onCreate() { super.onCreate(); captureThread=new HandlerThread("image-encoder"); captureThread.start(); capture=new Handler(captureThread.getLooper()); }
    public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(server!=null)return START_NOT_STICKY;
        port=intent==null?8080:intent.getIntExtra("port",8080);
        byte[] random=new byte[16]; new SecureRandom().nextBytes(random);
        StringBuilder code=new StringBuilder(); for(byte b:random)code.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); token=code.toString();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("share","Transmisión local",NotificationManager.IMPORTANCE_LOW));
        foreground(false);
        try { server=new LocalServer(this,port); server.start(); instance=this; }
        catch(Exception e) { message="No se pudo abrir el puerto: "+e.getMessage(); android.widget.Toast.makeText(this,message,1).show(); stopSelf(); }
        return START_NOT_STICKY;
    }
    private void foreground(boolean screen) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ShareService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"share").setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Visor local activo").setContentText(uninstallPending.get()?"Solicitud de desinstalación. Abre la app para decidir.":"Compartiendo en la red local. Toca para controlar.")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Detener todo",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA|ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE|
            (screen?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:0));
        else startForeground(1,n);
    }
    public void startCamera(boolean front) {
        stopVisual();
        try {
            int chosen=-1; Camera.CameraInfo info=new Camera.CameraInfo();
            for(int i=0;i<Camera.getNumberOfCameras();i++) { Camera.getCameraInfo(i,info); if(info.facing==(front?1:0)){chosen=i;break;} }
            if(chosen<0)throw new IOException("Cámara no disponible");
            Camera.getCameraInfo(chosen,info);
            camera=Camera.open(chosen);
            Camera.Parameters p=camera.getParameters();
            List<Camera.Size> sizes=p.getSupportedPreviewSizes(); Camera.Size size=sizes.get(0);
            for(Camera.Size s:sizes) if(s.width<=960 && (size.width>960||s.width>size.width))size=s;
            p.setPreviewSize(size.width,size.height); p.setPreviewFormat(ImageFormat.NV21);
            if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            camera.setParameters(p); texture=new SurfaceTexture(17); camera.setPreviewTexture(texture);
            final int w=size.width,h=size.height;
            final int sensor=info.orientation;
            final boolean mirror=front;
            final Camera active=camera;
            camera.addCallbackBuffer(new byte[w*h*3/2]);
            camera.setPreviewCallbackWithBuffer((data,c)->{
                if(c!=camera)return;
                long now=System.currentTimeMillis();
                if(now-lastFrame<200){c.addCallbackBuffer(data);return;} lastFrame=now;
                int rotation=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()*90;
                final int angle=mirror?(sensor+rotation)%360:(sensor-rotation+360)%360;
                capture.post(()->{
                    try {
                        ByteArrayOutputStream raw=new ByteArrayOutputStream();
                        new YuvImage(data,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),65,raw);
                        Bitmap original=BitmapFactory.decodeByteArray(raw.toByteArray(),0,raw.size());
                        Matrix matrix=new Matrix(); matrix.postRotate(angle); if(mirror)matrix.postScale(-1,1);
                        Bitmap rotated=Bitmap.createBitmap(original,0,0,w,h,matrix,true);
                        ByteArrayOutputStream out=new ByteArrayOutputStream(); rotated.compress(Bitmap.CompressFormat.JPEG,70,out);
                        if(camera==active)frame=out.toByteArray();
                        if(rotated!=original)rotated.recycle(); original.recycle();
                    }catch(Exception e){message="Error de imagen: "+e.getMessage();}
                    main.post(()->{if(camera==active)try{active.addCallbackBuffer(data);}catch(Exception ignored){}});
                });
            });
            camera.setErrorCallback((error,c)->{if(c==camera){stopVisual();message="Cámara interrumpida. Vuelve a activarla.";}});
            camera.startPreview(); source=front?"cámara frontal":"cámara trasera"; message="Imagen disponible en el navegador.";
        }catch(Exception e) { stopVisual(); message="No se pudo iniciar la cámara: "+e.getMessage(); }
    }
    public void startScreen(int result,Intent consent) {
        stopVisual();
        try {
            foreground(true);
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(result,consent);
            final MediaProjection active=projection;
            projection.registerCallback(new MediaProjection.Callback(){public void onStop(){if(projection==active)stopVisual();}},main);
            DisplayMetrics metrics=new DisplayMetrics(); ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
            float scale=Math.min(1f,960f/Math.max(metrics.widthPixels,metrics.heightPixels));
            int w=(int)(metrics.widthPixels*scale),h=(int)(metrics.heightPixels*scale);
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->{
                try(Image image=r.acquireLatestImage()) {
                    if(image==null)return;
                    long now=System.currentTimeMillis();if(now-lastFrame<200)return;lastFrame=now;
                    Image.Plane plane=image.getPlanes()[0]; int padded=plane.getRowStride()/plane.getPixelStride();
                    Bitmap full=Bitmap.createBitmap(padded,h,Bitmap.Config.ARGB_8888);
                    java.nio.ByteBuffer pixels=plane.getBuffer();
                    if(pixels.remaining()<padded*h*4){java.nio.ByteBuffer complete=java.nio.ByteBuffer.allocateDirect(padded*h*4);complete.put(pixels);complete.rewind();pixels=complete;}
                    full.copyPixelsFromBuffer(pixels);
                    Bitmap crop=Bitmap.createBitmap(full,0,0,w,h); ByteArrayOutputStream out=new ByteArrayOutputStream();
                    crop.compress(Bitmap.CompressFormat.JPEG,70,out); if(projection==active)frame=out.toByteArray();
                    if(crop!=full)crop.recycle(); full.recycle();
                }catch(Exception e){message="Error de captura: "+e.getMessage();}
            },capture);
            display=projection.createVirtualDisplay("VisorLocal",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,capture);
            source="pantalla";message="Pantalla compartida; las superficies protegidas pueden verse negras. Si giras el teléfono, vuelve a activar la captura.";
        } catch(Exception e) {stopVisual();message="No se pudo compartir la pantalla: "+e.getMessage();}
    }
    public void stopVisual() {
        source="apagada";
        if(camera!=null){Camera old=camera;camera=null;old.setPreviewCallbackWithBuffer(null);old.release();}
        if(texture!=null){texture.release();texture=null;}
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){MediaProjection old=projection;projection=null;old.stop();foreground(false);}
        frame=null;
    }
    public void toggleMic() { if(mic)stopMic();else startMic(); }
    private void startMic() {
        try {
            int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(8192,min*2));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Micrófono no disponible");
            recorder.startRecording(); mic=true; final AudioRecord active=recorder;
            audioThread=new Thread(()->{
                byte[] buffer=new byte[3200];
                while(mic && recorder==active) {
                    int n=active.read(buffer,0,buffer.length);
                    if(n<0){main.post(()->{if(recorder==active){stopMic();message="Captura de audio interrumpida.";}});break;}
                    if(n>0){byte[] data=Arrays.copyOf(buffer,n);for(ArrayBlockingQueue<byte[]> q:listeners){if(!q.offer(data)){q.poll();q.offer(data);}}}
                }
            },"microphone"); audioThread.start();message="Micrófono activo. Pulsa Escuchar en el navegador.";
        }catch(Exception e){stopMic();message="No se pudo iniciar el micrófono: "+e.getMessage();}
    }
    private void stopMic() {
        mic=false;
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}try{if(audioThread!=null)audioThread.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}recorder.release();recorder=null;}
        for(ArrayBlockingQueue<byte[]> q:listeners)q.clear();
    }
    public ArrayBlockingQueue<byte[]> subscribe(){ArrayBlockingQueue<byte[]> q=new ArrayBlockingQueue<>(6);listeners.add(q);return q;}
    public void unsubscribe(ArrayBlockingQueue<byte[]> q){listeners.remove(q);}
    public String dashboard() throws IOException {try(InputStream in=getAssets().open("dashboard.html")){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toString("UTF-8");}}
    public void onDestroy(){instance=null;if(server!=null)server.close();stopMic();stopVisual();captureThread.quitSafely();stopForeground(true);super.onDestroy();}
}
