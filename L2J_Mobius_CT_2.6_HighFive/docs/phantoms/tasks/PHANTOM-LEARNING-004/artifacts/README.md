# Артефакты004

`working-checkpoint-8/checkpoint.zip` — штатный cold export: champion2, selected candidate8, optimizer8, replay/context replay и selected failure receipts. SHA в RESULT/EVIDENCE. Побайтная проверка отдельным durability process выполнена; архив не означает разрешение на apply.

`source-test-export.zip` — overlay над exact base003 `06d42410fbfe1ca9b7f462b0816e0da1784630d6`: полные изменённые Java/SQL/PowerShell/INI файлы, tests, неизменённые входные документы004, фиксированные raw native evidence. Пути внутри ZIP от Git root. Entry allowlist и SHA каждого файла — `source-test-export.json`. Private SQL/config/datadir, Geo, release binaries и сторонние worktrees исключены. JAR нужно собрать стандартной командой в своём clone; SHA проверенного JAR в RESULT.

Изменяемые финальные delivery-документы, сам ZIP/его manifest и checkpoint ZIP не вложены в overlay: актуальные отчёт/RESULT/HANDOFF/EVIDENCE/scope receipts берутся из опубликованной004 ветки, checkpoint ZIP лежит рядом. Это исключает циклические self-hashes и дублирование22MB checkpoint. Перед применением проверить base и все hashes; runtime apply остаётся явным.
