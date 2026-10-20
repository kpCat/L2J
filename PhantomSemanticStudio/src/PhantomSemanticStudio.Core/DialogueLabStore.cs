using System.Text.Json;

namespace PhantomSemanticStudio.Core;

/// <summary>Только явное локальное сохранение. Не используется при Clear/Close автоматически.</summary>
public static class DialogueLabStore
{
    public static string Save(WorkspaceStore workspace, DialogueLabSession session, PackSnapshot current, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested(); session.AssertCurrent(current); workspace.Check();
        PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(current.ModuleRoot), workspace.Root);
        var revision = session.Revision;
        var archive = new { Version = 1, SourceFingerprint = session.PackFingerprint, session.World,
            Turns = session.Turns, Messages = session.Messages, Status = "PRIVATE_EDITOR_HISTORY_NOT_RUNTIME", ModelCalls = session.ModelCalls };
        var bytes = JsonSerializer.SerializeToUtf8Bytes(archive, WorkspaceStore.JsonOptions);
        if (bytes.Length > 2 * 1024 * 1024 || archive.Turns.Count > 200 || archive.Messages.Count > 600)
            throw new InvalidDataException("Предел private lab history 2 MiB / 200 реплик / 600 сообщений.");
        var folder = PathSafety.ResolveRelative(workspace.Root, "labs");
        Directory.CreateDirectory(folder); PathSafety.AssertNoReparsePoints(folder);
        var id = Guid.NewGuid().ToString("N");
        var target = PathSafety.ResolveRelative(workspace.Root, "labs/" + id + ".json");
        var partial = PathSafety.ResolveRelative(workspace.Root, "labs/" + id + ".partial");
        try
        {
            session.AssertCurrent(new PackReader().Load(current.ModuleRoot, token));
            using (var file = new FileStream(partial, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { file.Write(bytes); file.Flush(true); }
            token.ThrowIfCancellationRequested(); workspace.Check(); session.AssertCurrent(new PackReader().Load(current.ModuleRoot, token));
            if (session.Revision != revision) throw new InvalidDataException("STALE: история изменилась во время сохранения.");
            PathSafety.AssertNoReparsePoints(partial); PathSafety.AssertNoReparsePoints(target); File.Move(partial, target, false); return target;
        }
        finally { if (File.Exists(partial)) { PathSafety.AssertNoReparsePoints(partial); File.Delete(partial); } }
    }
}
