package keb.ropes

import keb.ropes.internal.emptyClosedOpenRange
import keb.ropes.internal.symmetricDifference
import keb.ropes.ot.simpleEdit
import keb.ropes.ot.synthesize
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
    val revIdCount: Int

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
    // TODO: have `base_rev` be an index so that it can be used maximally
    // efficiently with the head revision, a token or a revision ID.
    // Efficiency loss of token is negligible but unfortunate.
    /// Attempts to apply a new edit based on the [`Revision`] specified by `base_rev`,
    /// Returning an [`Error`] if the `Revision` cannot be found.
    fun tryEditHistory(
        priority: Int,
        undoGroup: Int,
        baseRevToken: RevToken,
        delta: DeltaRope
    ): Boolean // maybe here we need EngineResult

    fun tryUpdateText(newText: Rope): Boolean

    fun tryUpdateTombstones(newTombstones: Rope): Boolean

    fun tryUpdateDeletesFromUnion(transform: Subset): Boolean
}

@JvmInline
value class EngineResult<out T> internal constructor(@PublishedApi internal val value: Any?) {

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (value !is Failed) value as T else null

    fun errorOrNull(): Failed? = if (value is Failed) value else null

    sealed interface Failed

    // An edit specified a revision that did not exist.
    // The revision may have been GC'd,
    // or it may have been specified incorrectly.
    class MissingRevision(@JvmField val value: RevToken) : Failed {
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

/**
 * Returns the [text][Rope] of head [Revision].
 * This is an alias for [Engine.text].
 */
val Engine.head get() = text

/**
 * Returns [RevId] of head revision.
 *
 * @throws NoSuchElementException if [Engine.revisions] are empty.
 */
val Engine.headRevId: RevId get() = revisions.last().id

val Engine.nextRevId: RevId get() = RevId(sessionId.first, sessionId.second, revIdCount)

/**
 * Returns the index of revision the specified [id], or -1 if it does exist.
 */
fun Engine.indexOfRev(id: RevId): Int {
    return revisions
        .asReversed() // lookup in recent ones first
        .indexOfFirst { it.id == id }
}

/**
 * Returns the index of revision with the specified [token], or -1 if it does exist.
 */
fun Engine.indexOfRev(token: RevToken): Int {
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
fun Engine.getRevContentForIndex(revIndex: Int): Rope {
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
 * Tries to find a [Revision] with the specified [revToken].
 */
fun Engine.findRevision(revToken: RevToken): Rope? {
    val revIndex = indexOfRev(revToken)
    if (revIndex == -1) return null
    return getRevContentForIndex(revIndex)
}

/**
 * TODO
 */
fun Engine(initialContent: Rope): Engine {
    val engine = emptyEngine()
    if (!initialContent.isEmpty()) {
        val firstRev = engine.headRevId.token()
        val delta = simpleEdit(emptyClosedOpenRange(), initialContent.root, 0)
        //TODO: engine.edit_rev()
    }
    return engine
}

internal fun emptyEngine(): Engine {
    val deletesFromUnion = Subset(0)
    val revId = RevId(0, 0, 0)
    val content = Undo(
        emptySet(),
        Subset(0)
    )
    val rev = Revision(revId, 0, content)
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

fun DeltaRopeNode.applyTo(rope: Rope): Rope {
    val newRoot = applyTo(rope.root)
    return Rope(newRoot)
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
) : MutableEngine {
    private var _sessionId = sessionId
    private var _revIdCount = revIdCount
    private var _text = text
    private var _tombstones = tombstones
    private var _deletesFromUnion = deletesFromUnion
    private var _undoneGroups = undoneGroups.toMutableSet()
    private var _history = history.toMutableList()

    override val sessionId: SessionId get() = _sessionId
    override val revIdCount: Int get() = _revIdCount
    override val text: Rope get() = _text
    override val tombstones: Rope get() = _tombstones
    override val deletesFromUnion: Subset get() = _deletesFromUnion
    override val undoneGroups: Set<Int> get() = _undoneGroups
    override val revisions: List<Revision> get() = _history

    override fun tryEditHistory(priority: Int, undoGroup: Int, baseRevToken: RevToken, delta: DeltaRope): Boolean {
        //TODO: mk_new_rev
        TODO("Not yet implemented")
    }

    override fun tryUpdateText(newText: Rope): Boolean {
        TODO("Not yet implemented")
    }

    override fun tryUpdateTombstones(newTombstones: Rope): Boolean {
        TODO("Not yet implemented")
    }

    override fun tryUpdateDeletesFromUnion(transform: Subset): Boolean {
        TODO("Not yet implemented")
    }
}