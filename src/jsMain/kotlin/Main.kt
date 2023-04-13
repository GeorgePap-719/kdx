package keb.frontend

import kotlinx.browser.document
import react.Fragment
import react.create
import react.dom.client.createRoot
import react.dom.html.ReactHTML.h1

fun main() {
    println("-------- triggerd -------")
    document.bgColor = "blue"
    val container = document.getElementById("root") ?: error("Couldn't find root container!")
    createRoot(container).render(Fragment.create {
        h1 {
            +"Hello, Keb,React+Kotlin/JS!"
        }
    })
}
