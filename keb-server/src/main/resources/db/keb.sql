CREATE DATABASE IF NOT EXISTS keb;
USE keb;

CREATE TABLE IF NOT EXISTS document_file
(
    id          mediumint auto_increment not null,
    document_id mediumint                not null,
    fileAddress mediumtext               not null,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS document
(
    id        mediumint auto_increment not null,
    text_rope JSON                     not null,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;