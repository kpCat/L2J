CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_events` (
  `event_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `action_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_profile` BIGINT UNSIGNED NOT NULL,
  `split` ENUM('TRAIN','TUNING','AUDIT') NOT NULL,
  `context_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin` ENUM('NATIVE_HEAL') NOT NULL,
  `receipt_sha` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_blob` MEDIUMBLOB NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_key`),
  UNIQUE KEY `tactical_native_action` (`action_key`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_splits` (
  `identity_kind` ENUM('ACTOR','CONTEXT') NOT NULL,
  `identity_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `split` ENUM('TRAIN','TUNING','AUDIT') NOT NULL,
  PRIMARY KEY (`identity_kind`,`identity_hash`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_runs` (
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dataset_sha` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `seed` BIGINT NOT NULL,
  `snapshot_blob` LONGBLOB NOT NULL,
  `state` ENUM('FROZEN','DRAFT','ACCEPTED_TEST','FAILED') NOT NULL,
  `reason` VARCHAR(256) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`run_id`),
  UNIQUE KEY `tactical_frozen_dataset` (`dataset_sha`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_audit_used` (
  `cohort_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`cohort_hash`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_models` (
  `model_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `algorithm` INT UNSIGNED NOT NULL,
  `algorithm_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `feature_schema` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `action_space` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `normalization` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin` ENUM('NATIVE_HEAL') NOT NULL,
  `status` ENUM('DRAFT','ACCEPTED_TEST') NOT NULL,
  `dataset_sha` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `weights_blob` MEDIUMBLOB NOT NULL,
  `sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `epochs` INT UNSIGNED NOT NULL,
  `train_count` INT UNSIGNED NOT NULL,
  `tuning_count` INT UNSIGNED NOT NULL,
  `audit_count` INT UNSIGNED NOT NULL,
  `audit_contexts` INT UNSIGNED NOT NULL,
  `audit_mse` DOUBLE NOT NULL,
  `constant_mse` DOUBLE NOT NULL,
  `prior_mse` DOUBLE NULL,
  `metrics_json` MEDIUMTEXT NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`model_id`),
  UNIQUE KEY `tactical_run_model` (`run_id`)
) ENGINE=InnoDB;
