param([string]$Sdk=$env:ANDROID_SDK_ROOT,[string]$NdkVersion='27.0.12077973',[string]$Go='go')
$ErrorActionPreference='Stop'
if(-not $Sdk){$Sdk=$env:ANDROID_HOME}
if(-not $Sdk){throw 'Pass -Sdk or set ANDROID_SDK_ROOT.'}
$env:GOPATH="$PSScriptRoot/.work/gopath"
$env:GOCACHE="$PSScriptRoot/.work/gocache"
$env:GOOS='android'; $env:GOARCH='arm64'; $env:CGO_ENABLED='1'
$env:CC="$Sdk/ndk/$NdkVersion/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android26-clang.cmd"
if(!(Test-Path $env:CC)){throw 'Install the required Android NDK.'}
Push-Location "$PSScriptRoot/upstream/bark-server-2.3.9"
try { & $Go build -trimpath -ldflags '-s -w' -o "$PSScriptRoot/libbark.so" .; if($LASTEXITCODE -ne 0){throw 'Engine build failed'} } finally { Pop-Location }
