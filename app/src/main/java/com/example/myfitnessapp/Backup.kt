package com.example.myfitnessapp

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ---------------------------------------------------------------------------
// Backups. A backup is one .zip holding everything needed to put the app back as it was: the
// whole database (workouts, sets, programs, body weight, custom exercises), the route pictures,
// and the settings and Profile. Restoring one replaces all of the app's data with it.
// ---------------------------------------------------------------------------

private const val DB_ENTRY = "fitness.db"
private const val INFO_ENTRY = "info.json"
private const val IMAGES_DIR = "route-images"
private const val PREFS_DIR = "prefs/"
// The preference files that are backed up: settings, Profile, and Health Connect's sync state.
private val BACKED_UP_PREFS = listOf("settings", "profile", "health_connect")

class BackupException(message: String) : Exception(message)

// A suggested name for a backup made now, such as "ProjectMe-backup-2026-11-10.zip".
fun backupFileName(date: LocalDate = LocalDate.now()): String = "ProjectMe-backup-$date.zip"

// The date in a backup file name, or null for any other file. Backups made before the app was
// renamed to Project Me start "MyFitnessApp-backup-", and those still count, so automatic
// backups keep tidying away the old ones.
fun backupDateOf(name: String): LocalDate? =
    Regex("""(?:ProjectMe|MyFitnessApp)-backup-(\d{4}-\d{2}-\d{2})(?:[ (\-].*)?\.zip""").matchEntire(name)
        ?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

// Writes a backup of everything to `out`.
// `database` is the app's own; tests pass another.
fun writeBackup(context: Context, out: OutputStream, database: WorkoutDatabase = WorkoutDatabase.getInstance(context)) {
    // A copy of the database made by SQLite itself, so it's complete and consistent even while
    // the app is using it.
    val copy = File(context.cacheDir, "backup-copy.db").apply { delete() }
    val db = database.openHelper.writableDatabase
    db.execSQL("VACUUM INTO '${copy.absolutePath.replace("'", "''")}'")

    try {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(INFO_ENTRY))
            zip.write(
                JSONObject()
                    .put("app", "ProjectMe")
                    .put("dbVersion", DB_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(2).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DB_ENTRY))
            copy.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            File(context.filesDir, IMAGES_DIR).listFiles()?.forEach { image ->
                zip.putNextEntry(ZipEntry("$IMAGES_DIR/${image.name}"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            for (name in BACKED_UP_PREFS) {
                zip.putNextEntry(ZipEntry("$PREFS_DIR$name.json"))
                zip.write(prefsToJson(context, name).toString(2).toByteArray())
                zip.closeEntry()
            }
        }
    } finally {
        copy.delete()
    }
}

// A preference file's values as JSON, with each value's type so it can be put back exactly.
private fun prefsToJson(context: Context, name: String): JSONObject {
    val json = JSONObject()
    for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
        val entry = when (value) {
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
            is String -> JSONObject().put("type", "string").put("value", value)
            is Set<*> -> JSONObject().put("type", "stringSet").put("value", JSONArray(value.map { it.toString() }))
            else -> continue
        }
        json.put(key, entry)
    }
    return json
}

private fun jsonToPrefs(context: Context, name: String, json: JSONObject) {
    // commit, not apply: the app restarts straight after a restore.
    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
        clear()
        for (key in json.keys()) {
            val entry = json.getJSONObject(key)
            when (entry.getString("type")) {
                "boolean" -> putBoolean(key, entry.getBoolean("value"))
                "int" -> putInt(key, entry.getInt("value"))
                "long" -> putLong(key, entry.getLong("value"))
                "float" -> putFloat(key, entry.getDouble("value").toFloat())
                "string" -> putString(key, entry.getString("value"))
                "stringSet" -> {
                    val array = entry.getJSONArray("value")
                    putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
            }
        }
    }
}

// What's in a backup file, checked and unpacked, ready to be restored.
class UnpackedBackup(val folder: File, val createdAt: Long?, val workoutCount: Int) {
    val createdText: String
        get() = createdAt?.let {
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        } ?: "an unknown date"
}

// Unpacks a backup into a temporary folder and checks it's one this app can restore. Nothing in
// the app is changed yet.
fun unpackBackup(context: Context, input: InputStream): UnpackedBackup {
    val folder = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            // Only plain names inside the folder, never paths that climb out of it.
            val target = File(folder, entry.name).canonicalFile
            if (!target.path.startsWith(folder.canonicalPath + File.separator)) continue
            if (entry.isDirectory) { target.mkdirs(); continue }
            target.parentFile?.mkdirs()
            target.outputStream().use { zip.copyTo(it) }
        }
    }

    val dbFile = File(folder, DB_ENTRY)
    if (!dbFile.exists()) throw BackupException("This isn't a Project Me backup.")
    val info = File(folder, INFO_ENTRY).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    // Read the database itself, to be sure it's one this version of the app can open.
    val (version, workouts) = try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.version
            val count = db.rawQuery("SELECT COUNT(*) FROM workouts", null).use { it.moveToFirst(); it.getInt(0) }
            version to count
        }
    } catch (e: Exception) {
        throw BackupException("The backup's data couldn't be read.")
    }
    if (version > DB_VERSION) throw BackupException("This backup is from a newer version of the app. Update the app, then restore it.")
    if (version < MIN_RESTORABLE_DB_VERSION) throw BackupException("This backup is too old to restore.")

    return UnpackedBackup(folder, info?.optLong("createdAt")?.takeIf { it > 0 }, workouts)
}

// Replaces all of the app's data with an unpacked backup, then restarts the app so everything is
// read afresh. A backup from an older version is upgraded as the app opens it.
fun restoreBackup(context: Context, backup: UnpackedBackup) {
    WorkoutDatabase.closeInstance()
    restoreBackupFiles(context, backup, context.getDatabasePath("fitness.db"))

    // Start the app again from scratch, so nothing still holds the old data.
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    Runtime.getRuntime().exit(0)
}

// Puts the backup's database, route pictures and settings in place. The database must be closed.
fun restoreBackupFiles(context: Context, backup: UnpackedBackup, dbPath: File) {
    val folder = backup.folder
    listOf("", "-wal", "-shm", "-journal").forEach { File(dbPath.path + it).delete() }
    File(folder, DB_ENTRY).copyTo(dbPath, overwrite = true)

    val images = File(context.filesDir, IMAGES_DIR)
    images.deleteRecursively()
    File(folder, IMAGES_DIR).takeIf { it.isDirectory }?.copyRecursively(images, overwrite = true)

    for (name in BACKED_UP_PREFS) {
        val file = File(folder, "$PREFS_DIR$name.json")
        if (file.exists()) jsonToPrefs(context, name, JSONObject(file.readText()))
    }
    folder.deleteRecursively()
}

// Before a restore replaces everything, the data as it is now is saved here, so a restore made
// by mistake can be undone.
fun safetyBackupFile(context: Context): File = File(context.filesDir, "before-restore.zip")
