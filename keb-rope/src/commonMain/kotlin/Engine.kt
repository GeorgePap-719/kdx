package keb.ropes

import keb.ropes.internal.AbstractEngine
import keb.ropes.internal.emptyClosedOpenRange
import keb.ropes.internal.symmetricDifference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline

/**
 * Represents the current state of a document and all of its history.
 */
interface Engine {
    /// The session ID used to create new `RevId`s for edits made on this device
    val sessionId: SessionId

    /// The incrementing revision number counter for this session used for `RevId`s
    val revisionIdCount: Int

    /// The current contents of the document as would be displayed on screen
    val text: Rope

    /// Storage for all the characters that have been deleted  but could
    /// return if a delete is un-done or an insert is re-done.
    val tombstones: Rope

    /// Imagine a "union string" that contained all the characters ever
    /// inserted, including the ones that were later deleted, in the locations
    /// they would be if they hadn't been deleted.
    ///
    /// This is a `Subset` of the "union string" representing the characters
    /// that are currently deleted, and thus in `tombstones` rather than
    /// `text`. The count of a character in `deletes_from_union` represents
    /// how many times it has been deleted, so if a character is deleted twice
    /// concurrently it will have count `2` so that undoing one delete but not
    /// the other doesn't make it re-appear.
    ///
    /// You could construct the "union string" from `text`, `tombstones` and
    /// `deletes_from_union` by splicing a segment of `tombstones` into `text`
    /// wherever there's a non-zero-count segment in `deletes_from_union`.
    val deletesFromUnion: Subset

    //TODO: `Set` under the hood is implemented by linkedHashSet,
    // which is an ordered implementation.
    // In our case, `order` is not just a simple implementation detail,
    // but a contract.
    // Should we use concrete impl?
    val undoneGroups: Set<Int> // set of undo_group id's

    /**
     * The revision history of the document.
     */
    val revisions: List<Revision>
}

interface MutableEngine : Engine {
    /// Merge the new content from another Engine into this one with a CRDT merge.
    fun merge(other: Engine)

    // Note: this function would need some work to handle retaining arbitrary revisions,
    // partly because the reachability calculation would become more complicated (a
    // revision might hold content from an undo group that would otherwise be gc'ed),
    // and partly because you need to retain more undo history, to supply input to the
    // reachability calculation.
    //
    // Thus, it's easiest to defer gc to when all plugins quiesce, but it's certainly
    // possible to fix it so that's not necessary.
    fun gc(gcGroups: Set<Int>)

    // Undo works conceptually by rewinding to the earliest point in history that a toggled undo group appears,
    // and replaying history from there but with revisions in the new undone_groups not applied.
    fun undo(groups: Set<Int>)

    /// Attempts to apply a new edit based on the [`Revision`] specified by `base_rev`,
    /// Returning an [`Error`] if the `Revision` cannot be found.
    //TODO: rethink naming,
    // this function, conceptually, is closer to tryApplyEditRevision().
    fun tryEditRevision(
        priority: Int,
        undoGroup: Int,
        baseRevision: RevisionToken,
        delta: DeltaRopeNode
    ): EngineResult<Unit>

    /**
     * Rebases [ops] on top of [expandBy],
     * and returns revision contents
     * that can be appended as new revisions on top of revisions represented by [expandBy].
     */
    //TODO: this is actually an internal API,
    // used only by merge()
    fun rebase(
        expandBy: MutableList<Pair<FullPriority, Subset>>,
        ops: List<DeltaOp>,
        maxUndoSoFar: Int
    ): RebasedResult
}

fun MutableEngine.editRevision(
    priority: Int,
    undoGroup: Int,
    baseRevision: RevisionToken,
    delta: DeltaRopeNode
) =
    tryEditRevision(priority, undoGroup, baseRevision, delta).getOrNull() ?: throw NoSuchElementException()

