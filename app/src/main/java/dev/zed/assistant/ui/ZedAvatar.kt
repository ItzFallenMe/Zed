package dev.zed.assistant.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import dev.zed.assistant.R

/** Emotional states of the animated squircle avatar. */
enum class AvatarMood {
    IDLE, LISTENING, SPEAKING, THINKING, GUARDED, ALERT, SLEEPY
}

/**
 * Zed's face. The artwork is ink-on-paper, so it is colour-inverted at draw time to sit
 * on the pure black canvas as white ink, and the GIF loops natively through Coil.
 */
@Composable
fun ZedAvatar(
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    size: Int = 118
) {
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val asset = when (mood) {
        AvatarMood.IDLE -> R.raw.zed_idle
        AvatarMood.LISTENING -> R.raw.zed_attentive
        AvatarMood.SPEAKING -> R.raw.zed_happy
        AvatarMood.THINKING -> R.raw.zed_curious
        AvatarMood.GUARDED -> R.raw.zed_wary
        AvatarMood.ALERT -> R.raw.zed_excited
        AvatarMood.SLEEPY -> R.raw.zed_sleepy
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(asset).build(),
        imageLoader = loader
    )

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = InkInvert,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color.Black)
            .padding(6.dp)
    )
}

/** Inverts RGB while preserving alpha: black ink becomes white ink. */
private val InkInvert: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)
