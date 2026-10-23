package com.akis.app.ui.screens

/** The wording of the word-list screen, so muted words and followed topics share it. */
data class WordsText(val title: String, val intro: String, val label: String, val empty: String)

val MutedText = WordsText(
    "Sessiz kelimeler",
    "Başlığında ya da metninde bu kelimeler geçen yazılar akışta gösterilmez.",
    "Gizlenecek kelime",
    "Henüz sessize alınmış kelime yok.",
)

val TopicText = WordsText(
    "Konu takibi",
    "Başlığında ya da metninde bu kelimeler geçen yazılar ana ekranda kendi çipinde toplanır.",
    "Takip edilecek kelime",
    "Henüz takip edilen konu yok.",
)
