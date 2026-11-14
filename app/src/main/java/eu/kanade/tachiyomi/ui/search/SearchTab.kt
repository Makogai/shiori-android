package eu.kanade.tachiyomi.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The iOS Search tab: global search across sources, without leaving the tab bar. */
data object SearchTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = stringResource(MR.strings.action_search),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Search),
        )

    @Composable
    override fun Content() {
        val screen = remember { GlobalSearchScreen(asTab = true) }
        screen.Content()
    }
}
