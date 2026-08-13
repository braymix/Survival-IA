# R8/ProGuard — regole app.

# --- JNI: mantieni intatti i metodi nativi ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# ONNX Runtime usa JNI: non offuscare né rimuovere.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# kotlinx.serialization (usiamo il parsing runtime di JsonElement).
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# Room/Hilt portano le proprie regole consumer; nulla di extra necessario qui.
