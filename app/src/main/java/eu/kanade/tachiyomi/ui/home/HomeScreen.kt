package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.presentation.components.FloatingTabBar
import eu.kanade.presentation.components.FloatingTabBarHeight
import eu.kanade.presentation.components.FloatingTabBarMargin
import eu.kanade.presentation.components.FloatingTabItem
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.recents.RecentsTab
import eu.kanade.tachiyomi.ui.search.SearchTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsState

object HomeScreen : Screen() {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    private val TABS = listOf(
        LibraryTab,
        RecentsTab,
        BrowseTab,
        SearchTab,
        MoreTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(
            tab = LibraryTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                var showBottomNav by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    showBottomNavEvent.receiveAsFlow().collectLatest { showBottomNav = it }
                }

                if (isTabletUi()) {
                    NavigationSuiteScaffold(
                        navigationSuiteType = NavigationSuiteType.NavigationRail,
                        navigationSuiteColors = NavigationSuiteDefaults.colors(
                            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        navigationItemVerticalArrangement = Arrangement.Center,
                        navigationItems = {
                            TABS.fastForEach { NavigationRailTab(it) }
                        },
                    ) {
                        TabContent(tabNavigator)
                    }
                } else {
                    val barInset by animateDpAsState(
                        if (showBottomNav) FloatingTabBarHeight + FloatingTabBarMargin + 8.dp else 0.dp,
                        label = "barInset",
                    )
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalFloatingBarInset provides barInset) {
                            TabContent(tabNavigator)
                        }
                        AnimatedVisibility(
                            visible = showBottomNav,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically { it * 2 } + fadeIn(),
                            exit = slideOutVertically { it * 2 } + fadeOut(),
                        ) {
                            FloatingTabBar(
                                items = TABS.map { floatingTabItem(it) },
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = FloatingTabBarMargin),
                            )
                        }
                    }
                }
            }

            val goToLibraryTab = { tabNavigator.current = LibraryTab }

            BackHandler(enabled = tabNavigator.current != LibraryTab, onBack = goToLibraryTab)

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToLibraryTab()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            Tab.Updates -> {
                                RecentsTab.show(RecentsTab.Segment.Updates)
                                RecentsTab
                            }
                            Tab.History -> {
                                RecentsTab.show(RecentsTab.Segment.History)
                                RecentsTab
                            }
                            is Tab.Browse -> {
                                if (it.toExtensions) {
                                    BrowseTab.showExtension()
                                }
                                BrowseTab
                            }
                            Tab.Search -> SearchTab
                            is Tab.More -> MoreTab
                        }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadQueueScreen)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TabContent(tabNavigator: TabNavigator) {
        AnimatedContent(
            targetState = tabNavigator.current,
            transitionSpec = {
                materialFadeThroughIn(
                    initialScale = 1f,
                    durationMillis = TabFadeDuration,
                ) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)
            },
            label = "tabContent",
        ) {
            tabNavigator.saveableState(key = "currentTab", it) {
                it.Content()
            }
        }
    }

    @Composable
    private fun floatingTabItem(tab: eu.kanade.presentation.util.Tab): FloatingTabItem {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val badge = tabBadge(tab)
        return FloatingTabItem(
            title = tab.options.title,
            icon = tab.options.icon!!,
            selected = selected,
            badge = badge.first,
            badgeDescription = badge.second,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
        )
    }

    @Composable
    private fun NavigationRailTab(tab: eu.kanade.presentation.util.Tab) {
        val item = floatingTabItem(tab)
        NavigationSuiteItem(
            navigationSuiteType = NavigationSuiteType.NavigationRail,
            selected = item.selected,
            onClick = item.onClick,
            icon = {
                Icon(
                    painter = item.icon,
                    contentDescription = item.title,
                )
            },
            label = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            badge = if (item.badge > 0) {
                {
                    Badge {
                        Text(
                            text = item.badge.toString(),
                            modifier = Modifier.semantics {
                                item.badgeDescription?.let { contentDescription = it }
                            },
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    /** The count shown on [tab] and how a screen reader reads it. */
    @Composable
    private fun tabBadge(tab: eu.kanade.presentation.util.Tab): Pair<Int, String?> {
        val context = LocalContext.current
        val graph = context.appGraph
        val count by produceState(initialValue = 0, tab) {
            when (tab) {
                is RecentsTab -> {
                    combine(
                        graph.libraryPreferences.newShowUpdatesCount.changes(),
                        graph.libraryPreferences.newUpdatesCount.changes(),
                    ) { show, count ->
                        if (show) count else 0
                    }
                        .collectLatest { value = it }
                }

                is BrowseTab -> {
                    combine(
                        graph.sourcePreferences.extensionUpdatesCount.changes(),
                        graph.sourceHealthMonitor.problemCount(),
                    ) { extensionUpdates, healthProblems -> extensionUpdates + healthProblems }
                        .collectLatest { value = it }
                }

                is MoreTab -> {
                    graph.downloadManager.queueState
                        .map { it.size }
                        .collectLatest { value = it }
                }

                else -> value = 0
            }
        }
        if (count <= 0) return 0 to null
        val desc = when (tab) {
            is RecentsTab -> pluralStringResource(
                MR.plurals.notification_chapters_generic,
                count = count,
                count,
            )

            is BrowseTab -> {
                val extensionUpdates by graph.sourcePreferences.extensionUpdatesCount.collectAsState()
                val healthProblems = count - extensionUpdates
                listOfNotNull(
                    extensionUpdates.takeIf { it > 0 }?.let {
                        pluralStringResource(MR.plurals.update_check_notification_ext_updates, count = it, it)
                    },
                    healthProblems.takeIf { it > 0 }?.let {
                        pluralStringResource(MR.plurals.source_health_failing, count = it, it)
                    },
                )
                    .joinToString()
            }

            is MoreTab -> pluralStringResource(MR.plurals.download_queue_summary, count = count, count)

            else -> null
        }
        return count to desc
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data object Search : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}
