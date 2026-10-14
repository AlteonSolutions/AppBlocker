package com.alteon.appgate

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Whether a newer AppGate has been released, so the "Download latest AppGate" button can say so
 * instead of leaving the parent to guess. Uses no GitHub API: github.com answers ".../releases/latest"
 * with a redirect to ".../releases/tag/v1.2.3", and that redirect carries the version.
 *
 * Framework-free: the parsing and comparison are unit tested, and the one network call uses java.net.
 */
object UpdateCheck {

    private const val REPO_URL = "https://github.com/AlteonSolutions/AppBlocker"

    /**
     * GitHub redirects this to the newest release's asset. release.yml uploads every release under this
     * fixed name as well as a versioned one, so this one link never goes stale.
     */
    const val LATEST_APK_URL = "$REPO_URL/releases/latest/download/appgate.apk"

    private const val LATEST_RELEASE_URL = "$REPO_URL/releases/latest"
    /** The settings screen shows the plain label until this passes; a slow network never blocks it. */
    private const val TIMEOUT_MS = 10_000

    enum class State { UPDATE_AVAILABLE, UP_TO_DATE, UNKNOWN }

    /** The version in a release-tag URL, e.g. ".../releases/tag/v0.4.4" gives "0.4.4". */
    fun versionFromTagUrl(url: String?): String? =
        url?.let { Regex("/releases/tag/v?(\\d+(?:\\.\\d+)*)/?$").find(it)?.groupValues?.get(1) }

    /** Numeric, part by part: 0.10.0 is newer than 0.9.9. Missing parts count as 0. Null if unparseable. */
    fun compare(a: String, b: String): Int? {
        val x = a.split('.').map { it.toIntOrNull() ?: return null }
        val y = b.split('.').map { it.toIntOrNull() ?: return null }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun state(installed: String, latest: String?): State {
        val c = latest?.let { compare(it, installed) } ?: return State.UNKNOWN
        return if (c > 0) State.UPDATE_AVAILABLE else State.UP_TO_DATE
    }

    /** The newest released version, or null if it can't be found. Blocking: call off the main thread. */
    fun fetchLatestVersion(): String? = try {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "HEAD"
        }
        try {
            versionFromTagUrl(conn.getHeaderField("Location"))
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        null
    }
}
