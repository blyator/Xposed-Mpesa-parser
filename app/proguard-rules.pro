# Keep Xposed entry point referenced by assets/xposed_init
-keep class com.blyator.mpesa.** { *; }
-keepclassmembers class com.blyator.mpesa.XposedInit { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
