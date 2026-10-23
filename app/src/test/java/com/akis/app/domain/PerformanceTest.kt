package com.akis.app.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

/**
 * Worst-case budgets on the JVM: the biggest data the limits allow. Budgets are loose on purpose (CI
 * machines vary); a failure here means an operation went from "instant" to "noticeable".
 */
class PerformanceTest {
    private val hour = 3_600_000L
    private val runs = 3
    private val words = "android sürüm güncelleme telefon uygulama haber yazılım güvenlik oyun ekonomi bilim uzay spor dünya sağlık".split(' ')

    private fun article(i: Int): Article {
        val title = (0 until 8).joinToString(" ") { words[(i * 7 + it * 3) % words.size] } + " $i"
        val text = (0 until 400).joinToString(" ") { words[(i + it) % words.size] }
        return Article(
            "id$i", "feed${i % Limits.MAX_FEEDS}", title, "https://e.com/$i", text, 10_000 * hour - i * hour,
            read = i % 3 == 0, starred = i % 50 == 0, later = i % 40 == 0,
            categories = listOf(words[i % words.size], words[(i + 4) % words.size]),
        )
    }

    private val feeds = (0 until Limits.MAX_FEEDS).map { Feed("feed$it", "https://f$it.com/rss", "Kaynak $it") }
    private val articles = (0 until Limits.MAX_ARTICLES).map(::article)
    private val data = AppData(feeds = feeds, articles = articles, settings = Settings(mutedWords = listOf("uzay", "borsa")))

    private inline fun <T> timed(name: String, budgetMs: Long, block: () -> T): T {
        block() // warm up the JIT, so the budget measures steady state
        // Best of three: another process on a busy CI machine must not fail a budget on its own.
        var ms = Long.MAX_VALUE
        var result = block()
        repeat(runs) {
            val start = System.nanoTime()
            result = block()
            ms = minOf(ms, (System.nanoTime() - start) / 1_000_000)
        }
        println("PERF $name: $ms ms (budget $budgetMs ms)")
        assertTrue("$name took $ms ms, budget $budgetMs ms", ms <= budgetMs)
        return result
    }

    @Test
    fun homeListPipelineIsInstantOnFullData() {
        timed("mute+filter+category", 150) {
            val visible = ReaderRules.withoutMuted(articles, data.settings.mutedWords)
            val narrowed = Categories.filter(visible, "haber")
            FeedRules.filter(narrowed, Filter.UNREAD)
            Categories.top(visible)
        }
    }

    @Test
    fun similarStoryDetectionScalesOnFullData() {
        val (shown, _) = timed("similar.collapse x1000", 600) { Similar.collapse(articles) }
        assertTrue(shown.isNotEmpty() && shown.size <= articles.size)
    }

    @Test
    fun searchAndSummaryStayFast() {
        timed("search", 250) { ReaderRules.search(articles, "güvenlik") }
        val log = List(Limits.MAX_READ_LOG) { ReadEntry(it % 400L, 5, "feed${it % 20}") }
        timed("week summary", 50) { ReaderRules.weekSummary(log, java.time.LocalDate.now()) }
    }

    @Test
    fun mergeOfAFullRefreshIsBounded() {
        val items = (0 until Limits.MAX_ITEMS_PER_FETCH).map { ParsedItem("n$it", "Başlık $it", "https://e.com/n$it", "metin", null) }
        val merged = timed("merge 1000+100", 150) { FeedRules.merge(articles, "feed1", items, 10_000 * hour) }
        assertTrue(merged.size <= Limits.MAX_ARTICLES)
        val refreshed = timed("applyFetch", 200) {
            var d = data
            repeat(20) { f -> d = FeedRules.applyFetch(d, "feed$f", FetchOutcome.Success(ParsedFeed("t", "", items), "", ""), 10_000 * hour) }
            d
        }
        assertTrue(refreshed.articles.size <= Limits.MAX_ARTICLES)
    }

    @Test
    fun storageJsonStaysSmallAndFast() {
        val text = timed("encode", 400) { Backup.encode(data) }
        val kb = text.length / 1024
        println("PERF stored json: $kb KB for ${articles.size} stories and ${feeds.size} sources")
        assertTrue("stored data is $kb KB", kb <= 4_000)
        val back = timed("restore", 800) { Backup.restore(text) }
        assertTrue(back != null && back.articles.size == articles.size)
    }

    @Test
    fun parserAndTextToolsHandleBigInputs() {
        val body = (0 until 150).joinToString("") { "<p>${words.joinToString(" ")} &uuml;&ouml; <b>kalın</b></p>" }
        val xml = "<rss><channel><title>t</title>" + (0 until Limits.MAX_ITEMS_PER_FETCH).joinToString("") {
            "<item><title>Başlık $it</title><link>https://e.com/$it</link><category>Tech</category>" +
                "<description><![CDATA[$body]]></description></item>"
        } + "</channel></rss>"
        println("PERF feed size: ${xml.length / 1024} KB")
        val feed = timed("parse 100 items", 900) { FeedParser.parse(KXmlParser().apply { setInput(ByteArrayInputStream(xml.toByteArray()), null) }) }
        assertTrue(feed != null && feed.items.size == Limits.MAX_ITEMS_PER_FETCH)
        timed("html.toText 100KB", 100) { Html.toText(body) }
        val text = (0 until 6_000).joinToString(" ") { words[it % words.size] }
        timed("fastread 6000 words", 100) { FastRead.segments(text) }
        timed("reading minutes", 20) { FeedRules.readingMinutes(text) }
        val page = "<html><body><article>" + body.repeat(3) + "</article></body></html>"
        timed("article extract", 300) { ArticleExtractor.extract(page) }
    }

    @Test
    fun cleanupAndAutoReadOnFullData() {
        val busy = data.copy(settings = data.settings.copy(cleanup = true, autoRead = true))
        timed("tidy", 100) { ReaderRules.tidy(busy, 20_000 * hour) }
    }
}
