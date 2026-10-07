# Код от координатора
AdmissionDecision023.java + AdmissionDecision023Test.java — компилируемая pure policy
таблица. Не импортировать как finished native fix: real reservation/capture/finalizer
должны использовать существующие Mobius lifetime primitives и проверяться native suites.

Эта таблица НЕ утверждает, что чужой OPEN игрок всегда доступен: facts формируются
после exact immutable capture, а фактический reserve/tryStart/recheck остаётся обязательным.
Ни один bool из remote input или config не должен выдавать capability.

Проверка без Mobius:
javac -encoding UTF-8 AdmissionDecision023.java AdmissionDecision023Test.java
java AdmissionDecision023Test

Python tools/verify_cohort.py — read-only validator prepared schema-1 JSON. Он проверяет
monotonicity/identity/progress и не создаёт исходные события. tools/test_verify_cohort.py
включает отрицательные случаи: missing actor, stale epoch, zero progress, corrupt sensor.
