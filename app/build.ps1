param([string]$Sdk=$env:ANDROID_SDK_ROOT,[string]$JavaHome=$env:JAVA_HOME,[string]$Keystore,[string]$KeyAlias='bark')
$ErrorActionPreference='Stop'
if(-not $Sdk){$Sdk=$env:ANDROID_HOME}
if(-not $Sdk -or -not $JavaHome){throw 'Pass -Sdk and -JavaHome, or set ANDROID_SDK_ROOT and JAVA_HOME.'}
$bt="$Sdk/build-tools/35.0.0"; $jar="$Sdk/platforms/android-35/android.jar"
$b=Join-Path $PSScriptRoot ('build/'+[DateTime]::Now.ToString('yyyyMMddHHmmss'))
$out=Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force "$b/classes","$b/dex","$b/assets",$out | Out-Null
Copy-Item "$PSScriptRoot/../upstream/bark-server-2.3.9/LICENSE" "$b/assets/LICENSE.bark-server"
Copy-Item "$PSScriptRoot/../third_party/LICENSE.SmsForwarder" "$b/assets/LICENSE.SmsForwarder"
Copy-Item "$PSScriptRoot/../SmsForwarder-official.apk" "$b/assets/SmsForwarder-official.apk"
foreach($name in @('monitor.sh','monitor-boot.sh')) {
 $content=[IO.File]::ReadAllText("$PSScriptRoot/assets/$name").Replace("`r`n","`n")
 [IO.File]::WriteAllText("$b/assets/$name",$content,[Text.UTF8Encoding]::new($false))
}
function Check {if($LASTEXITCODE -ne 0){throw "Build failed $LASTEXITCODE"}}
& "$bt/aapt.exe" package -f -M "$PSScriptRoot/AndroidManifest.xml" -S "$PSScriptRoot/res" -A "$b/assets" -I $jar -F "$b/resources.apk"
Check
$src=@(Get-ChildItem "$PSScriptRoot/src" -Filter '*.java' -Recurse | ForEach-Object FullName)
& "$JavaHome/bin/javac.exe" -encoding UTF-8 --release 8 -classpath $jar -d "$b/classes" @src
Check
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory("$b/classes","$b/classes.jar")
& "$JavaHome/bin/java.exe" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib $jar --output "$b/dex" "$b/classes.jar"
Check
Copy-Item "$b/resources.apk" "$b/unsigned.apk"
$z=[IO.Compression.ZipFile]::Open("$b/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,"$b/dex/classes.dex",'classes.dex');[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,"$PSScriptRoot/../libbark.so",'lib/arm64-v8a/libbark.so')}finally{$z.Dispose()}
& "$bt/zipalign.exe" -f 4 "$b/unsigned.apk" "$b/aligned.apk"
Check
$key=if($Keystore){$Keystore}else{"$out/local.keystore"}
if($Keystore -and !(Test-Path $key)){throw "Signing keystore does not exist"}
if(!(Test-Path $key)){& "$JavaHome/bin/keytool.exe" -genkeypair -keystore $key -storepass android -keypass android -alias bark -dname 'CN=Bark Local Personal Build' -keyalg RSA -keysize 2048 -validity 10000 -noprompt;Check}
if($Keystore){
 if(-not $env:SMS_STORE_PASS -or -not $env:SMS_KEY_PASS){throw 'Set SMS_STORE_PASS and SMS_KEY_PASS for release signing.'}
 $sign=@('--ks',$key,'--ks-key-alias',$KeyAlias,'--ks-pass','env:SMS_STORE_PASS','--key-pass','env:SMS_KEY_PASS')
}else{$sign=@('--ks',$key,'--ks-key-alias','bark','--ks-pass','pass:android')}
& "$JavaHome/bin/java.exe" -jar "$bt/lib/apksigner.jar" sign @sign --out "$out/SmsForwarder-Assistant-arm64.apk" "$b/aligned.apk"
Check
& "$JavaHome/bin/java.exe" -jar "$bt/lib/apksigner.jar" verify "$out/SmsForwarder-Assistant-arm64.apk"
Check
Get-FileHash "$out/SmsForwarder-Assistant-arm64.apk"
