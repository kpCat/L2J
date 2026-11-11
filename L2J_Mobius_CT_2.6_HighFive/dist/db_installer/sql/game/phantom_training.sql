CREATE TABLE IF NOT EXISTS `phantom_training_schema` (
  `version` INT UNSIGNED NOT NULL,
  `applied_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB;
INSERT IGNORE INTO `phantom_training_schema` (`version`) VALUES (1);

CREATE TABLE IF NOT EXISTS `phantom_training_control` (
  `singleton` TINYINT UNSIGNED NOT NULL,
  `installation_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `next_ordinal` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `epoch` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `checkpoint_epoch` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `active_model` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `active_sha` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `evaluating` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`singleton`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_episodes` (
  `episode_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `epoch` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` BIGINT UNSIGNED NOT NULL,
  `scenario` VARCHAR(160) NOT NULL,
  `scenario_seed` BIGINT NOT NULL,
  `scope` ENUM('SOLO','PARTY') NOT NULL,
  `split` ENUM('TRAIN','TUNING','AUDIT') NOT NULL,
  `state` ENUM('ACTIVE','SETTLING','COMMITTED','INCOMPLETE') NOT NULL,
  `before_json` MEDIUMTEXT,
  `after_json` MEDIUMTEXT,
  `actions_json` MEDIUMTEXT,
  `outcome` VARCHAR(160) NOT NULL DEFAULT '',
  `reason` VARCHAR(256) NOT NULL DEFAULT '',
  `finish_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `elapsed_millis` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `rich_pruned` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `finished_at` TIMESTAMP(6) NULL,
  PRIMARY KEY (`episode_id`),
  UNIQUE KEY `training_episode_ordinal` (`ordinal`),
  KEY `training_episode_state` (`state`,`ordinal`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_actors` (
  `actor_group` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `split` ENUM('TRAIN','TUNING','AUDIT') NOT NULL,
  PRIMARY KEY (`actor_group`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_events` (
  `event_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `episode_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_group` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope` ENUM('SOLO','PARTY') NOT NULL,
  `origin` ENUM('NATIVE') NOT NULL,
  `evidence_kind` VARCHAR(32) CHARACTER SET ascii NOT NULL,
  `policy_version` BIGINT UNSIGNED NOT NULL,
  `schema_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_catalog` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_id` INT UNSIGNED NOT NULL,
  `skill_level` INT UNSIGNED NOT NULL,
  `damage` DOUBLE NOT NULL,
  `reward` FLOAT NOT NULL,
  `features` VARBINARY(512) NOT NULL,
  `observation_json` MEDIUMTEXT,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_key`),
  KEY `training_event_episode` (`episode_id`),
  CONSTRAINT `training_event_episode_fk` FOREIGN KEY (`episode_id`) REFERENCES `phantom_training_episodes` (`episode_id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_attempts` (
  `attempt_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` ENUM('PREPARING','DRAFT','ACCEPTED','FAILED') NOT NULL,
  `reason` VARCHAR(256) NOT NULL DEFAULT '',
  `seed` BIGINT NOT NULL,
  `config_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dataset_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `skill_catalog` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expected_epoch` BIGINT UNSIGNED NOT NULL,
  `prior_model` BIGINT UNSIGNED NOT NULL,
  `prior_sha` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `algo_version` INT UNSIGNED NULL,
  `step` INT UNSIGNED NULL,
  `weights_blob` BLOB,
  `sha256` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `metrics_json` MEDIUMTEXT,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `finished_at` TIMESTAMP(6) NULL,
  PRIMARY KEY (`attempt_id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_dataset` (
  `attempt_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `episode_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_group` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope` ENUM('SOLO','PARTY') NOT NULL,
  `split` ENUM('TRAIN','TUNING','AUDIT') NOT NULL,
  `scenario` VARCHAR(160) NOT NULL,
  `scenario_seed` BIGINT NOT NULL,
  `policy_version` BIGINT UNSIGNED NOT NULL,
  `skill_id` INT UNSIGNED NOT NULL,
  `skill_level` INT UNSIGNED NOT NULL,
  `damage` DOUBLE NOT NULL,
  `reward` FLOAT NOT NULL,
  `features` VARBINARY(512) NOT NULL,
  `context_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`attempt_id`,`event_key`),
  KEY `training_dataset_audit` (`actor_group`,`scenario`,`split`),
  KEY `training_dataset_scenario_split` (`scenario`,`split`),
  CONSTRAINT `training_dataset_attempt_fk` FOREIGN KEY (`attempt_id`) REFERENCES `phantom_training_attempts` (`attempt_id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `phantom_training_audit_claims` (
  `cohort_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`cohort_key`),
  CONSTRAINT `training_audit_attempt_fk` FOREIGN KEY (`attempt_id`) REFERENCES `phantom_training_attempts` (`attempt_id`)
) ENGINE=InnoDB;
