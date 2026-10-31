# Keep third-party libraries that use reflection or service loading intact.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**

# OkHttp optional TLS providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
