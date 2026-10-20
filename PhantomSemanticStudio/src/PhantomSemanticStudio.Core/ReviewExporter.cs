using System.IO.Compression;
using System.Text;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

/// <summary>Экспорт ТОЛЬКО пакета ревью. Метода установки на сервер не существует.</summary>
public sealed class ReviewExporter
{
    public string Export(WorkspaceStore store, PackSnapshot snapshot, IReadOnlyList<Candidate> candidates)
    {
        store.Check();
        if (candidates.Any(c => c.Status == "APPROVED" && !CandidateReview.IsCurrent(c)))
            throw new InvalidDataException("Есть изменённый после одобрения кандидат. Экспорт не будет молча его пропускать: пересмотрите или отклоните его.");
        var approved = candidates.Where(CandidateReview.IsCurrent).ToList();
        if (approved.Count == 0) throw new InvalidDataException("Нет актуально одобренных кандидатов.");
        var current = new PackReader().Load(snapshot.ModuleRoot);
        if (current.Fingerprint != snapshot.Fingerprint) throw new InvalidDataException("Исходный пак изменён после импорта. Экспорт заблокирован; заново импортируйте и пересмотрите кандидатов.");
        foreach (var c in approved)
        {
            var errors = CandidateValidator.Validate(c, snapshot, candidates).Where(i => i.Severity == IssueSeverity.Error).ToList();
            if (errors.Count != 0) throw new InvalidDataException("Повторная проверка не пройдена: " + string.Join("; ", errors.Select(i => i.Message)));
        }
        var path = store.GetExportPath(); var temporary = path + ".partial";
        try
        {
            using (var stream = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            using (var archive = new ZipArchive(stream, ZipArchiveMode.Create))
            {
                Add(archive, "DO_NOT_INSTALL.txt", "REVIEW_ONLY_NOT_SERVER_VALIDATED\nПАКЕТ ДЛЯ РЕВЬЮ — НЕ УСТАНАВЛИВАТЬ НА СЕРВЕР.\nJava-validator: NOT_RUN.\nXML не экспортируется: условия, profanity/mature и runtime-фильтры не доказаны.\nОдобренный JSON сохраняет все редакционные ограничения.\nДля публикации нужен отдельный проверенный этап со штатным загрузчиком, тестами и откатом.\n");
                Add(archive, "approved-candidates.json", JsonSerializer.Serialize(approved, WorkspaceStore.JsonOptions));
                Add(archive, "source-baseline.json", JsonSerializer.Serialize(new { snapshot.ModuleRoot, snapshot.Fingerprint, snapshot.Files, Validator = "NOT_RUN" }, WorkspaceStore.JsonOptions));
                Add(archive, "review-summary.json", JsonSerializer.Serialize(new
                {
                    Status = "REVIEW_ONLY_NOT_SERVER_VALIDATED", Approved = approved.Count, XmlProposals = 0,
                    XmlStatus = "NOT_EXPORTED_UNVERIFIED_CONDITIONS_PROFANITY_RUNTIME",
                    RequiresGenderRuntime = approved.Count(c => c.Gender != "ANY"),
                    Checks = approved.Select(c => new { c.Id, Issues = CandidateValidator.Validate(c, snapshot, candidates) })
                }, WorkspaceStore.JsonOptions));
            }
            store.Check(); File.Move(temporary, path, false); return path;
        }
        catch { if (File.Exists(temporary)) File.Delete(temporary); throw; }
    }
    private static void Add(ZipArchive zip, string path, string text)
    {
        using var writer = new StreamWriter(zip.CreateEntry(path, CompressionLevel.Optimal).Open(), new UTF8Encoding(false)); writer.Write(text);
    }
}
