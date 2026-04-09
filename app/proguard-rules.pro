# Pocket AI — release shrinker rules (minimal keeps for JNI + serialization)

-dontwarn com.gemalto.jp2.JP2Decoder

-keepattributes *Annotation*, InnerClasses, Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# JNI (method names must match native symbols)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.google.ai.edge.litertlm.** { *; }

# kotlinx.serialization
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
