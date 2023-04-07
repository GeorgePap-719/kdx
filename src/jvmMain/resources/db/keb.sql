CREATE DATABASE IF NOT EXISTS keb;
USE keb;

CREATE TABLE IF NOT EXISTS workspace
(
    id mediumint not null,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS files
(
    name         smallint  not null,
    content      text      not null,
    workspace_id mediumint not null,
    CONSTRAINT PRIMARY KEY (name),
    CONSTRAINT FK_files_1 FOREIGN KEY (workspace_id) REFERENCES workspace (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS users
(
    name         varchar(255) not null,
    workspace_id mediumint    not null,
    PRIMARY KEY (name),
    CONSTRAINT FK_files_1 FOREIGN KEY (workspace_id) REFERENCES workspace (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;