package com.mcyao.barkserver;
import android.app.*;import android.os.*;import android.widget.*;import android.text.InputType;import java.util.*;
public class MonitorActivity extends Activity {
 EditText interval;TextView status;Switch toggle;Button save,now,refresh;boolean rendering,busy;Handler h=new Handler();
 void text(LinearLayout l,String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(0,16,0,16);l.addView(v);}
 Button button(LinearLayout l,String label,Runnable r){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->r.run());l.addView(b);return b;}
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout l=new LinearLayout(this);l.setPadding(30,32,30,32);l.setOrientation(1);ScrollView sc=new ScrollView(this);sc.addView(l);setContentView(sc);
  text(l,"自动检查与恢复",24);text(l,"Bark 服务需在后台持续运行。独立 Root 任务约每 10 秒检查 Bark 和短信转发器后台服务，按下方间隔检查 Bark 接口、短信接收及通知类短信权限；异常时自动恢复。清理界面后仍有效，开机应用数据解锁后恢复。",16);
  toggle=new Switch(this);toggle.setText("开启自动检查与恢复");l.addView(toggle);
  toggle.setOnCheckedChangeListener((v,on)->{if(rendering)return;rendering=true;toggle.setChecked(!on);rendering=false;configure(on);});
  text(l,"完整检查间隔（分钟，1–1440）",16);interval=new EditText(this);interval.setInputType(InputType.TYPE_CLASS_NUMBER);interval.setSingleLine(true);interval.setText("5");l.addView(interval);
  save=button(l,"保存检查间隔",()->configure(toggle.isChecked()));now=button(l,"立即检查并恢复",()->perform(1));refresh=button(l,"刷新上次检查结果",()->perform(0));
  status=new TextView(this);l.addView(status);text(l,"手动停止 Bark 服务或关闭短信转发总开关后，对应服务不会自动拉起。关闭检测不停止已有服务。此功能检查安卓本机服务，不代表苹果通知必达。深度休眠可能延后检查，恢复前可能短暂中断。卸载前请关闭自动检查并停止服务。",15);perform(0);
 }
 void controls(boolean value){busy=value;toggle.setEnabled(!value);interval.setEnabled(!value);save.setEnabled(!value);now.setEnabled(!value);refresh.setEnabled(!value);}
 void configure(boolean enabled){if(busy)return;final int minutes;try{minutes=MonitorPolicy.minutes(interval.getText().toString().trim());}catch(Exception e){interval.setError(e.getMessage());return;}controls(true);status.setText("正在保存，请留意 Root 授权…");new Thread(()->{try{MonitorBridge.configure(this,enabled,minutes);show(MonitorBridge.status(),enabled?"已开启自动恢复":"已关闭检查，现有服务继续运行");}catch(Exception e){error(e);}}).start();}
 void perform(int action){if(busy)return;controls(true);new Thread(()->{try{if(action==1)MonitorBridge.now();show(MonitorBridge.status(),"");}catch(Exception e){error(e);}}).start();}
 void error(Exception e){h.post(()->{if(isDestroyed())return;controls(false);status.setText(e.getMessage()+"\n立即检查需要先开启自动检查。");});}
 void show(String raw,String message){h.post(()->{if(isDestroyed())return;controls(false);String[] a=raw.trim().split("\n");boolean enabled=a.length>0&&a[0].trim().equals("enabled");rendering=true;toggle.setChecked(enabled);rendering=false;interval.setText(a.length>1?a[1].trim():"5");String s=message+"\n"+(enabled?"自动检查已开启":"自动检查已关闭");if(a.length>3){try{s+="\n上次检查："+java.text.DateFormat.getDateTimeInstance().format(new Date(Long.parseLong(a[2].trim())*1000));}catch(Exception ignored){}s+="\n"+a[3];}status.setText(s);});}
}
