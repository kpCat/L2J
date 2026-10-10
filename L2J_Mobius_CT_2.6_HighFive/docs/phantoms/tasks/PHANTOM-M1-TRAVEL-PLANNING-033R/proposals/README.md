# Вспомогательный код

BoundedTravelSearch033R.java и Test — самодостаточный reference selector на Java17+.
Компилировались/исполнялись координатором на OpenJDK21, не на runtime JDK25 Mobius.
15assertions: U-обход, направленность, разные этажи, транспорт/ресурсы, multi-label
стоимость, cancellation/limits, immutable результат. Initial stub дал semantic RED,
последняя версия GREEN. Это не интеграционный RED/GREEN на Mobius.

Пример принимает уже проверенный граф. Он НЕ доказывает geodata и НЕ должен быть
скопирован с `verified=true` для всех ребер. В production graph proof привязан к
actualinstance/floor/epoch/generation/route; перед MOVE проверяется повторно.

Запуск:
```powershell
javac -encoding UTF-8 -d .reference-bin BoundedTravelSearch033R.java BoundedTravelSearch033RTest.java
java -cp .reference-bin BoundedTravelSearch033RTest
```
Код можно использовать как основу cost-search, адаптируя к existing types.
Не подключать к Ant до понимания, нужен ли он вместо уже существующего weighted query.
