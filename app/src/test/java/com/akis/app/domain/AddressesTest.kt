package com.akis.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressesTest {
    private fun url(input: String) = (Addresses.convert(input) as Addresses.Result.Converted).url

    @Test
    fun youtubeChannelAndPlaylist() {
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=UC123", url("https://www.youtube.com/channel/UC123"))
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=UC123", url("m.youtube.com/channel/UC123"))
        assertEquals("https://www.youtube.com/feeds/videos.xml?playlist_id=PL9", url("https://youtube.com/playlist?list=PL9&si=x"))
        assertNull(Addresses.convert("https://www.youtube.com/@handle"))
    }

    @Test
    fun socialSitesWithFixedPatterns() {
        assertEquals("https://www.reddit.com/r/android/.rss", url("https://www.reddit.com/r/android/top/"))
        assertEquals("https://www.reddit.com/user/bob/.rss", url("reddit.com/user/bob"))
        assertNull(Addresses.convert("https://www.reddit.com/r/android/.rss"))
        assertEquals("https://mastodon.social/@ali.rss", url("https://mastodon.social/@ali"))
        assertEquals("https://bsky.app/profile/ali.bsky.social/rss", url("https://bsky.app/profile/ali.bsky.social"))
        assertEquals("https://github.com/a/b/releases.atom", url("https://github.com/a/b"))
        assertEquals("https://github.com/a/b/tags.atom", url("https://github.com/a/b/tags"))
        assertEquals("https://medium.com/feed/@yazar", url("https://medium.com/@yazar"))
        assertEquals("https://kul.substack.com/feed", url("https://kul.substack.com"))
    }

    @Test
    fun sitesWithoutFeedsExplainThemselves() {
        val addresses = listOf("https://x.com/nasa", "https://twitter.com/nasa", "https://www.instagram.com/x", "https://t.me/kanal")
        (addresses + "https://www.tiktok.com/@x").forEach {
            val r = Addresses.convert(it)
            assertTrue(it, r is Addresses.Result.Unsupported && "RSS" in r.reason)
        }
    }

    @Test
    fun ordinaryAddressesAreLeftAlone() {
        assertNull(Addresses.convert("https://example.com/feed.xml"))
        assertNull(Addresses.convert("example.com"))
        assertNull(Addresses.convert("http://insecure.com/@x"))
        assertNull(Addresses.convert(""))
    }
}
