# R8 rules for the release build (app/build.gradle.kts -> proguard-android-optimize.txt + this file).
# Hilt, Room, Compose, kotlinx.serialization, ML Kit GenAI, Firebase (ai/analytics/crashlytics) and Ktor ship their own
# consumer rules inside their AARs; only what they do not cover is kept here.

# Readable crash stack traces in Crashlytics / logcat.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- MediaPipe Tasks (GenAI + Text) -----------------------------------------------------------
# Neither tasks-genai nor tasks-text ship consumer rules (checked in the R8 configuration).
# The native libraries call back into these classes by name via JNI and parse protobuf messages
# reflectively, so renaming/removing them breaks LlmInference / TextEmbedder at runtime.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.proto.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**

# --- LiteRT-LM (com.google.ai.edge.litertlm) --------------------------------------------------
# JNI-backed engine: native code resolves classes, fields and methods by name.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# --- ML Kit GenAI Prompt API / AICore (Gemini Nano) -------------------------------------------
# com.google.mlkit:genai-prompt (and mlkit:common) bundle their own consumer rules, so nothing extra
# is needed here (verified in the R8 configuration.txt of assembleRelease).

# --- Firebase AI Logic + Ktor + kotlinx.serialization -----------------------------------------
# firebase-ai serializes its request/response models with kotlinx.serialization (generated
# serializers are found through the companion's serializer() function) and runs on Ktor/OkHttp.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class com.google.firebase.ai.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlinx.coroutines.debug.**
