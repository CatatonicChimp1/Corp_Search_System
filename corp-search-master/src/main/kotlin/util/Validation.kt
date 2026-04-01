package com.example.util


fun requireTrimmed(value: String, field: String, min: Int = 1, max: Int = 5000): String {
    val v = value.trim()
    require(v.length in min..max) { "Invalid $field length" }
    return v
}

fun parseTags(raw: String?): List<String> =
    raw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?: emptyList()

fun normalizeEmail(raw: String): String {
    val v = raw.trim()
    require(v.length in 3..255) { "Invalid email length" }
    return v.lowercase()
}