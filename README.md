# ScanPrint – Android app

WebView app that bundles ScanPrint (app/src/main/assets/index.html). Works offline.
Camera, gallery, Save PDF/PNG, Share and Print are connected to Android.

## Get the APK (pick one)

### A) Android Studio (easiest if installed)
1. File > Open > select this folder, wait for Gradle sync.
2. Build > Build App Bundle(s) / APK(s) > Build APK(s).
3. APK: app/build/outputs/apk/debug/app-debug.apk -> copy to the phone and open it
   (allow "Install unknown apps" for your file manager when asked).

### B) Command line (JDK 17 + Android SDK installed)
    ./gradlew assembleDebug

### C) No installs – GitHub builds it for you
1. Create a new GitHub repository and upload everything in this folder
   (keep the .github folder).
2. Open the Actions tab > "Build APK" > Run workflow (it also runs on every push).
3. When it finishes, download the "ScanPrint-apk" artifact (a zip containing app-debug.apk).

The APK is debug-signed, which is fine for installing on your own phone.
