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
data class WorkspaceEntity(
    val id: Int, // uuid to identify workspace
    val users: Set<UserEntity>,
    val files: Set<FileEntity>
)

@Table("users")
data class UserEntity(
    val name: String
)

@Table("files")
data class FileEntity(val content: String)

//data class Widget(val value: String)