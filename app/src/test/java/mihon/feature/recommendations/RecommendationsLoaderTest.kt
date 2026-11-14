package mihon.feature.recommendations

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsLoaderTest {

    private val items = listOf(Recommendation("Monster", null), Recommendation("Pluto", "cover"))

    private fun TestScope.loader(fetch: suspend () -> List<Recommendation>) =
        RecommendationsLoader(this, fetch)

    @Test
    fun `stays idle until asked, then loads once`() = runTest {
        var calls = 0
        val loader = loader {
            calls++
            items
        }
        advanceUntilIdle()
        loader.state.value shouldBe RecommendationsState.Idle
        calls shouldBe 0

        loader.load()
        loader.state.value shouldBe RecommendationsState.Loading
        advanceUntilIdle()
        loader.state.value shouldBe RecommendationsState.Loaded(items)

        loader.load()
        advanceUntilIdle()
        calls shouldBe 1
    }

    @Test
    fun `no recommendations is empty, not hidden`() = runTest {
        val loader = loader { emptyList() }
        loader.load()
        advanceUntilIdle()
        loader.state.value shouldBe RecommendationsState.Empty
    }

    @Test
    fun `a failure can be retried`() = runTest {
        var fail = true
        val loader = loader {
            if (fail) throw IOException("offline")
            items
        }
        loader.load()
        advanceUntilIdle()
        loader.state.value shouldBe RecommendationsState.Failed

        fail = false
        loader.load()
        advanceUntilIdle()
        loader.state.value shouldBe RecommendationsState.Loaded(items)
    }
}
