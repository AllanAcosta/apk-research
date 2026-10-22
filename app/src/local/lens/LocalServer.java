package local.lens;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** HTTP/1.0 local server: no external libraries, no remote sensor activation. */
public final class LocalServer implements AutoCloseable {
    private final ShareService service;
    private final ServerSocket listener;
    private final Set<Socket> clients=Collections.newSetFromMap(new ConcurrentHashMap<Socket,Boolean>());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,8,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>());
    private volatile boolean running=true;
    private Thread acceptor;
    public LocalServer(ShareService service,int port)throws IOException {
        this.service=service;listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(port));
    }
    public void start(){acceptor=new Thread(()->{
        while(running)try{
            Socket socket=listener.accept();
            InetAddress peer=socket.getInetAddress();
            if(!peer.isLoopbackAddress()&&!peer.isSiteLocalAddress()&&!peer.isLinkLocalAddress()){socket.close();continue;}
            socket.setSoTimeout(5000);clients.add(socket);
            try{workers.execute(()->handle(socket));}catch(RejectedExecutionException e){clients.remove(socket);socket.close();}
        }catch(IOException e){if(running)service.message="Error de red: "+e.getMessage();}
    },"local-http");acceptor.start();}
    private String line(InputStream in)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;
        while((c=in.read())!=-1){if(c=='\n')return b.toString("US-ASCII").replace("\r","");if(b.size()>=4096)throw new IOException("Header too long");b.write(c);}
        return b.size()==0?null:b.toString("US-ASCII");
    }
    private void handle(Socket socket){
        try(Socket s=socket){
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();String request=line(in);
            if(request==null)return;
            String[] parts=request.split(" ");if(parts.length!=3||!parts[0].equals("GET")){reply(out,405,"text/plain","GET required".getBytes(StandardCharsets.UTF_8));return;}
            String auth="";int count=0;String h;
            while((h=line(in))!=null&&!h.isEmpty()){
                if(++count>32)throw new IOException("Too many headers");
                if(h.regionMatches(true,0,"Authorization:",0,14))auth=h.substring(14).trim();
            }
            String path=parts[1].split("\\?",2)[0];
            if(path.equals("/")){reply(out,200,"text/html; charset=utf-8",service.dashboard().getBytes(StandardCharsets.UTF_8));return;}
            if(!MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+service.token).getBytes(StandardCharsets.UTF_8))){reply(out,401,"text/plain","Código incorrecto".getBytes(StandardCharsets.UTF_8));return;}
            if(path.equals("/frame")){
                byte[] frame=service.frame;if(frame==null)reply(out,204,"image/jpeg",new byte[0]);else reply(out,200,"image/jpeg",frame);
            }else if(path.equals("/status")){
                String status="{\"source\":\""+service.source+"\",\"mic\":"+service.mic+"}";
                reply(out,200,"application/json",status.getBytes(StandardCharsets.UTF_8));
            }else if(path.equals("/audio")){
                if(!service.mic){reply(out,409,"text/plain","Activa el micrófono en el teléfono".getBytes(StandardCharsets.UTF_8));return;}
                ArrayBlockingQueue<byte[]> q=service.subscribe();
                try{
                    headers(out,200,"application/octet-stream",-1);
                    while(running&&service.mic){byte[] data=q.poll(2,TimeUnit.SECONDS);if(data!=null){out.write(data);out.flush();}}
                }finally{service.unsubscribe(q);}
            }else reply(out,404,"text/plain",new byte[0]);
        }catch(Exception ignored){}finally{clients.remove(socket);}
    }
    private void headers(OutputStream out,int status,String type,int length)throws IOException {
        String h="HTTP/1.0 "+status+" "+(status==200?"OK":status==204?"No Content":status==401?"Unauthorized":"Error")+"\r\nContent-Type: "+type+
            "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\n"+
            "Content-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'\r\n"+
            (length>=0?"Content-Length: "+length+"\r\n":"")+"Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));out.flush();
    }
    private void reply(OutputStream out,int code,String type,byte[] data)throws IOException{headers(out,code,type,data.length);out.write(data);out.flush();}
    public void close(){
        running=false;try{listener.close();}catch(IOException ignored){}
        try{if(acceptor!=null)acceptor.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        for(Socket s:clients)try{s.close();}catch(IOException ignored){}
        workers.shutdownNow();
        try{workers.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
