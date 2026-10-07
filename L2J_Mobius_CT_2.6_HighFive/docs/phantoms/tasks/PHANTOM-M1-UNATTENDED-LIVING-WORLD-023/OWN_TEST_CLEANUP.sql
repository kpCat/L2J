START TRANSACTION;
SELECT profile_id,character_object_id,row_version,created_at FROM phantom_profiles WHERE profile_id=74469 FOR UPDATE;
DELETE FROM phantom_profiles
WHERE profile_id=74469 AND character_object_id=268435465 AND row_version=0
AND created_at='2026-10-07 02:34:39.637'
AND NOT EXISTS (SELECT 1 FROM characters WHERE charId=268435465)
AND EXISTS (SELECT 1 FROM phantom_profile_components WHERE profile_id=74469 AND component_type='goal.runtime' AND row_version=0 AND SHA2(payload,256)='7f060d35cbc0eb33fd159c25f7450f072ecfacc191265ae3882bd323365192ee')
AND (SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=74469)=1;
SELECT ROW_COUNT() AS exact_owned_deleted;
COMMIT;
SELECT COUNT(*) AS retained_exact_profile FROM phantom_profiles WHERE profile_id=74469;
