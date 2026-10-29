import {createHash} from 'node:crypto';
import {readFile,writeFile,rename} from 'node:fs/promises';
const digest=b=>createHash('sha256').update(b).digest('hex');
async function obtain(path,url,expected){
const target=new URL(path,import.meta.url);
try{const b=await readFile(target);if(digest(b)===expected){console.log(`Verified cached ${path}`);return;}}catch{}
const response=await fetch(url,{signal:AbortSignal.timeout(120000)});
if(!response.ok)throw new Error(`Download failed: ${response.status}`);
const bytes=Buffer.from(await response.arrayBuffer());
if(digest(bytes)!==expected)throw new Error(`Checksum mismatch: ${path}`);
const temp=new URL(path+'.part',import.meta.url);await writeFile(temp,bytes);await rename(temp,target);
console.log(`Downloaded and verified ${path}`);
}
await obtain('./SmsForwarder-official.apk',
 'https://github.com/pppscn/SmsForwarder/releases/download/v3.5.0/SmsF_3.5.0.260224_300055_arm64-v8a_release.apk',
 '726918cc6132728ffcadf36e09cc39213875c63dfabdb50ade3f47854364f319');
await obtain('./upstream/bark-server-2.3.9/apns/apns_certs.go',
 'https://raw.githubusercontent.com/Finb/bark-server/v2.3.9/apns/apns_certs.go',
 'a6f21221c70cd7a3cddfd30aaa59a5f8ab300465f8065fb273cb1b12bce30c82');
