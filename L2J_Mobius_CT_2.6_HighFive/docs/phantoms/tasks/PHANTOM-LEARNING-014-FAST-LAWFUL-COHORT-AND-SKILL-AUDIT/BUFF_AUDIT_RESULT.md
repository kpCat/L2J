# Read-only аудит баффов014

Production buff/Party/SkillData/config/XML не изменялись. Проверен текущий код на baseline42d6b675 и существующий DB-free `post002-support`:9/9 PASS, exit0. Это доказательство contracts и статических authority checks; реальные casts, длительность и замена abnormal этим запуском не доказаны. Старый native POST002 test использует другую TEST DB и в014 не запускался.

| Область | Статус | Основание и граница |
|---|---|---|
| MISSING/NEAR_EXPIRY/HEALTHY | PASS_CODE_CONTRACT | `PhantomPartyTactics.addMaintenance` пропускает HEALTHY; планирует missing/expiring. Existing cases03/09 проверяют подавление и отсутствие starvation следующего баффа. Native effect status в этих fixtures задаётся backend. |
| Реальный abnormal/уровень/остаток | PASS_CODE; UNKNOWN_NATIVE014 | `PhantomSupportEffectAuthority.status`: exact skill ID, native abnormal type/level, actual `BuffInfo.getTime`; weaker existing effect позволяет upgrade. Реальный stronger/near-expiry replacement014 не исполнялся. |
| Физический получатель | UNKNOWN_EFFECT_BENEFIT | Scope/learned/native target checks есть. Оценка полезности конкретных P.Atk/P.Def/attack-speed эффектов именно этому получателю не доказана. |
| Магический получатель | UNKNOWN_EFFECT_BENEFIT | Та же canonical authority. Нет доказательства оптимального M.Atk/cast-speed/MP набора для состава. |
| Смешанный состав | UNKNOWN_EFFECT_BENEFIT | Полезный смешанный эффект не запрещается по имени профессии. Приоритет по marginal benefit каждому получателю в просмотренном пути не реализован как отдельный evidence contract. |
| Self/Party/friendly/instance | PASS_CODE | `scopeAllows`, `L2jPhantomPartyBackend.supportEffectStatus`, exact current learned level; другой instance или чужая Party не становятся допустимыми кастами. Статус MISSING сам по себе не даёт разрешения на cast. |
| Songs/dances | PASS_CONTRACT; UNKNOWN_NATIVE014 | Existing case04: общий data-owned предел2 maintenance directives, все три capability families. Реальные эффекты и их длительности014 не измерены. |
| MP и текущая готовность | PASS_CODE_CONTRACT | Actor MP floor35%; `maintenanceAffordable` сохраняет35% после оплаты, current learned skill/native cast conditions/reuse проверяются в owners. Case07 отвергает not-ready поддержку. |
| Срочное лечение и защита | PASS_CONTRACT | Cases02/06/08: urgent/pressured heal раньше maintenance, выбор по нужде получателя; существующие Party fixtures проверяют protective idle и peer-before-self. Полный Party tactics suite повторно в014 не запускался. |
| Нативное исполнение | PASS_STATIC_AUTHORITY; UNKNOWN_EFFECT014 | Existing case05 проверяет native cast path, reuse Raid/Party authority и отсутствие direct effect injection. Это не actual paid native event. |
| Neural BUFF adapter | NOT_SUPPORTED | V3 TRAIN поддерживает ограниченные DAMAGE/HEAL. Работоспособность штатного баффинга не означает обучаемое действие BUFF. |

В `realCapabilities` contextual score зависит от MP актёра, а authored capabilities используют rank; явной модели полезности эффекта конкретному mage/fighter/hybrid здесь не найдено. Это ограничение качества выбора, а не доказанный сбой native баффинга. По текущим данным значимая функциональная ошибка не подтверждена; оснований менять работающую production-логику в014 нет.

Если владелец захочет закрыть UNKNOWN, минимальный отдельный scope — existing native integration на разрешённой TEST с effect-before/after, expiry/stacking и warrior/mage/hybrid recipients. Развитие recipient-aware ranking потребует отдельного решения и измерений. Ни один вариант в014 не применялся.
