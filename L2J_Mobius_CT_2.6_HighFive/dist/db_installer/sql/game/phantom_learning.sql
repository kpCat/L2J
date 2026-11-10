CREATE TABLE IF NOT EXISTS `phantom_ai_training_examples` (
  `event_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_group` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope` ENUM('SOLO','PARTY') NOT NULL,
  `origin` ENUM('NATIVE','SYNTHETIC') NOT NULL,
  `evidence_kind` VARCHAR(32) CHARACTER SET ascii NOT NULL,
  `policy_version` BIGINT UNSIGNED NOT NULL,
  `schema_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_catalog` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_id` INT UNSIGNED NOT NULL,
  `skill_level` INT UNSIGNED NOT NULL,
  `damage` DOUBLE NOT NULL,
  `reward` FLOAT NOT NULL,
  `features` VARBINARY(512) NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_key`),
  KEY `training_retention` (`created_at`, `event_key`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_ai_model_versions` (
  `model_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `algo_version` INT UNSIGNED NOT NULL,
  `schema_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_catalog` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `step` INT UNSIGNED NOT NULL,
  `training_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `weights_blob` BLOB NOT NULL,
  `sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `training_count` INT UNSIGNED NOT NULL,
  `holdout_count` INT UNSIGNED NOT NULL,
  `initial_mse` DOUBLE NOT NULL,
  `holdout_mse` DOUBLE NOT NULL,
  `origin` ENUM('NATIVE','SYNTHETIC') NOT NULL,
  `status` ENUM('DRAFT','ACCEPTED','REVOKED') NOT NULL DEFAULT 'DRAFT',
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`model_id`)
) ENGINE=InnoDB;
