package eu.kanade.presentation.util

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

/**
 * For invoking back press to the parent activity
 */
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

interface Tab : cafe.adriel.voyager.navigator.tab.Tab {
    suspend fun onReselect(navigator: Navigator) {}
}

abstract class Screen : Screen {

    override val key: ScreenKey = uniqueScreenKey
}

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}

@Composable
fun DefaultNavigatorScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val slideDistance = rememberSlideDistance()
    ScreenTransition(
        navigator = navigator,
        transition = {
            // A predictive back gesture seeks towards the screen under the current one, which is still in the stack
            val from = navigator.items.indexOf(initialState)
            val to = navigator.items.indexOf(targetState)
            materialSharedAxisX(
                forward = if (from >= 0 && to >= 0) to > from else navigator.lastEvent != StackEvent.Pop,
                slideDistance = slideDistance,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun ScreenTransition(
    navigator: Navigator,
    transition: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
) {
    val transitionState = remember { SeekableTransitionState(navigator.lastItem) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(navigator.lastItem) {
        if (transitionState.targetState != navigator.lastItem) transitionState.animateTo(navigator.lastItem)
    }
    rememberTransition(transitionState, label = "transition").AnimatedContent(
        transitionSpec = transition,
        modifier = modifier,
    ) { screen ->
        navigator.saveableState("transition", screen) {
            content(screen)
        }
    }

    // Android 14+ back preview: the gesture drags the transition to the previous screen, and pops when released
    PredictiveBackHandler(enabled = navigator.canPop) { progress ->
        val current = navigator.lastItem
        val previous = navigator.items[navigator.items.lastIndex - 1]
        try {
            progress.collect { event -> transitionState.seekTo(event.progress, previous) }
            transitionState.animateTo(previous)
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch { transitionState.animateTo(current) }
            throw e
        }
    }
}
