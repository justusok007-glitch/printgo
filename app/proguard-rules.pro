# PrintGo release (R8) rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Data classes that are stored with Room or serialised with Moshi, and the IPP model.
-keep class com.printgo.app.data.local.** { *; }
-keep class com.printgo.app.model.** { *; }
-keep class com.printgo.app.data.ipp.** { *; }

# Moshi generated adapters
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# Optional TLS providers referenced by OkHttp
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
