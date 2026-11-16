param(
 [Parameter(Mandatory=$true)][string]$KeyStore,
 [Parameter(Mandatory=$true)][string]$KeyAlias,
 [string]$Sdk="$env:LOCALAPPDATA\Android\Sdk",
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$Flutter='flutter'
)
$ErrorActionPreference='Stop'
if(!$env:BUS_KEYSTORE_PASSWORD -or !$env:BUS_KEY_PASSWORD){throw 'Set BUS_KEYSTORE_PASSWORD and BUS_KEY_PASSWORD first.'}
if(!$JavaHome){throw 'Set JAVA_HOME or provide -JavaHome.'}
$signer=Join-Path $Sdk 'build-tools\36.1.0\lib\apksigner.jar'
$java=Join-Path $JavaHome 'bin\java.exe'
$env:JAVA_HOME=$JavaHome
Push-Location $PSScriptRoot
try {
 & $Flutter build apk --release --target-platform android-arm,android-arm64,android-x64
 if($LASTEXITCODE -ne 0){throw 'Flutter build failed'}
 & $java -jar $signer sign --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass env:BUS_KEYSTORE_PASSWORD --key-pass env:BUS_KEY_PASSWORD --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out build\Bus-Announcement-Guide-0.01.apk build\app\outputs\flutter-apk\app-release.apk
 if($LASTEXITCODE -ne 0){throw 'Signing failed'}
 & $java -jar $signer verify --verbose --min-sdk-version 23 build\Bus-Announcement-Guide-0.01.apk
 if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
} finally { Pop-Location }
