# Publication receipt

Status до commit: BLOCKED docs-only audit готовится к exact-path публикации.
CODE_SHA=461a4abe32be4aa08532b8417a6147684a8889c6. Принятого production fix нет.

После первого normal push receipt обновляется отдельным docs-only commit: exact report
SHA, observed remote HEAD, exit codes и tree scope proof. Итоговый remote SHA также
сообщается в финальном ответе. Этот файл не объявляет ещё не выполненный push успешным.

Exact staging allowlist: все перечисленные PUBLICATION_DOCS строки CHANGED_FILES.tsv,
только task006 и обязательный docs/phantoms/reports/PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006.md.
13 candidate paths, шесть SQL bytes, configs, .phantom-local, geodata и binaries не входят.

Scope exception: supplied task package + sanitised native reports образуют одну artifact
family, больше10 files; это bounded publication TASK, без production integration.
