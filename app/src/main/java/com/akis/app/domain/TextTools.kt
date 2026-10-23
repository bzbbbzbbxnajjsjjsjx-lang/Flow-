package com.akis.app.domain

import java.nio.charset.Charset
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Turns feed HTML into bounded plain text; nothing here can run a script or load a resource. */
object Html {
    private const val MAX_RAW = 100_000
    private const val HEX_RADIX = 16
    private val comments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val blocks = Regex("<(script|style)[^>]*>.*?</\\1\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val lineBreaks = Regex("<br\\s*/?>|</(li|tr)\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphBreaks = Regex("</(p|div|h[1-6]|blockquote|ul|ol|table)\\s*>", RegexOption.IGNORE_CASE)
    private val bullets = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]*>")
    private val entity = Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{1,9});")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val manyBlankLines = Regex("\\n{3,}")

    private const val LATIN1_START = 160

    /** HTML 4 Latin-1 names in code point order, starting at U+00A0. */
    private val latin1 = (
        "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn sup2 sup3 acute micro " +
            "para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil " +
            "Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave " +
            "Uacute Ucirc Uuml Yacute THORN szlig agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml " +
            "igrave iacute icirc iuml eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute " +
            "thorn yuml"
        ).split(' ')

    private val named: Map<String, String> = buildMap {
        put("amp", "&")
        put("lt", "<")
        put("gt", ">")
        put("quot", "\"")
        put("apos", "'")
        latin1.forEachIndexed { i, name -> put(name, (LATIN1_START + i).toChar().toString()) }
        // Turkish letters outside Latin-1, and the punctuation feeds use most.
        mapOf(
            "gbreve" to "ğ", "Gbreve" to "Ğ", "scedil" to "ş", "Scedil" to "Ş", "Idot" to "İ", "imath" to "ı", "inodot" to "ı",
            "hellip" to "…", "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“",
            "rdquo" to "”", "bdquo" to "„", "bull" to "•", "trade" to "™", "euro" to "€", "larr" to "←", "rarr" to "→",
            "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        ).forEach { (k, v) -> put(k, v) }
    }

    fun toText(html: String, limit: Int = Limits.MAX_TEXT): String {
        var s = html.take(MAX_RAW)
        if ('<' in s) {
            s = comments.replace(s, "")
            s = blocks.replace(s, "")
            s = lineBreaks.replace(s, "\n")
            s = paragraphBreaks.replace(s, "\n\n")
            s = bullets.replace(s, "• ")
            s = tags.replace(s, "")
        }
        s = unescape(s)
        val cleaned = s.replace("\r", "").lines().joinToString("\n") { spaces.replace(it, " ").trim() }
        return manyBlankLines.replace(cleaned, "\n\n").trim().let { if (it.length > limit) it.take(limit - 1) + "…" else it }
    }

    /** Undoes HTML entities (&amp;, &#038;, &#x26;, &nbsp; ...); unknown ones stay as written. */
    fun unescape(s: String): String = if ('&' in s) entity.replace(s) { decode(it.groupValues[1]) } else s

    private fun decode(body: String): String = when {
        body.startsWith("#x") -> body.drop(2).toIntOrNull(HEX_RADIX)?.let(::codePoint)
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePoint)
        else -> named[body]
    } ?: "&$body;"

    private fun codePoint(value: Int): String? =
        if (Character.isValidCodePoint(value) && value != 0) String(Character.toChars(value)) else null
}

/** Feed dates come as RFC 822 (RSS) or ISO 8601 (Atom); anything else is "no date". */
object FeedDates {
    private val formatters = listOf(DateTimeFormatter.RFC_1123_DATE_TIME)

    fun parse(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: parseRfc(s)
    }

