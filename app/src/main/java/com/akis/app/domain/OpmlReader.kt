package com.akis.app.domain

import org.xmlpull.v1.XmlPullParser

/** Walks the outlines of an OPML file, remembering the folder each feed sits in. */
internal class OpmlReader {
    val found = linkedMapOf<String, OpmlEntry>()
    private val folders = ArrayDeque<String>()
    private val opened = ArrayDeque<Boolean>()
    private val groups = linkedSetOf<String>()

    fun start(parser: XmlPullParser) {
        val url = (parser.getAttributeValue(null, "xmlUrl") ?: parser.getAttributeValue(null, "xmlurl"))?.let(FeedRules::normalizeUrl)
        val title = (parser.getAttributeValue(null, "title") ?: parser.getAttributeValue(null, "text")).orEmpty().take(Limits.MAX_TITLE)
        val isFolder = url == null && title.isNotBlank()
        if (url != null) add(url, title) else if (isFolder) folders.addLast(title.trim())
        opened.addLast(isFolder)
    }

    fun end() {
        if (opened.removeLastOrNull() == true) folders.removeLastOrNull()
    }

    private fun add(url: String, title: String) {
        val folder = folders.lastOrNull().orEmpty().take(Limits.MAX_GROUP)
        val allowed = folder.isNotEmpty() && (folder in groups || groups.size < Limits.MAX_GROUPS)
        if (allowed) groups += folder
        found.putIfAbsent(url, OpmlEntry(title, url, if (allowed) folder else ""))
    }
}
