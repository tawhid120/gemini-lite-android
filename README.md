# Gemini Lite - Ultra-fast Android Go Client

A lightweight, high-performance Android application for **Google Gemini (`https://gemini.google.com/`)**, custom-built and optimized specifically for entry-level devices such as the **itel A48** (Android Go Edition, 2GB RAM).

---

## 🚀 Key Features & itel A48 (2GB RAM) Optimizations

1. **Ultra-Low Memory Footprint**:
   - Zero background services, zero bloatware, and zero tracking SDKs.
   - Built-in `onTrimMemory` memory scavenging that releases bitmap and page caches whenever Android Go signals memory pressure, avoiding app crashes.
   - `android:largeHeap="true"` enabled to give the WebView sufficient heap space during heavy chat sessions and multi-turn conversations.

2. **Full Gemini Feature Support**:
   - **Google Account & History**: Persistent cookies and DOM storage (`IndexedDB` & `LocalStorage`). Once signed in, your account stays signed in, and all past chat sessions and history load instantly.
   - **File & Image Upload**: Custom `WebChromeClient.onShowFileChooser` implementation enabling you to snap photos with the camera or pick images/documents directly from your gallery.
   - **Voice Input**: Integrated audio permission handler for Gemini's voice recognition.
   - **File Downloads**: Integrated `DownloadManager` for downloading generated images, code files, and exported chat docs.

3. **Google Sign-In Disallowed User-Agent Bypass**:
   - Normal WebViews are blocked by Google with `403: disallowed_useragent`. Gemini Lite dynamically sanitizes the User-Agent to match native Chrome Mobile, allowing seamless Google authentication.

4. **Hardware Accelerated & Smooth Scrolling**:
   - Hardware acceleration (`android:hardwareAccelerated="true"`) enabled for 60fps scrolling and fast rendering.
   - Top horizontal Material progress indicator and pull-to-refresh (`SwipeRefreshLayout`).
   - Clean offline error screen with one-tap Retry.

---

## 📁 Project Structure

```
gemini-lite-app/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/lite/geminiapp/MainActivity.java
│           └── res/
│               ├── drawable/
│               ├── layout/activity_main.xml
│               ├── mipmap-anydpi-v26/
│               ├── values/
│               └── xml/file_paths.xml
├── build.gradle
├── gradle.properties
├── local.properties
├── settings.gradle
└── README.md
```

---

## 📦 How to Install on itel A48

1. Copy `app-debug.apk` to your itel A48 via USB cable, Google Drive, WhatsApp, or Bluetooth.
2. Tap on the APK file in the "Files by Google" app on your itel A48.
3. If prompted, allow "Install unknown apps" for the file manager.
4. Tap **Install** and open **Gemini Lite**.
5. Sign in to your Google Account once. All your Gemini chats, prompts, images, and history will be ready to use!
