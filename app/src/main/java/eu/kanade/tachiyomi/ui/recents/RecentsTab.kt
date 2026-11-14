package eu.kanade.tachiyomi.ui.recents

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Schedule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SegmentedPicker
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The iOS Recents tab: reading history and library updates on one tab, switched with a segmented
 * control under the title. Both keep their own screens, data and actions.
 */
data object RecentsTab : Tab {

    enum class Segment { History, Updates }

    private val segment = MutableStateFlow(Segment.History)

    fun show(target: Segment) {
        segment.value = target
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(MR.strings.label_recents),
            icon = rememberVectorPainter(MaterialSymbols.RoundedFilled.Schedule),
        )

    override suspend fun onReselect(navigator: Navigator) {
        when (segment.value) {
            Segment.History -> HistoryTab.onReselect(navigator)
            Segment.Updates -> UpdatesTab.onReselect(navigator)
        }
    }

    @Composable
    override fun Content() {
        val current by segment.collectAsState()
        val stateHolder = rememberSaveableStateHolder()
        val header = RecentsHeader(
            title = stringResource(MR.strings.label_recents),
            picker = {
                SegmentedPicker(
                    options = listOf(
                        stringResource(MR.strings.label_recent_manga),
                        stringResource(MR.strings.label_recent_updates),
                    ),
                    selectedIndex = current.ordinal,
                    onSelect = { segment.value = Segment.entries[it] },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                )
            },
        )
        CompositionLocalProvider(LocalRecentsHeader provides header) {
            stateHolder.SaveableStateProvider(current) {
                when (current) {
                    Segment.History -> HistoryTab.Content()
                    Segment.Updates -> UpdatesTab.Content()
                }
            }
        }
    }
}

/** Title and segmented picker the History and Updates screens show while they are inside [RecentsTab]. */
class RecentsHeader(
    val title: String,
    val picker: @Composable () -> Unit,
)

val LocalRecentsHeader = staticCompositionLocalOf<RecentsHeader?> { null }
