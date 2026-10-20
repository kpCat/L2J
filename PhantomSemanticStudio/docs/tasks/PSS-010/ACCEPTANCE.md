# Условия приёмки

- [ ] `origin/main` перед задачей ровно `9d5399e65c6c260ea2026c2da006cb48ba7bffce`; исходный пользовательский `.sln` сохранён без изменения/стейджинга.
- [ ] Реальный RED на базовой Studio: пользовательские greet/mood возвращают unsupported, не просто unit mock.
- [ ] После исправления: `привет` → `greet.02` или другой доказуемый safe литерал существующего пакa; `как дела` → `mood.share.02` или другой safe литерал. Полная трасса и ID, без выдуманных плейсхолдеров, без ложного `FUNCTIONAL_OR_MEMORY_UNSUPPORTED`.
- [ ] `меня слили в пвп` без реального matching PATTERN остаётся честным `NO_PACK_MATCH`, world hint можно GAME advisory, но не выдуманный ответ.
- [ ] Нет новых L2J/XML/Java/DB/core functionality writes; source/approval/session сохраняются при обычном диалоге.
- [ ] Functional/fact/recall не выдаются за обслуженные. UNKNOWN placeholder/unresolved context не превращается в фейковые данные. Mature/profanity и band/register gates не ослаблены.
- [ ] `PackPreview` и `DialogueLabSession` согласованы в статусах и template rendering, никакого `reply.Text == template.Text` для rendered text.
- [ ] Targeted RED/GREEN, отрицательная мутация, Release Build-Verify и legacy regressions выполнены реально; report содержит stdout/exit, count. Не менять unrelated code ради тестов.
- [ ] Сохранить PSS-009 WinForms размеры и Designer; `InitializeComponent` не трогать. Реальная UI-приёмка — только при фактической проверке человеком или доступным interactive test, иначе `NOT_TESTED`.
- [ ] UTF-8, mojibake и escaped Cyrillic отдельные PASS или честный отказ; private inputs отсутствуют в Git.
- [ ] Инвентарь файлов точно совпадает с коммитом; обычный non-force push только в Studio `origin/main`; remote/public SHA совпадают. STOP, без PSS-011.