@JvmInline
value class EngineResult<out T> internal constructor(@PublishedApi internal val value: Any?) {

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (value !is Failed) value as T else null

    fun errorOrNull(): Failed? = if (value is Failed) value else null

    sealed interface Failed

    // An edit specified a revision that did not exist.
    // The revision may have been GC'd,
    // or it may have been specified incorrectly.
    class MissingRevision(@JvmField val value: RevisionToken) : Failed {
        override fun equals(other: Any?): Boolean = other is MissingRevision && value == other.value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "MissingRevision($value)"
    }

    /// A delta was applied which had a `base_len`
    // that did not match the length of the revision it was applied to.
    class MalformedDelta(@JvmField val revisionLen: Int, @JvmField val deltaLen: Int) : Failed {
        override fun equals(other: Any?): Boolean =
            other is MalformedDelta && revisionLen == other.revisionLen && deltaLen == other.deltaLen

        override fun hashCode(): Int {
            var result = revisionLen
            result = 31 * result + deltaLen
            return result
        }

        override fun toString(): String = "MalformedDelta($revisionLen,$deltaLen)"
    }

    companion object {
        fun <E> success(value: E): EngineResult<E> = EngineResult(value)
        fun <E> failure(cause: Failed): EngineResult<E> = EngineResult(cause)
    }

    override fun toString(): String {
        return when (value) {
            is Failed -> value.toString()
            else -> "Value($value)"
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> EngineResult<T>.getOrElse(onFailure: (failure: EngineResult.Failed) -> T): T {
    contract {
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    @Suppress("UNCHECKED_CAST")
    return if (value is EngineResult.Failed) onFailure(value) else value as T
}

class RebasedResult(
    val newRevisions: List<Revision>,
    val text: Rope,
    val tombstones: Rope,
    val deletesFromUnion: Subset
)

/**
 * Returns the [text][Rope] of head [Revision].
 * This is an alias for [Engine.text].
 */
val Engine.head get() = text

/**
 * Returns [RevisionId] of head revision.
 *
 * @throws NoSuchElementException if [Engine.revisions] are empty.
 */
val Engine.headRevisionId: RevisionId get() = revisions.last().id

val Engine.nextRevisionId: RevisionId get() = RevisionId(sessionId.first, sessionId.second, revisionIdCount)

/**
 * Returns the index of revision the specified [id], or -1 if it does exist.
 */
fun Engine.indexOfRevision(id: RevisionId): Int {
    return revisions
        .asReversed() // lookup in recent ones first
        .indexOfFirst { it.id == id }
}

/**
 * Returns the index of revision with the specified [token], or -1 if it does exist.
 */
fun Engine.indexOfRevision(token: RevisionToken): Int {
    return revisions
        .asReversed() // lookup in recent ones first
        .indexOfFirst { it.id.token() == token }
}

/**
 * Returns the [Engine.deletesFromUnion] at the time of given [revIndex].
 * In other words, the `deletes` from the "union string" at that time.
 */
fun Engine.getDeletesFromUnionForIndex(revIndex: Int): Subset {
    return getDeletesFromUnionBeforeIndex(revIndex + 1, true)
}

/// Garbage collection means undo can sometimes need to replay the very first
/// revision, and so needs a way to get the deletion set before then.
fun Engine.getDeletesFromUnionBeforeIndex(revIndex: Int, insertUndos: Boolean): Subset {
    // These two are supposed to be implemented via `Cow` operations.
    var deletesFromUnion = deletesFromUnion
    var undoneGroups = undoneGroups
    // Invert the changes to [deletesFromUnion]
    // starting in the present
    // and working backwards.
    val revView = revisions.subList(revIndex, revisions.size).asReversed()
    for (revision in revView) {
        deletesFromUnion = when (val content = revision.edit) {
            is Edit -> {
                if (undoneGroups.contains(content.undoGroup)) {
                    // No need to un-delete undone inserts
                    // since we'll just shrink them out.
                    deletesFromUnion.transformShrink(content.inserts)
                }
                val undeleted = deletesFromUnion.subtract(content.deletes)
                undeleted.transformShrink(content.inserts)
            }

            is Undo -> {
                if (insertUndos) {
                    val symmetricDifference = undoneGroups.symmetricDifference(content.toggledGroups)
                    val newUndone = symmetricDifference.toSet()
                    undoneGroups = newUndone
                    deletesFromUnion.xor(content.deletesBitXor)
                } else {
                    deletesFromUnion
                }
            }
        }
    }
    return deletesFromUnion
}

/**
 * Returns the contents of the document at the given [revIndex].
 */
fun Engine.getRevisionContentForIndex(revIndex: Int): Rope {
    val oldDeletesFromCurUnion = getDeletesFromCurUnionForIndex(revIndex)
    val delta = synthesize(tombstones.root, deletesFromUnion, oldDeletesFromCurUnion)
    val newRoot = delta.applyTo(text.root)
    return Rope(newRoot)
}

/**
 * Returns the [Subset] to delete from the current "union string"
 * in order to obtain a revision's content.
 */
fun Engine.getDeletesFromCurUnionForIndex(revIndex: Int): Subset {
    var deletesFromUnion = getDeletesFromUnionForIndex(revIndex)
    val revView = revisions.subList(revIndex + 1, revisions.size)
    for (revision in revView) {
        val content = revision.edit
        if (content is Edit) {
            if (content.inserts.isEmpty()) continue
            deletesFromUnion = deletesFromUnion.transformUnion(content.inserts)
        }
    }
    return deletesFromUnion
}

/**
 * Returns the largest undo-group-id used so far.
 *
 * @throws NoSuchElementException if [Engine.revisions] are empty.
 */
val Engine.maxUndoGroupId: Int get() = revisions.last().maxUndoSoFar

/**
 * Returns a revisions [content][Rope] with the specified [revisionToken], or `null` if it is not found.
 */
fun Engine.getRevisionContentOrNull(revisionToken: RevisionToken): Rope? {
    val revIndex = indexOfRevision(revisionToken)
    if (revIndex == -1) return null
    return getRevisionContentForIndex(revIndex)
}

/**
 * Creates a new [MutableEngine] with a single edit that inserts [initialContent], if it is not empty.
 */
/// It needs to be a separate commit rather than just
/// part of the initial contents since any two `Engine`s need a common
/// ancestor in order to be mergeable.
fun MutableEngine(initialContent: Rope): MutableEngine {
    val engine = emptyMutableEngine()
    if (initialContent.isEmpty()) return engine
    val firstRev = engine.headRevisionId.token()
    val delta = simpleEdit(emptyClosedOpenRange(), initialContent.root, 0)
    engine.editRevision(0, 0, firstRev, delta)
    return engine
}

/**
 * Creates a new empty [MutableEngine].
 */
fun emptyMutableEngine(): MutableEngine {
    val deletesFromUnion = emptySubset()
    val revisionId = RevisionId(0, 0, 0)
    val content = Undo(
        emptySet(),
        emptySubset()
    )
    val rev = Revision(revisionId, 0, content)
    return EngineImpl(
        defaultSession,
        1,
        emptyRope(),
        emptyRope(),
        deletesFromUnion,
        emptySet(),
        listOf(rev)
    )
}

/// the session ID component of a `RevId`
typealias SessionId = Pair<Long, Int>

private val defaultSession = SessionId(1, 0)

// Revision 0 is always an `Undo` of the empty set of groups.
private const val initialRevisionCounter = 1

class FullPriority(val priority: Int, val sessionId: SessionId) : Comparable<FullPriority> {
    /**
     * It compares based on [Lexicographic](https://en.wikipedia.org/wiki/Lexicographic_order) ordering.
     *
     * Properties:
     * * Two sequences are compared element by element.
     * * The first mismatching element defines which sequence is lexicographically less or greater than the other.
     * * If one sequence is a prefix of another, the shorter sequence is lexicographically less than the other.
     * * If two sequences have equivalent elements and are of the same length,
     * then the sequences are lexicographically equal.
     * * An empty sequence is lexicographically less than any non-empty sequence.
     * * Two empty sequences are lexicographically equal.
     *
     * Adapted from rust's [ordering trait](https://doc.rust-lang.org/std/cmp/trait.Ord.html#lexicographical-comparison).
     */
    //TODO: this needs testing and more research about its correctness.
    override fun compareTo(other: FullPriority): Int {
        val priority = priority.compareTo(other.priority)
        if (priority != 0) return priority
        return sessionId.compareTo(other.sessionId)
    }

    private fun SessionId.compareTo(other: SessionId): Int {
        val firstComp = first.compareTo(other.first)
        if (firstComp != 0) return firstComp
        return second.compareTo(other.second)
    }
}

internal class EngineImpl(
    sessionId: SessionId,
    revIdCount: Int,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset,
    undoneGroups: Set<Int>,
    history: List<Revision>
) : AbstractEngine() {
    private var _sessionId = sessionId
    private var _revIdCount = revIdCount
    private var _text = text
    private var _tombstones = tombstones
    private var _deletesFromUnion = deletesFromUnion
    private var _undoneGroups = undoneGroups.toMutableSet()
    private var _revisions = history.toMutableList()

    override val sessionId: SessionId get() = _sessionId
    override val revisionIdCount: Int get() = _revIdCount
    override val text: Rope get() = _text
    override val tombstones: Rope get() = _tombstones
    override val deletesFromUnion: Subset get() = _deletesFromUnion
    override val undoneGroups: Set<Int> get() = _undoneGroups
    override val revisions: List<Revision> get() = _revisions

    override fun trySetText(value: Rope): Boolean {
        _text = value
        return true
    }

    override fun trySetTombstones(value: Rope): Boolean {
        _tombstones = value
        return true
    }

    override fun trySetDeletesFromUnion(value: Subset): Boolean {
        _deletesFromUnion = value
        return true
    }

    override fun trySetUndoneGroups(newUndoneGroups: Set<Int>): Boolean {
        _undoneGroups = newUndoneGroups.toMutableSet()
        return true
    }

    override fun reverseRevisions() = _revisions.reverse()
    override fun appendRevision(element: Revision): Boolean = _revisions.add(element)
    override fun appendRevisions(elements: List<Revision>): Boolean = _revisions.addAll(elements)
    override fun clearRevisions() = _revisions.clear()

    override fun incrementRevIdCountAndGet(): Int = ++_revIdCount
}