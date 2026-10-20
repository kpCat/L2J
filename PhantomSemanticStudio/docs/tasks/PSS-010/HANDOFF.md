# Передача результатов PSS-010

Codex возвращает только:

- статусы PSS-010 implementation, targeted RED/GREEN, финальный Release Build-Verify;
- конкретные три тестовых диалога: вход → действительный PATTERN/Template ID → реальный PACK текст/корректный отказ; только публичные/синтетические строки;
- список затронутых source/test/script docs с количеством файлов, полный safe `reports/PSS-010-final.md`, `PSS-010-ui.md`, `PSS-010-owned-files.txt`;
- Critical/Important reviewer status, source SHA guards, user `.sln` unchanged;
- exact commit, parent, push exit, remote/public SHA equality, clean **task-owned** tree, user dirt preserved;
- отдельно live LM/Java/UI/VS statuses без ложного PASS.

Не прикладывать review ZIP. Ассистент самостоятельно проверит опубликованный GitHub и затем пользователь проверит живой диалог на своём WinForms.
