# Goal012 — последнее ограниченное исправление native-first curriculum

Spec: явное разрешение владельца от15.11.2026 в текущем Goal012, пункты1–9; исходный TASK012 и утверждённый exact source allowlist остаются действующими.

База GitHub/source004/TEST006:90cad0ad361fbe8620b1fca78d1926674b2eddcb, feature/phantom-world. Goal не новый. Только TEST-only V2; JDK25/Ant. MAIN read-only. Native012 по-прежнему0/2GS и0/2100000ms.

Гипотеза подтверждена: planCells вызывает classify/order по acquiredScenes раньше nativeAdmission.admit. Поэтому теоретический HIGHER может быть отвергнут полным Party MP/DPS admission, а остальные законные кандидаты уже не рассматриваются как HIGHER.

Решение задано владельцем: сначала полный native A/B admission и physical parity, затем относительная стратификация только принятых кандидатов. Сохранить cap12, seed183, sourceCursor0, native ограничения и все прежние receipts. Альтернатива с ослаблением MP/DPS запрещена. Расширение production не рассматривается.

Source changes только в3из7разрешённых файлов:
- PhantomAutonomousTrainingNative.java: V2 probe order без ранней страты; реальные допустимые roster subsets в фиксированном порядке без outcome inputs; сохранять paired-native witness и отрицательные отказы; после полного допуска выбрать2Solo+2Party с различными burden и AB/BA; записывать бюджет и failure evidence даже при cap12.
- PhantomEvaluationCurriculumV2.java: фильтр полного парного допуска перед classify; deterministic lower/higher внутри admitted direction; bounded roster subsets сохраняют learner и реальные экземпляры. Никаких class/skill/NPC allowlists.
- PhantomEvaluationOutcomeV2Checks.java: focused admission-before-strata, rejected theoretical higher, ties/partial admission, roster retention/bounds/determinism.

Порядок исполнения:
- [ ] Сохранить exact7source before/R1 и frozen safety/прошлые012 receipts hashes; подтвердить source clean.
- [ ] RED regression воспроизводит раннюю стратификацию на существующем поведении, затем GREEN native-first selection. Compile errors не считать behavioral RED.
- [ ] Минимальная интеграция в V2; V1 path/codecs byte unchanged. Targeted Ant012/011/autonomous009/008/007/JAR последовательно; encoding27+6 и exact diff.
- [ ] Один финальный независимый review, затем только необходимые contract fixes без расширения source allowlist.
- [ ] Scoped commit/non-force push и GitHub independent full-body readback; TEST tracked-clean FF и canonical Prepare ровно один исправленный headless pass с общим cap12. Без reroll seed или повторов reserved native episodes.
- [ ] Доказать для каждой из2Solo+2Party сцен оба native pre-CUT arms, physical parity и настоящую разницу burden. Только после полного offline PASS возможен EVAL012 в том же frozen campaign/budget.
- [ ] При недостаточном законном наборе: точный bounded unsatisfiable evidence (sources/HP/MP/learned skills/targets/native refusals/coverage/непроверенное), GS0, PARTIAL без дальнейших расширений или микросогласований. Не объявлять глобальную невозможность PvE по bounded catalog.
- [ ] Final OFF/STOP/owned0/portsfree, SQL/model/optimizer/pointer/исторические budgets parity, append-only R1 docs/evidence/archive и независимый GitHub final readback. №6/13, осталось7; следующий Goal не начинать.

Review focus: запрет classifier до полного допуска; обе стороны и parity; cap при исключениях; дубликаты roster/candidate; семейство Party с AB/BA; отсутствие outcome/model-score inputs; старые receipts не перезаписываются.

Авторизация исполнения уже дана последним сообщением владельца. Повторного согласования этого прямого исправления не требуется. Непроверенное: какие roster/targets объективно проходят native eligibility и появятся ли две различные Party нагрузки. Дополнительного разрешения при неуспехе не запрашивать.


Фактические rulings и завершённые шаги перечислены в R1_REGRESSION.md; общий cap12 был сохранён как4+8, без сброса.
