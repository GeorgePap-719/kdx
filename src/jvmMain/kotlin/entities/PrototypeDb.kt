package keb.server.entities

import io.r2dbc.spi.Row
import keb.Text
import keb.server.utils.append
import keb.server.utils.getColumn
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.mapping.OutboundRow
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Component

/*
 * A big assumption for this editor is that we will always have one unified ui.
 */

//@Table("editor")
// class Editor

/**
 * Core entity to represent application's UI.
 */
//@Table("workspaces")
//data class WorkspaceEntity(
//    val id: Int, // uid to identify workspace
//    val users: Set<UserEntity>,
//    val files: Set<DocumentEntity>
//)
//
//@Table("users")
//data class UserEntity(
//    val name: String
//)

@Table("files")
data class DocumentEntity(
    @Id
    @Column("name")
    val name: String,

    @Column("text")
    val text: Text
)


@Component
@WritingConverter
class DocumentToRowConverter : Converter<DocumentEntity, OutboundRow> {
    override fun convert(source: DocumentEntity): OutboundRow {
        return OutboundRow()
            .append("name", source.name)
            .append("text", source.text.value)
    }
}

@Component
@ReadingConverter
class RowToDocumentConverter : Converter<Row, DocumentEntity> {
    override fun convert(source: Row): DocumentEntity {
        return DocumentEntity(
            source.getColumn("name"),
            source.getColumn("text")
        )
    }
}