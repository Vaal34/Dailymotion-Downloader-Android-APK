# Proguard rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
