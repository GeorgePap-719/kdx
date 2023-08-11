package keb.ropes

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
}

// for nicer API?
val Engine.head get() = text

val Engine.headRevId: RevId get() = history.last().id

fun Engine.nextRevId(): RevId = RevId(sessionId.first, sessionId.second, revIdCount)

fun Engine(initial: Rope): Engine {
    TODO()
}

fun emptyEngine(): Engine {
    TODO()
}

/// the session ID component of a `RevId`
typealias SessionId = Pair<Long, Int>

//TODO: EmptyEngine