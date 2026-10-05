# PHANTOM-M1-INVENTORY-TRIANGULATION-013

Следующая bounded-задача после 012R.

Факты:
- remote production base всё ещё `88b7dd76643cb80b78246668cd16052e740a55aa`;
- 012R production не менял и не публиковал;
- guarded TEST восстановлен точно;
- profile68 дошёл до настоящего Player.load/afterPlayerLoad;
- autoGet совпал;
- обнаружено ровно 5 inventory.objects различий;
- inventory не нормализован;
- M1 OPEN.

Цель:
не "принять native inventory", а определить источник расхождения через четыре
одновременных представления одного inventory и исправить только доказанную границу.
