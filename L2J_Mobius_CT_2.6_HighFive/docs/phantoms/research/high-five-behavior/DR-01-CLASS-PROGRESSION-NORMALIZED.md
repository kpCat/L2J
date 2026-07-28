# DR-01 — class progression

`PlayerClass.values()` является полным пространством class identity текущей хроники: 103 значения. Graph строится по числовому `classId`; enum parent и XML `parentClassId` skill tree сохраняются как разные факты.

`SkillTreeData.getCompleteClassSkillTree(PlayerClass)` задаёт полное наследуемое CLASS learning-множество. Direct tree source и стоимость/уровень/предыдущий навык/required items копируются без вывода по именам.

Male Soul Hound `132` и Female Soul Hound `133` остаются отдельными identity. Inspector `135` наследуется от Warder `126`; Judicator `136` наследуется от Inspector и терминален.

Profession graph описывает только структурно допустимые immediate targets. Текущий код не предоставляет общего безопасного non-packet facade для profession quest, поэтому разрешение возвращается как `CANONICAL_QUEST_REQUIRED`; реальные изменения класса только наблюдаются.

Источники: `PlayerClass.java`, `data/stats/players/skillTrees/*.xml`, `SkillTreeData.java`, `RequestAcquireSkill.java`.

Authority: `SERVER_LOADER_FACT` и `SERVER_CODE_FACT`. Confidence: `HIGH`.
