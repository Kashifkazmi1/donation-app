# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ---------- General ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.Unit
-dontwarn kotlinx.coroutines.**

# ---------- Kotlin metadata / data classes used as DTOs ----------
-keepclassmembers class com.givewp.donationterminal.data.remote.dto.** { *; }
-keep class com.givewp.donationterminal.data.remote.dto.** { *; }
-keep class com.givewp.donationterminal.domain.model.** { *; }
-keepclassmembers class com.givewp.donationterminal.domain.model.** { *; }

# ---------- Retrofit ----------
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembernames,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------- Moshi ----------
-keep @com.squareup.moshi.JsonQualifier interface *
-keepnames @com.squareup.moshi.JsonClass class *
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-dontwarn com.squareup.moshi.**
-keep class kotlin.Metadata { *; }

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---------- Hilt / Dagger ----------
-dontwarn com.google.errorprone.annotations.**

# ---------- Stripe Terminal SDK ----------
-keep class com.stripe.stripeterminal.** { *; }
-keep interface com.stripe.stripeterminal.** { *; }
-dontwarn com.stripe.stripeterminal.**
-keepattributes *Annotation*

# ---------- Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.android.** { *; }

# Keep line numbers for stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
