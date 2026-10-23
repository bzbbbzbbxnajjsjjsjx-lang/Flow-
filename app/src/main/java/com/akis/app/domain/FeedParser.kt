package com.akis.app.domain

import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Reads RSS 2.0, RSS 1.0 (RDF) and Atom from an already configured pull parser. Everything is
 * bounded: at most [Limits.MAX_ITEMS_PER_FETCH] items, every text field is cut to its limit while
 * it is read, and malformed or truncated input yields null instead of a partial feed.
 */
@Suppress("TooManyFunctions")
object FeedParser {
    private const val MAX_RAW_FIELD = 120_000
    private const val MAX_RAW_CATEGORIES = 12
    private val roots = setOf("rss", "feed", "rdf:rdf", "rdf")
    private val textNames = setOf("content:encoded", "content", "description", "summary")
    private val mediaNames = setOf("media:thumbnail", "media:content", "enclosure", "itunes:image")
    private val dateNames = setOf("pubdate", "published", "updated", "dc:date", "date")

    private class Draft {
        var title = ""
        var link = ""
        var key = ""
        var summary = ""
        var content = ""
        var date = ""
        var image = ""
        val categories = mutableListOf<String>()
    }

    fun parse(parser: XmlPullParser): ParsedFeed? = try {
        Reading(parser).run()
    } catch (_: XmlPullParserException) {
        null
    } catch (_: IOException) {
        null
    }

    private class Reading(private val p: XmlPullParser) {
        private var sawRoot = false
        private var rootClosed = false
        private var channelTitle = ""
        private var siteUrl = ""
        private var draft: Draft? = null
        private val items = mutableListOf<ParsedItem>()

        fun run(): ParsedFeed? {
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT && items.size < Limits.MAX_ITEMS_PER_FETCH) {
                when (event) {
                    XmlPullParser.START_TAG -> if (!start(p.name.lowercase())) return null
                    XmlPullParser.END_TAG -> end(p.name.lowercase())
                }
                event = p.next()
            }
            val truncated = event == XmlPullParser.END_DOCUMENT && !rootClosed
            return if (!sawRoot || truncated) null else ParsedFeed(channelTitle, siteUrl, items)
        }

        /** Returns false when the document does not start like a feed at all. */
        private fun start(name: String): Boolean {
            val current = draft
            when {
                !sawRoot -> {
                    if (name !in roots) return false
                    sawRoot = true
                }
                name == "item" || name == "entry" -> draft = Draft()
                current != null -> current.take(name, p)
                name == "title" && channelTitle.isEmpty() -> channelTitle = clean(readText(p))
                name == "link" && siteUrl.isEmpty() -> siteUrl = linkOf(p).take(Limits.MAX_URL)
            }
            return true
        }

        private fun end(name: String) {
            if (name in roots) rootClosed = true
            val current = draft
            if ((name == "item" || name == "entry") && current != null) {
                toItem(current)?.let(items::add)
                draft = null
            }
        }
    }

    private fun Draft.take(name: String, p: XmlPullParser) {
        when {
            name == "title" -> title = readText(p)
            name == "link" -> takeLink(p)
            name in mediaNames -> takeMedia(name, p)
            name == "category" || name == "dc:subject" -> takeCategory(p)
            name == "guid" || name == "id" -> key = readText(p).trim()
            name == "content:encoded" || name == "content" -> content = readText(p)
            name in textNames -> summary = readText(p)
            name in dateNames -> date = readText(p)
        }
    }

    /** RSS puts the label in the element's text, Atom in its term attribute. */
    private fun Draft.takeCategory(p: XmlPullParser) {
        val term = p.getAttributeValue(null, "term")
        val text = readText(p)
        if (categories.size < MAX_RAW_CATEGORIES) categories += term ?: text
    }

    private fun Draft.takeMedia(name: String, p: XmlPullParser) {
        when (name) {
            "media:thumbnail" -> takeImage(p.getAttributeValue(null, "url"))
            "itunes:image" -> takeImage(p.getAttributeValue(null, "href"))
            else -> {
                val type = p.getAttributeValue(null, "type").orEmpty()
                val medium = p.getAttributeValue(null, "medium").orEmpty()
                if (type.startsWith("image/") || medium == "image") takeImage(p.getAttributeValue(null, "url"))
            }
        }
    }

    private fun Draft.takeImage(url: String?) {
        if (image.isEmpty() && url != null) image = url
    }

    /** RSS links are text; Atom links carry href and may be self, an image enclosure or the real address. */
    private fun Draft.takeLink(p: XmlPullParser) {
        val href = p.getAttributeValue(null, "href")
        if (href == null) {
            link = readText(p).trim()
            return
        }
        val rel = p.getAttributeValue(null, "rel")
        val type = p.getAttributeValue(null, "type").orEmpty()
        skip(p)
        if (rel == null || rel == "alternate") {
            link = href.trim()
        } else if (rel == "enclosure" && type.startsWith("image/")) {
            takeImage(href)
        }
    }

    /** Atom links carry the address in href (and may be rel=self or enclosure); RSS links are text. */
    private fun linkOf(p: XmlPullParser): String {
        val href = p.getAttributeValue(null, "href")
        if (href != null) {
            val rel = p.getAttributeValue(null, "rel")
            skip(p)
            return if (rel == null || rel == "alternate") href.trim() else ""
        }
        return readText(p).trim()
    }

    private fun toItem(d: Draft): ParsedItem? {
        val title = clean(d.title)
        val text = Html.toText(d.content.ifBlank { d.summary })
        val link = d.link.trim().take(Limits.MAX_URL)
        val key = d.key.ifBlank { link.ifBlank { title } }
        if (key.isBlank() || (title.isBlank() && text.isBlank())) return null
        val shownTitle = title.ifBlank { text.lineSequence().first().take(Limits.MAX_TITLE) }
        val image = FeedRules.normalizeImage(d.image.ifBlank { firstImage(d.content) }.ifBlank { firstImage(d.summary) }).orEmpty()
        val labels = d.categories.mapNotNull(Categories::normalize).distinct().take(Limits.MAX_CATEGORIES)
        val links = Links.extract(d.content.ifBlank { d.summary }, link)
        return ParsedItem(key.take(Limits.MAX_URL), shownTitle, link, text, FeedDates.parse(d.date), image, labels, links)
    }

    private val imgSrc = Regex("<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** The first picture in the item's own markup, entities such as &amp; undone. */
    private fun firstImage(html: String): String =
        imgSrc.find(html.take(MAX_RAW_FIELD))?.groupValues?.get(1)?.let(Html::unescape).orEmpty()

    private fun clean(raw: String): String = Html.toText(raw, Limits.MAX_TITLE).replace('\n', ' ')

    /** Collects the text of the current element, including nested XHTML, then leaves the parser on its end tag. */
    private fun readText(p: XmlPullParser): String {
        val out = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (out.length < MAX_RAW_FIELD) out.append(p.text)
                XmlPullParser.END_DOCUMENT -> return out.toString()
            }
        }
        return out.toString()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
