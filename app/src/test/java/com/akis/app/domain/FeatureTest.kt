package com.akis.app.domain

import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class FeatureTest {
    private fun parse(xml: String) = FeedParser.parse(KXmlParser().apply { setInput(ByteArrayInputStream(xml.toByteArray()), null) })

    @Test
    fun fastReadSplitsWordsAndRoundTrips() {
        assertEquals(0, FastRead.prefixLength(0))
        assertEquals(1, FastRead.prefixLength(1))
        assertEquals(1, FastRead.prefixLength(2))
        assertEquals(2, FastRead.prefixLength(4))
        assertEquals(3, FastRead.prefixLength(6))
        val text = "Merhaba, dünya! 2026 yılı."
        val segments = FastRead.segments(text)
        assertEquals(text, segments.joinToString("") { it.first })
        assertEquals(listOf("Mer", "dü", "20", "yı"), segments.filter { it.second }.map { it.first })
        assertTrue(FastRead.segments("").isEmpty())
    }

    @Test
    fun fastReadKeepsSurrogatePairsWhole() {
        val text = "😀😀😀"
        assertEquals(text, FastRead.segments(text).joinToString("") { it.first })
    }

    @Test
    fun extractorPicksTheArticleAndDropsChrome() {
        val filler = "Bu cümle makalenin gerçek içeriğidir ve yeterince uzundur. ".repeat(3)
        val html = """<html><body><nav><p>Menü menü menü menü menü menü menü</p></nav>
            <article><h2>Alt başlık</h2><p>$filler</p><p>Kısa</p><ul><li>Liste öğesi için yeterince uzun bir metin var burada</li></ul>
            <script>var x = "<p>kötü</p>";</script></article><footer><p>Telif hakkı yazısı yeterince uzun bir alt bilgi metni</p></footer></body></html>"""
        val text = ArticleExtractor.extract(html)!!
        assertTrue(text.contains("Alt başlık"))
        assertTrue(text.contains("gerçek içeriğidir"))
        assertTrue(text.contains("• Liste öğesi"))
        assertTrue(!text.contains("Menü") && !text.contains("Telif") && !text.contains("kötü") && !text.contains("Kısa"))
    }

    @Test
    fun extractorGivesUpOnThinPagesAndCapsLength() {
        assertNull(ArticleExtractor.extract("<html><body><p>çok kısa</p></body></html>"))
        val long = (1..400).joinToString("") { "<p>Paragraf numarası $it burada yeterince uzun bir cümle olarak yazıldı.</p>" }
        val text = ArticleExtractor.extract("<main>$long</main>", limit = 1_000)!!
        assertTrue(text.length <= 1_000)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun charsetFollowsHeaderThenMetaThenUtf8() {
        assertEquals("ISO-8859-9", HtmlCharset.pick("text/html; charset=iso-8859-9", ByteArray(0)).name())
        val meta = """<meta http-equiv="x" content="text/html; charset=windows-1254">""".toByteArray()
        assertEquals("windows-1254", HtmlCharset.pick(null, meta).name())
        assertEquals("UTF-8", HtmlCharset.pick("text/html", "<html>".toByteArray()).name())
        assertEquals("UTF-8", HtmlCharset.pick("text/html; charset=bilinmeyen-kod", ByteArray(0)).name())
    }

    @Test
    fun parserFindsImagesInOrderOfPreference() {
        val rss = """<rss xmlns:media="http://search.yahoo.com/mrss/" xmlns:itunes="x"><channel><title>t</title>
            <item><title>a</title><guid>1</guid><media:thumbnail url="https://i.example/thumb.jpg"/><description>x</description></item>
            <item><title>b</title><guid>2</guid><enclosure url="https://i.example/e.png" type="image/png"/><description>x</description></item>
            <item><title>c</title><guid>3</guid><enclosure url="https://i.example/a.mp3" type="audio/mpeg"/><description>&lt;img src='http://i.example/in.jpg?a=1&amp;amp;b=2'&gt; metin</description></item>
            <item><title>d</title><guid>4</guid><description>resimsiz</description></item>
            <item><title>e</title><guid>5</guid><media:content url="https://i.example/c.jpg" medium="image"/><description>x</description></item>
            <item><title>f</title><guid>6</guid><description>&lt;img src="/goreli.jpg"&gt; x</description></item></channel></rss>"""
        val items = parse(rss)!!.items
        assertEquals("https://i.example/thumb.jpg", items[0].imageUrl)
        assertEquals("https://i.example/e.png", items[1].imageUrl)
        assertEquals("https://i.example/in.jpg?a=1&b=2", items[2].imageUrl)
        assertEquals("", items[3].imageUrl)
        assertEquals("https://i.example/c.jpg", items[4].imageUrl)
        assertEquals("", items[5].imageUrl)
    }

    @Test
    fun parserReadsAtomEnclosureAndYoutubeGroup() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/" xmlns:yt="y">
            <title>Kanal</title><link rel="alternate" href="https://www.youtube.com/channel/x"/>
            <entry><id>yt:video:abcdefghijk</id><title>Video</title><link rel="alternate" href="https://www.youtube.com/watch?v=abcdefghijk"/>
            <published>2026-10-21T07:28:00+00:00</published>
            <media:group><media:title>Video</media:title><media:thumbnail url="https://i2.ytimg.com/vi/abcdefghijk/hqdefault.jpg" width="480"/>
            <media:description>Açıklama</media:description></media:group></entry>
            <entry><id>2</id><title>Resimli</title><link rel="enclosure" type="image/jpeg" href="https://i.example/p.jpg"/>
            <link href="https://a.example/2"/><summary>s</summary></entry></feed>"""
        val items = parse(atom)!!.items
        assertEquals("https://i2.ytimg.com/vi/abcdefghijk/hqdefault.jpg", items[0].imageUrl)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", items[0].link)
        assertEquals("https://i.example/p.jpg", items[1].imageUrl)
        assertEquals("https://a.example/2", items[1].link)
    }

    @Test
    fun imageAddressesInsideCdataAreUnescaped() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>t</title><entry><id>1</id><title>Verge</title>
            <link rel="alternate" href="https://a.example/1"/>
            <content type="html"><![CDATA[<figure><img alt="x" data-a="b" src="https://i.example/p.jpg?quality=90&#038;strip=all&amp;crop=0,0" /></figure><p>Metin</p>]]></content></entry></feed>"""
        assertEquals("https://i.example/p.jpg?quality=90&strip=all&crop=0,0", parse(atom)!!.items.single().imageUrl)
        assertEquals("a & b ' é", Html.unescape("a &amp; b &#039; &#xE9;"))
    }

    @Test
    fun namedEntitiesCoverTurkishAndLatin1() {
        val page = "s&uuml;r&uuml;m&uuml;nde &ouml;nemli &ccedil;al&imath;&scedil;ma"
        assertEquals("sürümünde önemli çalışma", Html.toText(page))
        assertEquals("Çok Özel Ünlü İyi ğ ş", Html.toText("&Ccedil;ok &Ouml;zel &Uuml;nl&uuml; &Idot;yi &gbreve; &scedil;"))
        assertEquals("½ ² é ß ÿ", Html.toText("&frac12; &sup2; &eacute; &szlig; &yuml;"))
        assertEquals("a b", Html.toText("a&nbsp;b"))
        assertEquals("“x” – y…", Html.toText("&ldquo;x&rdquo; &ndash; y&hellip;"))
        assertEquals("&yok; &x1;", Html.toText("&yok; &x1;"))
    }

    @Test
    fun imageUrlsAreUpgradedOrDropped() {
        assertEquals("https://a.example/x.jpg", FeedRules.normalizeImage("http://a.example/x.jpg"))
        assertEquals("https://a.example/x.jpg", FeedRules.normalizeImage(" https://a.example/x.jpg#f "))
        assertNull(FeedRules.normalizeImage("/x.jpg"))
        assertNull(FeedRules.normalizeImage("data:image/png;base64,AAAA"))
        assertNull(FeedRules.normalizeImage("ftp://a.example/x.jpg"))
        assertNull(FeedRules.normalizeImage(""))
    }

    @Test
    fun videoIdsFromYoutubeLinks() {
        assertEquals("abcdefghijk", FeedRules.videoId("https://www.youtube.com/watch?v=abcdefghijk&t=3"))
        assertEquals("abcdefghijk", FeedRules.videoId("https://m.youtube.com/watch?feature=x&v=abcdefghijk"))
        assertEquals("abcdefghijk", FeedRules.videoId("https://youtu.be/abcdefghijk?si=1"))
        assertEquals("abcdefghijk", FeedRules.videoId("https://www.youtube.com/shorts/abcdefghijk"))
        assertNull(FeedRules.videoId("https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx"))
        assertNull(FeedRules.videoId("https://example.com/watch?v=abcdefghijk"))
        assertNull(FeedRules.videoId("not a url"))
    }

    @Test
    fun readingMinutesRoundUp() {
        assertEquals(0, FeedRules.readingMinutes("   "))
        assertEquals(1, FeedRules.readingMinutes("tek"))
        assertEquals(1, FeedRules.readingMinutes("a ".repeat(200)))
        assertEquals(2, FeedRules.readingMinutes("a ".repeat(201)))
    }

    private fun article(i: Int, text: String = "", starred: Boolean = false) =
        Article("a$i", "f", "T$i", publishedMillis = i.toLong(), fullText = text, starred = starred)

    @Test
    fun fullTextIsKeptButBounded() {
        val articles = (1..60).map { article(it, text = "x") }
        val data = FeedRules.setFullText(AppData(articles = articles), "a60", "y".repeat(40_000))
        assertEquals(Limits.MAX_FULL_TEXTS, data.articles.count { it.fullText.isNotEmpty() })
        assertEquals(Limits.MAX_FULL_TEXT, data.articles.first { it.id == "a60" }.fullText.length)
        assertTrue(data.articles.first { it.id == "a1" }.fullText.isEmpty())
        val starred = (1..60).map { article(it, text = "x", starred = it == 1) }
        assertEquals("x", FeedRules.setFullText(AppData(articles = starred), "a60", "y").articles.first { it.id == "a1" }.fullText)
    }

    @Test
    fun mergeKeepsFullTextAndImage() {
        val feedId = "f"
        val item = ParsedItem("k", "T", "https://a.example/1", "m", 5, "https://i.example/1.jpg")
        val first = FeedRules.merge(emptyList(), feedId, listOf(item), now = 10)
        val withText = first.map { it.copy(fullText = "tam") }
        val again = FeedRules.merge(withText, feedId, listOf(ParsedItem("k", "T", "https://a.example/1", "m2", 5, "")), now = 20)
        assertEquals("tam", again.single().fullText)
        assertEquals("https://i.example/1.jpg", again.single().imageUrl)
        assertEquals("m2", again.single().text)
    }

    @Test
    fun settingsAreClampedAndOldFilesStillLoad() {
        val bad = FeedRules.sanitize(Settings(textStep = 9, dailyLimit = 7, breakMinutes = 99))
        assertEquals(3, bad.textStep)
        assertEquals(30, bad.dailyLimit)
        assertEquals(30, bad.breakMinutes)
        val none = Settings(textStep = 0, dailyLimit = 0, breakMinutes = 0)
        assertEquals(none, FeedRules.sanitize(none.copy(textStep = -4)))
        val legacy = """{"feeds":[{"id":"1","url":"https://a.example/f"}],
            "articles":[{"id":"x","feedId":"1","title":"T"}],"lastRefreshMillis":5}"""
        val data = Json { ignoreUnknownKeys = true }.decodeFromString(AppData.serializer(), legacy)
        assertEquals(Settings(), data.settings)
        assertEquals("", data.articles.single().imageUrl)
        assertEquals(5L, data.lastRefreshMillis)
    }
}
