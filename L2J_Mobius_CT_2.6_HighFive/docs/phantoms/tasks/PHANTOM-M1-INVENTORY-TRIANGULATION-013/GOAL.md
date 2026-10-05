# GOAL

Required remote base:
`88b7dd76643cb80b78246668cd16052e740a55aa`

Branch:
`experiment/m1-candidate007-observe008`

GPT-6.1 Sol, reasoning High, без субагентов.

Используй локальный 012R RESULT как READ-ONLY evidence:
`C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012R/RESULT.md`

Не изменяй/не stage существующие локальные 012R test/docs diffs.
Создай отдельный worktree от exact remote base.

Для exact profile68 воспроизведи inventory mismatch и за один native run зафиксируй:

A = background PhantomBackgroundState.inventory.objects
B = canonical DB item rows ДО Player.load
C = loaded Player inventory ПОСЛЕ Player.load
D = canonical DB item rows ПОСЛЕ Player.load, до cleanup/store

Классифицируй каждый из 5 diff.

Разрешён production fix только после доказанной классификации и только в Phantom слоях.
Player/Inventory/Item core в этой задаче не менять.

После fix требуется:
- exact five-diff regression GREEN;
- existing focused native-context controls GREEN;
- task011 ecology 18/18;
- cheap regression 6/6;
- ant -q jar;
- fresh observe013 runtime 1280/8/8/10000;
- ручной вход TestAdmin;
- natural materialization gate 120 секунд.

PASS endpoint:
хотя бы один Phantom одновременно materialized=true, worldPresent=true,
objectId>0, epoch>0.

Следующий gameplay blocker не чинить. M1 OPEN.
