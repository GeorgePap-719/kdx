package kdx

actual fun assert(value: () -> Boolean) {}

private var counter = 0

internal actual val Any.hexAddress: String
    get() {
        var result = this.asDynamic().__debug_counter
        if (jsTypeOf(result) !== "number") {
            result = ++counter
            this.asDynamic().__debug_counter = result

        }
        return (result as Int).toString()
    }

internal actual val Any.classSimpleName: String get() = this::class.simpleName ?: "Unknown"