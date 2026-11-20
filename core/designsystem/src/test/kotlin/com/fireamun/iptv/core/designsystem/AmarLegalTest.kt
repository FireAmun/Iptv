package com.fireamun.iptv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AmarLegalTest {
    private val assets = File("src/main/assets")

    @Test fun parsesTheBundledMarkdownSubset() {
        val blocks = parseLegalMarkdown("# Title\n\nVersion 1\n\n## Section\n\nFirst line\ncontinues here.\n\n- one\n- two\n")
        assertEquals(listOf(
            LegalBlock.Title("Title"), LegalBlock.Paragraph("Version 1"), LegalBlock.Heading("Section"),
            LegalBlock.Paragraph("First line continues here."), LegalBlock.Bullet("one"), LegalBlock.Bullet("two"),
        ), blocks)
    }

    @Test fun licenseTextIsReflowedPerBlankLineSeparatedParagraph() {
        val blocks = licenseBlocks("   Apache License\n   Version 2.0\r\n\r\n\n   1. Definitions.\r\n")
        assertEquals(listOf(LegalBlock.License("Apache License Version 2.0"), LegalBlock.License("1. Definitions.")), blocks)
    }

    @Test fun englishAndArabicDocumentsMatchSectionForSection() {
        for (name in listOf("terms", "privacy")) {
            val english = parseLegalMarkdown(File(assets, "legal/en/$name.md").readText())
            val arabic = parseLegalMarkdown(File(assets, "legal/ar/$name.md").readText())
            assertEquals("$name block structure", english.map { it::class }, arabic.map { it::class })
            for (document in listOf(english, arabic)) {
                assertTrue("$name names the current Terms version", document.any { it.text.contains("${AmarLegal.TERMS_VERSION}") })
                assertTrue("$name gives the support contact", document.any { it.text.contains(AmarLegal.SUPPORT_EMAIL) })
            }
        }
    }

    @Test fun noticesShipEveryReferencedLicenseText() {
        for (path in listOf("legal/en/notices.md", "licenses/Apache-2.0.txt", "licenses/Manrope-OFL.txt", "licenses/Cairo-OFL.txt"))
            assertTrue(path, File(assets, path).readText().isNotBlank())
    }
}
