package keb

data class Document(val name: String, val text: Text)

data class Text(val value: String) {
    operator fun plus(other: Text): Text = Text(value + other.value)
}