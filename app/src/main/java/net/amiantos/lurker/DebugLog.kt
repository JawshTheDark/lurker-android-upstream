// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tiny in-app diagnostics log. Captures connection lifecycle, IRC/service errors,
 * and uncaught exceptions into a ring buffer that's mirrored to an app-private
 * file — so a user hitting a problem (or a hard crash) can open
 * Settings → Diagnostics and copy/share exactly what happened, instead of
 * "it was repeatedly terminated" with no detail.
 *
 * Privacy: this can be shared by the user, so callers MUST NOT log secrets
 * (tokens, passwords, message bodies). Log lifecycle + error shapes only.
 */
object DebugLog {
    enum class Level { INFO, WARN, ERROR }

    data class Entry(val time: Long, val level: Level, val tag: String, val msg: String)

    private const val MAX = 500
    private const val MAX_FILE_BYTES = 256L * 1024
    private const val NL = "\u0001" // newline placeholder for one-line-per-entry storage
    private var file: File? = null
    private val ts = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Newest-last list the Diagnostics screen observes. */
    val entries = mutableStateListOf<Entry>()

    /** Call once from Application.onCreate: load any prior log + trap crashes. */
    fun init(context: Context) {
        val f = File(context.applicationContext.filesDir, "debug.log")
        file = f
        runCatching {
            if (f.exists()) f.readLines().takeLast(MAX).forEach { line -> parse(line)?.let(entries::add) }
        }
        // Record uncaught exceptions before chaining to the previous handler (so
        // the crash — and Play's normal reporting — still happens as usual).
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { log(Level.ERROR, "crash", "Uncaught on ${t.name}: ${Log.getStackTraceString(e)}") }
            prev?.uncaughtException(t, e)
        }
        i("app", "diagnostics started")
    }

    @Synchronized
    fun log(level: Level, tag: String, msg: String) {
        val e = Entry(System.currentTimeMillis(), level, tag, msg)
        entries.add(e)
        while (entries.size > MAX) entries.removeAt(0)
        val f = file ?: return
        runCatching {
            f.appendText(serialize(e) + "\n")
            // Rotate by rewriting the trimmed in-memory buffer once it grows large.
            if (f.length() > MAX_FILE_BYTES) {
                f.writeText(entries.joinToString("\n") { serialize(it) } + "\n")
            }
        }
    }

    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg)

    @Synchronized
    fun clear() {
        entries.clear()
        runCatching { file?.writeText("") }
    }

    /** Plain-text dump for copy/share, oldest-first. */
    fun dump(): String = entries.joinToString("\n") { render(it) }

    fun render(e: Entry): String =
        "${ts.format(Instant.ofEpochMilli(e.time))} ${e.level} [${e.tag}] ${e.msg}"

    private fun serialize(e: Entry): String =
        "${e.time}\t${e.level}\t${e.tag}\t${e.msg.replace("\n", NL)}"

    private fun parse(line: String): Entry? {
        val p = line.split('\t', limit = 4)
        if (p.size < 4) return null
        val time = p[0].toLongOrNull() ?: return null
        val level = runCatching { Level.valueOf(p[1]) }.getOrNull() ?: return null
        return Entry(time, level, p[2], p[3].replace(NL, "\n"))
    }
}
