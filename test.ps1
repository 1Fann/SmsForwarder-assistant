param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JavaHome){throw 'Set JAVA_HOME or pass -JavaHome.'}
$out="$PSScriptRoot/app/test-build"
New-Item -ItemType Directory -Force $out | Out-Null
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $out "$PSScriptRoot/app/src/com/mcyao/barkserver/RetryPreferences.java" "$PSScriptRoot/app/tests/RetryPreferencesTest.java" "$PSScriptRoot/app/src/com/mcyao/barkserver/MonitorPolicy.java" "$PSScriptRoot/app/tests/MonitorPolicyTest.java"
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
foreach($test in @('RetryPreferencesTest','com.mcyao.barkserver.MonitorPolicyTest')){
 & "$JavaHome/bin/java.exe" -cp $out $test
 if($LASTEXITCODE -ne 0){throw "Test failed: $test"}
}
