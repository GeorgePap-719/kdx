package keb.ropes

/**
 * Represents a single edit to the document.
 */
class Revision(
    // This uniquely represents the identity of this revision and it stays
    /// the same even if it is rebased or merged between devices.
    val id: RevId,
    /// The largest undo group number of any edit in the history up to this
    /// point. Used to optimize undo to not look further back.
    val maxUndoSoFar: Int,
    val edit: Content
)

sealed class Content
class Edit(
    /// Used to order concurrent inserts, for example auto-indentation
    /// should go before typed text.
    val priority: Int,
    /// Groups related edits together so that they are undone and re-done
    /// together. For example, an auto-indent insertion would be un-done
    /// along with the newline that triggered it.
    val undoGroup: Int,
    /// The subset of the characters of the union string from after this
    /// revision that were added by this revision.
    val inserts: Subset,
    /// The subset of the characters of the union string from after this
    /// revision that were deleted by this revision.
    val deletes: Subset
) : Content()

class Undo(
    /// The set of groups toggled between undone and done.
    /// Just the `symmetric_difference` (XOR) of the two sets.
    val toggledGroups: Set<Int>,
    /// Used to store a reversible difference between the deleted
    /// characters before and after this operation.
    val deletesBitXor: Subset
) : Content()

/**
 * An unique identifier for a [Revision].
 */
class RevId(
    // 96 bits has a 10^(-12) chance of collision with 400 million sessions and 10^(-6) with 100 billion.
    // `session1==session2==0` is reserved for initialization which is the same on all sessions.
    // A colliding session will break merge invariants and the document will start crashing Xi.
    val session1: Long,
    // if this was a tuple field instead of two fields, alignment padding would add 8 more bytes.
    val session2: Int,
    /**
     * A counter.
     */
    // There will probably never be a document with more than 4 billion edits
    // in a single session.
    val counter: Int = 0
)

/// Valid within a session. If there's a collision the most recent matching
/// Revision will be used, which means only the (small) set of concurrent edits
/// could trigger incorrect behavior if they collide, so u64 is safe.
typealias RevToken = Long