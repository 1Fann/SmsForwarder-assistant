package com.mcyao.barkserver;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if((Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))&&c.getSharedPreferences("server",0).getBoolean("enabled",false))c.startForegroundService(new Intent(c,BarkService.class));
 if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())&&c.getSharedPreferences("monitor",0).getBoolean("enabled",false)){final PendingResult pending=goAsync();new Thread(()->{try{MonitorBridge.configure(c,true,c.getSharedPreferences("monitor",0).getInt("minutes",5));}catch(Exception ignored){}finally{pending.finish();}}).start();}
 }
}
