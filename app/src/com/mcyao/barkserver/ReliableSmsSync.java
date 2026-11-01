package com.mcyao.barkserver;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;

/** The provider is the source of truth. Advance only after durable acceptance. */
final class ReliableSmsSync {
 private final Context context;
 private volatile boolean running;
 private Thread worker;
 private long migrationNext, migrationDelay=5000;
 ReliableSmsSync(Context context){this.context=context;}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("reliable_sms",0);}
 static long maximum(Context c){
  try(Cursor rows=c.getContentResolver().query(Uri.parse("content://sms"),new String[]{"_id"},null,null,"_id DESC")){
   if(rows==null)throw new IllegalStateException("SMS provider unavailable");return rows.moveToFirst()?rows.getLong(0):0;
  }
 }
 static void prepare(Context c){
  SharedPreferences p=prefs(c);if(p.getBoolean("prepared",false))return;
  if(!p.edit().putString("generation",UUID.randomUUID().toString().replace("-","")).putLong("cursor",maximum(c)).putBoolean("prepared",true).commit())throw new IllegalStateException("Cannot save SMS sync position");
 }
 static void enable(Context c){
  SharedPreferences p=prefs(c);if(!p.getBoolean("prepared",false))throw new IllegalStateException("SMS sync position missing");
  if(!p.edit().putBoolean("enabled",true).commit())throw new IllegalStateException("Cannot enable SMS sync");
 }
 static synchronized void reconcileRebuiltInbox(Context c){
  SharedPreferences p=prefs(c);
  if(!p.getBoolean("enabled",false)||!p.getBoolean("reset_required",false))throw new IllegalStateException("无需重建短信同步位置");
  if(!p.edit().putString("generation",UUID.randomUUID().toString().replace("-","")).putLong("cursor",0)
    .putBoolean("reset_required",false).putString("status","已确认重新对账当前收件箱").commit())throw new IllegalStateException("无法保存新的同步位置");
 }
 static synchronized void enableWithRoot(Context c)throws Exception{
  if(prefs(c).getBoolean("enabled",false))return;
  if(!prefs(c).edit().putBoolean("migration_requested",true).putBoolean("migration_review_required",false).commit())throw new IOException("Cannot save migration request");
  if(android.os.Process.myUid()/100000!=0)throw new IOException("Primary user required");
  if(!new File(c.getFilesDir(),"device-key").isFile())throw new IOException("Pairing required");
  String self="com.mcyao.barkserver",sms="cn.ppps.forwarder";
  RootShell.Result grant=RootShell.run("set -e\npm grant "+self+" android.permission.READ_SMS\ncmd appops set --uid "+self+" READ_SMS allow\ncmd appops set "+self+" READ_SMS allow");
  if(grant.code!=0)throw new IOException("SMS permission unavailable");
  // Persist the boundary BEFORE pausing the old sender. Every later provider ID
  // is recoverable even if migration or the app dies before enable commits.
  prepare(c);
  try{
   if(RootShell.run("am force-stop "+sms).code!=0)throw new IOException("Cannot pause old sender");
   String database=ProvisionRunner.DB;
   String command="set -e\nowner=$(stat -c '%u:%g' "+database+")\n"+
    "trap 'for suffix in \"\" -wal -shm; do if [ -f "+database+"\"$suffix\" ]; then chown \"$owner\" "+database+"\"$suffix\"; restorecon "+database+"\"$suffix\"; fi; done' EXIT\n"+
    "CLASSPATH="+SetupActivity.quote(c.getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin com.mcyao.barkserver.ProvisionRunner --reliable "+SetupActivity.quote(BarkService.prefix(c));
   RootShell.Result result=RootShell.run(command);
   if(result.code!=0||!result.out.contains("RELIABLE_TAKEOVER_OK")){
    if(result.out.contains("PROVISION_FAILED: IllegalStateException"))prefs(c).edit().putBoolean("migration_requested",false).putBoolean("migration_review_required",true).commit();
    throw new IOException("Owned default forwarding rule unavailable; review setup");
   }
   enable(c);
  }finally{RootShell.run("am start-foreground-service -n "+sms+"/.service.ForegroundService -a START");}
 }
 synchronized void start(){
  if(running)return;running=true;
  worker=new Thread(()->{while(running){try{sync();}catch(Exception e){prefs(context).edit().putString("status","等待权限、配对或本机队列恢复").apply();}
   try{Thread.sleep(5000);}catch(InterruptedException e){break;}
  }},"sms-provider-sync");worker.start();
 }
 synchronized void stop(){running=false;if(worker!=null)worker.interrupt();}
 private void sync()throws Exception{
  SharedPreferences p=prefs(context);
  if(p.getBoolean("migration_requested",false)&&!p.getBoolean("enabled",false)&&context.getSharedPreferences("server",0).getBoolean("enabled",false)){
   long now=android.os.SystemClock.elapsedRealtime();
   if(now>=migrationNext){
    try{enableWithRoot(context);migrationDelay=5000;p.edit().putString("migration_status","enabled").commit();}
    catch(Exception e){p.edit().putString("migration_status","retrying; original sync position retained").commit();migrationNext=android.os.SystemClock.elapsedRealtime()+migrationDelay;migrationDelay=Math.min(300000,migrationDelay*2);throw e;}
   }
  }
  if(!p.getBoolean("enabled",false)||!context.getSharedPreferences("server",0).getBoolean("enabled",false))return;
  if(p.getBoolean("reset_required",false)){p.edit().putString("status","短信编号回退，需要确认重新对账当前收件箱").apply();return;}
  String generation=p.getString("generation","");long cursor=p.getLong("cursor",-1);
  if(!generation.matches("[a-f0-9]{32}")||cursor<0)throw new IOException("Invalid sync position");
  // Never silently replay history after a provider database reset.
  if(maximum(context)<cursor){if(!p.edit().putBoolean("reset_required",true).putString("status","短信编号回退，需要确认重新对账当前收件箱").commit())throw new IOException("Cannot persist provider reset warning");return;}
  String key=new String(Files.readAllBytes(new File(context.getFilesDir(),"device-key").toPath()),StandardCharsets.UTF_8).trim();
  if(!key.matches("[A-Za-z0-9]{10,100}"))throw new IOException("Pairing unavailable");
  try(Cursor rows=context.getContentResolver().query(Uri.parse("content://sms/inbox"),new String[]{"_id","address","body"},"_id > ?",new String[]{Long.toString(cursor)},"_id ASC")){
   if(rows==null)throw new IOException("SMS provider unavailable");
   for(int n=0;n<32&&running&&rows.moveToNext();n++){
    long id=rows.getLong(0);
    JSONObject payload=new JSONObject().put("request_id","sms:"+generation+":"+id).put("device_key",key)
      .put("title","短信 · "+(rows.isNull(1)?"未知号码":rows.getString(1))).put("body",rows.isNull(2)?"":rows.getString(2))
      .put("group","安卓短信").put("isArchive",1);
    HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:18080/"+BarkService.prefix(context)+"/reliable-sms").openConnection();
    try{
     connection.setConnectTimeout(3000);connection.setReadTimeout(5000);connection.setRequestMethod("POST");connection.setDoOutput(true);
     connection.setRequestProperty("Content-Type","application/json");byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
     try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
     if(connection.getResponseCode()!=200)throw new IOException("Queue did not acknowledge SMS");
     if(!p.edit().putLong("cursor",id).putString("status","短信已交给持久投递队列").commit())throw new IOException("Cannot save acknowledged position");
    }finally{connection.disconnect();}
   }
  }
 }
}
