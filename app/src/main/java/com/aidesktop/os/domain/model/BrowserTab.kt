package com.aidesktop.os.domain.model

import java.util.UUID

/** A single real Browser tab: a live WebView URL/title pair, nothing simulated. */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "https://www.google.com",
    var title: String = "New Tab"
)

/** Desktop User-Agent string so sites like GitHub/Gmail/Docs render their desktop layout. */
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
