package keb.ropes

/**
 * Represents a single edit to the document.
 */
class Revision(
    // This uniquely represents the identity of this revision and it stays
    /// the same even if it is rebased or merged between devices.
    val id: RevisionId,
    /// The largest undo group number of any edit in the history up to this
    /// point. Used to optimize undo to not look further back.
    val maxUndoSoFar: Int,
    val edit: Content
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Revision
        if (id != other.id) return false
        if (maxUndoSoFar != other.maxUndoSoFar) return false
        if (edit != other.edit) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + maxUndoSoFar
        result = 31 * result + edit.hashCode()
        return result
    }

    override fun toString(): String {
        return "Revision(id=$id,maxUndoSoFar=$maxUndoSoFar,edit=$edit)"
    }
}

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
) : Content() {
    override fun toString(): String {
        return "Edit(priority=$priority,undoGroup=$undoGroup,inserts=$inserts,deletes=$deletes)"
    }
}

class Undo(
    /// The set of groups toggled between undone and done.
    /// Just the `symmetric_difference` (XOR) of the two sets.
    val toggledGroups: Set<Int>,
    /// Used to store a reversible difference between the deleted
    /// characters before and after this operation.
    val deletesBitXor: Subset
) : Content() {
    override fun toString(): String {
        return "Undo(toggledGroups=$toggledGroups,deletesBitXor=$deletesBitXor)"
    }
}

/**
 * Returns the hash of this [RevisionId].
 */
/// Returns a u64 that will be equal for equivalent revision IDs and
/// should be as unlikely to collide as two random u64s.
//TODO: research about kotlin's hashCode() collision rate.
fun RevisionId.token(): RevisionToken = hashCode().toLong()

/**
 * An unique identifier for a [Revision].
 */
class RevisionId(
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
    val count: Int = 0
) {
    val sessionId: SessionId = SessionId(session1, session2)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RevisionId
        if (session1 != other.session1) return false
        if (session2 != other.session2) return false
        if (count != other.count) return false
        if (sessionId != other.sessionId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = session1.hashCode()
        result = 31 * result + session2
        result = 31 * result + count
        result = 31 * result + sessionId.hashCode()
        return result
    }

    override fun toString(): String {
        return "RevisionId(session1=$session1,session2=$session2,count=$count,sessionId=$sessionId)"
    }
}

/// Valid within a session. If there's a collision the most recent matching
/// Revision will be used, which means only the (small) set of concurrent edits
/// could trigger incorrect behavior if they collide, so u64 is safe.
typealias RevisionToken = Long