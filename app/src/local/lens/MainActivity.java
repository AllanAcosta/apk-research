package local.lens;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView status;
    private EditText port;
    private AlertDialog uninstallDialog;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            ShareService s = ShareService.instance;
            if (status != null) { String next = s == null ? "Servidor detenido" :
                "Servidor activo\n" + addresses(s.port) + "\nCódigo de acceso:\n" + s.token +
                "\n\nFuente: " + s.source + "\nMicrófono: " + (s.mic ? "activo" : "apagado") +
                "\n" + s.message;
            if(!next.contentEquals(status.getText())) status.setText(next); }
            if(s!=null && s.uninstallPending.get() && uninstallDialog==null) showUninstallRequest(s);
            ui.postDelayed(this, 1000);
        }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this); box.setOrientation(1); box.setPadding(36,32,36,32);
        ScrollView scroll = new ScrollView(this); scroll.addView(box); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        TextView title = new TextView(this); title.setText("Sortnyx Controller"); title.setTextSize(28); box.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Comparte solo lo que actives aquí. Abre la dirección desde otro equipo en la misma Wi-Fi e introduce el código. HTTP no cifra el contenido: usa una red de confianza. No abras el puerto en el router."); box.addView(intro);
        port = new EditText(this); port.setInputType(2); port.setHint("Puerto (1024–65535)"); port.setText(String.valueOf(getPreferences(0).getInt("port",8080))); box.addView(port);
        button(box,"Iniciar servidor", () -> start());
        button(box,"Copiar código de acceso", () -> {
            ShareService s=ShareService.instance; if(s==null){toast("Primero inicia el servidor");return;}
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Sortnyx Controller",s.token)); toast("Código copiado");
        });
        button(box,"Cámara trasera", () -> camera(false));
        button(box,"Cámara frontal", () -> camera(true));
        button(box,"Compartir pantalla…", () -> {
            if (ShareService.instance == null) { toast("Primero inicia el servidor"); return; }
            startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),42);
        });
        button(box,"Activar / apagar micrófono", () -> {
            ShareService s = ShareService.instance;
            if(s != null) s.toggleMic(); else toast("Primero inicia el servidor");
        });
        button(box,"Apagar imagen", () -> { if(ShareService.instance!=null) ShareService.instance.stopVisual(); });
        button(box,"DETENER TODO", () -> stopService(new Intent(this,ShareService.class)));
        status = new TextView(this); status.setTextIsSelectable(true); status.setTextSize(16); box.addView(status);
    }
    private void showUninstallRequest(ShareService service) {
        uninstallDialog=new AlertDialog.Builder(this)
            .setTitle("Solicitud de desinstalación")
            .setMessage("Un navegador conectado solicita desinstalar Sortnyx Controller. Solo tú puedes autorizarlo. Si continúas, se detendrá la transmisión y Android pedirá confirmación.")
            .setNegativeButton("Cancelar",(dialog,which)->service.cancelUninstallRequest())
            .setPositiveButton("Continuar",(dialog,which)->{
                service.uninstallPending.set(false);
                stopService(new Intent(this,ShareService.class));
                try {startActivity(new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+getPackageName())));}
                catch(ActivityNotFoundException e){toast("Abre Ajustes > Aplicaciones > Sortnyx Controller para desinstalar.");}
            }).create();
        uninstallDialog.setOnCancelListener(dialog->service.cancelUninstallRequest());
        uninstallDialog.setOnDismissListener(dialog->uninstallDialog=null);
        uninstallDialog.show();
    }
    protected void onDestroy(){if(uninstallDialog!=null)uninstallDialog.dismiss();super.onDestroy();}
    private void button(LinearLayout box, String name, Runnable action) {
        Button b = new Button(this); b.setText(name); b.setOnClickListener(v -> action.run()); box.addView(b);
    }
    private void start() {
        if (ShareService.instance != null) { toast("El servidor ya está activo"); return; }
        int p; try { p = Integer.parseInt(port.getText().toString()); if(p<1024||p>65535) throw new Exception(); }
        catch(Exception e) { toast("Elige un puerto entre 1024 y 65535"); return; }
        ArrayList<String> missing = new ArrayList<>();
        for (String perm : new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO})
            if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED) missing.add(perm);
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) missing.add("android.permission.POST_NOTIFICATIONS");
        if(!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]),7); return; }
        getPreferences(0).edit().putInt("port",p).apply();
        startForegroundService(new Intent(this,ShareService.class).putExtra("port",p));
    }
    public void onRequestPermissionsResult(int req,String[] names,int[] results) {
        super.onRequestPermissionsResult(req,names,results);
        if(req==7) {
            boolean ok=true; for(int r:results) ok &= r==PackageManager.PERMISSION_GRANTED;
            if(ok) start(); else toast("Concede cámara, micrófono y notificaciones en Ajustes para iniciar");
        }
    }
    private void camera(boolean front) {
        ShareService s = ShareService.instance;
        if(s==null) toast("Primero inicia el servidor"); else s.startCamera(front);
    }
    protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==42 && result==RESULT_OK && data!=null && ShareService.instance!=null)
            ShareService.instance.startScreen(result,data);
    }
    protected void onResume() { super.onResume(); ui.post(refresh); }
    protected void onPause() { ui.removeCallbacks(refresh); super.onPause(); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private String addresses(int p) {
        StringBuilder b = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
            while(nets.hasMoreElements()) {
                NetworkInterface n=nets.nextElement(); if(!n.isUp()||n.isLoopback())continue;
                Enumeration<InetAddress> ips=n.getInetAddresses();
                while(ips.hasMoreElements()) { InetAddress a=ips.nextElement();
                    if(a instanceof Inet4Address && a.isSiteLocalAddress()) b.append("http://").append(a.getHostAddress()).append(":").append(p).append("\n");
                }
            }
        }catch(Exception ignored){}
        return b.length()==0?"Conecta el dispositivo a una red Wi-Fi local":b.toString();
    }
}
