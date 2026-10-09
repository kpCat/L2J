# TASK030 — согласованный farm-план и непрерывное доказательство сохранения

Продолжение TASK029, не повтор проектирования vitality и не новый боевой AI.
Required base: `758ee295518be5b9abba0f757cc240782bfd2987`.
Branch: `experiment/m1-candidate007-observe008`.

Продуктовый вопрос: может ли естественный Phantom сменить farm-план, получить законный
новый drop, сохраниться, продолжить background и вернуться к обычной игре без отравления
readiness? Отдельная обязанность измерения — не терять lifetimes при долгой работе JVM.

Сначала TASK.md + ROOT_CAUSES.md + DESIGN.md, затем PLAN.md. PATCH_GUIDE.md содержит
выдержки exact source. Ни файлы proposals, ни таблицы решений не являются готовым
исправлением сервера. Ни один production-файл архивом не заменяется.
