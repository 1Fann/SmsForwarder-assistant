package com.mcyao.barkserver;
import android.content.Context;import java.io.*;
final class MonitorBridge {
 static final String BASE="/data/adb/sms-push-assistant",HOOK="/data/adb/service.d/97-sms-push-assistant.sh";
 static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
 static String run(String s)throws Exception{RootShell.Result r=RootShell.run(s);if(r.code!=0)throw new IOException("操作失败，请检查 Root 授权与 Magisk BusyBox");return r.out;}
 static void configure(Context c,boolean enabled,int minutes)throws Exception{
  MonitorPolicy.minutes(Integer.toString(minutes));
  if(android.os.Process.myUid()/100000!=0)throw new IOException("自动检查仅支持手机主用户");
  File dir=new File(c.getFilesDir(),"monitor-assets");dir.mkdirs();
  for(String name:new String[]{"monitor.sh","monitor-boot.sh"})try(InputStream in=c.getAssets().open(name);FileOutputStream out=new FileOutputStream(new File(dir,name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  String command="set -e\numask 077\nBB=\nfor b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do if [ -x \"$b\" ]; then BB=$b; break; fi; done\n[ -n \"$BB\" ]\n"+
   "[ ! -L "+BASE+" ]\nif [ -e "+BASE+" ] && [ ! -f "+BASE+"/.owned ]; then exit 21; fi\nmkdir -p "+BASE+"\nchmod 700 "+BASE+"\ntouch "+BASE+"/.owned\n"+
   "p=$(cat "+BASE+"/loop.pid 2>/dev/null || true)\ncase $p in ''|*[!0-9]*) ;; *) if [ -r /proc/$p/cmdline ] && tr '\\000' ' ' </proc/$p/cmdline | grep -qF '"+BASE+"/monitor.sh --loop'; then kill -TERM $p; n=0; while kill -0 $p 2>/dev/null && [ $n -lt 10 ]; do sleep 1; n=$((n+1)); done; fi;; esac\n"+
   "for name in monitor.sh monitor-boot.sh; do [ ! -L "+BASE+"/\"$name\" ]; cp "+q(dir.getPath())+"/\"$name\" "+BASE+"/\"$name\".new; chmod 700 "+BASE+"/\"$name\".new; mv -f "+BASE+"/\"$name\".new "+BASE+"/\"$name\"; done\n"+
   "printf '%s\\n' "+minutes+" > "+BASE+"/minutes\n";
  if(enabled){command+="mkdir -p /data/adb/service.d\nif [ -e "+HOOK+" ] && ! head -n 3 "+HOOK+" | grep -q sms-push-assistant-owned; then exit 22; fi\ncp "+BASE+"/monitor-boot.sh "+HOOK+"\nchmod 700 "+HOOK+"\ntouch "+BASE+"/enabled\nsleep 1\n\"$BB\" setsid /system/bin/sh "+BASE+"/monitor.sh loop </dev/null >/dev/null 2>&1 3>&- 4>&- 5>&- 6>&- 7>&- 8>&- 9>&- &\n";}
  else{command+="rm -f "+BASE+"/enabled\nif [ -f "+HOOK+" ] && head -n 3 "+HOOK+" | grep -q sms-push-assistant-owned; then rm -f "+HOOK+"; fi\n";}
  run(command);c.getSharedPreferences("monitor",0).edit().putBoolean("enabled",enabled).putInt("minutes",minutes).commit();
 }
 static String status()throws Exception{return run("if [ -f "+BASE+"/enabled ]; then echo enabled; else echo disabled; fi\ncat "+BASE+"/minutes 2>/dev/null || echo 5\ncat "+BASE+"/status 2>/dev/null || true");}
 static void now()throws Exception{run("test -f "+BASE+"/enabled && /system/bin/sh "+BASE+"/monitor.sh once");}
}
