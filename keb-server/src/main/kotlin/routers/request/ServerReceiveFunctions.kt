package keb.server.routers.request

import keb.server.serialization.Json
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBodyOrNull

suspend inline fun <reified T : Any> ServerRequest.awaitAndReceive(): T {
    val body = awaitBodyOrNull<String>()
    requireNotNull(body) { bodyTypeErrorMessage<T>() }
    return deserializeBody(body)
}

// private API
inline fun <reified T : Any> deserializeBody(body: String): T {
    try {
        return Json.decodeFromString<T>(body)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(bodyTypeErrorMessage<T>())
    }
}

// private API
inline fun <reified T : Any> bodyTypeErrorMessage(): String {
    return "Body is expected to be type of ${T::class.simpleName}"
}

fun ServerRequest.pathVariableOrNull(name: String): String? {
    val vars = pathVariables()
    return vars[name]
}