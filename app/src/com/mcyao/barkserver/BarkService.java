package com.mcyao.barkserver;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;

public class BarkService extends Service {
 private volatile boolean active; private java.lang.Process child; private PowerManager.WakeLock wake;
 static String prefix(Context c) {
  android.content.SharedPreferences p=c.getSharedPreferences("server",0);
  String s=p.getString("prefix",null);
  if(s==null){s=UUID.randomUUID().toString().replace("-","");p.edit().putString("prefix",s).commit();}
  return s;
 }
 public void onCreate(){super.onCreate();
  showForeground();
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"bark:server");wake.acquire();
  active=true; new Thread(()->{while(active){try{
   File log=new File(getFilesDir(),"server.log");if(log.length()>1048576)log.delete();
   ProcessBuilder pb=new ProcessBuilder(getApplicationInfo().nativeLibraryDir+"/libbark.so","--addr","0.0.0.0:18080","--data",new File(getFilesDir(),"data").getPath(),"--url-prefix","/"+prefix(this));
   pb.environment().put("BARK_ANDROID_STATE",getFilesDir().getPath());
   pb.redirectErrorStream(true);pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
   synchronized(this){if(!active)break;child=pb.start();}child.waitFor();
  }catch(Exception e){try{java.nio.file.Files.write(new File(getFilesDir(),"last-error").toPath(),e.toString().getBytes());}catch(Exception ignored){}}
  if(active)try{Thread.sleep(5000);}catch(Exception ignored){}
  }},"bark-supervisor").start();
 }
 private void showForeground(){
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("server","Bark 服务状态",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"server").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Bark 本机服务运行中").setContentText("短信通过本机服务推送至 iPhone").setContentIntent(pi).build());
 }
 public int onStartCommand(Intent i,int f,int id){
  if(i!=null&&i.getBooleanExtra("monitor_recovery",false)&&!getSharedPreferences("server",0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
  showForeground();getSharedPreferences("server",0).edit().putBoolean("enabled",true).commit();return START_STICKY;
 }
 public synchronized void onDestroy(){active=false;if(child!=null)child.destroy();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}
