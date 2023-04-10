package keb

data class Document(val name: String, val text: Text)

data class Text(val value: String) {
    operator fun plus(other: Text): Text = Text(value + other.value)
    operator fun minus(other: Text): Text {
        val otherChars = other.value
        if (!value.contains(otherChars)) return this
        return Text(value.filter { !otherChars.contains(it) })
    }
}