# Полная финальная cohort024

Frozen SHA: ca3cbef9c03b695dcb9f84a734c5535b4ac72027; A=383.7788321s, B=382.7250723s. Primaries A=110/175; B=252/832. Все заранее выбранные участники сохранены в отчёте.

|Scene|Profile|Primary|Same epoch|Missing samples|Max idle s|Cycles|Rewards|Tail120|EXP/SP delta|Result|
|---|---:|---|---|---:|---:|---:|---:|---:|---|---|
|A|110|True|True|0|307.9|5|5|0|5565/265|FAIL|
|A|175|True|False|4|270.3|0|0|0|0/0|FAIL|
|A|187|False|True|0|75.9|13|14|7|2940/140|PASS|
|A|252|False|True|0|383.8|0|0|0|0/0|FAIL|
|A|275|False|False|5|38.7|0|0|0|0/0|FAIL|
|A|506|False|False|24|91.1|0|0|0|0/0|FAIL|
|B|110|False|False|13|76.8|0|0|0|0/0|FAIL|
|B|175|False|False|57|382.7|0|0|0|0/0|FAIL|
|B|252|True|True|0|310.9|4|5|0|1050/50|FAIL|
|B|832|True|True|8|105.5|26|28|3|2144/245|FAIL|
|B|879|False|True|0|349.1|3|4|0|233/25|FAIL|
|B|1179|False|False|28|186.7|0|0|0|0/0|FAIL|

A: 1/6 PASS; B: 0/6 PASS. Overflow/PHASE_DEADLINE, отсутствующие samples и нулевой tail120 не исключены. Даже 832 с26 cycles не прошёл: missing8, idle105.5s, overflow=true. Изменение epoch не разрешало подменить исходную natural cohort.

Class coverage: A class25 ELVEN_MAGE; B class25 mages и class18 ELVEN_FIGHTER832/1179. Mapping проверен PlayerClass.java; значения взяты из canonical character export, class не подменялся. Fighter/mage coverage есть, но это не cohort PASS.

Raw: evidence/FINAL_R4_SCENE_A и FINAL_R4_SCENE_B. Это обычный full GameServer, stock scheduler/NPC, ActorMode=Synthetic. Клиентский REAL pass отсутствует. Данные EXP/SP в таблице только диагностические deltas, они никогда не применялись к SQL.
