package com.nishanth.instamapper.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nishanth.instamapper.BuildConfig
import com.nishanth.instamapper.data.Updates
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.data.Actions
import com.nishanth.instamapper.data.AiProvider
import com.nishanth.instamapper.data.AppSettings
import com.nishanth.instamapper.data.InstagramFetcher

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember(vm.settings) { mutableStateOf(vm.settings.provider) }
    var key by remember(vm.settings) { mutableStateOf(vm.settings.apiKey) }
    var model by remember(vm.settings) { mutableStateOf(vm.settings.model) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(animatedBrush(Palette.grape + Palette.candy))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column {
                Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Version ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.88f))
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Instagram account
            TintCard(Palette.insta) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Instagram", "📸")
                    Text(
                        if (vm.loggedIn) "✅ Signed in. Reels load with location tag, audio and video."
                        else "Not signed in. Only the caption and thumbnail are available for most reels.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "You sign in on Instagram's own page inside the app. Your password goes only to Instagram; the app keeps just the login cookie on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (vm.loggedIn) {
                        OutlinedButton(onClick = { vm.logout() }) { Text("Sign out") }
                    } else {
                        GradientButton("Sign in to Instagram", Modifier.fillMaxWidth(), Palette.insta) { vm.showLogin = true }
                    }
                }
            }

            // AI
            TintCard(Palette.grape) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("AI assistant (optional)", "✨")
                    Text(
                        "Better place detection from captions, plus local tips (festivals, crowds) in the best-time report. " +
                            "Each person uses their own key. Gemini has a free tier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        AiProvider.NONE to "Off",
                        AiProvider.GEMINI to "Google Gemini (free tier)",
                        AiProvider.CLAUDE to "Anthropic Claude",
                    ).forEach { (p, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = provider == p, onClick = { provider = p }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = provider == p, onClick = { provider = p })
                            Text(label)
                        }
                    }
                    if (provider != AiProvider.NONE) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text("API key") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model (optional)") },
                            placeholder = { Text(AppSettings(provider = provider).effectiveModel) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = {
                            Actions.openUrl(
                                ctx,
                                if (provider == AiProvider.GEMINI) "https://aistudio.google.com/apikey" else "https://console.anthropic.com/settings/keys",
                            )
                        }) { Text(if (provider == AiProvider.GEMINI) "Get a free Gemini key →" else "Get a Claude API key →") }
                    }
                    GradientButton("Save", Modifier.fillMaxWidth(), Palette.grape) {
                        vm.updateSettings(AppSettings(provider, key, model))
                    }
                }
            }

            // App
            TintCard(Palette.mint) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Share & updates", "🚀")
                    GradientButton("📤 Share Insta Mapper with friends", Modifier.fillMaxWidth(), Palette.mint) {
                        Actions.share(
                            ctx,
                            "Check out Insta Mapper — turn Instagram travel reels into places on your map, " +
                                "best time to visit, songs and more. Download the Android app: ${BuildConfig.RELEASES_URL}",
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            checking = true
                            scope.launch {
                                val u = Updates.check()
                                checking = false
                                if (u != null) {
                                    Actions.toast(ctx, "Version ${u.version} is available")
                                    Actions.openUrl(ctx, u.apkUrl ?: BuildConfig.RELEASES_URL)
                                } else {
                                    Actions.toast(ctx, "You're on the latest version 🎉")
                                }
                            }
                        },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (checking) "Checking…" else "Check for updates") }
                    OutlinedButton(onClick = { vm.runDiagnostics() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Check connection")
                    }
                    OutlinedButton(onClick = { vm.showOnboarding = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Show the intro again")
                    }
                }
            }

            Text(
                "Insta Mapper reads public reel information for personal trip planning. Please respect creators: " +
                    "credit them when sharing and don't re-upload downloaded videos. Map data © OpenStreetMap contributors. " +
                    "Weather data: Open-Meteo. Not affiliated with Instagram or Meta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            CookieManager.getInstance().flush()
            onDone()
        }
    }
    BackHandler { finish() }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        TopAppBar(
            title = { Text("Sign in to Instagram", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = finish) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            },
            actions = { TextButton(onClick = finish) { Text("Done") } },
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            CookieManager.getInstance().flush()
                            val onLoginPage = url?.contains("/accounts/") == true || url?.contains("/challenge/") == true
                            if (InstagramFetcher.isLoggedIn() && !onLoginPage) finish()
                        }
                    }
                    loadUrl("https://www.instagram.com/accounts/login/")
                }
            },
        )
    }
}
