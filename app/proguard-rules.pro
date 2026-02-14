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

# Lombok (used by MoneroKit at compile time)
-dontwarn lombok.**
