# Готовые вспомогательные инструменты, не production patch

Python3.10+, standard library. Файлы не импортируют Mobius и не доказывают server PASS.
Пример read-only анализа существующего outcome (подставить captured exact identity):

```powershell
python proposals/mailbox_audit.py --mailbox "<own-or-retained-mailbox>" --request-id "<uuid>" --session-id "<uuid>" --run-id "<uuid>" --sequence 12 --operation MOVE_SELF --actor-id 101
```

Путь — конкретный session mailbox с inbox/processing/journal/results, не runtime root.
Output не содержит account/candidate/credentials и не является разрешением replay.
Native перемещение, heartbeat, expiry, causal producer и gameplay result проверяются
отдельными server-side facts. Filesystem snapshot намеренно помечен NONATOMIC.

```powershell
python proposals/window_budget.py --elapsed 0 --commands 20 --phase setup=60 --phase scene=380
python -m unittest discover -s proposals -p test_tools028.py -v
```

window_budget не продлевает server TTL и не пишет конфиг. При отрицательном headroom
не начинать эпизод; перепланировать до baseline, а не обнулять уже начатые часы.
