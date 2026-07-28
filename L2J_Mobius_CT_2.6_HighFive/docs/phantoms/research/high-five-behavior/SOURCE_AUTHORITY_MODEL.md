# Модель авторитетности источников

Порядок источников:

1. Текущее runtime-состояние canonical `Player`, inventory, skills, summon/pet.
2. Штатные loader-ы `PlayerClass`, `SkillTreeData`, `SkillData`, `ItemData`, `PetDataTable`, `NpcData`, `CategoryData`.
3. Строго разобранные XML, когда публичный loader не раскрывает нужный параметр или source identity.
4. Версионированные curated capability rules с точными class/skill ID и target scope.

Curated metadata не заменяет loader-owned class, skill, item, NPC или pet facts. Display name и локализованное имя никогда не являются identity, ключом capability или основанием для hash.

Если внешний тезис расходится с текущим кодом, каталог хранит `CURRENT_SERVER_IMPLEMENTATION`, а противоречие документируется без изменения server core.

Authority: `SERVER_CODE_FACT`. Confidence: `HIGH`. Источник: `docs/phantoms/tasks/013-class-progression-capability-catalog/SOURCE_OF_TRUTH.md`.
