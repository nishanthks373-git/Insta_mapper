package com.nishanth.instamapper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import coil.Coil
import coil.ImageLoader
import com.nishanth.instamapper.data.Http
import com.nishanth.instamapper.ui.AppRoot
import com.nishanth.instamapper.ui.InstaMapperTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Thumbnails use the same DNS fallback as the rest of the app.
        Coil.setImageLoader(ImageLoader.Builder(applicationContext).okHttpClient { Http.client }.crossfade(true).build())
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            InstaMapperTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshLogin()
    }

    /** Called when you share a reel from Instagram to this app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        vm.analyze(text)
    }
}
