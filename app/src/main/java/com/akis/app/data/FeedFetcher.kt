package com.akis.app.data

import com.akis.app.domain.FeedRules
import com.akis.app.domain.Limits
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed interface HttpResult {
    data object NotModified : HttpResult
    data class Ok(
        val body: ByteArray,
        val etag: String,
        val lastModified: String,
        val contentType: String = "",
        val finalUrl: String = "",
    ) : HttpResult
    data class Failed(val reason: String) : HttpResult
}

/**
 * One bounded HTTPS GET: timeouts, a body cap, conditional headers, and redirects that are followed
 * by hand so that a redirect can never downgrade to plain http.
 */
object FeedFetcher {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 5
    private const val HTTP_OK = 200
    private const val HTTP_NOT_MODIFIED = 304
    private val redirects = setOf(301, 302, 303, 307, 308)
    const val ACCEPT_FEED = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5"
    const val ACCEPT_ANY = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, text/html;q=0.7, */*;q=0.5"
    const val ACCEPT_HTML = "text/html, application/xhtml+xml;q=0.9, */*;q=0.5"
    const val ACCEPT_IMAGE = "image/webp, image/png, image/jpeg, image/*;q=0.8"

    private sealed interface Step {
        data class Done(val result: HttpResult) : Step
        data class Redirect(val url: String) : Step
    }

    fun fetch(url: String, etag: String, lastModified: String, accept: String = ACCEPT_FEED): HttpResult {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            when (val step = once(current, etag, lastModified, accept)) {
                is Step.Done -> return step.result
                is Step.Redirect -> current = step.url
            }
        }
        return HttpResult.Failed("Çok fazla yönlendirme")
    }

    private fun once(url: String, etag: String, lastModified: String, accept: String): Step = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Akis/1.0 (RSS reader)")
            conn.setRequestProperty("Accept", accept)
            if (etag.isNotEmpty()) conn.setRequestProperty("If-None-Match", etag)
            if (lastModified.isNotEmpty()) conn.setRequestProperty("If-Modified-Since", lastModified)
            respond(conn, url)
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        Step.Done(HttpResult.Failed(describe(e)))
    }

    private fun respond(conn: HttpURLConnection, url: String): Step = when (val code = conn.responseCode) {
        HTTP_OK -> Step.Done(readBody(conn, url))
        HTTP_NOT_MODIFIED -> Step.Done(HttpResult.NotModified)
        in redirects -> redirectTarget(conn.getHeaderField("Location"), url)
        else -> Step.Done(HttpResult.Failed("HTTP $code"))
    }

    private fun redirectTarget(location: String?, from: String): Step {
        val target = try {
            location?.let { FeedRules.normalizeUrl(URI(from).resolve(it).toString()) }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return if (target == null) Step.Done(HttpResult.Failed("Güvenli olmayan yönlendirme")) else Step.Redirect(target)
    }

    private fun readBody(conn: HttpURLConnection, url: String): HttpResult {
        val body = if (conn.contentLengthLong > Limits.MAX_BODY_BYTES) null else conn.inputStream.use { it.readUpTo(Limits.MAX_BODY_BYTES) }
        return if (body == null) {
            HttpResult.Failed("Yanıt çok büyük")
        } else {
            HttpResult.Ok(
                body,
                conn.getHeaderField("ETag").orEmpty(),
                conn.getHeaderField("Last-Modified").orEmpty(),
                conn.contentType.orEmpty(),
                url,
            )
        }
    }

    private fun describe(e: IOException): String = when (e) {
        is SocketTimeoutException -> "Zaman aşımı"
        is UnknownHostException -> "Sunucu bulunamadı"
        is SSLException -> "Güvenli bağlantı kurulamadı"
        else -> "Bağlantı hatası"
    }
}
