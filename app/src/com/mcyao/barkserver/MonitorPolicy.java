package com.mcyao.barkserver;
import java.io.IOException;
final class MonitorPolicy {
 // /proc/cmdline is normalized from NUL separators to literal spaces before grep.
 // Match the owned absolute script path and a complete mode argument, not a suffix.
 static final String OWNED_WORKER_PATTERN="(^| )/data/adb/(sms-push-assistant/monitor(-boot)?\\.sh|service\\.d/97-sms-push-assistant\\.sh) +--(ash-worker|worker|loop)( |$)";
 static int minutes(String input)throws IOException{if(input==null||!input.matches("[0-9]{1,4}"))throw new IOException("请输入 1–1440 分钟");int n=Integer.parseInt(input);if(n<1||n>1440)throw new IOException("请输入 1–1440 分钟");return n;}
}
