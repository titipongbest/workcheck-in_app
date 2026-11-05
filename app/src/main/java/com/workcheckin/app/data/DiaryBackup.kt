package com.workcheckin.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.workcheckin.app.App
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * นำเข้า/ส่งออก บันทึกการทำงานเป็นไฟล์ JSON และสำรองอัตโนมัติลงโฟลเดอร์ที่ผู้ใช้เลือก
 * (Storage Access Framework) ทุกครั้งที่ออกจากแอป ถ้ามีการเปลี่ยนแปลงตั้งแต่สำรองครั้งก่อน
 */
object DiaryBackup {
    const val BACKUP_FILE_NAME = "WorkCheckIn-worklog-backup.json"
    private const val PREFS = "diary_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_FILE = "file_uri"
    private const val KEY_LAST = "last_backup"
    private const val KEY_DIRTY = "dirty"
    private const val KEY_ERROR = "last_error"

    data class Settings(
        val enabled: Boolean,
        val treeUri: Uri?,
        val folderName: String?,
        val lastBackupMillis: Long,
        val lastError: String?,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun settings(context: Context): Settings {
        val p = prefs(context)
        val tree = p.getString(KEY_TREE, null)?.let(Uri::parse)
        return Settings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            treeUri = tree,
            folderName = tree?.let { folderName(context, it) },
            lastBackupMillis = p.getLong(KEY_LAST, 0),
            lastError = p.getString(KEY_ERROR, null),
        )
    }

    fun markDirty(context: Context) {
        prefs(context).edit().putBoolean(KEY_DIRTY, true).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).putBoolean(KEY_DIRTY, true).apply()
    }

    /** เก็บสิทธิ์เข้าถึงโฟลเดอร์ไว้ถาวร แล้วเปิดการสำรองอัตโนมัติ */
    fun setFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        val old = prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)
        resolver.takePersistableUriPermission(tree, flags)
        if (old != null && old != tree) {
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        prefs(context).edit()
            .putString(KEY_TREE, tree.toString())
            .remove(KEY_FILE)
            .remove(KEY_ERROR)
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_DIRTY, true)
            .apply()
    }

    /** เรียกตอนออกจากแอป: สำรองเฉพาะเมื่อเปิดใช้งานและข้อมูลมีการเปลี่ยนแปลง */
    suspend fun autoBackup(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ENABLED, false) || p.getString(KEY_TREE, null) == null) return
        if (!p.getBoolean(KEY_DIRTY, false)) return
        backupNow(context)
    }

    /** สำรองทันที คืน null ถ้าสำเร็จ หรือข้อความผิดพลาด */
    suspend fun backupNow(context: Context): String? {
        val p = prefs(context)
        val tree = p.getString(KEY_TREE, null)?.let(Uri::parse) ?: return "ยังไม่ได้เลือกโฟลเดอร์"
        val logs = (context.applicationContext as App).repository.allWorkLogs()
        val json = toJson(logs)
        val error = runCatching {
            val file = p.getString(KEY_FILE, null)?.let(Uri::parse)
            val written = file != null && runCatching { write(context, file, json) }.isSuccess
            if (!written) {
                val target = findOrCreateFile(context, tree)
                write(context, target, json)
                p.edit().putString(KEY_FILE, target.toString()).apply()
            }
        }.exceptionOrNull()?.let { "สำรองไม่สำเร็จ: ${it.message ?: it.javaClass.simpleName}" }

        if (error == null) {
            p.edit().putLong(KEY_LAST, System.currentTimeMillis()).putBoolean(KEY_DIRTY, false).remove(KEY_ERROR).apply()
        } else {
            p.edit().putString(KEY_ERROR, error).apply()
        }
        return error
    }

    private fun write(context: Context, uri: Uri, json: String) {
        val resolver = context.contentResolver
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("เปิดไฟล์ไม่ได้")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun findOrCreateFile(context: Context, tree: Uri): Uri {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == BACKUP_FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val created = DocumentsContract.createDocument(resolver, dir, "application/json", BACKUP_FILE_NAME)
            ?: error("สร้างไฟล์ในโฟลเดอร์ไม่ได้")
        // บางที่เก็บ (เช่น Drive / มือถือบางรุ่น) อาจตัดหรือเติมนามสกุลเอง ให้ชื่อเป็น .json ตรงตัวเสมอ
        if (displayName(context, created) != BACKUP_FILE_NAME) {
            runCatching { DocumentsContract.renameDocument(resolver, created, BACKUP_FILE_NAME) }
                .getOrNull()?.let { return it }
        }
        return created
    }

    private fun displayName(context: Context, doc: Uri): String? = runCatching {
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun folderName(context: Context, tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment

    // ---- รูปแบบไฟล์ ----

    fun toJson(logs: List<WorkLog>): String {
        val arr = JSONArray()
        logs.sortedBy { it.epochDay }.forEach { log ->
            arr.put(
                JSONObject()
                    .put("date", log.date.toString())
                    .put("text", log.text)
                    .put("updatedAt", log.updatedAtMillis)
            )
        }
        return JSONObject()
            .put("app", "WorkCheckIn")
            .put("kind", "worklog")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", arr)
            .toString(2)
    }

    /** อ่านไฟล์ที่ส่งออกจากแอปนี้ (รับทั้งแบบ object ที่มี entries และ array ตรงๆ) */
    fun fromJson(json: String): List<WorkLog> {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("entries")
        val now = System.currentTimeMillis()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
            val text = o.optString("text").trim()
            if (text.isEmpty()) return@mapNotNull null
            WorkLog(epochDay = date.toEpochDay(), text = text, updatedAtMillis = o.optLong("updatedAt", now))
        }
    }
}
