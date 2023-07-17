package keb.server.dto

import keb.server.model.Text
import kotlinx.serialization.Serializable

@Serializable
data class Document(val name: String, val text: Text)

