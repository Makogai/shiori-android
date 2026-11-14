package mihon.domain.source.health

/** What a source request was for, so the Source health screen can say where it went wrong. */
enum class SourceOperation {
    LibraryUpdate,
    Browse,
    Details,
    Pages,
    HealthCheck,
}

/** Keeps each source's recent request outcomes for the Source health screen. */
interface SourceHealthRecorder {
    /** Records a request to [sourceId]: a success when [error] is null. */
    fun record(sourceId: Long, operation: SourceOperation, error: Throwable? = null)
}

/** Runs a request to [sourceId] and records how it went. */
inline fun <T> SourceHealthRecorder.recording(
    sourceId: Long,
    operation: SourceOperation,
    block: () -> T,
): T {
    val result = try {
        block()
    } catch (e: Throwable) {
        record(sourceId, operation, e)
        throw e
    }
    record(sourceId, operation)
    return result
}
