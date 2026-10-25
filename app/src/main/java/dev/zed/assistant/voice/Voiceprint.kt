package dev.zed.assistant.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tiny on-device speaker recogniser.
 *
 * There is no model to download and nothing leaves the phone: the voice is reduced to a
 * mel-spectrum fingerprint (how the speaker's vocal tract colours sound), which is stable
 * across what they say and cheap to compute on any phone. Enough to know *who* is talking
 * out of the people who enrolled their voice, which is what the personality layer needs.
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object Voiceprint {

    const val SAMPLE_RATE = 16_000
    private const val FRAME_SIZE = 512
    private const val HOP = 256
    private const val MEL_BANDS = 24

    /** Anything quieter than this is room tone, not a voice worth fingerprinting. */
    private const val MIN_RMS = 60.0

    /** 48 dimensions: mean and spread of the log-mel energies. */
    const val DIMENSIONS = MEL_BANDS * 2

    fun embed(samples: ShortArray): FloatArray? {
        if (samples.size < SAMPLE_RATE / 2) return null
        var energy = 0.0
        for (sample in samples) energy += sample.toDouble() * sample
        if (sqrt(energy / samples.size) < MIN_RMS) return null

        val window = FloatArray(FRAME_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
        }
        val filters = melFilterBank()

        val sums = DoubleArray(MEL_BANDS)
        val squares = DoubleArray(MEL_BANDS)
        var frames = 0

        var start = 0
        while (start + FRAME_SIZE <= samples.size) {
            val real = FloatArray(FRAME_SIZE)
            val imag = FloatArray(FRAME_SIZE)
            for (i in 0 until FRAME_SIZE) {
                real[i] = samples[start + i] / 32768f * window[i]
            }
            fft(real, imag)

            val power = FloatArray(FRAME_SIZE / 2) { bin ->
                val r = real[bin]
                val im = imag[bin]
                r * r + im * im
            }
            for (band in 0 until MEL_BANDS) {
                var energy = 0.0
                for (bin in filters[band].indices) {
                    energy += filters[band][bin] * power[bin]
                }
                val logEnergy = ln(energy + 1e-8)
                sums[band] += logEnergy
                squares[band] += logEnergy * logEnergy
            }
            frames++
            start += HOP
        }
        if (frames == 0) return null

        val means = DoubleArray(MEL_BANDS)
        val deviations = DoubleArray(MEL_BANDS)
        for (band in 0 until MEL_BANDS) {
            means[band] = sums[band] / frames
            val variance = (squares[band] / frames) - means[band] * means[band]
            deviations[band] = sqrt(variance.coerceAtLeast(0.0))
        }

        val vector = FloatArray(DIMENSIONS)
        var norm = 0f
        for (band in 0 until MEL_BANDS) {
            vector[band] = means[band].toFloat()
            vector[MEL_BANDS + band] = deviations[band].toFloat()
        }
        for (value in vector) norm += value * value
        norm = sqrt(norm)
        if (norm <= 0f) return null
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator <= 0f) 0f else dot / denominator
    }

    // -------------------------------------------------------------- internals

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until length / 2) {
                    val even = i + k
                    val odd = even + length / 2
                    val vr = real[odd] * curReal - imag[odd] * curImag
                    val vi = real[odd] * curImag + imag[odd] * curReal
                    real[odd] = real[even] - vr
                    imag[odd] = imag[even] - vi
                    real[even] += vr
                    imag[even] += vi
                    val nextReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                }
                i += length
            }
            length = length shl 1
        }
    }

    private fun melFilterBank(): Array<FloatArray> {
        val bins = FRAME_SIZE / 2
        val lowMel = hzToMel(80.0)
        val highMel = hzToMel(7600.0)
        val points = DoubleArray(MEL_BANDS + 2) { index ->
            melToHz(lowMel + (highMel - lowMel) * index / (MEL_BANDS + 1))
        }
        val binFrequency = { bin: Int -> bin.toDouble() * SAMPLE_RATE / FRAME_SIZE }

        return Array(MEL_BANDS) { band ->
            val left = points[band]
            val centre = points[band + 1]
            val right = points[band + 2]
            FloatArray(bins) { bin ->
                val frequency = binFrequency(bin)
                when {
                    frequency < left || frequency > right -> 0f
                    frequency <= centre -> ((frequency - left) / (centre - left)).toFloat()
                    else -> ((right - frequency) / (right - centre)).toFloat()
                }
            }
        }
    }

    private fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)

    private fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)
}
