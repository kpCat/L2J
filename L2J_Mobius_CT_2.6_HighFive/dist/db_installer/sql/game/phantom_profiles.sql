CREATE TABLE IF NOT EXISTS `phantom_profiles`
(
    `profile_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `character_object_id` INT NULL DEFAULT NULL,
    `schema_version` SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`profile_id`),
    UNIQUE KEY `uq_phantom_profiles_character_object_id` (`character_object_id`)
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS `phantom_profile_components`
(
    `profile_id` BIGINT UNSIGNED NOT NULL,
    `component_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `component_schema_version` SMALLINT UNSIGNED NOT NULL,
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `payload` VARBINARY(4096) NOT NULL,
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`profile_id`, `component_type`),
    CONSTRAINT `fk_phantom_profile_components_profile`
        FOREIGN KEY (`profile_id`)
        REFERENCES `phantom_profiles` (`profile_id`)
        ON DELETE CASCADE
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;
