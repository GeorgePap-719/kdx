package keb.server.routers.util

import keb.server.serialization.Json
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBodyOrNull

suspend inline fun <reified T : Any> ServerRequest.awaitAndRequireBody(): T {
    val body = awaitBodyOrNull<String>()
    requireNotNull(body) { bodyTypeErrorMessage<T>() }
    return deserializeBody(body)
}

inline fun <reified T : Any> deserializeBody(body: String): T {
    try {
        return Json.decodeFromString<T>(body)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(bodyTypeErrorMessage<T>())
    }
}

inline fun <reified T : Any> bodyTypeErrorMessage(): String {
    return "Body is expected to be type of ${T::class.simpleName}"
}

fun ServerRequest.pathVariableOrNull(name: String): String? {
    val vars = pathVariables()
    return vars[name]
}