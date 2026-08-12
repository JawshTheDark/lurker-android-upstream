// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.amiantos.lurker.ui.theme.Ui
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Live message translation (ported from Scully, the GTK desktop client).
 *
 * Device-local by design: which translator a device can reach is a property of
 * THAT device, so none of this touches the Lurker protocol — the client calls the
 * translator directly. Per-buffer opt-in, because translating ships other
 * people's messages to a third-party endpoint, and that's a privacy decision made
 * per conversation (same class as link previews).
 *
 * The original text is never mutated: translation is a render-time overlay keyed
 * by message id, so toggling it off shows the original again instantly and
 * anything that scans a message (links, media embeds) still sees what was
 * actually sent.
 */

/** Where translations come from. */
enum class TranslateBackend { OFF, LIBRETRANSLATE, OPENAI }

/** A finished translation for one message. [lang] is the detected source, absent
 *  for LLM backends (which return no detection metadata). */
data class Translation(val text: String, val lang: String?)

private sealed interface TEntry {
    data object InFlight : TEntry
    data class Done(val text: String, val lang: String?) : TEntry

    /** Asked, and the answer is "nothing to render" — a low-confidence detection
     *  or a result identical to the input. A VERDICT: caching it is what stops the
     *  re-ask loop that a dropped entry would cause on every recomposition. */
    data object None : TEntry
}

object Translator {
    // Session-only, never persisted. Snapshot-backed so a row that reads it
    // recomposes by itself the moment its translation lands.
    private val cache = mutableStateMapOf<Long, TEntry>()
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Reading translation is on for this buffer. */
    fun readingOn(bufferKey: String): Boolean =
        Ui.translateBackend != TranslateBackend.OFF && bufferKey in Ui.translateRead

    /** The outgoing language for this buffer, or null when posting untranslated. */
    fun outgoingLang(bufferKey: String): String? =
        if (Ui.translateBackend == TranslateBackend.OFF) null else Ui.translateOutgoing[bufferKey]

    /** The finished translation for [id], or null while absent/in flight. */
    fun done(id: Long): Translation? =
        (cache[id] as? TEntry.Done)?.let { Translation(it.text, it.lang) }

    /** Kick off a translation for [msg] if one isn't cached or already running. */
    suspend fun ensure(msg: Msg, target: String) {
        if (msg.id <= 0 || cache.containsKey(msg.id)) return
        val mu = locks.getOrPut(msg.id) { Mutex() }
        mu.withLock {
            if (cache.containsKey(msg.id)) return
            cache[msg.id] = TEntry.InFlight
            // gate = true: for INCOMING, a low-confidence detection means we'd be
            // translating from the wrong source and the answer is neither language.
            val out = withContext(Dispatchers.IO) {
                runCatching { translate(msg.text, target, gateConfidence = true) }
            }
            locks.remove(msg.id)
            if (out.isFailure) {
                // TRANSIENT (transport/HTTP). Drop the entry so a translator that
                // was down for a minute doesn't strand every message from that
                // minute until the app restarts.
                cache.remove(msg.id)
                DebugLog.e("translate", "failed for msg ${msg.id}: ${out.exceptionOrNull()?.message}")
            } else {
                // DEFINITIVE, including "nothing worth showing" — cache it either
                // way, or every recomposition re-asks about the same message
                // forever (a URL-only line scores 0.0 and would loop endlessly).
                val t = out.getOrNull()
                cache[msg.id] = if (t != null) TEntry.Done(t.text, t.lang) else TEntry.None
            }
        }
    }

    /** Drop every cached translation — used when the buffer's toggle or the backend
     *  settings change, so no stale overlay survives. */
    fun clear() {
        cache.clear()
        locks.clear()
    }

    /** Translate [text] into [target]. Throws on transport/protocol failure so the
     *  caller can decline to cache it. */
    suspend fun translateNow(text: String, target: String): Translation =
        withContext(Dispatchers.IO) {
            // gate = false: POSTING picks the target explicitly, so how confidently
            // the server guessed the SOURCE is irrelevant. Short sentences score
            // low routinely — "Not a problem my friend." scores 43 and still
            // translates correctly — and gating them made them unsendable.
            translate(text, target, gateConfidence = false)
                ?: throw java.io.IOException("no translation")
        }

