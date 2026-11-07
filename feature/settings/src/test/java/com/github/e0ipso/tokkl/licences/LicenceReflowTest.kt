package com.github.e0ipso.tokkl.licences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [licenceBlocks] over the OFL the app ships, res/raw/anybody_ofl.txt. */
class LicenceReflowTest {

    /** Gradle runs unit tests in the app module's directory. */
    private val shipped = File("src/main/res/raw/anybody_ofl.txt").readText()

    private val blocks = licenceBlocks(shipped)

    private val lines = blocks.flatMap { it.split('\n') }

    @Test
    fun keepsTheThreeBlocksBetweenTheRules() {
        assertEquals(3, blocks.size)
        assertEquals("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007", blocks[1])
    }

    @Test
    fun keepsEveryWordInOrder() {
        val rule = Regex("""^-+$""")
        val source = shipped.lines().filterNot { rule.matches(it) }.flatMap(::words)
        assertEquals(source, blocks.flatMap(::words))
    }

    @Test
    fun keepsTheHeadingsOnTheirOwnLines() {
        val headings = listOf("PREAMBLE", "DEFINITIONS", "PERMISSION & CONDITIONS", "TERMINATION", "DISCLAIMER")
        for (heading in headings) {
            val at = lines.indexOf(heading)
            assertTrue("$heading is not a line of its own", at >= 0)
            assertTrue("no text follows $heading on the next line", lines[at + 1].isNotBlank())
        }
    }

    @Test
    fun startsEachNumberedConditionOnItsOwnLine() {
        val conditions = lines.filter { Regex("""^\d+\)""").containsMatchIn(it) }
        assertEquals(listOf("1)", "2)", "3)", "4)", "5)"), conditions.map { it.substringBefore(' ') })
        assertEquals(
            "1) Neither the Font Software nor any of its individual components, " +
                "in Original or Modified Versions, may be sold by itself.",
            conditions.first(),
        )
    }

    @Test
    fun joinsTheLinesOfEachParagraph() {
        assertTrue(
            lines.contains(
                "The goals of the Open Font License (OFL) are to stimulate worldwide development of " +
                    "collaborative font projects, to support the font creation efforts of academic and " +
                    "linguistic communities, and to provide a free and open framework in which fonts " +
                    "may be shared and improved in partnership with others.",
            ),
        )
        // The disclaimer is in capitals, like a heading, but stays one paragraph under its heading.
        val disclaimer = lines[lines.indexOf("DISCLAIMER") + 1]
        assertTrue(disclaimer.startsWith("THE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS"))
        assertTrue(disclaimer.endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."))
    }

    /** A line ends before the next one only at a paragraph's end, after a heading or before a condition. */
    @Test
    fun breaksALineOnlyAtAParagraphEndAHeadingOrACondition() {
        for (block in blocks) {
            val blockLines = block.split('\n')
            for ((previous, line) in blockLines.zipWithNext()) {
                if (previous.isEmpty() || line.isEmpty()) continue
                val afterHeading = previous.none(Char::isLowerCase) && previous.last().isLetterOrDigit()
                val beforeCondition = Regex("""^\d+\)""").containsMatchIn(line)
                assertTrue("\"$previous\" breaks before \"$line\"", afterHeading || beforeCondition)
            }
        }
    }

    @Test
    fun joinsWithOneSpaceAndKeepsNoTrailingSpace() {
        for (line in lines) {
            assertTrue("a double space in \"$line\"", "  " !in line)
            assertEquals("a trailing space in \"$line\"", line.trimEnd(), line)
        }
    }

    private fun words(text: String): List<String> = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
}
