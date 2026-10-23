package com.akis.app.domain

/** A well-known feed the "Keşfet" screen offers; every address below was fetched and answered with a feed. */
data class CatalogFeed(val category: String, val name: String, val url: String)

object Catalog {
    val feeds: List<CatalogFeed> = listOf(
        CatalogFeed("Teknoloji", "Webtekno", "https://www.webtekno.com/rss.xml"),
        CatalogFeed("Teknoloji", "ShiftDelete", "https://shiftdelete.net/feed"),
        CatalogFeed("Teknoloji", "DonanımHaber", "https://www.donanimhaber.com/rss/tum/"),
        CatalogFeed("Teknoloji", "Technopat", "https://www.technopat.net/feed/"),
        CatalogFeed("Teknoloji", "LOG", "https://www.log.com.tr/feed/"),
        CatalogFeed("Teknoloji", "The Verge", "https://www.theverge.com/rss/index.xml"),
        CatalogFeed("Teknoloji", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"),
        CatalogFeed("Teknoloji", "Engadget", "https://www.engadget.com/rss.xml"),
        CatalogFeed("Teknoloji", "TechCrunch", "https://techcrunch.com/feed/"),
        CatalogFeed("Teknoloji", "Wired", "https://www.wired.com/feed/rss"),
        CatalogFeed("Teknoloji", "9to5Google", "https://9to5google.com/feed/"),
        CatalogFeed("Teknoloji", "Android Authority", "https://www.androidauthority.com/feed/"),
        CatalogFeed("Teknoloji", "Hacker News", "https://hnrss.org/frontpage"),
        CatalogFeed("Haber", "BBC Türkçe", "https://feeds.bbci.co.uk/turkce/rss.xml"),
        CatalogFeed("Haber", "DW Türkçe", "https://rss.dw.com/rdf/rss-tur-all"),
        CatalogFeed("Haber", "TRT Haber", "https://www.trthaber.com/manset.rss"),
        CatalogFeed("Haber", "Anadolu Ajansı", "https://www.aa.com.tr/tr/rss/default?cat=guncel"),
        CatalogFeed("Haber", "Hürriyet", "https://www.hurriyet.com.tr/rss/gundem"),
        CatalogFeed("Haber", "Cumhuriyet", "https://www.cumhuriyet.com.tr/rss/son_dakika.xml"),
        CatalogFeed("Haber", "NTV", "https://www.ntv.com.tr/gundem.rss"),
        CatalogFeed("Haber", "Habertürk", "https://www.haberturk.com/rss"),
        CatalogFeed("Haber", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml"),
        CatalogFeed("Haber", "The Guardian", "https://www.theguardian.com/world/rss"),
        CatalogFeed("Haber", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
        CatalogFeed("Bilim", "NASA", "https://www.nasa.gov/rss/dyn/breaking_news.rss"),
        CatalogFeed("Bilim", "ScienceDaily", "https://www.sciencedaily.com/rss/all.xml"),
        CatalogFeed("Bilim", "Nature", "https://www.nature.com/nature.rss"),
        CatalogFeed("Bilim", "Quanta Magazine", "https://api.quantamagazine.org/feed/"),
        CatalogFeed("Bilim", "Popular Science", "https://www.popsci.com/feed/"),
        CatalogFeed("Bilim", "Evrim Ağacı", "https://evrimagaci.org/rss.xml"),
        CatalogFeed("Oyun", "IGN", "https://feeds.ign.com/ign/all"),
        CatalogFeed("Oyun", "Eurogamer", "https://www.eurogamer.net/feed"),
        CatalogFeed("Oyun", "Kotaku", "https://kotaku.com/rss"),
        CatalogFeed("Ekonomi", "Bloomberg HT", "https://www.bloomberght.com/rss"),
        CatalogFeed("Ekonomi", "Dünya", "https://www.dunya.com/rss"),
        CatalogFeed("Ekonomi", "The Economist", "https://www.economist.com/finance-and-economics/rss.xml"),
        CatalogFeed("Spor", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml"),
        CatalogFeed("Spor", "ESPN", "https://www.espn.com/espn/rss/news"),
        CatalogFeed("Güvenlik", "Krebs on Security", "https://krebsonsecurity.com/feed/"),
        CatalogFeed("Güvenlik", "The Hacker News", "https://feeds.feedburner.com/TheHackersNews"),
        CatalogFeed("Güvenlik", "BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
        CatalogFeed("Güvenlik", "Schneier on Security", "https://www.schneier.com/feed/atom/"),
        CatalogFeed("Güvenlik", "EFF", "https://www.eff.org/rss/updates.xml"),
        CatalogFeed("Yazılım", "Kotlin Blog", "https://blog.jetbrains.com/kotlin/feed/"),
        CatalogFeed("Yazılım", "Android Developers Blog", "https://feeds.feedburner.com/blogspot/hsDu"),
        CatalogFeed("Yazılım", "Martin Fowler", "https://martinfowler.com/feed.atom"),
        CatalogFeed("Yazılım", "Simon Willison", "https://simonwillison.net/atom/everything/"),
        CatalogFeed("Yazılım", "GitHub Blog", "https://github.blog/feed/"),
        CatalogFeed("Kültür", "xkcd", "https://xkcd.com/atom.xml"),
        CatalogFeed("Kültür", "Open Culture", "https://www.openculture.com/feed"),
    )

    /** The categories in the order they first appear, with their feeds. */
    val byCategory: List<Pair<String, List<CatalogFeed>>> = feeds.groupBy { it.category }.toList()

    /** The catalog feeds of one category as OPML entries; the category becomes the group, so the lists stay tidy. */
    fun entries(category: String): List<OpmlEntry> =
        feeds.filter { it.category == category }.map { OpmlEntry(it.name, it.url, it.category) }
}
