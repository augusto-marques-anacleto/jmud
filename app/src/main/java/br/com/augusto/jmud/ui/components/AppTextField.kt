package br.com.augusto.jmud.ui.components

import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView

private class DispatchedValue {
    var text: String? = null
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onEditTextCreated: ((EditText) -> Unit)? = null
) {
    val dispatched = remember { DispatchedValue() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val baseInputType = when (keyboardOptions.keyboardType) {
        KeyboardType.Number -> InputType.TYPE_CLASS_NUMBER
        KeyboardType.Email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        else -> InputType.TYPE_CLASS_TEXT
    }
    val resolvedInputType = if (singleLine) {
        baseInputType
    } else {
        baseInputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    val resolvedImeOptions = when (keyboardOptions.imeAction) {
        ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
        ImeAction.Done -> EditorInfo.IME_ACTION_DONE
        ImeAction.Send -> EditorInfo.IME_ACTION_SEND
        ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
        ImeAction.Go -> EditorInfo.IME_ACTION_GO
        else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val editText = EditText(context)
            editText.id = View.generateViewId()
            editText.hint = label
            editText.inputType = resolvedInputType
            editText.imeOptions = resolvedImeOptions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                editText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            if (singleLine) {
                editText.isSingleLine = true
            } else {
                editText.minLines = minLines
                if (maxLines != Int.MAX_VALUE) {
                    editText.maxLines = maxLines
                }
                editText.gravity = Gravity.TOP or Gravity.START
                editText.isVerticalScrollBarEnabled = true
            }

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newText = s?.toString() ?: ""
                    dispatched.text = newText
                    currentOnValueChange(newText)
                }
            })

            editText.setOnEditorActionListener { _, actionId, _ ->
                val action = currentOnImeAction
                if (action != null && actionId != EditorInfo.IME_ACTION_UNSPECIFIED && actionId != EditorInfo.IME_NULL) {
                    action()
                    true
                } else {
                    false
                }
            }

            editText.setOnKeyListener { _, _, event ->
                currentOnKeyEvent?.invoke(event) ?: false
            }

            onEditTextCreated?.invoke(editText)
            editText
        },
        update = { editText ->
            if (editText.hint?.toString() != label) {
                editText.hint = label
            }
            if (editText.currentTextColor != textColor) {
                editText.setTextColor(textColor)
            }
            if (editText.currentHintTextColor != hintColor) {
                editText.setHintTextColor(hintColor)
            }
            if (editText.text.toString() != value && value != dispatched.text) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
        }
    )
}
