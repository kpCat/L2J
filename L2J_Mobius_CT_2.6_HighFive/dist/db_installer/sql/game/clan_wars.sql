CREATE TABLE IF NOT EXISTS `clan_wars` (
  `war_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `clan1` varchar(35) NOT NULL DEFAULT '',
  `clan2` varchar(35) NOT NULL DEFAULT '',
  `wantspeace1` decimal(1,0) NOT NULL DEFAULT '0',
  `wantspeace2` decimal(1,0) NOT NULL DEFAULT '0',
  PRIMARY KEY (`war_id`),
  UNIQUE KEY `uq_clan_wars_pair` (`clan1`,`clan2`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;