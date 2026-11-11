param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome pointing to a JDK (Android Studio jbr is supported).' }
$tools = Join-Path $Sdk 'build-tools\36.1.0'
$androidJar = Join-Path $Sdk 'platforms\android-35\android.jar'
$java = Join-Path $JavaHome 'bin\java.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build failed: exit $LASTEXITCODE" } }
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build\classes,build\dex | Out-Null
    & "$tools\aapt2.exe" compile --dir res -o build\compiled.zip
    Check-Exit
    & "$tools\aapt2.exe" link -I $androidJar --manifest AndroidManifest.xml -A assets -o build\resources.apk build\compiled.zip
    Check-Exit
    & $javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d build\classes src\com\led\phone\PhoneActivity.java src\com\led\phone\BusWidget.java src\com\led\phone\WidgetPlaybackService.java
    Check-Exit
    & $jar cf build\phone.jar -C build\classes .
    Check-Exit
    & $java -cp "$tools\lib\d8.jar" com.android.tools.r8.D8 --min-api 26 --lib $androidJar --output build\dex build\phone.jar
    Check-Exit
    Copy-Item build\resources.apk build\unsigned.apk -Force
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot 'build\unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
    try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot 'build\dex\classes.dex'),'classes.dex') | Out-Null } finally { $zip.Dispose() }
    & "$tools\zipalign.exe" -f -p 4 build\unsigned.apk build\aligned.apk
    Check-Exit
    # A new LOCAL test key, distinct from the key used by the published APK.
    if (!(Test-Path build\local-debug.p12)) {
        & $keytool -genkeypair -keystore build\local-debug.p12 -storetype PKCS12 -alias androiddebugkey -storepass android -keypass android -dname 'CN=Android Debug' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
        Check-Exit
    }
    & $java -jar "$tools\lib\apksigner.jar" sign --ks build\local-debug.p12 --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out build\Bus-Announcement-Guide-V2.0-debug.apk build\aligned.apk
    Check-Exit
    & $java -jar "$tools\lib\apksigner.jar" verify build\Bus-Announcement-Guide-V2.0-debug.apk
    Check-Exit
    Write-Output (Join-Path $PSScriptRoot 'build\Bus-Announcement-Guide-V2.0-debug.apk')
} finally { Pop-Location }
