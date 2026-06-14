package net.kdt.pojavlaunch.kotlin

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ListView
import androidx.fragment.app.Fragment
import java.net.HttpURLConnection
import java.net.URL

object VersionListFix {
    fun ensureListVisible(fragment: Fragment, listId: Int = android.R.id.list) {
        Handler(Looper.getMainLooper()).postDelayed({
            fragment.view?.findViewById<ListView>(listId)?.visibility = View.VISIBLE
        }, 500)
    }

    fun fetchWithUserAgent(url: String, callback: (String) -> Unit) {
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "FearLauncher/2.0 (Android; Mobile)")
                conn.connectTimeout = 10000
                val result = conn.inputStream.bufferedReader().use { it.readText() }
                Handler(Looper.getMainLooper()).post { callback(result) }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
