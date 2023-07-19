package keb.server.entities

import keb.server.model.Text
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("documentFile")
data class DocumentFileEntity(
    @Id
    @Column("id")
    val id: Long,

    @Column("document")
    val document: DocumentEntity,

    @Column("fileAddress")
    val fileAddress: FileAddress
)

data class FileAddress(val value: String)

@Table("document")
data class DocumentEntity(
    @Id
    @Column("id")
    val id: Long,

    @Column("text")
    val text: Text
)