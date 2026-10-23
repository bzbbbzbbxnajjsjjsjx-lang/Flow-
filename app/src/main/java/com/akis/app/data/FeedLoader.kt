package com.akis.app.data

import android.util.Xml
import com.akis.app.domain.DiscoveredFeed
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedDiscovery
import com.akis.app.domain.FeedParser
import com.akis.app.domain.FeedRules
import com.akis.app.domain.FetchOutcome
import com.akis.app.domain.HtmlCharset
import com.akis.app.domain.ParsedFeed
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Network plus parsing for one feed; blocking, call from an IO dispatcher. */
object FeedLoader {
    fun load(feed: Feed): FetchOutcome = when (val r = FeedFetcher.fetch(feed.url, feed.etag, feed.lastModified)) {
        HttpResult.NotModified -> FetchOutcome.NotModified
        is HttpResult.Failed -> FetchOutcome.Failure(r.reason)
        is HttpResult.Ok -> parse(r.body)?.let { FetchOutcome.Success(it, r.etag, r.lastModified) }
            ?: FetchOutcome.Failure("Akış okunamadı")
    }

    sealed interface Probe {
        data class IsFeed(val success: FetchOutcome.Success) : Probe
        data class Candidates(val feeds: List<DiscoveredFeed>) : Probe
        data class Failure(val reason: String) : Probe
    }

    /** Fetches an address once: it is either a feed itself, or a page that advertises feeds, or neither. */
    fun probe(url: String): Probe = when (val r = FeedFetcher.fetch(url, "", "", FeedFetcher.ACCEPT_ANY)) {
        is HttpResult.Ok -> parse(r.body)?.let { Probe.IsFeed(FetchOutcome.Success(it, r.etag, r.lastModified)) } ?: discover(r, url)
        is HttpResult.Failed -> Probe.Failure(r.reason)
        HttpResult.NotModified -> Probe.Failure("Akış okunamadı")
    }

    private fun discover(r: HttpResult.Ok, url: String): Probe {
        val html = String(r.body, HtmlCharset.pick(r.contentType, r.body))
        val base = r.finalUrl.ifEmpty { url }
        val found = FeedDiscovery.find(html, base).ifEmpty { guess(base) }
        return if (found.isEmpty()) Probe.Failure("Bu sayfada RSS akışı bulunamadı") else Probe.Candidates(found)
    }

    /** The page names no feed: try the usual paths on its host and keep those that really are feeds. */
    private fun guess(base: String): List<DiscoveredFeed> {
        val root = FeedDiscovery.origin(base) ?: return emptyList()
        return FeedDiscovery.commonPaths.mapNotNull { tryFeed(root + it) }.distinctBy { it.url }
    }

    private fun tryFeed(url: String): DiscoveredFeed? {
        val ok = FeedFetcher.fetch(url, "", "", FeedFetcher.ACCEPT_FEED) as? HttpResult.Ok
        val parsed = ok?.let { parse(it.body) }
        val address = ok?.let { FeedRules.upgradeAndNormalize(it.finalUrl) }
        return if (parsed != null && address != null) {
            DiscoveredFeed(parsed.title.ifBlank { address.substringAfter("://").substringBefore('/') }, address)
        } else {
            null
        }
    }

    fun parse(body: ByteArray): ParsedFeed? = parser(body)?.let(FeedParser::parse)

    /** A pull parser over [body], or null when the bytes cannot even be opened as XML. */
    fun parser(body: ByteArray): XmlPullParser? = try {
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(body), null) }
    } catch (_: XmlPullParserException) {
        null
    }
}