    private fun parseRfc(s: String): Long? = formatters.firstNotNullOfOrNull { f ->
        try {
            ZonedDateTime.parse(s.replace(Regex("\\s+"), " "), f.withLocale(Locale.ENGLISH)).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Bold the first letters of every word so the eye can skim; pieces always join back to the original text. */
object FastRead {
    private val word = Regex("[\\p{L}\\p{N}]+")
    private const val SHARE_NUM = 2
    private const val SHARE_DEN = 5

    /** How many of [length] letters are emphasised: 40 %, rounded up. */
    fun prefixLength(length: Int): Int = if (length <= 1) length else (length * SHARE_NUM + SHARE_DEN - 1) / SHARE_DEN

    fun segments(text: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        var last = 0
        for (m in word.findAll(text)) {
            if (m.range.first > last) out += text.substring(last, m.range.first) to false
            val w = m.value
            val cut = w.offsetByCodePoints(0, prefixLength(w.codePointCount(0, w.length)))
            out += w.substring(0, cut) to true
            if (cut < w.length) out += w.substring(cut) to false
            last = m.range.last + 1
        }
        if (last < text.length) out += text.substring(last) to false
        return out
    }
}

/** Picks the main text out of an article page without running anything: markup in, plain paragraphs out. */
object ArticleExtractor {
    private const val MAX_RAW = 1_000_000
    private const val MIN_TEXT = 200
    private const val MIN_PARAGRAPH = 25
    private val flags = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val comments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val junk = Regex("<(script|style|noscript|svg|iframe|form|nav|footer|header|aside|figure|button|select)\\b[^>]*>.*?</\\1\\s*>", flags)
    private val article = Regex("<article\\b[^>]*>(.*?)</article>", flags)
    private val main = Regex("<main\\b[^>]*>(.*?)</main>", flags)
    private val blocks = Regex("<(p|h[2-4]|blockquote|li)\\b[^>]*>(.*?)</\\1\\s*>", flags)

    /** Null when no region holds enough text to be an article. */
    fun extract(html: String, limit: Int = Limits.MAX_FULL_TEXT): String? {
        val cleaned = junk.replace(comments.replace(html.take(MAX_RAW), ""), "")
        val biggestArticle = article.findAll(cleaned).map { it.groupValues[1] }.maxByOrNull { it.length }
        val candidates = listOfNotNull(biggestArticle, main.find(cleaned)?.groupValues?.get(1), cleaned)
        return candidates.asSequence().map { collect(it, limit) }.firstOrNull { it.length >= MIN_TEXT }
    }

    private fun collect(region: String, limit: Int): String {
        val parts = mutableListOf<String>()
        var total = 0
        for (m in blocks.findAll(region)) {
            val tag = m.groupValues[1].lowercase()
            val text = Html.toText(m.groupValues[2], limit).let { if (tag == "li") "• $it" else it }
            val keep = text.length >= MIN_PARAGRAPH || (tag.startsWith("h") && text.isNotBlank())
            // A nested block (a paragraph inside a list item) repeats its parent's text; skip it.
            if (keep && parts.lastOrNull()?.contains(text.removePrefix("• ")) != true) {
                parts += text
                total += text.length
                if (total >= limit) break
            }
        }
        return parts.joinToString("\n\n").let { if (it.length > limit) it.take(limit - 1) + "…" else it }
    }
}

/** Chooses how to decode a page: the Content-Type header, then a meta tag near the top, then UTF-8. */
object HtmlCharset {
    private const val HEAD_BYTES = 2_048
    private val declared = Regex("charset=\\s*[\"']?([A-Za-z0-9_\\-:.]+)", RegexOption.IGNORE_CASE)

    fun pick(contentType: String?, head: ByteArray): Charset {
        val name = contentType?.let { declared.find(it)?.groupValues?.get(1) }
            ?: declared.find(String(head.copyOf(minOf(head.size, HEAD_BYTES)), Charsets.ISO_8859_1))?.groupValues?.get(1)
        return name?.let {
            try {
                Charset.forName(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: Charsets.UTF_8
    }
}