    /** Throws on a TRANSIENT failure; returns null when the answer is definitively
     *  "nothing to show". The two are different and must stay different. */
    private fun translate(text: String, target: String, gateConfidence: Boolean): Translation? =
        when (Ui.translateBackend) {
            TranslateBackend.LIBRETRANSLATE -> libre(text, target, gateConfidence)
            TranslateBackend.OPENAI -> openai(text, target)
            TranslateBackend.OFF -> throw IllegalStateException("translation off")
        }

    private fun libre(text: String, target: String, gateConfidence: Boolean): Translation? {
        val body = JSONObject()
            .put("q", text)
            .put("source", "auto")
            .put("target", target)
            .put("format", "text")
        Ui.translateApiKey.takeIf { it.isNotBlank() }?.let { body.put("api_key", it) }
        val req = Request.Builder()
            .url(Ui.translateEndpoint.trimEnd('/') + "/translate")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            val o = JSONObject(res.body?.string().orEmpty())
            val out = o.optString("translatedText")
            if (out.isEmpty()) throw java.io.IOException("empty translation")
            val det = o.optJSONObject("detectedLanguage")
            val lang = det?.optString("language")?.ifBlank { null }
            val confidence = det?.optDouble("confidence", 100.0) ?: 100.0
            // Below the gate the DETECTION is wrong, which means we translated from
            // the wrong source and the answer is neither language. Short informal
            // Spanish scored 57 as Italian in the field; clean speech scores 85+.
            // Not an error — a verdict. Render the original, untouched and
            // unbadged, and don't come back.
            if (gateConfidence && confidence < CONFIDENCE_GATE) return null
            return Translation(out, lang)
        }
    }

    private fun openai(text: String, target: String): Translation {
        val body = JSONObject()
            .put("model", Ui.translateModel.ifBlank { "gpt-4o-mini" })
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(target)))
                    .put(JSONObject().put("role", "user").put("content", text)),
            )
        val b = Request.Builder()
            .url(chatCompletionsUrl(Ui.translateEndpoint))
            .post(body.toString().toRequestBody(JSON))
        // Ollama and LM Studio ignore auth; only send a header when one is set.
        Ui.translateApiKey.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        http.newCall(b.build()).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            val o = JSONObject(res.body?.string().orEmpty())
            val out = o.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")?.trim()
            if (out.isNullOrEmpty()) throw java.io.IOException("empty completion")
            // No detection metadata from a chat model — bare globe on the badge.
            return Translation(out, null)
        }
    }
}

// ---- Pure helpers (network-free, unit-tested) --------------------------------

internal const val CONFIDENCE_GATE = 60.0

/** The system prompt IS the product: a model that "helpfully" answers the message
 *  instead of translating it is worse than no translation at all. */
internal fun systemPrompt(target: String): String =
    "You are a translator for a live chat. Translate each message into $target. " +
        "Output ONLY the translation — no commentary, no quotes. Preserve tone, slang, " +
        "formatting and emoji. If the message is already in $target, output it unchanged."

/** Append `/v1/chat/completions` — but only add `/v1` when the configured endpoint
 *  doesn't already carry it, since people paste both forms. */
internal fun chatCompletionsUrl(endpoint: String): String {
    val base = endpoint.trim().trimEnd('/')
    return if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
}

// Stored COLLAPSED, because the lookup collapses its input: "oof" collapses to
// "of" and "woo" to "wo", so comparing a collapsed input against the raw spellings
// silently failed to match the very words in this list.
private val INTERJECTIONS = setOf(
    "lol", "rofl", "roflmao", "lmao", "lmfao", "haha", "hah", "hehe", "heh", "hm",
    "brb", "afk", "wtf", "omg", "smh", "idk", "imo", "imho", "btw", "fyi", "gg",
    "wp", "xd", "yay", "woo", "wow", "ugh", "meh", "pfft", "bruh", "welp", "oof",
).mapTo(HashSet()) { collapseRuns(it) }

