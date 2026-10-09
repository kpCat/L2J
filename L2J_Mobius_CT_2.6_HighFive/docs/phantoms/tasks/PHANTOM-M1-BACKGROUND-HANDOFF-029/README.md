# TASK029 — native → background → native без потери поведения и состояния

**Назначение:** закрыть обычный невидимый farm между natural FINALIZE и новым native
owner, сохранить доказательства всех lifetimes и устранить конкретные regressions028.
Это не новый combat engine, не повтор TASK027 и не задача «аудитируй всё до GREEN».

Точка базы: `2b9496c935748803f8505c472bf4085977fb04d2`.
Проверенная production028: `07c2c1cc4036b47487c98e96943a39ce295a3920`.
Ветка: `experiment/m1-candidate007-observe008`.

Читать в порядке: TASK → ROOT_CAUSES → DESIGN → PLAN → SCENARIOS/ACCEPTANCE.
PATCH_GUIDE содержит действительные CURRENT-фрагменты и требуемые интерфейсы.
SOURCE_MAP — точный scope; SOURCE_REVIEW — что coordinator действительно прочитал.

Выход: `TASK029_PASS` только при lawful background farm + complete returned cohort +
whole-lifetime persistence + regression/healthy stop. Это ещё не REAL_FINAL.
Вспомогательные программы в proposals не являются исправленным сервером.
