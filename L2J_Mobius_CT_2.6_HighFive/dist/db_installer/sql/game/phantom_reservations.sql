-- Goal 022 Checkpoint 1 follows the parent phantom_profiles migration.
CREATE TABLE IF NOT EXISTS `phantom_economy_operations`
(
    `operation_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `profile_id` BIGINT UNSIGNED NOT NULL,
    `character_object_id` INT NOT NULL,
    `goal_id` BIGINT UNSIGNED NOT NULL,
    `goal_revision` BIGINT UNSIGNED NOT NULL,
    `operation_kind` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `operation_state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `attempt_no` SMALLINT UNSIGNED NOT NULL,
    `intent_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `authority_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `intent_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `activity_generation` BIGINT UNSIGNED NOT NULL,
    `activity_tick` BIGINT UNSIGNED NOT NULL,
    `before_payload` VARBINARY(4096) NOT NULL,
    `intent_payload` VARBINARY(4096) NOT NULL,
    `terminal_result` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `terminal_reason` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `expires_at` TIMESTAMP(3) NOT NULL,
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`operation_id`),
    KEY `idx_phantom_economy_operations_profile_state` (`profile_id`, `operation_state`, `updated_at`),
    KEY `idx_phantom_economy_operations_character_state` (`character_object_id`, `operation_state`),
    CONSTRAINT `fk_phantom_economy_operations_profile`
        FOREIGN KEY (`profile_id`)
        REFERENCES `phantom_profiles` (`profile_id`)
        ON DELETE CASCADE
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS `phantom_economy_reservations`
(
    `canonical_resource_key` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `operation_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `reservation_ordinal` SMALLINT UNSIGNED NOT NULL,
    `profile_id` BIGINT UNSIGNED NOT NULL,
    `owner_object_id` INT NOT NULL,
    `owner_class_index` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `resource_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `object_id` INT NOT NULL DEFAULT 0,
    `item_id` INT NOT NULL DEFAULT 0,
    `reserved_count` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `expected_count` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `expected_enchant_level` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `expected_location` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`canonical_resource_key`),
    UNIQUE KEY `uq_phantom_economy_reservation_operation_ordinal` (`operation_id`, `reservation_ordinal`),
    KEY `idx_phantom_economy_reservations_owner` (`owner_object_id`, `resource_kind`, `object_id`, `item_id`),
    CONSTRAINT `fk_phantom_economy_reservations_operation`
        FOREIGN KEY (`operation_id`)
        REFERENCES `phantom_economy_operations` (`operation_id`)
        ON DELETE CASCADE
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS `phantom_economy_audit`
(
    `audit_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operation_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `profile_id` BIGINT UNSIGNED NOT NULL,
    `character_object_id` INT NOT NULL,
    `operation_kind` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `terminal_state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `result_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `reason_key` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `authority_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `intent_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `consequence_payload` VARBINARY(4096) NOT NULL,
    `items_consumed` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `items_produced` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `adena_source` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `adena_sink` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `crystals_produced` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `target_items_destroyed` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`audit_id`),
    UNIQUE KEY `uq_phantom_economy_audit_operation` (`operation_id`),
    KEY `idx_phantom_economy_audit_profile_created` (`profile_id`, `created_at`, `audit_id`),
    CONSTRAINT `fk_phantom_economy_audit_profile`
        FOREIGN KEY (`profile_id`)
        REFERENCES `phantom_profiles` (`profile_id`)
        ON DELETE CASCADE
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;
