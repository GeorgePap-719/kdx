package keb.ropes

// Notes:
// This probably will need to be a concurrent safe structure.
class JvmEngine : Engine {
    override val sessionId: SessionId
        get() = TODO("Not yet implemented")
    override val revIdCount: Int
        get() = TODO("Not yet implemented")
    override val text: Rope
        get() = TODO("Not yet implemented")
    override val tombstones: Rope
        get() = TODO("Not yet implemented")
    override val deletesFromUnion: Subset
        get() = TODO("Not yet implemented")
    override val undoneGroups: Set<Int>
        get() = TODO("Not yet implemented")
    override val history: List<Revision>
        get() = TODO("Not yet implemented")


}