# Gyango — release shrinker rules (minimal keeps for JNI + serialization)

# Pad + release: strip all android.util.Log calls (no verbose logcat; failures surface as UX/state only).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

-dontwarn com.gemalto.jp2.JP2Decoder

-keepattributes *Annotation*, InnerClasses, Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# JNI (method names must match native symbols)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# kotlinx.serialization
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
