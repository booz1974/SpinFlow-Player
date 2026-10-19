# R8-regels voor de release-build. AndroidX, Media3, OkHttp, Coil en Moshi leveren hun
# eigen regels mee; hier alleen wat via reflectie loopt.

# Sendspin-protocol: Moshi (de)serialiseert deze klassen, deels via KotlinJsonAdapterFactory
# (reflectie op Kotlin-metadata). Het hele package heel laten is het veiligst.
-keep class com.sendspin.protocol.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# Debug-logging (Log.d/Log.v) uit de release halen, ook die in het audiopad.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Leesbare stacktraces in Play Console (met de mapping-file die R8 meelevert).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
