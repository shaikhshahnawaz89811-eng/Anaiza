package com.aidesktop.os.ui.browser

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.data.BrowserController
import com.aidesktop.os.data.local.dao.BrowserDao
import com.aidesktop.os.data.local.entity.BookmarkEntity
import com.aidesktop.os.data.local.entity.HistoryEntity
import com.aidesktop.os.domain.model.BrowserTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browserDao: BrowserDao,
    private val controller: BrowserController
) : ViewModel() {

    // Real tabs/active-tab now live in BrowserController (an app-wide singleton)
    // so the AI tool executor can navigate the exact same WebView tabs the
    // user sees, instead of a separate copy of browser state.
    val tabs get() = controller.tabs
    val activeTabId get() = controller.activeTabId

    // Exposed so the composable can register/unregister the real WebView instance
    // per tab (needed for AI automation scripts to run inside it).
    val browserController: BrowserController get() = controller

    val showBookmarksPanel = mutableStateOf(false)
    val showHistoryPanel = mutableStateOf(false)

    val bookmarks = browserDao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = browserDao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun activeTab(): BrowserTab = controller.activeTab()

    fun newTab() {
        controller.newTab()
    }

    fun closeTab(id: String) = controller.closeTab(id)
    fun selectTab(id: String) = controller.selectTab(id)

    fun onPageLoaded(tabId: String, url: String, title: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        tab.url = url
        tab.title = title.ifBlank { url }
        viewModelScope.launch {
            browserDao.addHistory(HistoryEntity(title = tab.title, url = url))
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch { browserDao.addBookmark(BookmarkEntity(title = title, url = url)) }
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { browserDao.deleteBookmark(bookmark) }
    }

    fun clearHistory() {
        viewModelScope.launch { browserDao.clearHistory() }
    }
}
