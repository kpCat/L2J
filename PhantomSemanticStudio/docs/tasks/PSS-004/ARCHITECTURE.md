# PSS-004 — контракт выбора партии и WinForms

## Граница данных

```text
SessionState: все кандидаты (включая APPROVED / stale / DRAFT / REJECTED)
    -> StageSelectionForm.SetCandidates(copies, sourceFingerprint)
    -> explicit checked ID set (initially EMPTY, max 20, persists across filter)
    -> StageBatchSelection.SelectExactIds(allPeers, selected IDs) [pure, fail closed]
    -> confirmation #1 (точные IDs/реплики) + #2 (editorial safety)
    -> IsolatedPackStager.Create(store, snapshot, allPeers, exact IDs, true, true, token)
    -> STAGED_UNVALIDATED stage under workspace only
```

`StageSelectionForm` не сохраняет никаких новых JSON полей, не изменяет `Candidate`, `Status`, `ReviewNote` или approval. Кандидаты передаются **копиями** (`Candidate.Copy`), результат — immutable snapshot/array точных строк ID, а не mutable `Candidate` или row index. Core selection не хранит state и не знает UI/файлов.

## Core

Предлагаемый public contract, при необходимости адаптировать имена под стиль репозитория:

```csharp
public static class StageBatchSelection
{
    public const int MaxItems = 20;
    public static IReadOnlyList<string> SelectExactIds(
        IReadOnlyList<Candidate> allPeers,
        IEnumerable<string> selectedIds);
}
```

Принять только 1..20 действительно выбранных, уникальных (`StringComparer.Ordinal`) ID, которые существуют в `allPeers` ровно в одном экземпляре, имеют `APPROVED` и `CandidateReview.IsCurrent`, не null/empty; вернуть в порядке Ordinal. Любая проблема = `InvalidDataException`, **не исключать** плохой элемент из набора. Никаких defaults/all-approved fallback. Полноту topic/act/profanity/source проверяет Stager, не этот метод. При stale baseline Stager блокирует даже формально валидный approval; UI показывает этот факт заранее.

## Modal WinForms

Рекомендуемый design (обычные controls, не custom owner-draw):

- `StageSelectionForm.cs`, `StageSelectionForm.Designer.cs`, `StageSelectionForm.resx`; `public StageSelectionForm() { InitializeComponent(); }`. Все static controls объявить/создать/связать в Designer с явными `Location`/`Size`/`Anchor`/`TabIndex`/`Text`.
- Пространство: сверху поиск, слева прокручиваемый список отметок (`CheckedListBox.CheckOnClick = true`), справа/снизу read-only детализация ВСЕХ отмеченных (ID, kind, topic/act, `Gender/Band/Register`, исходная реплика, отметка редактирования), счётчик `N / 20`, кнопки «Продолжить» и «Отмена». При N=0 или плохом ID продолжать нельзя. `MinimumSize`, адаптивный Anchor/Dock, AutoScaleMode.Font, AcceptButton и CancelButton. Никаких Java/LM/IO в конструкторе.
- Для 5000-кандидатного workspace использовать bounded фильтр списка (например max 500 видимых записей с честной статистикой и поиском) или прокручиваемый источник; **важно:** фильтр не сбрасывает выделения в скрытых строках. `HashSet<string>(StringComparer.Ordinal)` — источник истины; `ItemCheck` обновляет его с учётом `e.NewValue`, event guard на rebind. Запрещены «отмечено всё» / «выбрать все» по умолчанию.
- Изменённый после approval candidate показывать как `СТАРОЕ ОДОБРЕНИЕ / переодобрите`, и блокировать его выбор/завершение, не молча скрывать. `Gender!=ANY` и другие будущие non-stage constraints могут быть помечены предупреждением; окончательное решение только в Stager. Не объявлять такой список Java-safe.
- Если фильтр скрыл отмеченные ID, полная сводка снизу **по-прежнему показывает их**. В подтверждении перед стадией отображать ВСЕ выбранные ID и тексты без сокращения/потери.
- При отказе/закрытии модального окна возвращать Cancel без side effects; не использовать `DialogResult=OK` до повторной Core-проверки.

## Строгий Designer

`InitializeComponent` обоих форм **без** циклов, if/switch, LINQ, lambdas, IO, HttpClient, Task/async, model calls, dependency injection или runtime BuildUi. Для новой формы все стандартные компоненты `System.Windows.Forms` внутри `.Designer.cs`, события привязать методами, отдельно `.resx`. Настроить `.csproj` для корректной вложенности `.Designer.cs` и `.resx` в VS2026. Все циклы фильтра/перерисовки данных — исключительно вне `InitializeComponent` и конструктора.

## Safety

Нет запуска Java, Ant, CLI, Git, сервера, БД и нет новых methods install/apply. Никаких записей в L2J. Не передавать модели filesystem tools. Status C# stage всегда `STAGED_UNVALIDATED`; старый Java operator отдельный, Java evidence строго от предыдущего checkpoint не переносить как новый PASS.
