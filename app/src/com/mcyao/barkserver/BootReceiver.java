package com.mcyao.barkserver;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(i==null)return;
 if((Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))&&c.getSharedPreferences("server",0).getBoolean("enabled",false))try{c.startForegroundService(new Intent(c,BarkService.class).putExtra("monitor_recovery",true));}catch(RuntimeException ignored){/* Root supervisor retries after system startup restrictions clear. */}
 if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){final PendingResult pending=goAsync();new Thread(()->{try{MonitorBridge.refreshIfEnabled(c);}catch(Exception ignored){}finally{pending.finish();}}).start();}
 }
}
