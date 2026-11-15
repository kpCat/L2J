# Матрица обязательных доказательств — TEST016

Все ниже сначала DB-free в существующем Java/PS runner; одна mock-заглушка не может доказать реальное выполнение боевого навыка.

| ID | Случай | Обязательное поведение |
|---|---|---|
| R01 | `episodesPerBatch=84`, 48, иной version или EVAL-only | Отказ **до SQL** без создания нового campaign, без замены старых 015 receipts |
| R02 | `51`, три разные `PAIRED_PRECUT`, 2 класса, Solo+Party | `cohortCellLimit(51)=3`, 51 frozen slots, остановка отбора, seal marker LAST |
| R03 | первая STOCK рука без LEARNED | Не пара; при остановке не G3; нет training credit |
| R04 | обе A/B native-preCUT, разные incarnation IDs, matched physical | Идемпотентный append-only complete-pair receipt, no episode/credit |
| R05 | дубли source/cell/skill versions, одинаковые NPC objectId/epoch, неверные классы | Отказ, никогда не засчитывается покрытие |
| R06 | paired #1 готова, #2 прервана, cold resume | #1 восстановлена из immutable и не выполняется заново, #2 перепроверена либо новая пара, cursor/visits не сбрасываются |
| R07 | прервана запись manifest/CRC/SHA mismatch/reparse | Нет готовности, запрет нового cut, сохранены ошибочные receipts |
| R08 | изменена model/JAR/config/source/inventory/Geo/owner nonce | Отказ или STALE, без молчаливого восстановления на другой контекст |
| R09 | timeout/pause/cancel/owned process crash в фазах | Exit proofs, SQL/worker OFF, ровно учтённый общий бюджет, исключён duplicate dispatch |
| R10 | pre-CUT legal but live target moved/despawned / skill disabled/MP insufficient | Новый native recheck, отказ **перед** CUT; старая prep-пара не разрешение на каст |
| R11 | 015 S2 raw6 arms | Читает только для read-only regression; новый 016 seal = 0 при импортировании исторических артефактов |
| R12 | candidate class/family/skill/acquired, level/enchant, NPC, Geo, MP cast, stock-baseline | Все от текущего owner, без hardcoded SkillId/класса; MP estimate shortfall — риск, не полный запрет |
| R13 | рискованный Party + нормальный бой + target owner fail | Оба вида допуска и негативные native проверки, production groupViable parity относительно 015 |
| R14 | schedule split/seed и порядка AB/BA | Детерминированно до результатов; TRAIN/TUNING/AUDIT не смешиваются; отсутствие leakage EVAL012 |
| R15 | actual GS paid effect + death/kill/timeouts | Native receipts и реальные терминалы; штатный Player/SkillData/World authority, не synthetic. Если native NOT_RUN — G5 FAIL |
| R16 | legal resumed training episode with exact reservation/credit | Надёжный stop/recovery; один event/nonce — один credit максимум, неоднозначный исход не повторять |
| R17 | insufficient fresh training material | FIT=0, weights/Adam не обновляются, конкретная причина без fake optimizer delta |
| R18 | valid one FIT (если достижимо) | Новая версия TEST/optimizer записаны атомарно и раздельно; accepted best сохранена; cold restore проверен |
| R19 | postflight/encoding/push | Private 5SELECT historical rows/ids, Geo/protected, OFF/ports, GitHub full file+evidence readback |

**Не считать FULL:** один успешный offline seed, 015 S2 raw-пара, 3 созданные папки, один pre-CUT результат, синтетический executor call, deterministic training reward без native events, copy старых TUNING/AUDIT и публикация без GameServer.
