package mihon.feature.friends.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Character name (optional)" under the crop: one line, at most [CardRules.NAME_LIMIT] characters, with a counter
 * once it gets close. Empty lets the AI name the card.
 */
@Composable
fun CardNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val description = stringResource(MR.strings.cards_name_description, CardRules.NAME_LIMIT)
    val counterDescription = stringResource(
        MR.strings.cards_name_counter_description,
        value.length,
        CardRules.NAME_LIMIT,
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(MR.strings.cards_name_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
            )
            if (value.length >= CardRules.NAME_COUNTER_FROM) {
                Text(
                    text = stringResource(MR.strings.cards_name_counter, value.length, CardRules.NAME_LIMIT),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (value.length >= CardRules.NAME_LIMIT) LIMIT_COLOR else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = counterDescription },
                )
            }
        }
        // A filled field with no outline, as the iOS one: white at 10 % on the black screen
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(CardRules.limitedNameInput(it)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics { contentDescription = description },
            decorationBox = { field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.cards_name_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    }
                    field()
                }
            },
        )
    }
}

private val LIMIT_COLOR = Color(0xFFFFA726)
