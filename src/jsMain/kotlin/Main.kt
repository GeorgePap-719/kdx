package keb.frontend

import csstype.Color
import csstype.Position
import emotion.react.css
import kotlinx.browser.document
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.client.createRoot
import react.dom.html.InputMode
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.textarea

fun main() {
    println("-------- rendering main -------")
    Fragment.create()

    val container = document.getElementById("root") ?: error("Couldn't find root container!")
    createRoot(container).render(App.create())
}

// app

val App = FC<Props> {
    h1 {
        +"Hello, Keb,React+Kotlin/JS!"
    }
    div {

        h3 {

            textarea {
                css {
                    position = Position.fixed
                    fontSize = csstype.FontSize.medium
                    backgroundColor = Color("#434248") // grey-dark
                    color = Color("#ECECF2")
                }
                itemID = "text"
                inputMode = InputMode.text
                draggable = false
            }
        }
    }
}
