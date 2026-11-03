package com.mcyao.barkserver;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;

public class BarkService extends Service {
 private volatile boolean active; private java.lang.Process child; private PowerManager.WakeLock wake; private ReliableSmsSync smsSync;
 private android.net.ConnectivityManager.NetworkCallback connectivity;
 private boolean pendingNetworkSignal;
 private long lastNetworkSignal;
 private synchronized void signalNetwork() {
  if(!active)return;
  pendingNetworkSignal=true;
  flushNetworkSignal();
 }
 private void flushNetworkSignal() {
  if(child==null||!pendingNetworkSignal)return;
  long now=SystemClock.elapsedRealtime();
  if(lastNetworkSignal!=0&&now-lastNetworkSignal<30000){pendingNetworkSignal=false;return;}
  try {
   OutputStream pipe=child.getOutputStream();pipe.write("network-restored\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));pipe.flush();
   pendingNetworkSignal=false;lastNetworkSignal=now;
  } catch(IOException ignored){ /* A replacement engine receives the pending signal. */ }
 }
 private void watchNetwork() {
  android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);
  if(cm==null)return;
  android.net.ConnectivityManager.NetworkCallback cb=new android.net.ConnectivityManager.NetworkCallback(){
   private android.net.Network current;private boolean validated;private Boolean v6Default;
   public void onAvailable(android.net.Network n){current=n;validated=false;v6Default=null;}
   public void onLost(android.net.Network n){if(n.equals(current)){current=null;validated=false;}}
   public void onCapabilitiesChanged(android.net.Network n,android.net.NetworkCapabilities caps){
    if(!n.equals(current))return;
    boolean ready=caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    if(ready&&!validated)signalNetwork();validated=ready;
   }
   public void onLinkPropertiesChanged(android.net.Network n,android.net.LinkProperties props){
    if(!n.equals(current))return;
    boolean hasRoute=false;
    for(android.net.RouteInfo route:props.getRoutes())if(route.isDefaultRoute() && route.getDestination().getAddress() instanceof java.net.Inet6Address){hasRoute=true;break;}
    if(validated && Boolean.FALSE.equals(v6Default) && hasRoute)signalNetwork();v6Default=hasRoute;
   }
  };
  try{cm.registerDefaultNetworkCallback(cb);connectivity=cb;}catch(RuntimeException ignored){ /* Normal durable retries remain active. */ }
 }
 static String prefix(Context c) {
  android.content.SharedPreferences p=c.getSharedPreferences("server",0);
  String s=p.getString("prefix",null);
  if(s==null){s=UUID.randomUUID().toString().replace("-","");p.edit().putString("prefix",s).commit();}
  return s;
 }
 public void onCreate(){super.onCreate();
  showForeground();
 }
 private synchronized void startWorker(){
  if(active)return;
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"bark:server");wake.acquire();
  active=true;watchNetwork(); new Thread(()->{long backoff=0;while(active){long started=SystemClock.elapsedRealtime();try{
   File log=new File(getFilesDir(),"server.log");if(log.length()>1048576)log.delete();
   ProcessBuilder pb=new ProcessBuilder(getApplicationInfo().nativeLibraryDir+"/libbark.so","--addr","0.0.0.0:18080","--data",new File(getFilesDir(),"data").getPath(),"--url-prefix","/"+prefix(this));
   pb.environment().put("BARK_ANDROID_STATE",getFilesDir().getPath());
   pb.redirectErrorStream(true);pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
   synchronized(this){if(!active)break;child=pb.start();flushNetworkSignal();}child.waitFor();
  }catch(Exception e){try{java.nio.file.Files.write(new File(getFilesDir(),"last-error").toPath(),e.toString().getBytes());}catch(Exception ignored){}}
  if(SystemClock.elapsedRealtime()-started>=60000)backoff=0;
  if(active&&backoff>0)try{Thread.sleep(backoff);}catch(InterruptedException ignored){Thread.currentThread().interrupt();break;}
  backoff=backoff==0?1000:Math.min(60000,backoff*2);
  }},"bark-supervisor").start();
  smsSync=new ReliableSmsSync(this);smsSync.start();
 }
 private void showForeground(){
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("server","Bark 服务状态",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"server").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Bark 本机服务运行中").setContentText("短信通过本机服务推送至 iPhone").setContentIntent(pi).build());
 }
 public int onStartCommand(Intent i,int f,int id){
  // Check desired state before spawning the engine or acquiring the wake lock.
  // A sticky restart has no intent and must also respect an explicit user stop.
  if((i==null||i.getBooleanExtra("monitor_recovery",false))&&!getSharedPreferences("server",0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
  showForeground();getSharedPreferences("server",0).edit().putBoolean("enabled",true).commit();startWorker();
  if(i!=null&&"RESET_RELIABLE_SMS".equals(i.getStringExtra("action"))&&i.getBooleanExtra("include_existing",false))try{ReliableSmsSync.reconcileRebuiltInbox(this);}catch(RuntimeException ignored){}
  if((i!=null&&"ENABLE_RELIABLE_SMS".equals(i.getStringExtra("action")))||(ReliableSmsSync.prefs(this).getBoolean("migration_requested",false)&&!ReliableSmsSync.prefs(this).getBoolean("enabled",false)))new Thread(()->{
   try{ReliableSmsSync.enableWithRoot(this);ReliableSmsSync.prefs(this).edit().putString("migration_status","enabled").commit();}
   catch(Exception e){ReliableSmsSync.prefs(this).edit().putString("migration_status","failed; retry preserves the original sync position").commit();}
  },"sms-reliable-setup").start();
  return START_STICKY;
 }
 public synchronized void onDestroy(){active=false;if(connectivity!=null){try{getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(connectivity);}catch(RuntimeException ignored){}connectivity=null;}if(smsSync!=null)smsSync.stop();if(child!=null)child.destroy();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}
