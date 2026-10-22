package local.lens;
import java.util.concurrent.ArrayBlockingQueue;
/** Host fixture only: Android sensors are validated separately on-device. */
public final class ShareService {
 public String token="test-access-code",source="apagada",message="";
 public volatile boolean mic=false;
 public byte[] frame;
 public ArrayBlockingQueue<byte[]> subscribe(){ArrayBlockingQueue<byte[]> q=new ArrayBlockingQueue<>(2);q.add(new byte[]{1,2,3,4});return q;}
 public void unsubscribe(ArrayBlockingQueue<byte[]> q){}
 public String dashboard(){return "<!doctype html><title>Visor local</title>";}
}
