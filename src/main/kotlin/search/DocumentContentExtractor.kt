package com.example.search

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.File
import java.io.FileInputStream

data class ExtractionResult(
    val text: String,
    val contentType: String? = null
)

object DocumentContentExtractor {
    private const val MAX_TEXT_CHARS = 250_000

    fun extract(file: File): ExtractionResult? {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null

        return runCatching {
            val parser = AutoDetectParser()
            val metadata = Metadata()
            val handler = BodyContentHandler(-1)
            val context = ParseContext()

            FileInputStream(file).use { input ->
                parser.parse(input, handler, metadata, context)
            }

            ExtractionResult(
                text = normalize(handler.toString()),
                contentType = metadata.get(Metadata.CONTENT_TYPE)
            )
        }.getOrNull()
    }

    private fun normalize(raw: String): String =
        raw
            .replace('\u0000', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TEXT_CHARS)
}
