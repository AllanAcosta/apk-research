package local.lens;
import java.net.*;
public final class ServerTest {
 static int port,count;
 static HttpURLConnection request(String path,boolean authorized)throws Exception {
  HttpURLConnection c=(HttpURLConnection)URI.create("http://127.0.0.1:"+port+path).toURL().openConnection();c.setReadTimeout(3000);c.setConnectTimeout(3000);
  if(authorized)c.setRequestProperty("Authorization","Bearer test-access-code");return c;
 }
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception {
  try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
  ShareService service=new ShareService();LocalServer server=new LocalServer(service,port);server.start();
  try {
   HttpURLConnection c=request("/",false);check(c.getResponseCode()==200 && new String(c.getInputStream().readAllBytes()).contains("Visor local"),"dashboard accessible");c.disconnect();
   for(String route:new String[]{"/status","/frame","/audio"}){c=request(route,false);check(c.getResponseCode()==401,"authentication required: "+route);c.disconnect();}
   c=request("/status",true);check(c.getResponseCode()==200&&new String(c.getInputStream().readAllBytes()).contains("\"mic\":false"),"source status");c.disconnect();
   c=request("/frame",true);check(c.getResponseCode()==204,"no image before consent");c.disconnect();
   service.frame=new byte[]{(byte)255,(byte)216,(byte)255,(byte)217};c=request("/frame",true);check(c.getResponseCode()==200&&c.getInputStream().readAllBytes().length==4&&"no-store".equals(c.getHeaderField("Cache-Control")),"image transport without cache");c.disconnect();
   c=request("/audio",true);check(c.getResponseCode()==409,"no microphone before activation");c.disconnect();
   service.mic=true;c=request("/audio",true);check(c.getResponseCode()==200&&c.getInputStream().readNBytes(4)[3]==4,"PCM audio streaming");c.disconnect();service.mic=false;
   c=request("/unknown",true);check(c.getResponseCode()==404,"unknown route");c.disconnect();
   c=request("/uninstall-request",false);c.setRequestMethod("POST");check(c.getResponseCode()==401&&!service.uninstallRequested,"uninstall requires authorization");c.disconnect();
   c=request("/uninstall-request",true);check(c.getResponseCode()==405&&!service.uninstallRequested,"GET cannot request uninstall");c.disconnect();
   c=request("/uninstall-request",true);c.setRequestMethod("POST");check(c.getResponseCode()==202&&service.uninstallRequested,"authenticated POST queues consent request");c.disconnect();
   c=request("/status",true);c.setRequestMethod("POST");check(c.getResponseCode()==405,"POST cannot invoke read routes");c.disconnect();
  }finally{server.close();}
  try(ServerSocket rebind=new ServerSocket()){rebind.setReuseAddress(true);rebind.bind(new InetSocketAddress(port));check(true,"server releases port");}
  System.out.println(count+" checks passed");
 }
}
