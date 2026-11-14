# ---------------------------------------------------------------------------
# Allegro 的 R8 规则
#
# 原则：只保"按名字被外部调用"的东西（JS 桥、反射枚举、Room 实体字段），
# 其余交给 R8 自己收缩，避免为了稳把整个包 keep 掉导致混淆白做。
# ---------------------------------------------------------------------------

# 注解本身要留着：@JavascriptInterface 靠它识别
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# WebView 的 JS 桥：页面里按方法名调用，名字必须保留
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin 元数据（协程/Compose 的调试信息，也便于线上堆栈还原）
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Room 实体：字段名参与 SQL 列映射，不要改
-keep class com.example.simplebrowser.data.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# 枚举按名字持久化（ThemeMode / DownloadStatus 等），valueOf 是反射调用
-keepclassmembers enum com.example.simplebrowser.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 站点设置/同步用的 JSON 键是手写的字符串，模型类改名没影响，
# 但 Parcelable 的 CREATOR 需要保留（Room 的 saveState 走 Parcel）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# OkHttp / Okio 在 Android 上会引用一些 JVM-only 类
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 保留行号，线上堆栈才有意义
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
