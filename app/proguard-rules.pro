# ── Room ─────────────────────────────────────────────────────────────────────
# Keep entity field names — Room's generated SQL uses them for column mapping.
# Without this, R8 renames fields and Room cannot find the correct DB columns.
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { <fields>; }
-keep @androidx.room.Database class *
-keep @androidx.room.Dao interface *

# ── MaxMind GeoLite2 ─────────────────────────────────────────────────────────
# The MaxMind DB reader loads the bundled mmdb by reflection — R8 must not rename it.
-keep class com.maxmind.** { *; }
-dontwarn com.maxmind.**

# ── WorkManager ──────────────────────────────────────────────────────────────
# WorkManager instantiates Worker subclasses by name via reflection.
# The constructor signature must be preserved exactly.
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Android system components ─────────────────────────────────────────────────
# VpnService and BroadcastReceiver subclasses are instantiated by Android OS.
-keep class * extends android.net.VpnService
-keep class * extends android.content.BroadcastReceiver

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Strip verbose/debug/info logging from release builds ─────────────────────
# Privacy + size: removes Log.v/d/i calls (which can carry domains and other
# user data) from the release APK. Log.w and Log.e are intentionally KEPT so
# real warnings and errors still surface for production triage.
# Official guidance: developer.android.com/privacy-and-security/risks/log-info-disclosure
# (assumenosideeffects is an assumption that forces removal — verified with an R8 build.)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── App classes — conservative keep for v1 ───────────────────────────────────
# Keeps all app code intact for the first release.
# Prevents any unexpected R8 breakage in Room-generated code, ViewModels,
# Compose state, and cross-process intents that reference class names.
-keep class com.sentinel.** { *; }
-keep class com.tacu.** { *; }
