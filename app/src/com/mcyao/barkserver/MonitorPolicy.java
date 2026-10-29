package com.mcyao.barkserver;
import java.io.IOException;
final class MonitorPolicy {
 static int minutes(String input)throws IOException{if(input==null||!input.matches("[0-9]{1,4}"))throw new IOException("请输入 1–1440 分钟");int n=Integer.parseInt(input);if(n<1||n>1440)throw new IOException("请输入 1–1440 分钟");return n;}
}
