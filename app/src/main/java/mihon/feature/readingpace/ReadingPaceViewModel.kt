package mihon.feature.readingpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.data.readingpace.ReadingPaceSource

/** The reader's pace, read from the history once per reader. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ReadingPaceViewModel(source: ReadingPaceSource) : ViewModel() {

    private val mutablePace = MutableStateFlow<ReadingPace?>(null)
    val pace: StateFlow<ReadingPace?> = mutablePace.asStateFlow()

    init {
        viewModelScope.launch {
            mutablePace.value = ReadingPace.of(source.recent(ReadingPace.RECENT_CHAPTERS.toLong()))
        }
    }
}
