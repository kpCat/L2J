# Scope и экономия лимитов

## Экономный стиль Codex

Мышление **High**; отдельный новый диалог. Реализовать один vertical slice, а не 9 разных задач и не 20 аудитов. В первые 15 минут — проверка source и решение GO/NO_GO по фактическим native rows; к 35-й минуте — либо работающий real sample→optimizer/cold proof, либо точный blocker. **Контроль общей стоимости:** целевой полный wall ≤60 минут, жёстко остановиться ≤80 минут включая отчёт/публикацию. Это лимиты работы, не обещание успеха. При реальном data blocker — прекратить раньше.

Focused offline ≤120сек каждый; один native smoke ≤8мин с boot/drain; суммарная проверка ≤12мин; GS starts ≤1, no EVAL, no full TRAIN campaign, no FIT/old promotion. Не запускать долгие 015/016 full readiness, 51-episode, 72-slot, general `ant test`, stress/backtests, GUI Studio.

## Разрешённый исходный scope

*Новые файлы* только в `java/org/l2jmobius/gameserver/phantoms/learning/` с именами `ActionValueV4.java`, `ActionLearningV4.java`, `ActionFeedbackV4.java` (или максимум 3 эквивалента по архитектуре) и в `test/java/org/l2jmobius/tests/phantoms/` один `PhantomLearning019...java` как combined focused unit + native TEST-only entrypoint.

*Существующие ограниченно изменяемые*:
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java` — только optional scoring hook, default OFF, fallback; no dispatch rewrite.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` — только отдельный read-only importer/typed accessor; не трогать legacy write/validation, schema.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — при необходимости TEST-only bounded route к existing `Runtime`, без изменения legacy main и production owner.
- `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `Test-Training.ps1` — только если нужно переиспользовать безопасный owned process runner или entrypoint019; никаких модификаций legacy016.
- `build.xml` — единственный targeted `phantom-learning019-test` (≤120сек); ничего широкого.

Допустимы task docs `docs/phantoms/tasks/PHANTOM-LEARNING-019-.../` и отчёт `docs/phantoms/reports/PHANTOM-LEARNING-019.md`, короткая строка roadmap. Вне этого списка — явное согласование владельца перед edit. Не создавать дополнительных Git worktrees/branches/clones; TEST runtime directory (не Git worktree) разрешён только под собственным owned root, на frozen history016 не писать.

## Известные protected состояния

Accepted TEST2, DRAFT14, terminal Adam903 updates/223 epochs, `pointer12`, исторические183 five-SELECT rows SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`, Goal015/016/017/018, Geo203/protected207, MAIN/PLAY, Studio и чужая worktree dirt. Перед/после граничный SHA/pin readback. Не печатать конфиденциальные data/evidence.

## Если остановились

Если source недоступен или real native недостижим без 51-batch, BLOCKED/PARTIAL без попытки написать новый training framework. Предложить 1–2 технически конкретных варианта, нельзя автоматически продлевать лимит/выдавать Goal020.
