CREATE TABLE IF NOT EXISTS `phantom_test_schema_manifest`
(
  `manifest_key` VARCHAR(64) NOT NULL,
  `schema_version` INT NOT NULL,
  `script_count` INT NOT NULL,
  `statement_count` INT NOT NULL,
  `aggregate_sha256` CHAR(64) NOT NULL,
  PRIMARY KEY (`manifest_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
