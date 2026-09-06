package com.igabr13l.papercut.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Tiny synthesized SFX engine (no audio assets needed). */
class Sfx {
    val sr = 44100
    val TAU = (2.0 * PI).toFloat()
    val handler = Handler(Looper.getMainLooper())
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun env(t: Float, dur: Float) = exp(-4f * t / dur)

    fun tone(durMs: Int, f: (t: Float, dur: Float) -> Float): ShortArray {
        val n = sr * durMs / 1000
        val out = ShortArray(n)
        val dur = durMs / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            val v = (f(t, dur) * 0.75f).coerceIn(-1f, 1f)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    val cache = HashMap<String, ShortArray>()
    fun sound(name: String): ShortArray = cache.getOrPut(name) {
        when (name) {
            "shot" -> tone(140) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t, d) * 0.9f + sin(TAU * 90f * t) * env(t, d) * 0.5f }
            "shotgun" -> tone(240) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t, d) + sin(TAU * 70f * t) * env(t, d) * 0.6f }
            "sniper" -> tone(320) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t * 0.6f, d) + sin(TAU * 55f * t) * env(t, d) }
            "slash" -> tone(120) { t, d -> sin(TAU * (1400f - 900f * t / d) * t) * env(t, d) * 0.5f }
            "hit" -> tone(70) { t, d -> sin(TAU * 620f * t) * env(t, d) }
            "splat" -> tone(200) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t * 1.6f, d) * 0.8f }
            "jump" -> tone(140) { t, d -> sin(TAU * (300f + 500f * t / d) * t) * env(t, d) * 0.55f }
            "hurt" -> tone(180) { t, d -> sin(TAU * (420f - 260f * t / d) * t) * env(t, d) }
            "explode" -> tone(450) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t * 1.2f, d) + sin(TAU * 55f * t) * env(t, d) * 0.7f }
            "wave" -> tone(400) { t, d -> sin(TAU * 440f * t) * env(t, d) * 0.4f + sin(TAU * 660f * t) * env(t, d) * 0.3f }
            "over" -> tone(900) { t, d -> sin(TAU * (300f - 180f * t / d) * t) * env(t * 0.5f, d) }
            "click" -> tone(40) { t, d -> sin(TAU * 900f * t) * env(t, d) * 0.5f }
            "block" -> tone(90) { t, d -> sin(TAU * 1500f * t) * env(t, d) * 0.5f }
            "grapple" -> tone(150) { t, d -> sin(TAU * (500f + 700f * t / d) * t) * env(t, d) * 0.5f }
            "pickup" -> tone(120) { t, d -> sin(TAU * (700f + 300f * t / d) * t) * env(t, d) * 0.45f }
            "reload" -> tone(90) { t, d -> (Random.nextFloat() * 2f - 1f) * env(t * 3f, d) * 0.4f }
            "empty" -> tone(50) { t, d -> sin(TAU * 300f * t) * env(t, d) * 0.4f }
            else -> ShortArray(1)
        }
    }

    var lastPlay = 0L
    fun play(name: String, vol: Float = 0.55f) {
        val now = System.currentTimeMillis()
        val minGap = if (name == "hit" || name == "shot") 40L else 60L
        if (now - lastPlay < minGap && name != "explode" && name != "over") return
        lastPlay = now
        val data = sound(name)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        track.setVolume(vol)
        track.write(data, 0, data.size)
        track.play()
        handler.postDelayed({ track.release() }, data.size * 1000L / sr + 120L)
    }

    fun click() = play("click", 0.35f)
    fun shot(wIdx: Int) = play(when (wIdx) { 1 -> "shotgun"; 3 -> "sniper"; 4 -> "slash"; else -> "shot" })
    fun hit() = play("hit", 0.4f)
    fun splat() = play("splat", 0.6f)
    fun hurt() = play("hurt", 0.6f)
    fun jump() = play("jump", 0.4f)
    fun explode() = play("explode", 0.7f)
    fun wave() = play("wave", 0.5f)
    fun over() = play("over", 0.7f)
    fun block() = play("block", 0.5f)
    fun grapple() = play("grapple", 0.45f)
    fun pickup() = play("pickup", 0.45f)
    fun reload() = play("reload", 0.4f)
    fun empty() = play("empty", 0.35f)
}
