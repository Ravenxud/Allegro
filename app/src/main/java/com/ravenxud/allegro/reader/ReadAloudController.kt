package com.ravenxud.allegro.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 页面朗读（系统 TTS）。
 *
 * 把页面文本按句子/段落切块，逐段朗读；支持暂停、继续、停止、跳段。
 * 不依赖网络，也不需要额外权限。
 */
class ReadAloudController(private val appContext: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var chunks: List<String> = emptyList()
    private var index = 0
    private var paused = false

    var onProgress: ((index: Int, total: Int, text: String) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    fun init(): Boolean {
        if (tts != null) return ready
        tts = TextToSpeech(appContext.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val lang = tts?.setLanguage(Locale.getDefault())
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (paused) return
                        index += 1
                        if (index < chunks.size) speakCurrent() else {
                            index = 0
                            onFinished?.invoke()
                        }
                    }

                    @Deprecated("legacy")
                    override fun onError(utteranceId: String?) = Unit
                })
            }
        }
        return ready
    }

    /** 传入整页文本，按标点切块。 */
    fun speak(text: String) {
        if (!init()) return
        chunks = split(text)
        index = 0
        paused = false
        speakCurrent()
    }

    fun pause() {
        paused = true
        tts?.stop()
    }

    fun resume() {
        if (!paused) return
        paused = false
        speakCurrent()
    }

    fun stop() {
        paused = false
        index = 0
        tts?.stop()
    }

    fun setRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true && !paused

    fun release() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun speakCurrent() {
        val chunk = chunks.getOrNull(index) ?: return
        onProgress?.invoke(index, chunks.size, chunk)
        tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "sb-$index")
    }

    /** 按句号/问号/感叹号/换行切块，控制单块长度，避免一次性读太长。 */
    private fun split(text: String): List<String> {
        val normalized = text.replace("\r", " ").replace("\n", "。")
        val sentences = normalized.split(Regex("(?<=[。！？!?；;])"))
            .map { it.trim() }
            .filter { it.length > 1 }

        val out = ArrayList<String>(sentences.size)
        val buffer = StringBuilder()
        for (sentence in sentences) {
            if (buffer.length + sentence.length > 120 && buffer.isNotEmpty()) {
                out += buffer.toString()
                buffer.clear()
            }
            buffer.append(sentence)
        }
        if (buffer.isNotEmpty()) out += buffer.toString()
        return if (out.isEmpty()) listOf(text.take(500)) else out
    }
}
