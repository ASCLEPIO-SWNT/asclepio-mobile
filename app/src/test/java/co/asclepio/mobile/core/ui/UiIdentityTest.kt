package co.asclepio.mobile.core.ui

import java.io.File
import java.security.MessageDigest
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class UiIdentityTest {
    @Test fun `R1 las diez paletas conservan los colores de marca y contraste semantico`() {
        val palettes = UiColorMode.entries.flatMap { mode -> listOf(uiPalette(mode, false), uiPalette(mode, true)) }
        assertEquals(10, palettes.size)
        assertEquals(0xFFF8FCFC, uiPalette(UiColorMode.NORMAL, false).background)
        assertEquals(0xFF34B2A7, uiPalette(UiColorMode.NORMAL, false).action)
        assertEquals(0xFF4DCBC1, uiPalette(UiColorMode.NORMAL, true).action)
        palettes.forEach { palette ->
            assertTrue(contrast(palette.foreground, palette.card) >= 4.5)
            assertTrue(contrast(palette.onAction, palette.action) >= 4.5)
            assertTrue(contrast(palette.error, palette.errorBackground) >= 4.5)
            assertTrue(contrast(palette.success, palette.successBackground) >= 4.5)
            assertTrue(contrast(palette.warning, palette.warningBackground) >= 4.5)
        }
    }

    @Test fun `R3 las fuentes nativas y licencia incluidas son los assets aprobados`() {
        val manifest = File("src/main/assets/ui-resource-sha256.txt").readLines().filter { it.isNotBlank() }
        assertEquals(9, manifest.size)
        manifest.forEach { line ->
            val (expected, path) = line.split("  ", limit = 2)
            val bytes = File("src/main/$path").readBytes()
            assertEquals(path, expected, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        for (name in listOf("axiforma_regular.ttf", "axiforma_semibold.ttf", "axiforma_bold.ttf")) {
            assertArrayEquals(byteArrayOf(0,1,0,0), File("src/main/res/font/$name").readBytes().take(4).toByteArray())
        }
        assertEquals("OTTO", File("src/main/res/font/opendyslexic_regular.otf").readBytes().take(4).toByteArray().decodeToString())
        assertTrue(File("src/main/assets/licenses/OpenDyslexic-OFL.txt").readText().contains("SIL OPEN FONT LICENSE"))
    }

    private fun contrast(a: Long, b: Long): Double {
        fun luminance(color: Long): Double {
            val channels = listOf(16,8,0).map { shift -> ((color shr shift) and 255).toDouble()/255 }.map { if (it <= .04045) it/12.92 else ((it+.055)/1.055).pow(2.4) }
            return channels[0]*.2126 + channels[1]*.7152 + channels[2]*.0722
        }
        val x=luminance(a); val y=luminance(b)
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
}
