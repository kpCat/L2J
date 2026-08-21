-- Goal 027C manual one-shot upgrade for existing High Five databases.
-- Stop LoginServer/GameServer, take a verified database backup, and apply this file exactly once.
-- Do not apply it to a database where ally_generation, ally_generation_counter, or war_id already exists.
-- MariaDB DDL commits implicitly: a failed statement must be diagnosed before any retry.

ALTER TABLE `clan_data`
  ADD COLUMN `ally_generation` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `ally_name`,
  ADD COLUMN `ally_generation_counter` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `ally_generation`;

-- Pre-027C active alliances have no typed incarnation history. Generation 1 is their
-- exact upgrade identity. Only active leaders initialize their own creation counter;
-- inactive/member clans start at 0 because no pre-027C typed stale operation exists.
UPDATE `clan_data`
SET `ally_generation` = 1,
    `ally_generation_counter` = CASE WHEN `clan_id` = `ally_id` THEN 1 ELSE 0 END
WHERE `ally_id` <> 0 AND `ally_generation` = 0;

-- Existing directed wars are preserved. MariaDB assigns each retained row one durable
-- war_id. The legacy pair remains unique, while W1 delete followed by W2 insert obtains
-- a different auto-increment identity that survives restart.
ALTER TABLE `clan_wars`
  DROP PRIMARY KEY,
  ADD COLUMN `war_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST,
  ADD PRIMARY KEY (`war_id`),
  ADD UNIQUE KEY `uq_clan_wars_pair` (`clan1`, `clan2`);