/**
 * Noise that must never leave the device: language detection is hopeless on it and
 * the answer is the input anyway. Catches near-empty text and stretched
 * interjections ("loooool" collapses to "lol").
 */
internal fun isSkippable(text: String): Boolean {
    // Strip URLs FIRST. A message that is just a link has nothing to translate,
    // but a URL is full of letters so the letter-count test below waves it
    // through — and the translator scores it 0.0 confidence and hands the address
    // straight back. Uses Mirc.findUrls, the same parser the renderer links with.
    var stripped = text
    for (r in Mirc.findUrls(text).asReversed()) stripped = stripped.removeRange(r)
    val t = stripped.trim()
    if (t.isEmpty()) return true
    // Anything with almost no letters — ":)", "?!", "+1", a bare emoji.
    if (t.count { it.isLetter() } <= 2) return true
    val collapsed = collapseRuns(t.lowercase().filter { it.isLetter() })
    return collapsed in INTERJECTIONS
}

/** "loooool" -> "lol", "hmmmm" -> "hm". */
internal fun collapseRuns(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
    return sb.toString()
}

/** A translation only counts as one when it actually changed the text; an
 *  identical answer means it was already in the target language, and badging it
 *  would be a lie. */
internal fun isMeaningful(original: String, translated: String): Boolean =
    translated.isNotBlank() && translated.trim() != original.trim()

/** The languages offered in the pickers — LibreTranslate's set. Codes are what the
 *  API takes; names are what the user reads. NEVER a free-text field. */
val TRANSLATE_LANGUAGES: List<Pair<String, String>> = listOf(
    "en" to "English", "ar" to "Arabic", "az" to "Azerbaijani", "bg" to "Bulgarian",
    "bn" to "Bengali", "ca" to "Catalan", "cs" to "Czech", "da" to "Danish",
    "de" to "German", "el" to "Greek", "eo" to "Esperanto", "es" to "Spanish",
    "et" to "Estonian", "eu" to "Basque", "fa" to "Persian", "fi" to "Finnish",
    "fr" to "French", "ga" to "Irish", "gl" to "Galician", "he" to "Hebrew",
    "hi" to "Hindi", "hu" to "Hungarian", "id" to "Indonesian", "it" to "Italian",
    "ja" to "Japanese", "ko" to "Korean", "lt" to "Lithuanian", "lv" to "Latvian",
    "ms" to "Malay", "nb" to "Norwegian", "nl" to "Dutch", "pl" to "Polish",
    "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian", "sk" to "Slovak",
    "sl" to "Slovenian", "sq" to "Albanian", "sr" to "Serbian", "sv" to "Swedish",
    "th" to "Thai", "tl" to "Tagalog", "tr" to "Turkish", "uk" to "Ukrainian",
    "ur" to "Urdu", "vi" to "Vietnamese", "zh" to "Chinese", "zt" to "Chinese (trad.)",
)

/** Display name for a code, falling back to the code itself. */
fun languageName(code: String): String =
    TRANSLATE_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

// ---- Compose entry point ------------------------------------------------------

/**
 * The translation to render in place of [msg]'s text, or null to show the original.
 *
 * Null covers every "don't touch this" case: translation off for the buffer, the
 * user's OWN message (there is nothing to translate, and auto-detection will
 * happily mangle your English into a marked "translation"), system rows, noise,
 * and a result identical to the input.
 *
 * A cache miss renders the original this pass and fires the request; the row
 * recomposes on its own when the answer lands, because the cache is snapshot state.
 */
@androidx.compose.runtime.Composable
fun rememberTranslation(msg: Msg, bufferKey: String?): Translation? {
    if (bufferKey == null || !Translator.readingOn(bufferKey)) return null
    if (msg.self || msg.system || msg.id <= 0) return null
    val skip = androidx.compose.runtime.remember(msg.text) { isSkippable(msg.text) }
    if (skip) return null
    val target = Ui.translateTargetLang
    androidx.compose.runtime.LaunchedEffect(msg.id, target, Ui.translateBackend) {
        Translator.ensure(msg, target)
    }
    return Translator.done(msg.id)?.takeIf { isMeaningful(msg.text, it.text) }
}
