package keb.server.repositories

abstract class RepositoryBase {

    suspend fun <T> runSingleSelect(selectAction: suspend () -> T): T {
        try {
            return selectAction()
        } catch (e: Throwable) {
            ifNeedWrapToIllegalStateExceptionOrThrow(e)
        }
    }

    suspend fun runSingleUpdate(updateAction: suspend () -> Long) {
        try {
            val affectedRows = updateAction()
            if (affectedRows != 1L) error("update operation affected:$affectedRows rows")
        } catch (e: Throwable) {
            ifNeedWrapToIllegalStateExceptionOrThrow(e)
        }
    }

    suspend fun runSingleDelete(deleteAction: suspend () -> Long) {
        try {
            val affectedRows = deleteAction()
            if (affectedRows != 1L) error("delete operation affected:$affectedRows rows")
        } catch (e: Throwable) {
            ifNeedWrapToIllegalStateExceptionOrThrow(e)
        }
    }

    private fun ifNeedWrapToIllegalStateExceptionOrThrow(e: Throwable): Nothing {
        if (e is IllegalArgumentException || e is NoSuchElementException) {
            throw IllegalStateException(e)
        } else {
            throw e
        }
    }
}