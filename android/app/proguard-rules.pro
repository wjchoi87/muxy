# Default ProGuard rules; expanded later for release builds.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.muxy.app.**$$serializer { *; }
-keepclassmembers class com.muxy.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.muxy.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
