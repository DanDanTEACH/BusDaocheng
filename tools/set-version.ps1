param([Parameter(Mandatory=$true)][ValidatePattern('^0\.[0-9]{2,}$')][string]$Version)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$old=Get-Content (Join-Path $root 'version.json') -Raw | ConvertFrom-Json
$number=[int]$Version.Split('.')[1]
$current=[int]$old.version.Split('.')[1]
if($number -ne $current+1){throw "Next version must be 0.$(($current+1).ToString('00'))."}
$next=[ordered]@{version=$Version;flutterVersion="0.0.$number";build=([int]$old.build+1)}
$files=git -C $root ls-files
foreach($relative in $files){
 if($relative -in @('AGENTS.md','VERSIONING.md','tools/set-version.ps1') -or $relative -match '(^|/)(assets|res)/' -or $relative -notmatch '\.(md|html|ps1|xml|kts|dart|json|cpp|rc|yaml|yml|pbxproj)$' -or $relative -eq 'pubspec.lock'){continue}
 $file=Join-Path $root $relative
 $text=Get-Content -LiteralPath $file -Raw
 $text=$text.Replace("$($old.flutterVersion)+$($old.build)","$($next.flutterVersion)+$($next.build)")
 $text=$text.Replace($old.version,$Version).Replace($old.flutterVersion,$next.flutterVersion)
 $text=$text.Replace("建置號 $($old.build)","建置號 $($next.build)")
 $text=$text.Replace((($old.flutterVersion.Split('.') -join ',')+','+$old.build),(($next.flutterVersion.Split('.') -join ',')+','+$next.build))
 if($relative -eq 'flutter_app/pubspec.yaml'){$text=[regex]::Replace($text,'(?m)^version: .*$',"version: $($next.flutterVersion)+$($next.build)")}
 if($relative -eq 'android/AndroidManifest.xml'){$text=[regex]::Replace($text,'versionCode="\d+"',"versionCode=`"$($next.build)`"")}
 Set-Content -LiteralPath $file -Value $text -Encoding utf8 -NoNewline
}
$next | ConvertTo-Json | Set-Content (Join-Path $root 'version.json') -Encoding utf8
Write-Output "Version $Version; platform version $($next.flutterVersion); build $($next.build)"
