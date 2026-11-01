package com.mcyao.barkserver;
import android.content.Context;import java.io.*;
final class MonitorBridge {
 static final String BASE="/data/adb/sms-push-assistant",HOOK="/data/adb/service.d/97-sms-push-assistant.sh";
 static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
 static String run(String s)throws Exception{RootShell.Result r=RootShell.run(s);if(r.code!=0)throw new IOException("操作失败，请检查 Root 授权与 Magisk BusyBox");return r.out;}
 static synchronized void refreshIfEnabled(Context c)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences("monitor",0);
  if(p.getBoolean("enabled",false))configure(c,true,p.getInt("minutes",5));
 }
 static synchronized void configure(Context c,boolean enabled,int minutes)throws Exception{
  MonitorPolicy.minutes(Integer.toString(minutes));
  if(android.os.Process.myUid()/100000!=0)throw new IOException("自动检查仅支持手机主用户");
  File dir=new File(c.getFilesDir(),"monitor-assets");dir.mkdirs();
  for(String name:new String[]{"monitor.sh","monitor-boot.sh","monitor-update.sh"})try(InputStream in=c.getAssets().open(name);FileOutputStream out=new FileOutputStream(new File(dir,name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  String command="set -eu\numask 077\n"+
   "[ ! -L "+BASE+" ]\nif [ -e "+BASE+" ] && [ ! -f "+BASE+"/.owned ]; then exit 21; fi\nmkdir -p "+BASE+"\nchmod 700 "+BASE+"\ntouch "+BASE+"/.owned\n"+
   "[ ! -L "+BASE+"/monitor-update.sh ]\ncp "+q(dir.getPath()+"/monitor-update.sh")+" "+BASE+"/monitor-update.sh.new.$$\nchmod 700 "+BASE+"/monitor-update.sh.new.$$\nmv -f "+BASE+"/monitor-update.sh.new.$$ "+BASE+"/monitor-update.sh\n"+
   "/system/bin/sh "+BASE+"/monitor-update.sh "+q(dir.getPath())+" "+(enabled?"1":"0")+" "+minutes+"\n";
  run(command);c.getSharedPreferences("monitor",0).edit().putBoolean("enabled",enabled).putInt("minutes",minutes).commit();
 }
 static String status()throws Exception{return run("if [ -f "+BASE+"/enabled ]; then echo enabled; else echo disabled; fi\ncat "+BASE+"/minutes 2>/dev/null || echo 5\ncat "+BASE+"/status 2>/dev/null || true");}
 static void now()throws Exception{run("test -f "+BASE+"/enabled && /system/bin/sh "+BASE+"/monitor.sh once");}
}
