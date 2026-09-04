# Attributes required for Retrofit, Gson, and Kotlin Coroutines reflection
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Exceptions

# Retrofit 2 & Kotlin Coroutines
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class kotlin.coroutines.Continuation { *; }

-dontnote retrofit2.Platform
-dontnote retrofit2.Platform$Java8

# Keep Retrofit interface methods and annotations completely intact
-keep interface com.canim.app.data.remote.** { *; }
-keepclassmembers interface com.canim.app.data.remote.** {
    <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep our domain models, local storage, remote models & API services
-keep class com.canim.app.data.model.** { *; }
-keepclassmembers class com.canim.app.data.model.** { *; }
-keep class com.canim.app.data.local.** { *; }
-keepclassmembers class com.canim.app.data.local.** { *; }
-keep class com.canim.app.data.remote.** { *; }
-keepclassmembers class com.canim.app.data.remote.** { *; }
-keep class com.canim.app.data.repository.** { *; }
-keepclassmembers class com.canim.app.data.repository.** { *; }

# Keep Kotlin Coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AndroidX Security Crypto & Google Tink
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.errorprone.annotations.**

