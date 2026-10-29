package com.mcyao.barkserver;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
public final class RetryPreferences {
 public static String update(String xml) throws Exception {
  return update(xml,false);
 }
 public static String update(String xml,boolean enableSms) throws Exception {
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
  if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))throw new IOException("配置含不支持的 XML 声明");
  Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
  Element root=d.getDocumentElement();
  if(!"map".equals(root.getTagName()))throw new IOException("短信转发器配置格式不受支持");
  if(enableSms){
   NodeList children=root.getChildNodes();boolean agreed=false;
   for(int n=children.getLength()-1;n>=0;n--){Node child=children.item(n);if(!(child instanceof Element))continue;Element e=(Element)child;
    if("is_agree_privacy_key".equals(e.getAttribute("name"))&&"true".equals(e.getAttribute("value")))agreed=true;
    if("enable_sms".equals(e.getAttribute("name")))root.removeChild(e);
   }
   if(!agreed)throw new IOException("请先打开短信转发器，完成首次使用确认，再回来启用");
   Element enabled=d.createElement("boolean");enabled.setAttribute("name","enable_sms");enabled.setAttribute("value","true");root.appendChild(enabled);
  }
  String[] names={"request_retry_times","request_delay_time","request_timeout"};int[] values={5,5,20};
  for(int i=0;i<names.length;i++){
   NodeList children=root.getChildNodes();
   for(int n=children.getLength()-1;n>=0;n--){Node child=children.item(n);if(child instanceof Element&&names[i].equals(((Element)child).getAttribute("name")))root.removeChild(child);}
   Element e=d.createElement("int");e.setAttribute("name",names[i]);e.setAttribute("value",Integer.toString(values[i]));root.appendChild(e);
  }
  StringWriter out=new StringWriter();Transformer t=TransformerFactory.newInstance().newTransformer();t.transform(new DOMSource(d),new StreamResult(out));return out.toString();
 }
}
