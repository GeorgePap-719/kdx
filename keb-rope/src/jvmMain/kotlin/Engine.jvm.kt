package keb.ropes

import keb.ropes.internal.ConcurrentRope
import keb.ropes.internal.toConcurrentLinkedList
import keb.ropes.internal.toConcurrentSet
import kotlinx.atomicfu.atomic

// Notes:
// This probably will need to be a concurrent safe structure.
class JvmEngine(
    sessionId: SessionId,
    revIdCount: Int,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset,
    undoneGroups: Set<Int>,
    history: List<Revision>

) : Engine {
    private val _sessionId = atomic(sessionId)
    private val _revIdCount = atomic(revIdCount)
    private val _text = ConcurrentRope(text)
    private val _tombstones = ConcurrentRope(tombstones)
    private val _deletesFromUnion = atomic(deletesFromUnion)
    private val _undoneGroups = undoneGroups.toConcurrentSet()
    private val _history = history.toConcurrentLinkedList()


    override val sessionId: SessionId get() = _sessionId.value
    override val revIdCount: Int get() = _revIdCount.value
    override val text: Rope get() = _text.value
    override val tombstones: Rope get() = _tombstones.value
    override val deletesFromUnion: Subset get() = _deletesFromUnion.value
    override val undoneGroups: Set<Int> get() = _undoneGroups
    override val revisions: List<Revision> get() = _history
    override fun tryEditHistory(priority: Int, undoGroup: Int, baseRevToken: RevToken, delta: DeltaRope): Boolean {
        TODO("Not yet implemented")
    }
    //

}

fun JvmEngine(initialContent: Rope): JvmEngine {
    val engine = emptyJvmEngine()
    if (!initialContent.isEmpty()) {
        engine.headRevId.token()
        //let first_rev = engine.get_head_rev_id().token();
        //let delta = Delta::simple_edit(Interval::new(0, 0), initial_contents, 0);
        //engine.edit_rev(0, 0, first_rev, delta);
        //TODO: delta.
    }
    return engine
}

private fun emptyJvmEngine(): JvmEngine {
    val deletesFromUnion = Subset(0)
    val revId = RevId(0, 0, 0)
    val content = Undo(
        emptySet(),
        Subset(0)
    )
    val rev = Revision(revId, 0, content)
    return JvmEngine(
        defaultSession,
        1,
        emptyRope(),
        emptyRope(),
        deletesFromUnion,
        emptySet(),
        listOf(rev)
    )
}

private val defaultSession = SessionId(1, 0)