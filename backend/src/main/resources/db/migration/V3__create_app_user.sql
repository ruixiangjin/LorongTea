CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL,
    password_hash VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    store_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT fk_app_user_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT ck_app_user_role_store CHECK (
        (role = 'CUSTOMER' AND store_id IS NULL) OR (role = 'STAFF' AND store_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
