# Шаблон RESULT (заполняется фактическими данными)

```text
TASK_RESULT=
RUNTIME_BEHAVIOR=
AUTOPLAY_5_CYCLES=
M1=OPEN
codeSHA=
reportSHA=
sourceManifestSha256=
gameJarSha256=
loginJarSha256=
experimentalBranch=experiment/m1-candidate007-observe008
observationDurationSeconds=
visualReview=PERFORMED/NOT_PERFORMED
observedCohortCount=
firstBlocker=
originalPlayRestored=
consentOffConfirmed=
observationProcessesStopped=
```

## Что увидел игрок

5–10 предложений без внутренних аббревиатур: место, сколько персонажей, кто бил,
кто кастовал/стоял, был ли loot/следующая цель, что произошло при уходе/возврате.
Отдельно — что реально просмотрено на кадрах, что известно только из server evidence.

## Участники

| profile/object/epoch | класс/уровень | наблюдаемое поведение | cycles/reward/loot | исход |
|---|---|---|---|---|

Не удалять FAILED и исчезнувших. Недостающие участники и отсутствие natural death —
явные NOT_OBSERVED, не выдуманные успешные проверки.

## Самый важный вывод

Доказана ли работа нынешнего AutoPlay/AutoUse path на5 циклах? Если нет — где прервалась
цепочка. Первый причинный exception с файлом/line/time, не весь архив повторных ошибок.
Не назначать себе исправление и не превращать отчёт в повторный архитектурный аудит.

## Воспроизводимость и безопасность

Exact worktree/runtime/codeSHA/JAR/clone names; source/behavior config differences;
какие commands/tooling patches выполнялись; original process transition/health;
сохранность исходных файлов; OFF/STOP. Локальные пути большого приватного evidence,
ссылки на несколько sanitised кадров и raw scalar snapshots без credentials/tokens.
reportSHA сообщить в финальном ответе после push. Не создавать новые receipt commits
только ради записи собственного SHA в тот же commit.

## HANDOFF (отдельный короткий файл)

Где находятся frozen source и runtime; какие данные получены; один ближайший вопрос
архитектору; что не запускалось; исходный PLAY сейчас RUNNING/STOPPED/UNKNOWN;
никаких автоматических продолжений. Не объявлять исторические007 tests текущими GREEN.
