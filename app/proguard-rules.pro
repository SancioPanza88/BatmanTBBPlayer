# Regole di default: il progetto non usa la minificazione, ma le regole base
# vengono comunque applicate se in futuro si abilita isMinifyEnabled.

-keep class com.toonitalia.batmanplayer.** { *; }
-keepclassmembers class com.toonitalia.batmanplayer.** {
    @android.webkit.JavascriptInterface <methods>;
}
