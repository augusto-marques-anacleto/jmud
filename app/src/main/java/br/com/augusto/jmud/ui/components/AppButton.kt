package br.com.augusto.jmud.ui.components

import android.content.res.ColorStateList
import android.widget.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val containerColor = MaterialTheme.colorScheme.primary.toArgb()
    val contentColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val button = Button(context)
            button.isAllCaps = false
            button.setOnClickListener { currentOnClick() }
            button.setOnLongClickListener {
                val handler = currentOnLongClick
                if (handler != null) {
                    handler()
                    true
                } else {
                    false
                }
            }
            button
        },
        update = { button ->
            if (button.text.toString() != text) {
                button.text = text
            }
            if (button.isEnabled != enabled) {
                button.isEnabled = enabled
            }
            val backgroundColor = if (enabled) containerColor else disabledContainerColor
            if (button.backgroundTintList?.defaultColor != backgroundColor) {
                button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            }
            val textColor = if (enabled) contentColor else disabledContentColor
            if (button.currentTextColor != textColor) {
                button.setTextColor(textColor)
            }
            if (onLongClick != null && longClickLabel != null && button.tag != longClickLabel) {
                button.tag = longClickLabel
                ViewCompat.replaceAccessibilityAction(
                    button,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    longClickLabel,
                    null
                )
            }
        }
    )
}
