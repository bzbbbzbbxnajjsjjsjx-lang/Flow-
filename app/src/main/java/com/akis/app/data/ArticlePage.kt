package com.akis.app.data

import com.akis.app.domain.ArticleExtractor
import com.akis.app.domain.FeedRules
import com.akis.app.domain.HtmlCharset

sealed interface PageResult {
    data class Text(val text: String) : PageResult
    data class Failure(val reason: String) : PageResult
}

/** Fetches an article's own page over HTTPS and reduces it to plain text; blocking, call from IO. */
object ArticlePage {
    fun load(link: String): PageResult {
        val url = FeedRules.normalizeUrl(link) ?: return PageResult.Failure("Bağlantı güvenli değil")
        return when (val r = FeedFetcher.fetch(url, "", "", FeedFetcher.ACCEPT_HTML)) {
            is HttpResult.Ok -> {
                val html = String(r.body, HtmlCharset.pick(r.contentType, r.body))
                ArticleExtractor.extract(html)?.let { PageResult.Text(it) } ?: PageResult.Failure("Sayfadan metin çıkarılamadı")
            }
            is HttpResult.Failed -> PageResult.Failure(r.reason)
            HttpResult.NotModified -> PageResult.Failure("Sayfa boş döndü")
        }
    }
}
