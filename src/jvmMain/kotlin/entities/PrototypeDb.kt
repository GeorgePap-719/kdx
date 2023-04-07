package keb.server.entities

import org.springframework.data.relational.core.mapping.Table

/*
 * A big assumption for this editor is that we will always have one unified ui.
 */

//@Table("editor")
// class Editor

/**
 * Core entity to represent application's UI.
 */
@Table("workspaces")
data class Workspace(
    val content: String, // TODO: convert it to `Text`
    val users: Set<User>,
    val files: Set<File>
)

@Table("users")
data class User(
    val name: String
)

@Table("files")
data class File(val content: String)

data class Widget(val value: String)