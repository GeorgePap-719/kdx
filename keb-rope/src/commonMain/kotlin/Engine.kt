package keb.ropes

import keb.ropes.internal.emptyClosedOpenRange
import keb.ropes.internal.symmetricDifference
import keb.ropes.operations.simpleEdit

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
    val undoneGroups: Set<Int> // set of undo_group id's

    /**
     * The revision history of the document.
     */
    val history: List<Revision>

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
}

// for nicer API?
val Engine.head get() = text

val Engine.headRevId: RevId get() = history.last().id

val Engine.nextRevId: RevId get() = RevId(sessionId.first, sessionId.second, revIdCount)

fun Engine.findRevision(id: RevId): Int? {
    val indexOfRev = history
        .asReversed()
        .indexOfFirst { it.id == id }
    return if (indexOfRev == -1) null else indexOfRev
}

fun Engine.findRevToken(token: RevToken): Int? {
    val indexOfToken = history
        .asReversed() // lookup in recent ones first
        .indexOfFirst { it.id.token() == token }
    return if (indexOfToken == -1) null else indexOfToken
}

/// Garbage collection means undo can sometimes need to replay the very first
/// revision, and so needs a way to get the deletion set before then.
fun Engine.deletesFromUnionBeforeIndex(revIndex: Int, insertUndos: Boolean): Subset {
    // These two are supposed to be implemented via `Cow` operations.
    var deletesFromUnion = deletesFromUnion
    var undoneGroups = undoneGroups
    // Invert the changes to deletesFromUnion
    // starting in the present and working backwards.
    val revisions = history.subList(revIndex, history.size).asReversed()
    for (revision in revisions) {
        deletesFromUnion = when (val content = revision.edit) {
            is Edit -> {
                if (undoneGroups.contains(content.undoGroup)) {
                    // No need to un-delete undone inserts
                    // since we'll just shrink them out
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

/// the session ID component of a `RevId`
typealias SessionId = Pair<Long, Int>

private val defaultSession = SessionId(1, 0)

// Revision 0 is always an `Undo` of the empty set of groups.
private const val initialRevisionCounter = 1

internal class EngineImpl(
    sessionId: SessionId,
    revIdCount: Int,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset,
    undoneGroups: Set<Int>,
    history: List<Revision>
) : Engine {
    private var _sessionId = sessionId
    private var _revIdCount = revIdCount
    private var _text = text
    private var _tombstones = tombstones
    private var _deletesFromUnion = deletesFromUnion
    private var _undoneGroups = undoneGroups.toMutableSet()
    private var _history = history.toMutableList()

    override val sessionId: SessionId get() = _sessionId
    override val revIdCount: Int get() = _revIdCount
    override val text: Rope
        get() = _text
    override val tombstones: Rope get() = _tombstones
    override val deletesFromUnion: Subset get() = _deletesFromUnion
    override val undoneGroups: Set<Int> get() = _undoneGroups
    override val history: List<Revision> get() = _history

    override fun tryEditHistory(priority: Int, undoGroup: Int, baseRevToken: RevToken, delta: DeltaRope): Boolean {
        //TODO: mk_new_rev
        TODO("Not yet implemented")
    }
}