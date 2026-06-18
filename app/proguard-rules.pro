# Keep Room entities
-keep class com.financeos.hub.core.database.entities.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep Hilt generated code
-dontwarn com.google.dagger.**
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Keep parser classes (reflection used for pattern matching)
-keep class com.financeos.hub.core.parser.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
