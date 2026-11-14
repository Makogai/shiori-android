package mihon.feature.friends.domain

/**
 * What an upload pass changes beyond the new reads (README v5): my rows on the server that no longer qualify are
 * deleted, and qualifying reads the server lacks (finished after the cursor passed them) are sent again.
 */
object ReadReconcile {

    data class Diff(
        /** Client ids to delete from the server, sorted. */
        val stale: List<String>,
        /** Qualifying reads at or after the Arena start that the server doesn't have, oldest first. */
        val missing: List<LocalRead>,
    )

    /**
     * [reads] is the whole local history; [serverIds] my client ids on the server; [startMillis] when I joined the
     * Arena (null: nothing is re-sent). With no local history at all nothing is deleted: that is a fresh install
     * signed in to an old player, not a player who unread everything.
     */
    fun diff(reads: List<LocalRead>, serverIds: Collection<String>, startMillis: Long?): Diff {
        val qualifying = reads.filter(ReadRows::qualifies).associateBy(ReadRows::clientId)
        val onServer = serverIds.toSet()
        val stale = if (reads.isEmpty()) emptyList() else onServer.filter { it !in qualifying }.sorted()
        val missing = if (startMillis == null) {
            emptyList()
        } else {
            qualifying.filter { (id, read) -> id !in onServer && read.readAt.toEpochMilli() >= startMillis }
                .values
                .sortedBy { it.readAt }
        }
        return Diff(stale, missing)
    }
}
