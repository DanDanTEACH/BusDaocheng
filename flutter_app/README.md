# 公車報站&導乘 0.02 — Flutter 多平台專案

支援 Android、iOS、網頁與 Windows。介面、路線圖、站名動畫、自動切站與一般播放邏輯共用 Dart 程式。

- `lib/main.dart`：主要介面與播放控制。
- `lib/platform_bridge.dart`：Android 原生橋接，以及其他平台的音效、設定及圖片選擇實作。
- `lib/bell_model.dart`：其他平台共用的下車鈴繪圖。
- `lib/version.dart`：顯示版本 0.02。
- `assets/phone/`：共用的 45 條路線、圖片與音檔。
- `android/`：Android 桌面小工具、背景服務及原有下車鈴繪圖橋接。
- `ios/`、`web/`、`windows/`：各平台工程。

## 開發與測試

目前驗證環境為 Flutter 3.50.0 / Dart 3.14.0。

```sh
flutter pub get
flutter analyze
flutter test
flutter run
```

## Android

```sh
flutter build apk --release
```

正式發布使用 `build-release.ps1` 與自己的簽署金鑰，啟用 V1/V2/V3。原始金鑰不在儲存庫中。套件名稱仍為 `com.led.demo.phone`，建置號 45，保留舊版設定；Android 小工具為此平台專屬。

## 網頁

```sh
flutter build web --release --base-href /BusDaocheng/app/
```

透過 HTTP/HTTPS 伺服器提供 `build/web`，不能只用檔案總管開啟 index.html。瀏覽器聲音由使用者點擊啟動，背景分頁可能暫停播放。非 Android 平台匯入背景圖片目前限制 3 MB。

## Windows

安裝 Visual Studio 的 Desktop development with C++ 工作負載後：

```sh
flutter build windows --release
```

發佈 `build/windows/x64/runner/Release/` **整個資料夾**，不能只複製 exe。GitHub Actions 亦提供 Windows 建置。

## iOS

需要 macOS 與 Xcode：

```sh
flutter build ios --release --no-codesign
```

GitHub Actions 會建置並包成標示 `unsigned` 的 IPA。此 IPA 沒有 Apple 簽章，**無法直接安裝到 iPhone**。欲使用實機或 TestFlight，需以自己的 Apple Developer Team、憑證及 provisioning profile 簽署。未提供 iOS 桌面小工具。

## 版本

對外顯示 **0.02**；平台內部版本為 **0.0.2+45**，符合 Flutter / Apple 格式，並保持 Android 更新號遞增。後續更新必須同步 [version.json](../version.json) 及 [版本規則](../VERSIONING.md)。
