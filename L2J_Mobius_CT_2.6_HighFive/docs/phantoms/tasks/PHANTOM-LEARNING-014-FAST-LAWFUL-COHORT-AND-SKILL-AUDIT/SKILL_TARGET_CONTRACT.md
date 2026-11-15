# Как фантом должен понимать навыки

## Разделить обнаружение, законность и тактику

1. **Acquired**: `Player.getAllSkills()` — фактически изучено, не потенциальная профессия; идентичность skillId+level+classIndex+enchantRoute/enchantLevel/active effects и изменения при reskill/subclass.
2. **Action type**: SINGLE_TARGET_DAMAGE, HEAL, SELF_BUFF, PARTY_BUFF, AOE, CONTROL, DEBUFF, SUMMON, RESURRECT, MOBILITY и т.д. Для отсутствующего adapter сохранять UNKNOWN/NOT_SUPPORTED; не предлагать действие в V3 как DAMAGE/HEAL по умолчанию.
3. **Target semantics**: native real Skill target type + conditions, friendly/hostile, self/party/single, owner, instance, range/LOS/Geo, cooldown, status, resources, weapon. Должны проверяться непосредственно перед cast.
4. **Policy**: stock baseline + bounded lawful shortlist. При >4 доступных skills — полная инвентаризация отдельно от shortlist, воспроизводимая ротация; не всегда первые четыре. V3 may score <=16; `NULL/NO_LEGAL_ACTION` является честным результатом.
5. **Execution**: только native owner, exact selected skill/version, реальный target/actor, evidence of paid HP/MP/skill, no forged effects/rewards.

## Негативные проверки

- Третья профессия с изученным старым навыком (Hydro Blast как пример, но НЕ hardcode списка) видит его в доступных действиях, если он подходит ситуации. Неизученный skill не выдаётся.
- После заточки текущая версия skill отличается от прежней, в cache и перед кастом не остаётся устаревший ref.
- Attack не выбирается для friendly target; heal не выбирается для hostile Monster; self buff не выдаётся другому игроку; неправильные targetType/instance/Geo не проходят.
- CC/AOE/SUMMON/RETURN, которые не поддерживаются нейросетевым action adapter, не переименовываются в DAMAGE для галочки. Возможность отдельного штатного owner не означает поддержку в V3.
- Низкий MP, cooldown, no ammo/shot/item, wrong weapon, immunity/abnormal stacking, changed owner/cast state должны давать корректный отказ или fallback, а не нелегальный cast.
- Не оценивать действия только по размеру числового skillId или по классу. На приоритет влияют actual action features и цель, но это пока ограниченный single-target scorer, не вся тактическая система.

## Реальные сравнения

Нужны минимум 3 типа eligible action-context: два lawful DAMAGE навыка Monster (различные реальные MP/cast/effect), законный HEAL союзнику, invalid target refused. Как минимум два разных изученных класса, без hardcoded classes in production algorithm. В TEST можно выбирать конкретные canonical fixture персонажи и фиксировать их для воспроизводимости; не выдавать навыки ради теста.

Сначала DB-free RED→GREEN. Native evidence только если headless G3 PASS. Публиковать matrix: allLearned, TRAIN-supported, legal, offered, selected, executed, fallback; без подмены категории `unsupported` на `missing`.
