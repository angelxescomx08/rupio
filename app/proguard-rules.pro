# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.rupio.app.** { *** Companion; }
-keepclasseswithmembers class com.rupio.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rupio.app.**$$serializer { *; }
