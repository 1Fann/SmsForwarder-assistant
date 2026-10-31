package com.mcyao.barkserver;
public class MonitorPolicyTest {
 public static void main(String[] args)throws Exception{
  for(String input:new String[]{"1","5","1440"})if(MonitorPolicy.minutes(input)!=Integer.parseInt(input))throw new AssertionError();
  for(String input:new String[]{null,"","0","1441","-1","1.5","5;reboot","999999999999999"}){
   boolean failed=false;try{MonitorPolicy.minutes(input);}catch(java.io.IOException e){failed=true;}if(!failed)throw new AssertionError("accepted invalid interval");
  }
  java.util.regex.Pattern owned=java.util.regex.Pattern.compile(MonitorPolicy.OWNED_WORKER_PATTERN);
  for(String line:new String[]{
   "/data/adb/magisk/busybox ash /data/adb/service.d/97-sms-push-assistant.sh --ash-worker ",
   "/data/adb/magisk/busybox ash /data/adb/sms-push-assistant/monitor-boot.sh --ash-worker ",
   "/system/bin/sh /data/adb/sms-push-assistant/monitor-boot.sh --worker ",
   "/data/adb/magisk/busybox ash /data/adb/sms-push-assistant/monitor.sh --loop "
  })if(!owned.matcher(line).find())throw new AssertionError("owned worker not recognized: "+line);
  for(String line:new String[]{
   "/system/bin/sh /data/local/tmp/sms-push-assistant/monitor-boot.sh --ash-worker ",
   "/system/bin/sh /data/adb/serviceXd/97-sms-push-assistant.sh --ash-worker ",
   "/system/bin/sh /data/adb/service.d/97-sms-push-assistant.sh.backup --ash-worker ",
   "/system/bin/sh /data/adb/sms-push-assistant/monitor-boot.sh --ash-worker-other ",
   "/system/bin/sh /data/adb/sms-push-assistant/monitor.sh --looping ",
   "/system/bin/sh /data/adb/sms-push-assistant/monitor.sh ignored --loop ",
   "/system/bin/sh /other/data/adb/sms-push-assistant/monitor.sh --loop ",
   "/system/bin/sh /data/adb/sms-push-assistant/monitor-boot.sh --acquire "
  })if(owned.matcher(line).find())throw new AssertionError("unowned or partial match: "+line);
  System.out.println("PASS: interval bounds, malformed inputs, owned worker modes and token/path boundaries");
 }
}
