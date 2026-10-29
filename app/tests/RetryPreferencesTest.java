import com.mcyao.barkserver.RetryPreferences;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;
public class RetryPreferencesTest {
 public static void main(String[] args)throws Exception{
  String input="<map><boolean name='enable_sms' value='true'/><string name='token'>keep&amp;safe</string><int name='request_retry_times' value='0'/><int name='request_retry_times' value='2'/></map>";
  String output=RetryPreferences.update(input);
  Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(output.getBytes("UTF-8")));
  int retries=0,others=0;
  for(int i=0;i<d.getDocumentElement().getChildNodes().getLength();i++){
   Node n=d.getDocumentElement().getChildNodes().item(i);if(!(n instanceof Element))continue;Element e=(Element)n;
   if(e.getAttribute("name").equals("request_retry_times")){if(!e.getAttribute("value").equals("5"))throw new AssertionError();retries++;}
   if(e.getAttribute("name").equals("enable_sms")&&e.getAttribute("value").equals("true"))others++;
   if(e.getAttribute("name").equals("token")&&e.getTextContent().equals("keep&safe"))others++;
  }
  if(retries!=1||others!=2)throw new AssertionError("did not preserve unrelated settings");
  String twice=RetryPreferences.update(output);if(!twice.equals(output))throw new AssertionError("not idempotent");
  boolean consentRejected=false;try{RetryPreferences.update(input,true);}catch(IOException e){consentRejected=true;}if(!consentRejected)throw new AssertionError("first-run consent bypassed");
  String enabled=RetryPreferences.update("<map><boolean name='is_agree_privacy_key' value='true'/><boolean name='enable_sms' value='false'/><string name='unrelated'>preserved</string></map>",true);
  if(!enabled.contains("name=\"enable_sms\" value=\"true\"")||!enabled.contains("preserved"))throw new AssertionError("enable failed");
  for(String invalid:new String[]{"<other/>","<!DOCTYPE map [<!ENTITY x SYSTEM 'file:///secret'>]><map>&x;</map>"}){
   boolean rejected=false;try{RetryPreferences.update(invalid);}catch(Exception e){rejected=true;}if(!rejected)throw new AssertionError("unsafe XML accepted");
  }
  System.out.println("PASS: preservation, retry values, duplicate removal, idempotency, invalid/DTD rejection");
 }
}
