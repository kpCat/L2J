# Buff audit only — не переписывать уже работающую систему

Пользователь хочет живых фантомов, которые способны обновлять баффы для себя и Party, с различиями между физическими, магическими и смешанными составами. Исходники уже содержат поддержку maintenance; её НЕ менять в014 без отдельного согласования.

## Исходный код для проверки

- `PhantomPartyTactics.addMaintenance` планирует `combat.buff`, `combat.song`, `combat.dance` если эффект отсутствует или скоро закончится; пределы из `PhantomPartySupportPolicy`, экономия MP, priority combat/idle.
- `L2jPhantomPartyBackend.realCapabilities` выдаёт только фактически выученные native skill capabilities для законной цели.
- `L2jPhantomPartyBackend.supportEffectStatus` и `PhantomSupportEffectAuthority.status` оценивают native buff/abnormal status и срок истечения. Производственная логика status/stack/priority может иметь пробелы — не считать PASS без проверки.

## Таблица аудита

1. Physical recipient: применённые P.Atk/P.Def/attack-speed etc в зависимости от actual skill effect; магический buff без физической пользы не обязан автоматически быть запрещён (может давать смешанный эффект).
2. Magical recipient: M.Atk/cast speed/MP-related effect, native target applicability.
3. Hybrid recipient: сочетание физ/маг эффектов и разные роль/составы.
4. Self only vs Party/SINGLE_TARGET, из той же instance, настоящий learned skill, skill level/enchant, native owner.
5. Rebuff: MISSING → legal cast; NEAR_EXPIRY → recast; HEALTHY → no redundant cast. Abnormal type/stack/stronger buff должны оставаться каноническими.
6. Songs/dances, disabled/cooldown/no MP, combat urgent HEAL over long maintenance; reasonable saturation with many Party members.
7. Возможная общая проблема: static `contextualScore` может недостаточно учитывать именно потребность получателя. Подтвердить/опровергнуть code+tests, не объявлять оптимальную маг/воин политику по одному показателю.

Для каждого: `PASS`, `FAIL`, `UNKNOWN`, `UNSUPPORTED`; ссылка на source/test/native evidence. Если есть значимая функциональная ошибка, в отчёте описать варианты минимального исправления и последствия. **Не редактировать** `PhantomPartyTactics`, `L2jPhantomPartyBackend`, `PhantomSupportEffectAuthority`, production config/XML без нового явного решения владельца.

Ориентир будущего AI: buff selection определяется реальными характеристиками эффекта и потребностями role/recipient, а не только названием профессии; отдельное обучаемое действие — будущий scope.
