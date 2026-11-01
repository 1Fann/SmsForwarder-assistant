package com.mcyao.barkserver;
import android.database.sqlite.SQLiteDatabase;import android.database.Cursor;import android.content.ContentValues;
import org.json.JSONObject;import java.nio.file.*;
/** Runs with app_process under the explicitly authorized root session; no exported Android component. */
public final class ProvisionRunner {
 static final String DB="/data/user/0/cn.ppps.forwarder/databases/sms_forwarder.db";
 public static void main(String[] args){try{
  if(args.length==2&&args[0].equals("--reliable")){takeOver(args[1]);System.out.println("RELIABLE_TAKEOVER_OK");return;}
  if(args.length==2&&args[0].equals("--test")){test(args[1]);System.out.println("PROVISION_TEST_OK");return;}
  if(args.length!=1)throw new IllegalArgumentException("Missing configuration");
  JSONObject config=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
  String prefix=config.getString("prefix"),key=config.getString("key");
  if(!prefix.matches("[a-f0-9]{32}")||!key.matches("[A-Za-z0-9]{10,100}"))throw new IllegalArgumentException("Invalid binding");
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(DB,null,SQLiteDatabase.OPEN_READWRITE)){
   if(db.getVersion()!=21)throw new IllegalStateException("Unsupported database version");
   configure(db,"http://127.0.0.1:18080/"+prefix,key);
  }
  System.out.println("PROVISION_OK");
 }catch(Exception e){System.err.println("PROVISION_FAILED: "+e.getClass().getSimpleName());System.exit(1);}}
 static void takeOver(String prefix)throws Exception{
  if(!prefix.matches("[a-f0-9]{32}"))throw new IllegalArgumentException();
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(DB,null,SQLiteDatabase.OPEN_READWRITE)){
   if(db.getVersion()!=21)throw new IllegalStateException("Unsupported database version");
   db.beginTransaction();try{
    int matched=0;
    try(Cursor c=db.rawQuery("SELECT id,json_setting FROM Sender WHERE type=2",null)){
     while(c.moveToNext()){
      JSONObject settings=new JSONObject(c.getString(1));
      if(!settings.optString("server").startsWith("http://127.0.0.1:18080/"+prefix+"/"))continue;
      for(String name:new String[]{"icon","sound","badge","url","title","key","iv","call","autoCopy"})if(!settings.optString(name).isEmpty())throw new IllegalStateException("Customized sender requires manual migration");
      if(!settings.optString("transformation","none").equals("none")||!settings.optString("group","安卓短信").equals("安卓短信")||!settings.optString("level","active").equals("active"))throw new IllegalStateException("Customized sender requires manual migration");
      try(Cursor rules=db.rawQuery("SELECT sim_slot,silent_period_start,silent_period_end,silent_day_of_week,sender_list,sender_logic FROM Rule WHERE type='sms' AND title=? AND sender_id=? AND filed='transpond_all'",new String[]{"全部短信转发至 iPhone",Long.toString(c.getLong(0))})){
       while(rules.moveToNext())if(!"ALL".equals(rules.getString(0))||rules.getLong(1)!=0||rules.getLong(2)!=0||!"".equals(rules.getString(3))||!Long.toString(c.getLong(0)).equals(rules.getString(4))||!"ALL".equals(rules.getString(5)))throw new IllegalStateException("Customized rule requires manual migration");
      }
      ContentValues values=new ContentValues();values.put("status",0);
      matched+=db.update("Rule",values,"type='sms' AND title=? AND sender_id=? AND filed='transpond_all'",new String[]{"全部短信转发至 iPhone",Long.toString(c.getLong(0))});
     }
    }
    if(matched==0)throw new IllegalStateException("Assistant-created rule not found");
    db.setTransactionSuccessful();
   }finally{db.endTransaction();}
  }
 }
 static void configure(SQLiteDatabase db,String base,String key)throws Exception{
  db.beginTransaction();try{
   long sender=-1;String settings=null;
   try(Cursor c=db.rawQuery("SELECT id,json_setting FROM Sender WHERE type=2",null)){
    while(c.moveToNext()){try{JSONObject j=new JSONObject(c.getString(1));if(j.optString("server").startsWith(base+"/")){sender=c.getLong(0);settings=c.getString(1);break;}}catch(Exception ignored){}}
   }
   JSONObject j=settings==null?new JSONObject():new JSONObject(settings);
   j.put("server",base+"/"+key);if(settings==null){for(String n:new String[]{"icon","sound","badge","url","title","key","iv","call","autoCopy"})j.put(n,"");j.put("group","安卓短信");j.put("level","active");j.put("transformation","none");}
   ContentValues s=new ContentValues();s.put("type",2);s.put("name","本机 Bark → iPhone");s.put("json_setting",j.toString());s.put("status",1);s.put("time",System.currentTimeMillis());
   if(sender<0)sender=db.insertOrThrow("Sender",null,s);else db.update("Sender",s,"id=?",new String[]{""+sender});
   long rule=-1;
   try(Cursor c=db.rawQuery("SELECT id FROM Rule WHERE type='sms' AND title=? AND sender_id=?",new String[]{"全部短信转发至 iPhone",""+sender})){if(c.moveToFirst())rule=c.getLong(0);}
   ContentValues r=new ContentValues();r.put("type","sms");r.put("filed","transpond_all");r.put("`check`","is");r.put("value","");r.put("sender_id",sender);r.put("sim_slot","ALL");r.put("status",1);r.put("time",System.currentTimeMillis());r.put("sender_list",""+sender);r.put("sender_logic","ALL");r.put("silent_period_start",0);r.put("silent_period_end",0);r.put("silent_day_of_week","");r.put("title","全部短信转发至 iPhone");
   if(rule<0)db.insertOrThrow("Rule",null,r);else db.update("Rule",r,"id=?",new String[]{""+rule});
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 static void test(String path)throws Exception{
  if(!path.equals("/data/local/tmp/bark-provision-test.db"))throw new IllegalArgumentException();
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(path,null,SQLiteDatabase.OPEN_READWRITE)){
   long oldSender=count(db,"Sender"),oldRule=count(db,"Rule");
   configure(db,"http://127.0.0.1:18080/test-namespace","TestKeyFirst");
   configure(db,"http://127.0.0.1:18080/test-namespace","TestKeyChanged");
   if(count(db,"Sender")!=oldSender+1||count(db,"Rule")!=oldRule+1)throw new AssertionError("Duplicate rules");
   try(Cursor c=db.rawQuery("SELECT json_setting FROM Sender ORDER BY id DESC LIMIT 1",null)){if(!c.moveToFirst()||!new JSONObject(c.getString(0)).getString("server").endsWith("TestKeyChanged"))throw new AssertionError("Key not updated");}
  }
 }
 static long count(SQLiteDatabase db,String t){try(Cursor c=db.rawQuery("SELECT count(*) FROM "+t,null)){c.moveToFirst();return c.getLong(0);}}
}
