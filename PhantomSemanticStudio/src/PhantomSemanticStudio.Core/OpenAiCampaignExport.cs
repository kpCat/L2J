using System.Globalization;
using System.Text;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed partial class CorpusCampaign
{
    public string OpenAiSampleScene(string id, string task, int row)
    {
        var plan = LoadOpenAi(id);
        var work = OpenAiJournal(id).OpenAiRequest(task, row);
        var lane = plan.Lanes.Single(lane => lane.Id == work.LaneId);
        if (!plan.Pilot) lane = lane with { Format = plan.Settings.Format };
        var batch = OpenAiCampaignPrompts.Prepare(lane, work.Requested, work.Batch, plan.Settings.Model, plan.Settings.MatureOptIn);
        V4Rules.Require(batch.SceneHash == work.SceneHash && batch.Post.Hash == work.PromptHash, "OPENAI_RECEIPT_SCOPE_DRIFT");
        return "Категория " + lane.Label + " (" + lane.Category + ")\n"
            + "Режим " + lane.Mode + (lane.Event.Length == 0 ? "" : " · Событие " + lane.Event) + "\n"
            + "Следующим говорит " + (lane.Mode == "TRIGGER" ? "игрок" : "фантом")
            + " · Голос " + batch.Scope.Style.Voice + " · Канал " + batch.Scope.Style.Channel
            + " · Отношения " + batch.Scope.Style.Relation + "\n"
            + "Согласованный взрослый контекст " + (batch.Scope.Style.MatureRequested ? "да" : "нет") + "\n\n"
            + batch.Scope.Style.Situation + "\n" + string.Join('\n', batch.Scope.Style.Dialogue);
    }

    public string ExportOpenAi(string id, int page, bool confirmed)
    {
        V4Rules.Require(confirmed, "OPENAI_EXPLICIT_EXPORT_REQUIRED");
        var plan = CheckOpenAi(id, OpenAiHash(id));
        V4Rules.Require(!plan.Pilot, "OPENAI_PILOT_REVIEW_ONLY");
        var selected = OpenAiJournal(id).OpenAiSamples(plan, page, approvedOnly: true);
        V4Rules.Require(selected.Count > 0 && selected.All(sample => sample.State == "APPROVED" && sample.Grade?.Pass == true),
            "OPENAI_NO_APPROVED_ROWS");
        var identity = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(selected, PromptProfiles.CompactJson));
        var exportId = identity[..32];
        var files = new List<object>();
        foreach (var group in selected.GroupBy(sample => (sample.Category, sample.Mode)))
        {
            var bytes = CorpusCampaignWorker.OpenAiJsonl(group.Key.Category, group.Key.Mode, group.Select(row => row.JsonLine).ToArray());
            var name = "f" + files.Count.ToString("D5", CultureInfo.InvariantCulture) + ".jsonl";
            store.SaveCampaignExportBytes(exportId, name, bytes);
            files.Add(new { name, sha256 = V4Rules.Hash(bytes), category = group.Key.Category, mode = group.Key.Mode, count = group.Count() });
        }

        var root = store.CampaignExportRoot(exportId);
        var validation = store.IsolatedV4().Import(root, true);
        V4Rules.Require(validation.Success && validation.Entries.Count == selected.Count, "OPENAI_EXPORT_V4_INVALID");
        store.SaveOpenAiExportEvidence(exportId, new
        {
            status = "READY_FOR_EXPLICIT_IMPORT", campaign = id, planHash = OpenAiHash(id), registryHash = plan.RegistryHash,
            inputHash = validation.InputHash, evidenceHash = identity, files, rows = selected
        });
        return root;
    }

    public string SaveOpenAiCalibrationReport(string id, bool confirmed)
    {
        V4Rules.Require(confirmed, "OPENAI_PILOT_REVEAL_CONFIRMATION_REQUIRED");
        var plan = LoadOpenAi(id);
        V4Rules.Require(plan.Pilot, "OPENAI_PILOT_REQUIRED");
        var journal = OpenAiJournal(id);
        var all = journal.OpenAiSamples(plan, 0);
        var graded = all.Where(row => row.Grade != null).ToArray();
        V4Rules.Require(graded.Length >= 30, "OPENAI_NEED_30_BLIND_GRADES");
        var debts = journal.OpenAiView(plan);
        var report = new StringBuilder("# Калибровка живых русских реплик\n\n")
            .AppendLine($"Кампания {id}. Реестр {plan.RegistryHash}. Версия промпта {plan.PromptHash}.")
            .AppendLine($"Результат исполнения {debts.State}. Запросов {debts.Totals.Requests}, оценено владельцем {graded.Length} из {all.Count}.")
            .AppendLine($"Расчёт по токенам {debts.Totals.CostUsd:F6} USD. Неопределённый резерв {debts.Totals.HeldUsd:F6} USD.")
            .AppendLine("Оценки 0–3 выставлены владельцем. Ниже — текущая редакционная выборка. QUALITY003 — историческая справка, условия и модели различались.")
            .AppendLine();
        foreach (var arm in all.GroupBy(x => x.Format))
        {
            var reviewed = arm.Where(sample => sample.Grade != null).ToArray();
            report.AppendLine($"- {arm.Key} · {reviewed.Length}/{arm.Count()} оценено · {reviewed.Count(sample => sample.Grade!.Pass && sample.State != "REJECTED")} выдержали все проверки");
        }
        var ranked = all.OrderByDescending(sample => sample.Grade is { Pass: true } && sample.State != "REJECTED")
            .ThenByDescending(sample => sample.Grade?.Score ?? -1).ThenBy(sample => sample.Id).ThenBy(sample => sample.Number).ToArray();
        WriteExamples("Двадцать лучших доступных", ranked.Take(20));
        WriteExamples("Двадцать наиболее слабых или без оценки", ranked.Reverse().Take(20));
        report.AppendLine("\n## Журнал запросов\n");
        foreach (var request in journal.OpenAiLog(OpenAiCampaignPlan.PilotMaxPosts).Reverse())
            report.AppendLine($"- {request.AtUtc:O} · {request.Lane} · {request.State} · {request.Model} · вход {request.Usage?.Input}, кэш {request.Usage?.Cached}, выход {request.Usage?.Output} · расчёт {request.CostUsd:F6} USD · резерв {request.ReservedUsd:F6} USD · {request.PromptHash}");

        return store.SaveOpenAiPilotReport(id, report.ToString());

        void WriteExamples(string title, IEnumerable<OpenAiSample> samples)
        {
            report.AppendLine("\n## " + title + "\n");
            foreach (var sample in samples)
            {
                var grade = sample.Grade;
                report.AppendLine($"- {sample.Format} · {sample.Scene} · {sample.Category}/{sample.Mode}{(sample.Holdout ? " · holdout" : "")} · {(grade == null ? "без оценки" : "оценка " + grade.Score)}")
                    .AppendLine($"  {sample.Text}")
                    .AppendLine($"  {sample.State} {sample.Diagnostics}. {grade?.Note}")
                    .AppendLine($"  Модель {sample.Model}. Запрос {sample.Id}, строка {sample.Number}, расчёт запроса {sample.RequestCostUsd:F6} USD. Сцена {sample.SceneHash}. Промпт {sample.PromptHash}.");
            }
        }
    }
}

public sealed partial class PromptLabStore
{
    internal void SaveOpenAiExportEvidence<T>(string exportId, T evidence)
    {
        RequireId(exportId);
        var relative = "campaign-exports/" + exportId + "/openai-manifest.json";
        if (!File.Exists(Local(relative))) Write(relative, "OPENAI_EXPORT_MANIFEST", evidence, false);
        else V4Rules.Require(V4Rules.Hash(CompactData(relative, "OPENAI_EXPORT_MANIFEST"))
            == V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(evidence, PromptProfiles.CompactJson)),
            "OPENAI_EXPORT_EVIDENCE_DRIFT");
    }

    internal string SaveOpenAiPilotReport(string id, string report)
    {
        RequireId(id);
        var bytes = V4Rules.Utf8.GetBytes(report);
        var path = Local("campaigns/" + id + "/calibration/review-" + V4Rules.Hash(bytes)[..16] + ".md");
        SaveCampaignFile(path, bytes);
        return path;
    }
}
