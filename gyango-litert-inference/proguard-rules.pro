# LiteRT-LM (com.google.ai.edge.litertlm): liblitertlm_jni uses JNI GetMethodID on Kotlin/Java types
# (e.g. SamplerConfig getters). The published AAR has no consumer rules; R8 otherwise strips or renames
# members → CheckJNI "mid == null" / abort in nativeCreateConversation.

-keep class com.google.ai.edge.litertlm.** { *; }
