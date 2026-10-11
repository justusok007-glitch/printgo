package com.printgo.app.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.data.converter.DocumentConverter
import com.printgo.app.data.local.PrintGoDatabase
import com.printgo.app.data.local.RecentWebPageEntity
import com.printgo.app.model.PrintItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class WebError(
    val title: String,
    val description: String,
    val failingUrl: String
)

class WebPagesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PrintGoDatabase.getInstance(application)
    private val webPageDao = db.recentWebPageDao()
    private val converter = DocumentConverter(application)

    val recentPages: StateFlow<List<RecentWebPageEntity>> = webPageDao.getRecentWebPages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _loadedUrl = MutableStateFlow<String?>(null)
    val loadedUrl: StateFlow<String?> = _loadedUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _isReaderMode = MutableStateFlow(false)
    val isReaderMode: StateFlow<Boolean> = _isReaderMode.asStateFlow()

    private val _errorState = MutableStateFlow<WebError?>(null)
    val errorState: StateFlow<WebError?> = _errorState.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    fun onInputUrlChange(newUrl: String) {
        _inputUrl.value = newUrl
    }

    fun submitUrl(input: String) {
        val target = resolveUrl(input)
        if (target.isNotBlank()) {
            _errorState.value = null
            _inputUrl.value = target
            _loadedUrl.value = target
            _isReaderMode.value = false
        }
    }

    fun selectRecentPage(page: RecentWebPageEntity) {
        _errorState.value = null
        _inputUrl.value = page.url
        _loadedUrl.value = page.url
        _pageTitle.value = page.title
        _isReaderMode.value = false
    }

    fun deleteRecentPage(page: RecentWebPageEntity) {
        viewModelScope.launch {
            webPageDao.deleteWebPage(page.url)
        }
    }

    fun clearAllRecentPages() {
        viewModelScope.launch {
            webPageDao.clearAll()
        }
    }

    fun onPageStarted(url: String) {
        _isLoading.value = true
        _errorState.value = null
        _inputUrl.value = url
    }

    fun onPageFinished(url: String, title: String?) {
        _isLoading.value = false
        _loadingProgress.value = 100
        val resolvedTitle = if (title.isNullOrBlank()) url else title
        _pageTitle.value = resolvedTitle
        _inputUrl.value = url

        // Save to Room
        viewModelScope.launch {
            try {
                webPageDao.insertWebPage(RecentWebPageEntity(url = url, title = resolvedTitle))
                webPageDao.trimOldWebPages()
            } catch (_: Exception) {}
        }
    }

    fun onProgressChanged(progress: Int) {
        _loadingProgress.value = progress
        if (progress >= 100) {
            _isLoading.value = false
        }
    }

    fun onErrorReceived(title: String, description: String, failingUrl: String) {
        _isLoading.value = false
        _errorState.value = WebError(title, description, failingUrl)
    }

    fun toggleDesktopMode() {
        _isDesktopMode.value = !_isDesktopMode.value
    }

    fun toggleReaderMode(webView: WebView?) {
        webView ?: return
        val current = _isReaderMode.value
        _isReaderMode.value = !current

        if (!current) {
            val readerJs = """
                (function() {
                    var article = document.querySelector('article') || 
                                  document.querySelector('main') || 
                                  document.querySelector('.post-content') || 
                                  document.querySelector('.article-body') || 
                                  document.body;
                    var title = document.title;
                    var paragraphs = article.querySelectorAll('p, h1, h2, h3, h4, h5, img, blockquote');
                    var content = '';
                    for (var i = 0; i < paragraphs.length; i++) {
                        content += paragraphs[i].outerHTML;
                    }
                    document.body.innerHTML = '<div style="max-width: 680px; margin: 40px auto; padding: 24px; font-family: -apple-system, Roboto, sans-serif; font-size: 18px; line-height: 1.7; color: #1e293b;">' +
                        '<h1 style="font-size: 26px; line-height: 1.3; color: #0f172a; margin-bottom: 24px;">' + title + '</h1>' + 
                        content + 
                        '</div>';
                    document.body.style.backgroundColor = '#ffffff';
                })();
            """.trimIndent()
            webView.evaluateJavascript(readerJs, null)
        } else {
            webView.reload()
        }
    }

    fun printCurrentPage(webView: WebView, onPrintReady: (PrintItem) -> Unit) {
        viewModelScope.launch {
            _isConverting.value = true
            try {
                val doc = converter.convertWebPageFromView(webView, _pageTitle.value)
                _isConverting.value = false
                onPrintReady(doc.sourceItem)
            } catch (e: Exception) {
                _isConverting.value = false
            }
        }
    }

    private fun resolveUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val hasDot = trimmed.contains(".") && !trimmed.contains(" ")
        return if (hasDot) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        } else {
            "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
        }
    }
}
