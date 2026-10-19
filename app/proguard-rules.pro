# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Kotlin 协程
-dontwarn kotlinx.coroutines.**

# 保留注解与泛型签名，Room/Hilt 生成代码依赖
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Android 自带 org.json，R8 无法解析其实现
-dontwarn org.json.**

# MediaSessionService 由系统按 manifest 反射创建
-keep class * extends androidx.media3.session.MediaSessionService { *; }

# Compose 的运行时反射（极少数场景）
-dontwarn androidx.compose.**
