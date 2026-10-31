package com.nishanth.instamapper.data

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/** Everything that hands off to another app: maps, browser, share sheet, downloads. */
object Actions {

    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(ctx, "No app can open this link")
        }
    }

    /** Searching by name lands Google Maps on the exact spot; coordinates are the fallback. */
    fun mapsUrl(p: Place): String {
        val q = p.mapsQuery?.takeIf { it.isNotBlank() }
        return if (q != null) "https://www.google.com/maps/search/?api=1&query=${Uri.encode(q)}"
        else "https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}"
    }

    fun openInMaps(ctx: Context, p: Place) {
        if (!p.mapsQuery.isNullOrBlank()) {
            openUrl(ctx, mapsUrl(p))
            return
        }
        val geo = Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.name)})")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (_: ActivityNotFoundException) {
            openUrl(ctx, mapsUrl(p))
        }
    }

    fun directions(ctx: Context, p: Place) {
        val dest = p.mapsQuery?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) } ?: "${p.lat},${p.lng}"
        openUrl(ctx, "https://www.google.com/maps/dir/?api=1&destination=$dest")
    }

    fun nearby(ctx: Context, p: Place, what: String) =
        openUrl(ctx, "https://www.google.com/maps/search/${Uri.encode(what)}/@${p.lat},${p.lng},14z")

    fun share(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(send, "Share via"))
    }

    fun shareFile(ctx: Context, uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Export spots"))
    }

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(ctx, "Copied")
    }

    fun translate(ctx: Context, text: String) =
        openUrl(ctx, "https://translate.google.com/?sl=auto&tl=${Locales.language}&op=translate&text=${Uri.encode(text.take(1800))}")

    fun openApp(ctx: Context, pkg: String, fallbackUrl: String) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) ctx.startActivity(launch) else openUrl(ctx, fallbackUrl)
    }

    /** Saves the reel to Downloads/InstaMapper using Android's download manager. */
    fun downloadVideo(ctx: Context, info: ReelInfo): Boolean {
        val url = info.videoUrl ?: return false
        val name = listOfNotNull(info.username, info.shortcode).joinToString("_").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Reel by @${info.username ?: "instagram"}")
            .setDescription("Insta Mapper download")
            .setMimeType("video/mp4")
            .addRequestHeader("User-Agent", Http.BROWSER_UA)
            .addRequestHeader("Referer", "https://www.instagram.com/")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "InstaMapper/$name.mp4")
        if (android.os.Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "InstaMapper").mkdirs()
        }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(req)
        return true
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
