package mihon.feature.friends.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Opens a player's profile; null where avatars aren't tappable (the Library card opens the Arena instead). */
val LocalOpenPlayer = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Makes an avatar or name open that player's profile. */
@Composable
fun Modifier.openPlayer(userId: String?): Modifier {
    val open = LocalOpenPlayer.current
    if (open == null || userId == null) return this
    return clickable(role = Role.Button, onClickLabel = stringResource(MR.strings.friends_view_profile)) {
        open(userId)
    }
}
