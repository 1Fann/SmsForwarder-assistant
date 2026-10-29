package com.mcyao.barkserver;
public class MonitorPolicyTest {
 public static void main(String[] args)throws Exception{
  for(String input:new String[]{"1","5","1440"})if(MonitorPolicy.minutes(input)!=Integer.parseInt(input))throw new AssertionError();
  for(String input:new String[]{null,"","0","1441","-1","1.5","5;reboot","999999999999999"}){
   boolean failed=false;try{MonitorPolicy.minutes(input);}catch(java.io.IOException e){failed=true;}if(!failed)throw new AssertionError("accepted invalid interval");
  }System.out.println("PASS: interval bounds and malformed inputs");
 }
}
