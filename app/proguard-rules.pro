# Keep the methods the game's JavaScript calls.
-keepattributes JavascriptInterface
-keepclassmembers class com.popquest.bubbleshooter.NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
