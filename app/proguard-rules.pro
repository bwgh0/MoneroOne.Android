# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep MoneroKit classes
-keep class io.horizontalsystems.monerokit.** { *; }

# Keep model classes for serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep Monero JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Timber
-dontwarn org.jetbrains.annotations.**

# Strip all android.util.Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Lombok (used by MoneroKit at compile time)
-dontwarn lombok.**
