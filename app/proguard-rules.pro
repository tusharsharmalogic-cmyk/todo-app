# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.tushar.sharma.logic.todo.**$$serializer { *; }
-keepclassmembers class com.tushar.sharma.logic.todo.** {
    *** Companion;
}
-keepclasseswithmembers class com.tushar.sharma.logic.todo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# DataStore / coroutines
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# AndroidX general
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# Keep line numbers for crash reports (small size cost)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile