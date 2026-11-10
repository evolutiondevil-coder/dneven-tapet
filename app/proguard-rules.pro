# kotlinx.serialization: keep generated serializers of our own model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class bg.dnevnitapet.** {
    *** Companion;
}
-keepclasseswithmembers class bg.dnevnitapet.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class bg.dnevnitapet.**$$serializer { *; }

# Keep enum names stable (they are written to the settings file).
-keepclassmembers enum bg.dnevnitapet.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp optional platform classes.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
