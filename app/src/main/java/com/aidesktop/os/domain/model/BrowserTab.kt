package com.aidesktop.os.domain.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * A single real Browser tab: a live WebView URL/title pair, nothing simulated.
 *
 * `url`/`title` are backed by Compose State (`mutableStateOf`, read/written via
 * `by`) instead of plain `var`s. Constructor shape (id, url, title) is unchanged
 * so every existing call site — `BrowserTab()`, `BrowserTab(url = url)` — still
 * compiles exactly as before; only the storage underneath changed. This matters
 * because a plain `var` on a class instance living inside a
 * `mutableStateListOf<BrowserTab>()` is NOT itself observable: mutating
 * `tab.url = x` doesn't recompose anything reading `tab.url`, it only happened
 * to "work" before when some unrelated state (e.g. the window list) triggered a
 * recomposition anyway. Backing these with real State fixes that at the source.
 *
 * Not a data class anymore (data-class equals/hashCode/copy on live State
 * fields would be wrong — two tabs with the same current text would compare
 * equal, and copy() would share the same State instances). Nothing in the
 * codebase called BrowserTab's equals/hashCode/copy/toString, so this is safe.
 */
class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    url: String = "https://www.google.com",
    title: String = "New Tab"
) {
    private val urlState = mutableStateOf(url)
    private val titleState = mutableStateOf(title)

    var url: String by urlState
    var title: String by titleState
}
