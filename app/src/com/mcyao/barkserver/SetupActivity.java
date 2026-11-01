package com.mcyao.barkserver;
import android.app.*;import android.content.*;import android.content.pm.*;import android.os.*;import android.widget.*;import android.net.Uri;
import java.io.*;import java.util.*;import java.util.concurrent.TimeUnit;

public class SetupActivity extends Activity {
 static final String SMS="cn.ppps.forwarder", SELF="com.mcyao.barkserver";
 static final String PREF="/data/user_de/0/"+SMS+"/shared_prefs/"+SMS+".xml";
 TextView status; Handler handler=new Handler(); boolean busy;
 final ArrayList<Button> actions=new ArrayList<>();
 String[] suPrefix;
 void text(LinearLayout l,String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(0,12,0,12);l.addView(v);}
 void button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());l.addView(b);actions.add(b);}
 public void onCreate(Bundle b){super.onCreate(b);ScrollView sc=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setPadding(30,30,30,30);l.setOrientation(1);sc.addView(l);setContentView(sc);
  text(l,"首次配置与一键启用",24);
  text(l,"适用于已 Root 的 ARM64 安卓手机。首次在 Magisk 中允许本应用，按下面三步完成配置，以后无需电脑。",16);
  button(l,"1. 安装 / 打开内置短信转发器",()->work(2));
  text(l,"已内置官方短信转发器 3.5.0。首次打开时完成它的使用确认，再返回这里。",15);
  button(l,"2. 启动 Bark，查看 iPhone 配对地址",()->{startForegroundService(new Intent(this,BarkService.class));startActivity(new Intent(this,MainActivity.class));});
  text(l,"iPhone 与安卓连接同一 Wi-Fi，在 iPhone Bark 中添加首页显示的服务器地址。每次全新安装都会生成独立地址。",15);
  button(l,"3. 一键启用全部短信转发（Root）",()->work(3));
  button(l,"4. 启用可靠短信投递（Root）",()->work(4));
  button(l,"短信编号回退：重新对账当前收件箱",()->new AlertDialog.Builder(this).setTitle("重新对账当前收件箱？").setMessage("仅用于短信数据库重建或编号回退后的恢复。会重新投递当前收件箱的所有短信，可能重复提醒；已进入队列的通知继续保留。").setNegativeButton("取消",null).setPositiveButton("重新对账",(d,w)->{try{ReliableSmsSync.reconcileRebuiltInbox(this);status.setText("已保存新的同步位置，将重新对账当前收件箱");}catch(Exception e){status.setText(e.getMessage());}}).show());
  text(l,"从本次启用起，助手按系统短信编号对账并持久保存待投递通知，断网和进程重启后继续发送；不会推送启用前的短信。仅停用本助手创建的全部短信规则，其它规则保留。切换时第三方在途请求仍可能重复，请在空闲时启用。",15);
  text(l,"自动创建或更新本机 Bark 通道和全部 SIM 的短信规则，开启短信转发，修复权限并设置失败重试。保留已有其它通道和规则；已有规则仍可能同时发送。",15);
  status=new TextView(this);status.setTextIsSelectable(true);l.addView(status);
  button(l,"检查当前配置（Root）",()->work(false));
  button(l,"一键修复权限、后台与重试（Root）",()->work(true));
  text(l,"一键修复：授予短信接收/读取与手机状态权限；小米通知类短信和自启动操作设为允许；两个应用允许后台运行并加入电池优化白名单；重试设为 5 次、递增间隔 5 秒、单次超时 20 秒。会短暂重启短信转发器并备份原配置，仅支持当前验证过的 3.5.0 版本。",15);
  text(l,"小米系统设置",20);
  text(l,"系统可能重新覆盖 Root 设置。请在下方权限页面确认“读取短信与彩信”和“通知类短信”均为始终允许；在两个应用的信息页开启自启动，并把省电策略设为无限制。",16);
  button(l,"短信转发器：小米短信权限",()->openMiui());
  button(l,"短信转发器：自启动 / 省电设置",()->details(SMS));
  button(l,"Bark：自启动 / 省电设置",()->details(SELF));
  button(l,"Bark：申请忽略电池优化",()->open(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+SELF)),SELF));
  button(l,"短信转发器：手动设置失败重试",()->launchSms());
  text(l,"没有 Root 时，使用上面的系统设置入口授权，并在短信转发器 → 通用设置 → 请求重试机制填写 5 / 5 / 20。修复后请发送真实短信验证。断网期间有限次数重试，不能保证无限期补发。",15);
  status.setText(basic()+getSharedPreferences("setup",0).getString("lastReport",""));
 }
 String basic(){boolean installed;try{getPackageManager().getPackageInfo(SMS,0);installed=true;}catch(Exception e){installed=false;}
  return "短信转发器："+(installed?"已安装":"未安装，请先安装官方应用")+"\nBark 电池优化："+(((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(SELF)?"已豁免":"待设置")+"\nRoot 检查可读取实际短信拦截与后台状态。\n";
 }
 void open(Intent i,String pkg){try{startActivity(i);}catch(Exception e){details(pkg);}}
 void details(String pkg){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)));}catch(Exception e){Toast.makeText(this,"请在系统设置中打开对应应用信息",1).show();}}
 void openMiui(){Intent i=new Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter").putExtra("extra_pkgname",SMS);open(i,SMS);}
 void launchSms(){Intent i=getPackageManager().getLaunchIntentForPackage(SMS);if(i==null)Toast.makeText(this,"请先安装短信转发器",1).show();else startActivity(i);}
 static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 static class Result {int code;String out;Result(int c,String s){code=c;out=s;}}
 Result root(String script)throws Exception {RootShell.Result r=RootShell.run(script);return new Result(r.code,r.out);}
 String inspect() throws Exception {
  StringBuilder s=new StringBuilder();
  for(String pkg:new String[]{SMS,SELF}){
   Result r=root("cmd appops get "+pkg);s.append(pkg.equals(SMS)?"短信转发器\n":"\n短信推送助手\n");
   if(pkg.equals(SMS)){s.append(mode(r.out,"READ_SMS","读取短信"));s.append(mode(r.out,"RECEIVE_SMS","接收短信"));s.append(mode(r.out,"MIUIOP(10018)","小米通知类短信"));}
   s.append(mode(r.out,"MIUIOP(10008)","小米自启动"));s.append(mode(r.out,"RUN_ANY_IN_BACKGROUND","后台运行"));
   boolean exempt=((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(pkg);
   s.append("电池优化豁免：").append(exempt?"已开启":"未开启").append("\n");
  }
  Result prefs=root("grep -oE '<int name=\"request_(retry_times|delay_time|timeout)\" value=\"[0-9]+\"[^>]*>' "+PREF);
  s.append("\n失败重试\n");String[] names={"retry_times","delay_time","timeout"},labels={"最多重试次数","递增间隔（秒）","单次超时（秒）"};
  for(int i=0;i<names.length;i++){java.util.regex.Matcher m=java.util.regex.Pattern.compile("name=\"request_"+names[i]+"\" value=\"([0-9]+)\"").matcher(prefs.out);s.append(labels[i]).append("：").append(m.find()?m.group(1):"未确认，请手动检查").append("\n");}
  return s.toString();
 }
 static String mode(String output,String op,String label){boolean allowed=false,blocked=false;for(String line:output.split("\n")){String v=line.trim();if(v.startsWith(op+":")||v.startsWith("Uid mode: "+op+":")){if(v.contains(": allow"))allowed=true;else blocked=true;}}return label+"："+(blocked?"需要设置为允许":allowed?"允许":"未确认，请在系统设置核对")+"\n";}
 void work(boolean repair){work(repair?1:0);}
 void work(int operation){if(busy)return;busy=true;for(Button b:actions)b.setEnabled(false);status.setText("正在处理，请留意 Magisk 授权弹窗…");
  new Thread(()->{String result;try{
   Result auth=root("id -u");if(auth.code!=0||!auth.out.trim().equals("0"))throw new IOException("未获得 Root 权限。请使用下方系统设置入口，或在 Magisk 中授权后重试。");
   if(operation==2)result=prepare();
   else {getPackageManager().getPackageInfo(SMS,0);result=operation==4?enableReliable():operation==3?provision():operation==1?repair():"当前配置：\n";result+="\n"+inspect();}
  }catch(Exception e){result="未完成："+e.getMessage()+"\n请使用下方系统设置入口检查。";}
  final String report=result;getSharedPreferences("setup",0).edit().putString("lastReport",report).apply();handler.post(()->{if(isDestroyed())return;busy=false;for(Button b:actions)b.setEnabled(true);status.setText(report);});
  }).start();
 }
 String prepare()throws Exception{
  if(android.os.Process.myUid()/100000!=0)throw new IOException("首次配置目前仅支持手机主用户");
  boolean installed;try{getPackageManager().getPackageInfo(SMS,0);installed=true;}catch(Exception e){installed=false;}
  if(!installed){
   File asset=new File(getFilesDir(),"SmsForwarder-install.apk");
   try{
    java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
    try(InputStream in=getAssets().open("SmsForwarder-official.apk");FileOutputStream out=new FileOutputStream(asset)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){digest.update(b,0,n);out.write(b,0,n);}}
    StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
    if(!hash.toString().equals("726918cc6132728ffcadf36e09cc39213875c63dfabdb50ade3f47854364f319"))throw new IOException("内置安装包校验失败");
    String tmp="/data/local/tmp/bark-sms-"+java.util.UUID.randomUUID().toString()+".apk";
    Result install=root("set -e\ntrap 'rm -f "+tmp+"' EXIT\ncp "+quote(asset.getPath())+" "+tmp+"\nchmod 644 "+tmp+"\npm install "+tmp);
    if(install.code!=0||!install.out.contains("Success"))throw new IOException("安装短信转发器失败，请检查系统安装提示");
   }finally{asset.delete();}
  }
  startForegroundService(new Intent(this,BarkService.class));
  root("am start -W -n "+SMS+"/.activity.SplashActivity");
  return "短信转发器已准备好。请完成它的首次使用确认，然后回到 Bark 绑定 iPhone，最后点击第 3 步。";
 }
 String provision()throws Exception{
  if(ReliableSmsSync.prefs(this).getBoolean("enabled",false))return "可靠短信投递已启用，保留助手对账通道，不重复启用第三方规则。";
  if(android.os.Process.myUid()/100000!=0)throw new IOException("首次配置目前仅支持手机主用户");
  File keyFile=new File(getFilesDir(),"device-key");
  if(!keyFile.isFile())throw new IOException("请先完成第 2 步：在 iPhone Bark 添加此服务器");
  String key=new String(java.nio.file.Files.readAllBytes(keyFile.toPath()),"UTF-8").trim();
  if(!key.matches("[A-Za-z0-9]{10,100}"))throw new IOException("绑定信息异常，请重新注册 iPhone");
  Result prefs=root("cat "+PREF);if(prefs.code!=0)throw new IOException("请先完成第 1 步，打开短信转发器初始化");
  RetryPreferences.update(prefs.out,true); // Validate first-run consent before any changes.
  repair();
  if(root("am force-stop "+SMS).code!=0)throw new IOException("无法暂停短信转发器");
  File cfg=new File(getFilesDir(),"provision-binding.stage"),stage=new File(getFilesDir(),"provision-prefs.stage");
  try{
   prefs=root("cat "+PREF);if(prefs.code!=0)throw new IOException("无法读取短信转发配置");
   String updated=RetryPreferences.update(prefs.out,true);
   try(FileOutputStream out=new FileOutputStream(stage)){out.write(updated.getBytes("UTF-8"));}
   org.json.JSONObject json=new org.json.JSONObject();json.put("prefix",BarkService.prefix(this));json.put("key",key);
   try(FileOutputStream out=new FileOutputStream(cfg)){out.write(json.toString().getBytes("UTF-8"));}
   String db=ProvisionRunner.DB,backup=db+".bark-"+System.currentTimeMillis();
   String cmd="set -e\ntest -f "+db+"\nowner=$(stat -c '%u:%g' "+db+")\n"+
    "for suffix in '' -wal -shm; do if [ -f "+db+"\"$suffix\" ]; then cp -p "+db+"\"$suffix\" "+backup+"\"$suffix\"; fi; done\n"+
    "cp -p "+PREF+" "+PREF+".bark-enable-backup\n"+
    "trap 'for suffix in \"\" -wal -shm; do if [ -f "+db+"\"$suffix\" ]; then chown \"$owner\" "+db+"\"$suffix\"; restorecon "+db+"\"$suffix\"; fi; done' EXIT\n"+
    "CLASSPATH="+quote(getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin com.mcyao.barkserver.ProvisionRunner "+quote(cfg.getPath())+"\n"+
    "cp -p "+PREF+" "+PREF+".bark-new\ncat "+quote(stage.getPath())+" > "+PREF+".bark-new\nrestorecon "+PREF+".bark-new\nmv "+PREF+".bark-new "+PREF+"\nrestorecon "+PREF+"\n";
   Result applied=root(cmd);if(applied.code!=0||!applied.out.contains("PROVISION_OK"))throw new IOException("启用未完成，已保存原配置备份。请检查短信转发器版本是否为 3.5.0");
  }finally{cfg.delete();stage.delete();root("am start -W -n "+SMS+"/.activity.MainActivity\nam start -W -n "+SELF+"/.SetupActivity -f 0x20000");}
  return "全部短信转发已启用：所有号码、全部 SIM → 当前绑定 iPhone。\n通道和规则已保存，可重复点击更新绑定，不会重复创建本应用的规则。请发一条真实短信验证。\n";
 }
 String enableReliable()throws Exception{
  ReliableSmsSync.enableWithRoot(this);
  startForegroundService(new Intent(this,BarkService.class));
  return "可靠短信投递已启用。之后的新短信按系统编号补齐，断网时保存在本机队列，恢复网络后继续投递。";
 }
 String repair() throws Exception {
  PackageInfo pi=getPackageManager().getPackageInfo(SMS,0);
  if(pi.versionName==null||!pi.versionName.startsWith("3.5.0"))throw new IOException("当前短信转发器版本未经验证，请手动设置重试参数");
  StringBuilder s=new StringBuilder("set -e\n");
  for(String permission:new String[]{"RECEIVE_SMS","READ_SMS","READ_PHONE_STATE"})s.append("pm grant ").append(SMS).append(" android.permission.").append(permission).append("\n");
  for(String op:new String[]{"RECEIVE_SMS","READ_SMS","READ_PHONE_STATE"}){s.append("cmd appops set --uid ").append(SMS).append(" ").append(op).append(" allow\n");s.append("cmd appops set ").append(SMS).append(" ").append(op).append(" allow\n");}
  boolean miui;try{getPackageManager().getPackageInfo("com.miui.securitycenter",0);miui=true;}catch(Exception e){miui=false;}
  if(miui)s.append("cmd appops set ").append(SMS).append(" 10018 allow\n");
  for(String pkg:new String[]{SMS,SELF}){if(miui)s.append("cmd appops set ").append(pkg).append(" 10008 allow\n");
   s.append("cmd appops set ").append(pkg).append(" RUN_ANY_IN_BACKGROUND allow\ncmd appops set ").append(pkg).append(" RUN_IN_BACKGROUND allow\ndumpsys deviceidle whitelist +").append(pkg).append("\n");}
  Result permissions=root(s.toString());if(permissions.code!=0)throw new IOException("部分权限设置失败，请检查配置并使用系统设置入口");
  // Stop first so the target app cannot overwrite the preferences with a cached copy.
  Result stop=root("am force-stop "+SMS);if(stop.code!=0)throw new IOException("无法暂停短信转发器，未修改重试配置");
  File stage=new File(getFilesDir(),"retry-prefs.stage");
  try {
   Result before=root("cat "+PREF);if(before.code!=0)throw new IOException("无法读取短信转发器配置："+before.out.trim());
   String updated=RetryPreferences.update(before.out);
   try(FileOutputStream out=new FileOutputStream(stage)){out.write(updated.getBytes("UTF-8"));}
   String backup=PREF+".bark-backup-"+System.currentTimeMillis();
   String script="set -e\ntest -f "+PREF+"\ncp -p "+PREF+" "+backup+"\ncp -p "+PREF+" "+PREF+".bark-new\ncat "+quote(stage.getPath())+" > "+PREF+".bark-new\nrestorecon "+PREF+".bark-new\nmv "+PREF+".bark-new "+PREF+"\nrestorecon "+PREF+"\n";
   Result write=root(script);if(write.code!=0)throw new IOException("重试配置写入失败；原配置备份保存在短信转发器私有目录");
   Result after=root("cat "+PREF);if(after.code!=0||!after.out.contains("request_retry_times"))throw new IOException("无法核对重试配置");
  } finally {stage.delete();root("am start -W -n "+SMS+"/.activity.MainActivity\nam start -W -n "+SELF+"/.SetupActivity -f 0x20000");}
  getSharedPreferences("server",0).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,BarkService.class));
  return "修复操作已执行，重试设置为 5 次 / 5 秒 / 20 秒，已保存原配置备份。\n请核对下方状态，并在小米系统页面确认自启动、通知类短信和省电无限制。最后发一条真实短信验证。";
 }
}
