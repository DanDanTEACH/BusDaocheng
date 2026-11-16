# 公車報站&導乘 — Flutter

這是目前的 Flutter 版本。四個分頁、Material 3 介面、設定、路線圖與到站顯示／動畫、自動切站邏輯由 Dart 實作。

## 架構

- `lib/main.dart`：Flutter 介面、路線選擇、播放與自動播報、站名動畫、設備及設定。
- `android/app/src/main/java/com/led/phone/PhoneActivity.java`：Flutter 平台通道，連接 Android 音效、相簿、既有設定及下車鈴模型繪圖。
- `BusWidget.java`、`WidgetPlaybackService.java`：保留 Android 桌面小工具與背景播報。
- `test/`：分類、站序、操作介面、音檔播放鎖定、缺音自動續播測試。
- 路線、圖示與音檔共用儲存庫根目錄的 `android/assets/`、`android/res/`，不重複存放。

套件名稱維持 `com.led.demo.phone`，既有設定及桌面小工具資料可沿用。這次主要針對 Android 移植，未製作 iOS 或網頁版。

## 開發

安裝 Flutter stable（目前驗證版本：Flutter 3.50.0 / Dart 3.14.0）、JDK 與 Android SDK，保留完整儲存庫目錄結構，進入此資料夾：

```sh
flutter pub get
flutter analyze
flutter test
flutter run
```

## 建置

```sh
flutter build apk --release
```

預設輸出是供自行測試的簽章。需要發布時，以自己的金鑰執行 `build-release.ps1`，腳本會在 Flutter 建置完成後重新簽署並驗證 V1、V2、V3。原始發布金鑰不在 GitHub 中，使用其他金鑰不能覆蓋安裝正式 APK。

```powershell
$env:BUS_KEYSTORE_PASSWORD = '你的金鑰庫密碼'
$env:BUS_KEY_PASSWORD = '你的金鑰密碼'
.\build-release.ps1 -KeyStore 'D:\private\release.p12' -KeyAlias 'release' -JavaHome 'C:\Program Files\Android\Android Studio\jbr'
```

不要將金鑰、密碼、`local.properties` 或建置產物提交到 GitHub。
