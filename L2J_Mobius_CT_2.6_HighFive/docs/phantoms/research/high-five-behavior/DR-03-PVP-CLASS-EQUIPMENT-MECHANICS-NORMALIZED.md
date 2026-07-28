# DR-03 — PvP class и equipment mechanics

Сохранены только проверяемые механические входы: negative/control effects, PvP-only marker, suicide/special restrictions, target scope, weapon/equipment condition, resource items и Olympiad block facts.

Каталог не определяет target priority, engagement policy, chase, PK thresholds, talisman doctrine, class matchup или Olympiad strategy. Эти решения относятся к Goal 025; память и репутация — к Goal 018.

Текущий Mobius удаляет pet при входе в Olympiad, но не удаляет servitor тем же путём; входящий summon damage относится владельцу. Это current-server behavior, а не реализованная PvP-доктрина.

Источники: `Skill.java`, `Player.java`, `OlympiadGame.java`, `OlympiadGameNormal.java`, `ItemTemplate.checkCondition`.

Authority: `CURRENT_SERVER_IMPLEMENTATION`. Confidence: `HIGH`.
