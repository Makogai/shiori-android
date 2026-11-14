package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.theme.groupedRow
import tachiyomi.presentation.core.theme.separator
import kotlin.time.Duration.Companion.seconds

/**
 * Preference Screen composable which contains a list of [Preference] items
 * @param items [Preference] items which should be displayed on the preference screen. An item can be a single [PreferenceItem] or a group ([Preference.PreferenceGroup])
 * @param modifier [Modifier] to be applied to the preferenceScreen layout
 */
@Composable
fun PreferenceScreen(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = rememberLazyListState()
    val highlightKey = SearchableSettings.highlightKey
    if (highlightKey != null) {
        LaunchedEffect(Unit) {
            val i = items.findHighlightedIndex(highlightKey)
            if (i >= 0) {
                delay(0.5.seconds)
                state.animateScrollToItem(i)
            }
            SearchableSettings.highlightKey = null
        }
    }

    ScrollbarLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        // Rendered as iOS inset-grouped sections: a group's rows share one rounded card under its header,
        // and consecutive items outside a group share an untitled one.
        items.toSections().fastForEachIndexed { sectionIndex, section ->
            if (section.title != null) {
                item {
                    PreferenceGroupHeader(
                        title = section.title,
                        highlighted = section.title == highlightKey,
                    )
                }
            } else {
                item { Spacer(modifier = Modifier.height(if (sectionIndex == 0) 8.dp else 24.dp)) }
            }
            itemsIndexed(section.items) { index, item ->
                Column(modifier = Modifier.groupedRow(index, section.items.size)) {
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = if (item.icon != null) 56.dp else 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.separator,
                        )
                    }
                    PreferenceItem(
                        item = item,
                        highlightKey = highlightKey,
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

private class PreferenceSection(val title: String?, val items: List<Preference.PreferenceItem<*, *>>)

private fun List<Preference>.toSections(): List<PreferenceSection> {
    val sections = mutableListOf<PreferenceSection>()
    var loose = mutableListOf<Preference.PreferenceItem<*, *>>()
    fun flushLoose() {
        if (loose.isNotEmpty()) sections += PreferenceSection(null, loose)
        loose = mutableListOf()
    }
    forEach { preference ->
        when (preference) {
            is Preference.PreferenceGroup -> {
                flushLoose()
                val visible = preference.preferenceItems.filter { it.visible }
                if (preference.visible && visible.isNotEmpty()) {
                    sections += PreferenceSection(preference.title, visible)
                }
            }
            is Preference.PreferenceItem<*, *> -> if (preference.visible) loose += preference
        }
    }
    flushLoose()
    return sections
}

private fun List<Preference>.findHighlightedIndex(highlightKey: String): Int {
    return toSections().flatMap { section ->
        buildList<String?> {
            add(section.title) // Header or spacer
            addAll(section.items.map { it.title })
        }
    }.indexOfFirst { it == highlightKey }
}
