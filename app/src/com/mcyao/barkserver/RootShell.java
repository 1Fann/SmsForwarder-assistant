package com.mcyao.barkserver;
import java.io.*;import java.util.*;import java.util.concurrent.TimeUnit;
final class RootShell {
 static String[] suPrefix;
 static class Result {int code;String out;Result(int c,String s){code=c;out=s;}}
 static Result run(String script) throws Exception {
  if(suPrefix==null){
   java.lang.Process help=new ProcessBuilder("su","--help").redirectErrorStream(true).start();
   ByteArrayOutputStream hb=new ByteArrayOutputStream();byte[] block=new byte[2048];int count;
   while((count=help.getInputStream().read(block))!=-1)hb.write(block,0,count);
   help.waitFor(5,TimeUnit.SECONDS);String usage=hb.toString("UTF-8");
   // Android isolates app data mounts. Use the root implementation's documented global namespace option.
   suPrefix=usage.contains("--target")?new String[]{"su","-t","0"}:usage.contains("--mount-master")?new String[]{"su","--mount-master"}:new String[]{"su"};
  }
  ArrayList<String> command=new ArrayList<>(Arrays.asList(suPrefix));command.add("-c");command.add(script);
  java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  Thread reader=new Thread(()->{try{byte[] buf=new byte[2048];int n;while((n=p.getInputStream().read(buf))!=-1){if(bytes.size()<262144)bytes.write(buf,0,n);}}catch(IOException ignored){}});reader.start();
  if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();reader.join(2000);throw new IOException("Root 授权或操作超时，请在 Magisk 中检查授权后重试");}
  reader.join(2000);return new Result(p.exitValue(),bytes.toString("UTF-8"));
 }
}
