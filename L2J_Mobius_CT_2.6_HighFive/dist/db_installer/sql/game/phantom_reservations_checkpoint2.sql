CREATE INDEX IF NOT EXISTS idx_phantom_economy_reservations_profile_operation
	ON phantom_economy_reservations (profile_id, operation_id);

CREATE TABLE IF NOT EXISTS `phantom_economy_offers`
(
    `offer_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `initiating_profile_id` BIGINT UNSIGNED NOT NULL,
    `initiating_character_object_id` INT NOT NULL,
    `operation_kind` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `counterparty_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `counterparty_profile_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `counterparty_character_object_id` INT NOT NULL,
    `offer_state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `offer_payload` VARBINARY(4096) NOT NULL,
    `initiator_lines` TINYINT UNSIGNED NOT NULL,
    `counterparty_lines` TINYINT UNSIGNED NOT NULL,
    `goal_id` BIGINT UNSIGNED NOT NULL,
    `goal_revision` BIGINT UNSIGNED NOT NULL,
    `operation_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `terminal_reason` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `expires_at` TIMESTAMP(3) NOT NULL,
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`offer_id`),
    KEY `idx_phantom_economy_offers_initiator_goal` (`initiating_profile_id`, `goal_id`, `goal_revision`, `offer_state`),
    KEY `idx_phantom_economy_offers_counterparty_state` (`counterparty_profile_id`, `offer_state`, `updated_at`),
    KEY `idx_phantom_economy_offers_state_expiry` (`offer_state`, `expires_at`, `offer_id`),
    CONSTRAINT `fk_phantom_economy_offers_profile`
        FOREIGN KEY (`initiating_profile_id`)
        REFERENCES `phantom_profiles` (`profile_id`)
        ON DELETE CASCADE
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4;
