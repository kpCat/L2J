# PSS-009 — дизайн WinForms layout и инварианты

## Принцип

Исправление должно быть в нормальной Designer-совместимой компоновке, без самописного runtime пересоздания интерфейса. Выбирать наименьший обоснованный набор статических правок; новые контейнеры `Panel/TableLayoutPanel/FlowLayoutPanel/SplitContainer` допустимы, но не обязательны. Разделять layout defects и DPI scaling defects; не менять `AutoScaleMode` вслепую.

## Expected first-show invariants

1. Все шесть страниц получают корректный исходный размер/контейнерную геометрию **до** вычисления Anchor детей. Нельзя полагаться на TabPage default 200×100, если дочерние координаты проектировались на >1200px.
2. `headerPanel` Dock.Top, `statusStrip` Dock.Bottom, TabControl Dock.Fill; при изменении формы status/header не перекрывают tab content.
3. На каждой вкладке критические кнопки, подписи, grid, textbox и combo либо полностью в видимом `ClientRectangle` своего контейнера, либо реально доступны через `AutoScroll`, фокус/scroll/tab navigation. Жёстких координат за рабочим viewport без скролла быть не должно.
4. Отсутствие перекрытий **между интерактивными siblings** и читаемые подписи, в том числе длинные русские тексты при 100/125/150% DPI. Для длинных user data допускаются ScrollBars/ellipsis/tooltip, а не обрезание самой кнопки.
5. Designer round-trip: MainForm.cs / *.Designer.cs / *.resx / csproj nesting. Конструкторы без IO/UI factories: только `InitializeComponent();` и явные дизайнерские свойства/event wires в Designer.
6. Сохранить controls `Name`, события Click/SelectedIndexChanged/ItemCheck, публичные имена форм и функции главного приложения. Никаких новых `TabPage` в MainForm: их ровно шесть.

## Минимальное ожидаемое вмешательство

Вероятно достаточно задать согласованные начальные `TabPage.Size` у пяти страниц перед привязками или заменить конкретную ошибочную Anchor-структуру Designer-native контейнером; подтвердить на реальном runtime RED→GREEN. Нельзя просто добавить `.Size` и объявить PASS по grep: проверить реальную геометрию после Show/дисплейных трансформаций.

Отдельные формы StageSelection/ChatCorpus/DialogueLab/PackQuality/V3Proposal исследовать на аналогичные Anchor/AutoScroll/MinimumSize issues. Доработать только обнаруженные доказанные проблемы. При недостатке времени главное — все шесть табов MainForm; остальное перечислить отдельным manual gate, не скрывать.

## Ограничения

- `src/PhantomSemanticStudio.Core/*`, `Java`, `XML`, `Semantic Pack`, chat.zip, workspace/session, approvals/LM API, GitHub L2J — НЕ изменять.
- Не создавать production automated DPI changer, не менять реальный Windows масштаб и настройки пользователя.
- Никакого упрощения проверок/подавления legacy failures ради зелёной сборки.
- Исправление UI не является доказательством генерации Gemma или runtime-parity.
