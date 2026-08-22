CREATE TABLE IF NOT EXISTS `clan_social_identity` (
  `identity_name` varchar(32) NOT NULL,
  `high_water` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`identity_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

INSERT IGNORE INTO `clan_social_identity` (`identity_name`, `high_water`)
VALUES ('alliance_incarnation', 0);
