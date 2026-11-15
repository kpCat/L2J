# Независимый code review015 и один fix pass

Свежий read-only reviewer `/root/goal015_policy_review`, gpt-6-astra/xhigh, проверил восемь source diff, утверждённый план, спецификации и ближайшие native границы. Он не запускал SQL/TEST/процессы. Первоначальный вердикт `REQUEST_CHANGES`: Critical0, Important2, Minor1. Это не приёмка G3/G5/G8.

## Important — исправлены

1. Director после TRAIN admission проверял только Readiness014Session:015 мог вызвать лишний independent EVAL admission и записать неверный schema13 completion. Реальная COLLECT AST-ветка выполнена в существующем PowerShell runner с stubbed внешними Java/config dependencies. Поведенческий RED `READINESS015_DIRECTOR_RAN_INDEPENDENT_PROBE`; после общей ReadinessSession/GoalVersion развилки обе версии14/15 дают TRAIN_ONLY и ноль probe calls. Это тест управления, не synthetic TRAIN evidence.
2. Несовместимые config version0/evaluationOnly=true могли обойти bounded015 mode до SQL. Новый guard вызывается после source config чтения и после восстановления parent config. Поведенческий RED `READINESS015_UNBOUNDED_OR_EVAL_MODE_ACCEPTED`; GREEN отвергает оба случая и сохраняет legacy session0.

В том же fix pass root обнаружил setup gap: оба native сеанса унаследовали batch84. PIPELINE015 предусматривает51; canonical `cohortCellLimit(84)=4`, `cohortCellLimit(51)=3`. Новая проверка entry mode отклоняет неправильный batch до SQL. RED `READINESS015_PIPELINE_51_NOT_ENFORCED`, затем GREEN. Данные проведённых сеансов не переписаны под51, дополнительного native запуска не было.

Первые диагностические попытки director test не являлись RED: AST был выбран слишком широко, затем отсутствовали local config/campaign inputs. Они исправлены в fixture; засчитан только наблюдаемый вызов probe. До него production исправление не вносилось.

Финальные focused PowerShell tests015/014 и legacy Headless013 PASS; final015-java.log exit0,47s, Party/support/011–015/catalog PASS. Нового ревью-раунда не было: исправления проверены TDD одним pass. GameServer code/JAR по этим review исправлениям не менялся; изменились только два согласованных wrapper/test scripts.

## Minor — явно отложен

Observer отключает старые D03–D05 и новый MP receipt не содержит полный selected loadout/skill levels и actual support readiness каждого участника того же forecast pass. Есть реальные MP/stock/charge/buff/regen/pet facts и отдельные acquired inventory/learner shortlist proofs, но они не являются полной детализацией этого вычисления. Точный размер завышения3060 и оптимальная native ротация остаются UNKNOWN. В этом минимальном fix receipt не получает повторный combat calculation или выдуманные навыки; дальнейшая детализация зафиксирована как незакрытое ограничение, не выдаётся за выполненную.

## Не оценённые reviewer live пункты

Reviewer не подтверждал S2 outcome, полный World/Party A/B cohort, реальные native MP расходы/смертность после CUT, SQL/Geo preservation или публикацию. Их root проверяет отдельными terminal/postflight/GitHub receipts. S2 был кооперативно остановлен после находки Important1: exit2/CANCELLED, worker и SQL absent proven, independent evaluations directory отсутствует. GS/CUT/FIT0. FULL015 не заявляется.
