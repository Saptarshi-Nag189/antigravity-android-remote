# ==============================================================================
# ProGuard / R8 Configuration for Antigravity Remote
# Target: Android 15/16 (API 35/36) | Kotlin 2.0+ | F-Droid / IzzyOnDroid
# ==============================================================================

# 1. WebView JavaScript Interface Preservation (CRITICAL)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.sapta.antigravity.remote.bridge.AntigravityAccountBridge {
    public <methods>;
}
-keep class com.sapta.antigravity.remote.MainActivity$AntigravityAccountBridge {
    public <methods>;
}

# 2. WebChromeClient & WebView Client Callbacks
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public boolean onShowFileChooser(android.webkit.WebView, android.webkit.ValueCallback, android.webkit.WebChromeClient$FileChooserParams);
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void onPageFinished(android.webkit.WebView, java.lang.String);
    public void onReceivedError(android.webkit.WebView, android.webkit.WebResourceRequest, android.webkit.WebResourceError);
}

# 3. AndroidX WebKit Support Library
-keep class androidx.webkit.** { *; }
-dontwarn org.chromium.**

# 4. Material Components 3 & AndroidX Dynamic Inflation
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 5. Core Android Application Components (Manifest Entry Points)
-keep public class com.sapta.antigravity.remote.MainActivity
-keep public class com.sapta.antigravity.remote.RemoteKeepAliveService
-keep public class com.sapta.antigravity.remote.AuthSyncReceiver

# 6. Line Number Table Preservation for Clean Stack Traces
-keepattributes SourceFile, LineNumberTable
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
