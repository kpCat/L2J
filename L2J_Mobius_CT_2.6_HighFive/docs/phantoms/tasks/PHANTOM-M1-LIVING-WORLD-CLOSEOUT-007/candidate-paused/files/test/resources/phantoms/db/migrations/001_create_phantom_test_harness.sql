CREATE TABLE IF NOT EXISTS `phantom_test_harness`
(
  `fixture_key` VARCHAR(64) NOT NULL,
  `seed` BIGINT NOT NULL,
  `fixture_value` VARCHAR(128) NOT NULL,
  `created_marker` VARCHAR(32) NOT NULL,
  PRIMARY KEY (`fixture_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
