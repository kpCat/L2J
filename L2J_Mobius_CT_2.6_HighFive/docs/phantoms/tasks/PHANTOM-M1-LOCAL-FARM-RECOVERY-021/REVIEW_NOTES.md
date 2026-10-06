# Независимое read-first ревью координатора

На момент подготовки remote experiment HEAD=2bf2936083bf6b081104c3e03c254ab8f07afc87,
его parent=acbe5513da884f4f725e3353c69a1faebd1bf781. Прочитаны опубликованный RESULT020,
EVIDENCE020 и critical methods актуальных travel/history/planner/decision/store classes.
Проверен старый History до020: новый foreground decision admission действительно
добавлен, обычный replanVisibleFarmIfOutgrown остался COMPLETE-only.

Главная поправка к пользовательскому краткому отчёту: same-epoch counters0 сняты лишь
на двух точках за1.790001s; decision trace при этом показывает442→443→444. В evidence
есть goal mismatch29/28, которого в кратком итоговом сообщении почти не видно.
Water reason не доказывает корректность или ошибочность геодаты и не показывает
координат сегмента. NEXT_BLOCKER=D6 — полезная классификация, но не полная causal root.

Решение не отменяет пройденные materialization/admission fixes. Пересматривается только
контракт local visible intent: stale goal нельзя исполнять; terminal route не является
бесконечным retry; локальное перепланирование не требует завершить historical catchup.

Координатор не запускал Windows runtime/JDK tests и не считает будущий patch проверенным.
В архиве нет готовой модификации production, только конкретный implementation contract.
Проверены структура ZIP/UTF-8/целостность пакета. Git blob references получены от GitHub,
не являются SHA256 локально скачанных полных исходников.

## Primary sources, pinned to reviewed SHA

- RESULT: https://github.com/kpCat/L2J/blob/2bf2936083bf6b081104c3e03c254ab8f07afc87/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-DECISION-020/RESULT.md
- EVIDENCE: https://github.com/kpCat/L2J/blob/2bf2936083bf6b081104c3e03c254ab8f07afc87/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-DECISION-020/EVIDENCE020.json
- Travel: https://github.com/kpCat/L2J/blob/2bf2936083bf6b081104c3e03c254ab8f07afc87/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java
- History: https://github.com/kpCat/L2J/blob/2bf2936083bf6b081104c3e03c254ab8f07afc87/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java
- Atomic store: https://github.com/kpCat/L2J/blob/2bf2936083bf6b081104c3e03c254ab8f07afc87/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupStore.java
