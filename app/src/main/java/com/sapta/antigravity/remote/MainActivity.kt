package com.sapta.antigravity.remote

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.sapta.antigravity.remote.bridge.AntigravityAccountBridge
import com.sapta.antigravity.remote.bridge.ChatCommandInjector
import com.sapta.antigravity.remote.data.AppPreferences
import com.sapta.antigravity.remote.ui.companion.CompanionBottomSheet
import com.sapta.antigravity.remote.ui.controls.DraggableControlsController
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.concurrent.thread

/**
 * MainActivity
 *
 * Primary activity orchestrating the full-screen Antigravity WebView client,
 * system window insets, Draggable Controls, and Google Companion bottom sheet.
 */
class MainActivity : AppCompatActivity(),
    AntigravityAccountBridge.BridgeEventListener,
    CompanionBottomSheet.CompanionCallbacks {

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var offlineHudView: WebView
    private lateinit var btnQuickHud: MaterialButton

    private lateinit var preferences: AppPreferences
    private lateinit var controlsController: DraggableControlsController
    private lateinit var companionSheet: CompanionBottomSheet

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = AppPreferences(this)
        companionSheet = CompanionBottomSheet(this, preferences, this)

        rootContainer = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.webView)
        offlineHudView = findViewById(R.id.offlineHudView)
        btnQuickHud = findViewById(R.id.btnQuickHud)

        setupWindowInsets()
        setupControlsController()
        setupOfflineHud()
        setupPrimaryWebView()
        setupBackNavigation()
        applyDeskModeState(preferences.isDeskModeEnabled)

        // Initial navigation
        val initialUrl = if (preferences.lastRemoteLink.isNotBlank()) {
            preferences.lastRemoteLink
        } else {
            "https://antigravity.google.com/"
        }
        webView.loadUrl(initialUrl)

        // Background Keep-Alive service initialization
        if (preferences.isKeepAliveEnabled) {
            requestNotificationPermissionIfNeeded()
            RemoteKeepAliveService.start(this)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottomPadding = maxOf(systemBars.bottom, ime.bottom)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }
    }

    private fun setupControlsController() {
        controlsController = DraggableControlsController(
            pillView = btnQuickHud,
            containerView = rootContainer,
            appPreferences = preferences,
            onPillClicked = { companionSheet.show() }
        )
        controlsController.attach()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPrimaryWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // Security Hardening: Disable local file and content access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.saveFormData = false

        // Bridge registration
        val accountBridge = AntigravityAccountBridge(
            originProvider = { webView.url },
            listener = this
        )
        webView.addJavascriptInterface(accountBridge, AntigravityAccountBridge.JAVASCRIPT_NAME)

        // Chrome client for file chooser
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        // WebView Client
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                offlineHudView.visibility = View.GONE
                if (view != null) {
                    ChatCommandInjector.injectAccountExtractor(view)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    offlineHudView.visibility = View.VISIBLE
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupOfflineHud() {
        offlineHudView.settings.javaScriptEnabled = true
        offlineHudView.settings.domStorageEnabled = true
        offlineHudView.overScrollMode = View.OVER_SCROLL_NEVER
        offlineHudView.loadUrl("file:///android_asset/stitch_offline_hud.html")
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (offlineHudView.visibility == View.VISIBLE) {
                    offlineHudView.visibility = View.GONE
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    private fun applyDeskModeState(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            btnQuickHud.alpha = 1.0f
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            btnQuickHud.alpha = 0.85f
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // BridgeEventListener Callbacks
    override fun onAccountUpdated(name: String, email: String, avatarUrl: String, isPro: Boolean) {
        runOnUiThread {
            if (name.isNotBlank()) preferences.cachedUserName = name
            if (email.isNotBlank()) preferences.cachedUserEmail = email
            preferences.isUserPro = isPro

            if (avatarUrl.isNotBlank() && avatarUrl != preferences.cachedAvatarUrl) {
                preferences.cachedAvatarUrl = avatarUrl
                downloadAndCacheAvatar(avatarUrl)
            }
        }
    }

    override fun onTelemetryUpdated(ram: String, ssd: String, battery: String) {
        // Telemetry update hook
    }

    private fun downloadAndCacheAvatar(avatarUrl: String) {
        thread {
            try {
                val url = URL(avatarUrl)
                val host = url.host.lowercase()
                if (!host.endsWith(".googleusercontent.com") && !host.endsWith(".ggpht.com")) {
                    return@thread
                }

                val conn = url.openConnection()
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val input = conn.getInputStream()
                val targetFile = File(filesDir, "cached_avatar_primary.png")
                val output = FileOutputStream(targetFile)

                input.use { inStream ->
                    output.use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Avatar download skipped: ${e.message}")
            }
        }
    }

    // CompanionCallbacks
    override fun onNavigate(url: String) {
        webView.loadUrl(url)
    }

    override fun onInsertCommand(command: String) {
        ChatCommandInjector.insertCommandIntoChat(this, webView, command)
        Toast.makeText(this, R.string.toast_prompt_inserted, Toast.LENGTH_SHORT).show()
    }

    override fun onSignOutRequested() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
        }

        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()

        val cachedAvatar = File(filesDir, "cached_avatar_primary.png")
        if (cachedAvatar.exists()) {
            cachedAvatar.delete()
        }

        preferences.clearSession()
        webView.loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fantigravity.google.com%2F")
        Toast.makeText(this, R.string.toast_signed_out, Toast.LENGTH_LONG).show()
    }

    override fun onKeepAliveToggled(enabled: Boolean) {
        if (enabled) {
            requestNotificationPermissionIfNeeded()
            RemoteKeepAliveService.start(this)
        } else {
            RemoteKeepAliveService.stop(this)
        }
    }

    override fun onDeskModeToggled(enabled: Boolean) {
        applyDeskModeState(enabled)
    }

    override fun onReloadRequested() {
        webView.reload()
    }
}
