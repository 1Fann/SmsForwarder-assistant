package com.mcyao.barkserver;
import android.app.*;import android.os.*;import android.content.*;import android.widget.*;import java.net.*;import java.io.*;import java.util.*;
public class MainActivity extends Activity {
 TextView state,urls; Handler h=new Handler();
 String base(){return "http://127.0.0.1:18080/"+BarkService.prefix(this);}
 String key(){try{return new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(),"device-key").toPath())).trim();}catch(Exception e){return "";}}
 void button(LinearLayout l,String label,Runnable r){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->r.run());l.addView(b);}
 public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(30,36,30,30);scroll.addView(l);setContentView(scroll);
 TextView title=new TextView(this);title.setText("短信推送助手");title.setTextSize(25);l.addView(title);
 state=new TextView(this);l.addView(state);urls=new TextView(this);urls.setTextIsSelectable(true);l.addView(urls);
 button(l,"首次配置 / 一键启用 / 权限修复",()->startActivity(new Intent(this,SetupActivity.class)));
 button(l,"自动检查与恢复 · 定时间隔",()->startActivity(new Intent(this,MonitorActivity.class)));
 button(l,"启动服务",()->{startForegroundService(new Intent(this,BarkService.class));refresh();});
 button(l,"刷新状态 / 配对地址",()->refresh());
 button(l,"复制短信转发地址",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bark",base()+"/"+key()));Toast.makeText(this,"已复制本机地址",0).show();});
 button(l,"发送测试提醒",()->{if(key().isEmpty()){state.setText("请先在 iPhone Bark 添加此服务器");return;}new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(base()+"/"+key()+"/"+java.net.URLEncoder.encode("安卓 Bark 测试","UTF-8")+"/"+java.net.URLEncoder.encode("本机服务器已连接苹果推送","UTF-8")).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);int code=c.getResponseCode();c.disconnect();h.post(()->state.setText("测试接口 HTTP "+code+"，请检查 iPhone"));}catch(Exception e){h.post(()->state.setText("测试失败："+e.getMessage()));}}).start();});
 button(l,"允许后台持续运行",()->startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName()))));
 button(l,"停止服务",()->{getSharedPreferences("server",0).edit().putBoolean("enabled",false).commit();stopService(new Intent(this,BarkService.class));state.setText("服务已停止，自动检查不会重新启动它");});
 TextView help=new TextView(this);help.setText("\n首次配对：iPhone 与安卓连接同一 Wi-Fi，在 Bark 的服务器设置添加上方局域网地址。绑定后 iPhone 可使用蜂窝网络。\n\n短信转发器：Bark 发送通道使用本机转发地址；短信规则选择全部短信和全部 SIM。\n\n服务包含随机访问路径；不要分享地址。当前仅监听 IPv4，请勿将明文端口映射到公网。如需公网访问，请先配置 HTTPS。\n\n请在系统中允许本应用和短信转发器自启动、后台运行，并设置省电策略为无限制。\n\n内核：Finb/bark-server v2.3.9（MIT），包含 Android 适配。短信仍经 Apple APNs 发送。\n");l.addView(help);refresh();}
 void refresh(){StringBuilder s=new StringBuilder("\n在 iPhone Bark 添加服务器：\n");try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&!a.isLoopbackAddress())s.append("http://").append(a.getHostAddress()).append(":18080/").append(BarkService.prefix(this)).append("\n");}catch(Exception ignored){}s.append("\n短信转发器的 Bark 地址：\n").append(base()).append("/").append(key()).append("\n");urls.setText(s.toString());
 new Thread(()->{String status;try{HttpURLConnection c=(HttpURLConnection)new URL(base()+"/ping").openConnection();c.setConnectTimeout(2000);c.setReadTimeout(2000);status=c.getResponseCode()==200?"服务正常":"服务异常";c.disconnect();}catch(Exception e){status="服务未启动或正在启动";}final String f=status+(key().isEmpty()?" · 尚未绑定 iPhone":" · 已绑定 iPhone");h.post(()->state.setText(f));}).start();}
}
