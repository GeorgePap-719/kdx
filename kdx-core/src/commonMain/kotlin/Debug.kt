package kdx

internal expect val Any.hexAddress: String
internal expect val Any.classSimpleName: String
expect fun assert(value: () -> Boolean)