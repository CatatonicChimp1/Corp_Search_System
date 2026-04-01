package com.example.storage

import java.io.File

object FileStorage {
    const val DEFAULT_UPLOADS_DIR = "data/uploads"
    private const val FILE_PREFIX = "FILE:"

    fun ensureUploadsDir(storageDirPath: String = DEFAULT_UPLOADS_DIR): File =
        File(storageDirPath).apply { mkdirs() }

    fun isStoredFileReference(body: String): Boolean =
        body.startsWith(FILE_PREFIX)

    fun extractStoredFileName(body: String): String? =
        body.removePrefix(FILE_PREFIX).trim().takeIf { isStoredFileReference(body) && it.isNotBlank() }

    fun resolveStoredFile(body: String, storageDirPath: String = DEFAULT_UPLOADS_DIR): File? {
        val fileName = extractStoredFileName(body) ?: return null
        val file = File(ensureUploadsDir(storageDirPath), fileName).canonicalFile
        val root = ensureUploadsDir(storageDirPath).canonicalFile
        return file.takeIf { it.path.startsWith(root.path) }
    }

    fun presentableFileName(storedFileName: String): String =
        storedFileName.substringAfter('_', storedFileName)
}
