using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void OpenAiVaultTests()
    {
        if (!OperatingSystem.IsWindows())
        {
            Console.WriteLine("SKIP OpenAI DPAPI vault tests (Windows required)");
            return;
        }
        RunOpenAiVaultTests();
    }

    [SupportedOSPlatform("windows")]
    private static void RunOpenAiVaultTests()
    {
        Test("OpenAI vault stores only encrypted data and safely replaces or deletes credentials", () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "workspace"), fixture.Module);
            var root = Path.Combine(fixture.Root, "secrets");
            var vault = new OpenAiKeyVault(workspace, root);
            const string first = "offline-vault-one";
            const string next = "offline-vault-two";
            True(!vault.Exists() && !Directory.Exists(root));
            ThrowsVault("OPENAI_KEY_INVALID", () => vault.Save("invalid placeholder"));
            True(!Directory.Exists(root));
            vault.Save(first);
            var stored = Directory.GetFiles(root).Single();
            True(File.ReadAllBytes(stored).AsSpan().IndexOf(Encoding.ASCII.GetBytes(first)) < 0);
            True(new OpenAiKeyVault(workspace, root).Read() == first);
            vault.Save(next);
            True(vault.Read() == next && Directory.GetFiles(root).Single() == stored);
            vault.Delete();
            vault.Delete();
            True(!vault.Exists() && Directory.GetFiles(root).Length == 0);
            ThrowsVault("OPENAI_KEY_MISSING", () => vault.Read());
        });

        Test("OpenAI vault rejects a damaged encrypted credential and hides native errors", () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "workspace"), fixture.Module);
            var root = Path.Combine(fixture.Root, "secrets");
            var vault = new OpenAiKeyVault(workspace, root);
            vault.Save("offline-vault-damaged");
            var stored = Directory.GetFiles(root).Single();
            var protectedBytes = File.ReadAllBytes(stored);
            protectedBytes[^1] ^= 1;
            File.WriteAllBytes(stored, protectedBytes);
            ThrowsVault("OPENAI_KEY_UNREADABLE", () => vault.Read());
            vault.Delete();
            True(!vault.Exists());
        });

        Test("OpenAI vault rejects workspace paths, junctions, loose permissions and hard-linked credentials", () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "workspace"), fixture.Module);
            ThrowsVault("OPENAI_KEY_PATH_UNSAFE", () => new OpenAiKeyVault(workspace, Path.Combine(workspace.Root, "secrets")));
            var root = Path.Combine(fixture.Root, "secrets");
            var linkTarget = Directory.CreateDirectory(Path.Combine(fixture.Root, "redirected")).FullName;
            using (var result = Process.Start(new ProcessStartInfo("cmd.exe", $"/c mklink /J \"{root}\" \"{linkTarget}\"")
                { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true }))
            {
                result!.WaitForExit();
                True(result.ExitCode == 0);
            }
            try { ThrowsVault("OPENAI_KEY_PATH_UNSAFE", () => new OpenAiKeyVault(workspace, root)); }
            finally { Directory.Delete(root); }
            True(Directory.Exists(linkTarget));
            Directory.CreateDirectory(root);
            var vault = new OpenAiKeyVault(workspace, root);
            ThrowsVault("OPENAI_KEY_ACL_UNSAFE", () => vault.Save("offline-vault-blocked"));
            True(Directory.GetFiles(root).Length == 0);
            Directory.Delete(root);
            vault.Save("offline-vault-linked");
            var stored = Directory.GetFiles(root).Single();
            var file = new FileInfo(stored);
            var acl = file.GetAccessControl();
            var broadRead = new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.WorldSid, null),
                FileSystemRights.Read, AccessControlType.Allow);
            acl.AddAccessRule(broadRead);
            file.SetAccessControl(acl);
            ThrowsVault("OPENAI_KEY_ACL_UNSAFE", () => vault.Exists());
            acl.RemoveAccessRule(broadRead);
            file.SetAccessControl(acl);
            var linked = Path.Combine(fixture.Root, "shared.bin");
            True(CreateVaultTestHardLink(linked, stored, IntPtr.Zero));
            ThrowsVault("OPENAI_KEY_PATH_UNSAFE", () => vault.Read());
            ThrowsVault("OPENAI_KEY_PATH_UNSAFE", () => vault.Save("offline-vault-blocked"));
            ThrowsVault("OPENAI_KEY_PATH_UNSAFE", vault.Delete);
            True(File.Exists(stored) && File.Exists(linked));
        });
    }

    private static void ThrowsVault(string code, Action action)
    {
        try { action(); }
        catch (InvalidDataException error) when (error.Message == code && error.InnerException == null) { return; }
        throw new AssertionFailure("Expected vault error " + code);
    }

    [DllImport("kernel32.dll", EntryPoint = "CreateHardLinkW", CharSet = CharSet.Unicode, ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CreateVaultTestHardLink(string link, string existing, IntPtr reserved);
}
