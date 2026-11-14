package mihon.feature.enhance

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as AndroidRenderEffect

/** The reader's "Enhance": more contrast and a light sharpening, for washed-out or soft low-resolution scans. */
object PageEnhance {

    private const val CONTRAST = 1.12f
    private const val SHARPEN_AMOUNT = 0.45f

    /** [CONTRAST] around the mid tones, as a colour matrix on 0–255 channels. */
    fun contrast(): ColorMatrix {
        val offset = 127.5f * (1 - CONTRAST)
        return ColorMatrix(
            floatArrayOf(
                CONTRAST, 0f, 0f, 0f, offset,
                0f, CONTRAST, 0f, 0f, offset,
                0f, 0f, CONTRAST, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    /** A sharpening of what is drawn, in screen pixels so it works on the upscaled page; Android 13 and up. */
    fun sharpen(): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return sharpenEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sharpenEffect(): RenderEffect {
        val shader = RuntimeShader(SHARPEN_AGSL).apply { setFloatUniform("amount", SHARPEN_AMOUNT) }
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

// A 3×3 Laplacian sharpen, clamped to the (premultiplied) alpha.
private const val SHARPEN_AGSL = """
uniform shader content;
uniform float amount;

half4 main(float2 p) {
    half4 c = content.eval(p);
    half3 n = content.eval(p + float2(1, 0)).rgb + content.eval(p - float2(1, 0)).rgb +
        content.eval(p + float2(0, 1)).rgb + content.eval(p - float2(0, 1)).rgb;
    half3 sharp = c.rgb + (4.0 * c.rgb - n) * half(amount);
    return half4(clamp(sharp, half3(0.0), half3(c.a)), c.a);
}
"""
