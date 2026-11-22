-- Additive migration for existing game databases and fresh installations.
-- Apply to the game schema shared with LoginServer, before enabling DEDICATED.
-- Keep this table when disabling scouts so their durable identities can resume.
CREATE TABLE IF NOT EXISTS `phantom_dedicated_scouts`
(
    `slot` TINYINT UNSIGNED NOT NULL,
    `role` VARCHAR(24) NOT NULL,
    `account_name` VARCHAR(45) NOT NULL,
    `char_name` VARCHAR(35) NOT NULL,
    `ownership_token` VARCHAR(45) NOT NULL,
    `char_id` INT UNSIGNED NULL DEFAULT NULL,
    `row_version` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`slot`),
    UNIQUE KEY `uq_phantom_dedicated_scout_account` (`account_name`),
    UNIQUE KEY `uq_phantom_dedicated_scout_name` (`char_name`),
    UNIQUE KEY `uq_phantom_dedicated_scout_token` (`ownership_token`),
    UNIQUE KEY `uq_phantom_dedicated_scout_character` (`char_id`),
    CONSTRAINT `ck_phantom_dedicated_scout_slot` CHECK (`slot` BETWEEN 1 AND 32),
    CONSTRAINT `ck_phantom_dedicated_scout_role` CHECK (`role` = 'DEDICATED_SCOUT'),
    CONSTRAINT `ck_phantom_dedicated_scout_binding` CHECK
    (
        (`row_version` = 0 AND `char_id` IS NULL)
        OR (`row_version` = 1 AND `char_id` IS NOT NULL AND `char_id` BETWEEN 1 AND 2147483647)
    )
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;
