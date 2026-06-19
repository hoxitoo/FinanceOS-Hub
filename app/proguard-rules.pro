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

# PdfBox-Android: optional JPEG2000 support (com.gemalto.jp2) is not bundled
# The JPXFilter references JP2Decoder only when JPEG2000 images are present in PDFs.
# Russian bank statements never use JPEG2000, so this class is safe to ignore.
-dontwarn com.gemalto.jp2.**
