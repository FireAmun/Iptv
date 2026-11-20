package com.fireamun.iptv.core.designsystem

import android.content.Context

/** Bundled, offline-readable legal documents. The same text is published on the project's website. */
enum class LegalDocument(internal val file: String) { TERMS("terms"), PRIVACY("privacy"), NOTICES("notices") }

object AmarLegal {
    /** Raise when the Terms change meaningfully; users then accept the new version once. */
    const val TERMS_VERSION = 1
    const val SUPPORT_EMAIL = "gamerammar343@gmail.com"
}

sealed interface LegalBlock {
    val text: String
    data class Title(override val text: String) : LegalBlock
    data class Heading(override val text: String) : LegalBlock
    data class Paragraph(override val text: String) : LegalBlock
    data class Bullet(override val text: String) : LegalBlock
    /** One paragraph of a bundled license file. */
    data class License(override val text: String) : LegalBlock
}

/** Reads the small Markdown subset used by the bundled documents: `#`, `##`, `- ` and paragraphs. */
fun parseLegalMarkdown(source: String): List<LegalBlock> {
    val blocks = mutableListOf<LegalBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += LegalBlock.Paragraph(paragraph.joinToString(" "))
        paragraph.clear()
    }
    source.lineSequence().map { it.trim() }.forEach { line ->
        when {
            line.isEmpty() -> flush()
            line.startsWith("## ") -> { flush(); blocks += LegalBlock.Heading(line.removePrefix("## ").trim()) }
            line.startsWith("# ") -> { flush(); blocks += LegalBlock.Title(line.removePrefix("# ").trim()) }
            line.startsWith("- ") -> { flush(); blocks += LegalBlock.Bullet(line.removePrefix("- ").trim()) }
            else -> paragraph += line
        }
    }
    flush()
    return blocks
}

/** Splits a plain license file at blank lines and reflows each paragraph to fit any screen; the words are unchanged. */
fun licenseBlocks(source: String): List<LegalBlock> =
    source.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() }.map { LegalBlock.License(it) }

private val noticeLicenses = listOf(
    "Apache License 2.0" to "licenses/Apache-2.0.txt",
    "Manrope · SIL Open Font License 1.1" to "licenses/Manrope-OFL.txt",
    "Cairo · SIL Open Font License 1.1" to "licenses/Cairo-OFL.txt",
)

/** [arabic] is true only when the Arabic text was found; English fallback text must be laid out left to right. */
class LegalText(val blocks: List<LegalBlock>, val arabic: Boolean)

/** Arabic falls back to English where no translation is bundled (the open-source notices). */
fun loadLegalDocument(context: Context, document: LegalDocument, arabic: Boolean): LegalText {
    val assets = context.assets
    fun read(path: String) = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    val translated = arabic && runCatching { assets.list("legal/ar")?.contains("${document.file}.md") == true }.getOrDefault(false)
    val blocks = parseLegalMarkdown(read("legal/${if (translated) "ar" else "en"}/${document.file}.md")).toMutableList()
    if (document == LegalDocument.NOTICES) noticeLicenses.forEach { (title, path) ->
        blocks += LegalBlock.Heading(title)
        blocks += licenseBlocks(read(path))
    }
    return LegalText(blocks, translated)
}
