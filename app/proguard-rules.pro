# SortSnap R8 rules.
# ML Kit, Room, Hilt, WorkManager, Coil and kotlinx.serialization ship consumer rules; the rules below
# cover what they do not.

# kotlinx.serialization: keep serializers of our @Serializable classes (classifier evidence in Room).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.sortsnap.** {
    *** Companion;
}
-keepclasseswithmembers class com.sortsnap.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sortsnap.**$$serializer { *; }

# Enums stored by name in Room / DataStore.
-keepclassmembers enum com.sortsnap.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ML Kit bundled models load native code and reflectively-registered components.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-dontwarn com.google.mlkit.**

# WorkManager instantiates workers reflectively by class name (Hilt factory handles creation, but the
# class name must survive).
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
