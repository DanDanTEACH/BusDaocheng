# Android 原始碼

這裡是「公車報站&導乘 V2.0」目前使用的原生 Android Java 原始碼，包含 APP、桌面小工具、背景播報服務與所需素材，沒有使用 Flutter。

## 檔案

- `src/com/led/phone/PhoneActivity.java`：操作介面、播放畫面、音效、設定與路線圖。
- `src/com/led/phone/BusWidget.java`：桌面小工具。
- `src/com/led/phone/WidgetPlaybackService.java`：桌面小工具背景播報服務。
- `res/`：圖示、版面與小工具設定。
- `assets/phone/`：路線與站序、路線圖、站名及設備音檔。
- `AndroidManifest.xml`：應用程式與系統元件設定。
- `build.ps1`：從這些原始檔直接編譯 APK 的 Windows PowerShell 腳本。

## 建置

準備 JDK、Android SDK Platform 35 與 Build Tools 36.1.0。設定 `JAVA_HOME` 後，在此資料夾執行：

```powershell
.\build.ps1 -Sdk 'C:\Android\Sdk' -JavaHome 'C:\Program Files\Android\Android Studio\jbr'
```

請將兩個路徑改成自己的安裝位置。腳本會編譯資源與 Java、產生 DEX，並在 `build/` 產生使用本機測試金鑰簽署的 `Bus-Announcement-Guide-V2.0-debug.apk`。

正式發布的簽署金鑰不在儲存庫中。本機編譯 APK 的簽章與 Release APK 不同，無法直接覆蓋安裝正式版；如需測試，請使用另一台測試裝置，或先備份設定再移除正式版。

`build/`、簽署金鑰及其他編譯產物均排除於版本控制。專案目前採用 Android SDK 工具建置，未附 Gradle 專案。
