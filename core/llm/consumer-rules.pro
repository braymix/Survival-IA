# Regole consumer per il modulo llm. I metodi nativi JNI (Fase 4) vanno preservati.
-keepclasseswithmembernames class * {
    native <methods>;
}
