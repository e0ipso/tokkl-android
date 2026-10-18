package com.github.e0ipso.tokkl.licences

/**
 * [text], a licence hard-wrapped at about 75 characters as OFL.txt ships, as the blocks between
 * its rules of dashes, with each paragraph's lines joined so that it wraps at the screen's width.
 * The rules themselves are left out; the screen draws them as dividers.
 *
 * A line still starts a new line after a blank line, after a heading such as PREAMBLE and before
 * a numbered condition such as `1)`. Blank lines stay, so paragraphs keep their gaps.
 */
internal fun licenceBlocks(text: String): List<String> =
    splitAtRules(text.replace("\r\n", "\n")).map(::reflow)

/** The blocks of [text] between the lines made only of dashes, without those lines. */
private fun splitAtRules(text: String): List<String> {
    val blocks = mutableListOf(mutableListOf<String>())
    for (line in text.split('\n')) {
        if (line.isNotEmpty() && line.all { it == '-' }) blocks += mutableListOf<String>() else blocks.last() += line
    }
    return blocks.map { it.joinToString("\n") }
}

/** [block] with each line that continues a paragraph joined to the line before it with a space. */
private fun reflow(block: String): String {
    val lines = block.split('\n').map { it.trimEnd() }
    return buildString {
        for ((i, line) in lines.withIndex()) {
            if (i > 0) append(if (continuesParagraph(lines, i)) ' ' else '\n')
            append(line)
        }
    }
}

/** Whether `lines[i]` goes on from the line before it, in the same paragraph. */
private fun continuesParagraph(lines: List<String>, i: Int): Boolean {
    val line = lines[i]
    val previous = lines[i - 1]
    if (line.isEmpty() || previous.isEmpty() || NUMBERED_CONDITION.containsMatchIn(line)) return false
    val previousStartsParagraph = i == 1 || lines[i - 2].isEmpty()
    return !(previousStartsParagraph && isHeading(previous))
}

/**
 * Whether [line], the first line of a paragraph, is a heading such as PREAMBLE or PERMISSION &
 * CONDITIONS: in capitals, and ending in a letter or a digit. The disclaimer under DISCLAIMER is
 * in capitals too, but it starts no paragraph of its own, and its first line ends in a comma.
 */
private fun isHeading(line: String): Boolean =
    line.any(Char::isLetter) && line.none(Char::isLowerCase) && line.last().isLetterOrDigit()

/** The start of a numbered condition, such as `1)`. */
private val NUMBERED_CONDITION = Regex("""^\d+\)""")
