# Akış

Single-module Android RSS reader (`:app`, package `com.akis.app`). Jetpack Compose + Material 3
Expressive, no DI framework, no navigation library, no third-party runtime dependency beyond
AndroidX, Compose and kotlinx.serialization. The only permission is INTERNET and traffic is HTTPS only.
The app is meant to be calm and finite (no endless scrolling, no unread nagging); keep it that way.

## Commands

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew -I .claude/gradle/maven-central-mirror.gradle detekt lintDebug testDebugUnitTest assembleRelease
./gradlew -I .claude/gradle/maven-central-mirror.gradle recordRoborazziDebug   # re-record screenshots after a UI change
./gradlew -I .claude/gradle/maven-central-mirror.gradle verifyRoborazziDebug   # compare screens with app/src/test/screenshots/
```

Maven Central rate-limits the session proxy (HTTP 429), so always pass the mirror init script.
APKs are copied to `dist/` by hand (`Akis-<versionName>.apk`).

## Layout

- `domain/` - pure Kotlin, unit-tested (`FeedTest`, `FeatureTest`, `ReaderRulesTest`). `Models.kt`
  (all bounds in `Limits`, `Settings`, `AppData`), `ReaderRules.kt` (muted words, search, groups,
  auto-read, read log and weekly summary, one-time entity repair, shared-address parsing),
  `FeedDiscovery.kt` (RSS links of a normal page, common feed paths), `FeedParser.kt` (RSS 2.0 / RDF / Atom from an injected `XmlPullParser`,
  incl. picture extraction; tests use kxml2), `TextTools.kt` (`Html`, `FeedDates`, `FastRead`,
  `ArticleExtractor`, `HtmlCharset`), `FeedRules.kt` (URL checks, ids, merge and caps, `applyFetch`,
  filters, video ids, reading time, full-text bounds, settings clamping, relative time, OPML).
- `data/` - `FeedFetcher` (HTTPS only, redirects followed by hand, timeouts, body cap, ETag and
  Last-Modified), `FeedLoader` (also `probe`: feed, page that advertises feeds, or neither), `ArticlePage` (full text on demand), `ImageStore` (bounded picture
  cache; no request at all when the setting is off), `AppStore` (one JSON file, atomic conflated
  writes), `Io.kt`.
- `ui/AkisViewModel.kt` - refresh (at most 4 feeds in parallel, only when the data is 15 minutes
  stale at start, whenever the app becomes visible and every 5 minutes while it is open (`Settings.autoRefresh`), or on demand; no background work), add/remove feed, OPML import and export.
- `ui/screens/` - `HomeScreen` (two-column equal picture tiles, daily limit with an end card, "all
  read" block, connected filter group, FAB menu), `ReaderScreen` (plain text only, no WebView;
  text size, fast reading, full text, videos are only handed to another app), `SettingsScreen`,
  `SourcesScreen` (tap a source for `SourceArticlesScreen`: every stored story of it, read or not; read stories leave the home list, which offers Okunmamış, Sonra (read-later queue) and Yıldızlı, plus category chips (`Categories`: labels from the feed's own category tags, merged through an alias table) and a short-read chip; `Similar` folds one story reported by several sources into one tile; the tune icon of a source opens its options: quiet, auto full text, group), `AddFeedSheet`, `ReadingUi.kt` (shared groups and the "Aa" sheet).
- `ui/AkisApp.kt` - theme (dark, light, system), routes (home, sources, settings, muted words, weekly
  summary), shared-address handling, the break reminder (never blocks, only continue or close the app).
- `ui/SpeechController.kt` - read aloud with the phone's own speech engine (no network from Akış).

## Design

Screens are sketched first in M3E Canvas (`docs/m3e-canvas/`: `ana-ekran.json`, `ana-ekran-v3.json` (the Material 3 Expressive home: LargeFlexibleTopAppBar with a subtitle, connected ToggleButton filters, a wide `HeroTile` for the newest story with a picture), `okuyucu-v2.json`,
`sakin-okuma.json`, `gorselli-akis.json`, `ozellikler-v3.json`, `ayarlar-v3.json`; open them with *Open project* on
https://lnkiai.github.io/m3e-canvas/). Use the `tasarimci` subagent for any new screen
and get the user's approval of the sketch before writing code. Every tile uses `TileShape` (16 dp)
and `TileSpacing` (8 dp); progress is always wavy.

## Conventions

- Code, comments, commit messages: English. User-facing text: Turkish, inline in Kotlin.
- The manifest has one exported activity (launcher plus the "share an address" filter) and a `queries`
  entry for the speech engine; keep it that way.
- Never add a WebView, a cleartext exception, a new permission or a network library (videos open in
  another app, never inside Akış); every size,
  count and time bound lives in `Limits` or next to the code that enforces it.
- Detekt config is in `config/detekt/detekt.yml`; fix findings, there is no baseline.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every shipped APK.

- Reader remembers where you stopped (`Article.resume`), "Eski yazıları temizle" (`Settings.cleanup`) deletes old read stories that are not starred or queued; mark-all-read can be undone.
- Settings → Veri: one-file backup/restore (`Backup`, cut down to the usual limits on restore), notes sharing; the reader has a note field (`Article.note`).
- `PerformanceTest` holds worst-case time budgets on full-size data (1000 stories, 200 sources, a 2 MB feed); keep them passing and add a budget for any new list-wide operation.
- Reading comfort: `Settings.palette` (sepia, pure black), find-in-story bar in the reader, the "Okunan" tab lists the last opened first (`Article.openedMillis`), sources without a story for 3+ months are flagged on the Kaynaklar screen.
- Rest and comfort: reader line spacing and margins (`Settings.spacing`, `margin`), quiet hours (`Settings.quiet`, `ReaderRules.inQuietHours`: the unread list rests, Sonra and Yıldızlı stay), "Okuma listesini paylaş" (`ReaderRules.listText`, Markdown), today's minutes on the all-read block.
- Memory: `ImageStore` caps its cache at 24 MB and decoded pictures near 6 MB, `AkisApplication` drops decoded pictures when the UI is hidden or memory is low, `AppStore` streams the JSON file in and out, summaries are cut at `Limits.MAX_TEXT` (5000). The Ayarlar scroll state lives in `AkisApp`.
- Android 17: `targetSdk` 37. Large screens ignore orientation/size locks, so tile columns follow the width (`Adaptive.columns`) and text screens stop at `Adaptive.READING_MAX_DP`; never lock the orientation or the aspect ratio. Certificate Transparency is on by default for API 37 targets: a feed host without CT-logged certificates will fail with a certificate error on the Kaynaklar screen.
- `Tiles.kt` holds the connected tile group (`Group`, `SwitchTile`, `ChoiceTile`, `LinkTile`) shared by Ayarlar and the reader's "Aa" sheet; `Addresses` turns YouTube/Reddit/Mastodon/Bluesky/GitHub/Medium/Substack profile addresses into feed addresses before adding a source and explains sites without a feed (X, Instagram, Telegram...).
- Insight: `Summarizer` (three most telling sentences, plain word counting, shown by the reader above the text; `Settings.summary`), followed topics (`Settings.topics`: a chip each on the home screen, managed on a screen shared with the muted words), day headers on date-sorted lists (`ReaderRules.dayBucket`), a sleep timer for reading aloud (`SpeechController.cycleSleep`).
- Pinned sources (`Feed.pinned`, `Sections.build`: they lead the unread list under "Öncelikli"), the last four weeks on the Okuma özeti screen (`ReaderRules.weeklyTotals`), and "Sonra listesini hazırla" (`Settings.prefetchLater`: after a refresh the full text of up to `Limits.PREFETCH` read-later stories is fetched). There is deliberately no reading goal: the summary screen shows plain numbers only.
- Triage without opening: a long press on a tile opens `ArticleMenu` (later, star, read/unread, share; `ArticleActions`); the reader shows a wavy reading-progress bar in the slot it shares with the full-text loader; Kaynaklar shows each source's posts per week (`ReaderRules.postsPerWeek`).
- The reader's progress bar uses `ReaderRules.scrollProgress` (scroll position over the items that can scroll away, never visible-item counts); a story read to its end is stored as finished, otherwise Home offers it as "Okumaya devam et" for three days (`ReaderRules.resumeCandidate`). Sources can have a badge tint (`Feed.tint`, `TintColors`).
- Links in a story's own markup are kept (`Links.extract`, up to `Limits.MAX_LINKS`, https only) and listed at the end of the reader; a source can be muted for a day or a week (`Feed.mutedUntil`, `FeedRules.snooze`); "Sonra listesini temizle" (`Settings.laterCleanup`, `Article.laterAt`) lets stories leave the queue after `Limits.LATER_DAYS` days without deleting them.
- Keşfet (`Catalog`, `DiscoverScreen`): a built-in list of 50 feeds that were fetched and parsed with `FeedParser` when it was written (a feed over the 2 MB body cap, like PC Gamer's, must not go in); adding one needs no probing and uses the category as group. OPML keeps folders both ways (`OpmlReader`, `FeedRules.parseOpmlEntries`, `toOpml`). A long press on a merged tile lists the other sources' versions of the story (`Similar.groups`).
- Material 3 Expressive review against the `material3-expressive` skill (kept in the Notlar repo): grouped rows are the hand-built tile group of `Tiles.kt` (NOT the library's `SegmentedListItem`: the user approved the Ayarlar of 1.15.0, in which a row is icon, text and a control, and a choice puts its buttons under the title across the whole row; never change that structure, and build every new settings-like surface from `Group`, `SwitchTile`, `ChoiceTile` and `LinkTile`), icon buttons go through `ActionIconButton` (pressed shape morph), the home list has `PullToRefreshBox`, refreshing the home list shows ONE thing, the expressive morphing shape of `PullToRefreshDefaults.LoadingIndicator` (pull or refresh button), and no wavy bar there (the user chose this; never two progress indicators at once); other waits stay wavy (`CircularWavyProgressIndicator` for pictures and start-up, `LinearWavyProgressIndicator` for the reader), the lead tile's title uses `titleLargeEmphasized`. Akış now keeps Roborazzi screenshot tests too (`ui/ScreenshotTest.kt`, Robolectric SDK 34, PNGs in `app/src/test/screenshots/`): re-record after a UI change and LOOK at the PNGs before shipping; the `kontrolcu` agent does this. The skills (`material3-expressive`, `compose-ui`, `android-accessibility`, `android-testing`, `compose-performance-audit`) live in `.claude/skills/`; load the matching one before working in that area.
- Search (`SearchUi.kt`: pill `SearchField`, `RecentSearches` from `Settings.recentSearches`/`ReaderRules.pushRecent`, thumbnail `SearchResultRow` with the match marked): sketched in `docs/m3e-canvas/arama-v1.json`. The size caps and `cleanup` never drop stories that are starred, queued or carry a note (`FeedRules.keeps`); `AppStore` copies an unreadable file to `akis.corrupt.json` before starting empty.
