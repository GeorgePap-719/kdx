CREATE DATABASE IF NOT EXISTS keb;
USE keb;

CREATE TABLE IF NOT EXISTS document
(
    id        mediumint auto_increment not null,
    text_json JSON                     not null,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS document_file
(
    id           mediumint auto_increment not null,
    document_id  mediumint                not null,
    file_address mediumtext               not null,
    PRIMARY KEY (id),
    KEY FK_document_file (document_id),
    CONSTRAINT FK_document_file FOREIGN KEY (id) REFERENCES document (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;