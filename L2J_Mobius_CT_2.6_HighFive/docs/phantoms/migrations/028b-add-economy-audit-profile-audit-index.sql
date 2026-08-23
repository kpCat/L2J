-- Goal 028B standalone idempotent upgrade for existing High Five databases.
-- Stop LoginServer/GameServer and take a verified database backup before applying it.
-- The production database is forbidden for automated verification of this migration.
--
-- Exact existing (profile_id, audit_id) is a no-op. If the same index name has any
-- other shape, the selected ALTER deliberately fails with a duplicate key name;
-- the migration never drops or replaces the conflicting index.

SET @goal028b_index_rows :=
(
    SELECT COUNT(*)
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'phantom_economy_audit'
      AND `index_name` = 'idx_phantom_economy_audit_profile_audit'
);

SET @goal028b_index_shape :=
(
    SELECT GROUP_CONCAT(
        CONCAT(
            `seq_in_index`, ':',
            `column_name`, ':',
            COALESCE(`collation`, ''), ':',
            COALESCE(CAST(`sub_part` AS CHAR), 'FULL'), ':',
            `non_unique`, ':',
            `index_type`)
        ORDER BY `seq_in_index`
        SEPARATOR ',')
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'phantom_economy_audit'
      AND `index_name` = 'idx_phantom_economy_audit_profile_audit'
);

SET @goal028b_index_sql :=
    CASE
        WHEN @goal028b_index_rows = 0 THEN
            'ALTER TABLE `phantom_economy_audit` ADD KEY `idx_phantom_economy_audit_profile_audit` (`profile_id`, `audit_id`)'
        WHEN @goal028b_index_rows = 2
          AND @goal028b_index_shape = '1:profile_id:A:FULL:1:BTREE,2:audit_id:A:FULL:1:BTREE' THEN
            'SELECT ''Goal 028B index already exact no change.'' AS `goal028b_status`'
        ELSE
            'ALTER TABLE `phantom_economy_audit` ADD KEY `idx_phantom_economy_audit_profile_audit` (`profile_id`, `audit_id`)'
    END;

PREPARE `goal028b_index_statement` FROM @goal028b_index_sql;
EXECUTE `goal028b_index_statement`;
DEALLOCATE PREPARE `goal028b_index_statement`;
