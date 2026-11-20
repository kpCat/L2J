using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Runtime.Versioning;
using System.Security;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace PhantomSemanticStudio.Core;

[SupportedOSPlatform("windows")]
public sealed class OpenAiKeyVault
{
    private static readonly object Gate = new();
    private const string PathError = "OPENAI_KEY_PATH_UNSAFE";
    private const string AclError = "OPENAI_KEY_ACL_UNSAFE";
    private const string Unreadable = "OPENAI_KEY_UNREADABLE";
    private const int EntropyLength = 32;
    private const int MaxBlobLength = 8192;
    private const uint NoDpapiPrompt = 0x1;
    private const InheritanceFlags DirectoryInheritance = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
    private static ReadOnlySpan<byte> Format => "PSSKEY1\0"u8;

    private readonly WorkspaceStore workspace;
    private readonly string root;
    private readonly string credential;
    private readonly SecurityIdentifier user;
    private readonly SecurityIdentifier system;

    public OpenAiKeyVault(WorkspaceStore workspace) : this(workspace, DefaultRoot()) { }

    internal OpenAiKeyVault(WorkspaceStore workspace, string root)
    {
        this.workspace = workspace;
        this.root = WithPathErrors(() => PathSafety.Canonical(root));
        GuardLocation();
        credential = Path.Combine(this.root, "openai-key.dpapi");
        user = WithStorageErrors(() =>
        {
            using var identity = WindowsIdentity.GetCurrent();
            return identity.User ?? throw new InvalidDataException(AclError);
        });
        system = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);
    }

    public bool Exists() => Run(() =>
    {
        if (!CheckDirectory()) return false;
        using var file = OpenExisting();
        return file != null;
    });

    public string Read() => Run(() =>
    {
        using var file = CheckDirectory() ? OpenExisting() : null;
        if (file == null) throw new InvalidDataException("OPENAI_KEY_MISSING");
        var prefixLength = Format.Length + EntropyLength;
        var length = file.Length;
        V4Rules.Require(length > prefixLength && length <= prefixLength + MaxBlobLength, Unreadable);
        var data = new byte[(int)length];
        try
        {
            file.ReadExactly(data);
            V4Rules.Require(data.AsSpan(0, Format.Length).SequenceEqual(Format), Unreadable);
            var entropy = data.AsSpan(Format.Length, EntropyLength).ToArray();
            var protectedKey = data.AsSpan(prefixLength).ToArray();
            try
            {
                var key = TransformDpapi(protectedKey, entropy, protect: false);
                try
                {
                    var result = Encoding.ASCII.GetString(key);
                    OpenAiResponsesClient.ValidateKey(result);
                    return result;
                }
                finally { CryptographicOperations.ZeroMemory(key); }
            }
            finally
            {
                CryptographicOperations.ZeroMemory(entropy);
                CryptographicOperations.ZeroMemory(protectedKey);
            }
        }
        catch (Exception error) when (error is InvalidDataException or EndOfStreamException)
        {
            throw new InvalidDataException(Unreadable);
        }
        finally { CryptographicOperations.ZeroMemory(data); }
    });

    public void Save(string key)
    {
        OpenAiResponsesClient.ValidateKey(key);
        Run(() =>
        {
            CheckDirectory(create: true);
            var entropy = RandomNumberGenerator.GetBytes(EntropyLength);
            var plaintext = Encoding.ASCII.GetBytes(key);
            try
            {
                var encrypted = TransformDpapi(plaintext, entropy, protect: true);
                try { ReplaceCredential(entropy, encrypted); }
                finally { CryptographicOperations.ZeroMemory(encrypted); }
            }
            finally
            {
                CryptographicOperations.ZeroMemory(plaintext);
                CryptographicOperations.ZeroMemory(entropy);
            }
            return true;
        });
    }

    public void Delete() => Run(() =>
    {
        if (!CheckDirectory()) return false;
        using (var file = OpenExisting())
        {
            if (file == null) return false;
        }
        CheckCredentialPath();
        File.Delete(credential);
        return true;
    });

    private void ReplaceCredential(byte[] entropy, byte[] encrypted)
    {
        bool exists;
        using (var file = OpenExisting()) exists = file != null;
        var temporary = Path.Combine(root, "openai-" + Guid.NewGuid().ToString("N") + ".tmp");
        var created = false;
        try
        {
            var security = new FileSecurity();
            MakePrivate(security, InheritanceFlags.None);
            using (var file = new FileInfo(temporary).Create(FileMode.CreateNew, FileSystemRights.Read | FileSystemRights.Write,
                FileShare.None, 4096, FileOptions.None, security))
            {
                created = true;
                ValidateFile(file);
                file.Write(Format);
                file.Write(entropy);
                file.Write(encrypted);
                file.Flush(flushToDisk: true);
            }
            V4Rules.Require(CheckDirectory(), PathError);
            CheckCredentialPath();
            File.Move(temporary, credential, overwrite: exists);
        }
        finally
        {
            if (created)
            {
                WithPathErrors(() => { PathSafety.AssertNoReparsePoints(temporary); return true; });
                File.Delete(temporary);
            }
        }
    }

    private bool CheckDirectory(bool create = false)
    {
        GuardLocation();
        var directory = new DirectoryInfo(root);
        var attributes = Attributes(root);
        if (attributes == null)
        {
            if (!create) return false;
            var security = new DirectorySecurity();
            MakePrivate(security, DirectoryInheritance);
            directory.Create(security);
            GuardLocation();
            attributes = Attributes(root);
        }
        V4Rules.Require(attributes is { } value && (value & FileAttributes.Directory) != 0, PathError);
        ValidateAcl(directory.GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access),
            AceFlags.ContainerInherit | AceFlags.ObjectInherit);
        return true;
    }

    private FileStream? OpenExisting()
    {
        CheckCredentialPath();
        FileStream file;
        try { file = new FileStream(credential, FileMode.Open, FileAccess.Read, FileShare.Read); }
        catch (FileNotFoundException) { return null; }
        try
        {
            ValidateFile(file);
            return file;
        }
        catch
        {
            file.Dispose();
            throw;
        }
    }

    private void ValidateFile(FileStream file)
    {
        V4Rules.Require(GetFileInformationByHandle(file.SafeFileHandle, out var info) && info.Links == 1
            && (info.Attributes & (FileAttributes.Directory | FileAttributes.ReparsePoint)) == 0, PathError);
        ValidateAcl(file.GetAccessControl(), AceFlags.None);
    }

    private void MakePrivate(FileSystemSecurity security, InheritanceFlags inheritance)
    {
        security.SetOwner(user);
        security.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
        security.AddAccessRule(new FileSystemAccessRule(user, FileSystemRights.FullControl,
            inheritance, PropagationFlags.None, AccessControlType.Allow));
        if (system != user)
            security.AddAccessRule(new FileSystemAccessRule(system, FileSystemRights.FullControl,
                inheritance, PropagationFlags.None, AccessControlType.Allow));
    }

    private void ValidateAcl(FileSystemSecurity security, AceFlags flags)
    {
        var descriptor = new RawSecurityDescriptor(security.GetSecurityDescriptorBinaryForm(), 0);
        var acl = descriptor.DiscretionaryAcl;
        V4Rules.Require(descriptor.Owner == user && (descriptor.ControlFlags & ControlFlags.DiscretionaryAclProtected) != 0
            && acl != null, AclError);
        var principals = new HashSet<SecurityIdentifier> { user, system };
        foreach (var entry in acl!)
            V4Rules.Require(entry is CommonAce { IsCallback: false, AceQualifier: AceQualifier.AccessAllowed } ace
                && ace.AceFlags == flags && ace.AccessMask == (int)FileSystemRights.FullControl
                && principals.Remove(ace.SecurityIdentifier), AclError);
        V4Rules.Require(principals.Count == 0, AclError);
    }

    private void GuardLocation() => WithPathErrors(() =>
    {
        PathSafety.AssertDisjoint(workspace.Root, root);
        PathSafety.AssertDisjoint(workspace.ProtectedRoot, root);
        if (FindRepository(AppContext.BaseDirectory) is { } host) PathSafety.AssertDisjoint(host, root);
        V4Rules.Require(FindRepository(root) == null, PathError);
        return true;
    });

    private void CheckCredentialPath() => WithPathErrors(() =>
    {
        PathSafety.AssertNoReparsePoints(credential);
        V4Rules.Require((Attributes(credential).GetValueOrDefault() & FileAttributes.Directory) == 0, PathError);
        return true;
    });

    private static string DefaultRoot() => WithPathErrors(() => Path.Combine(
        PathSafety.Canonical(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData)),
        "PhantomSemanticStudio", "secrets"));

    private static string? FindRepository(string path)
    {
        for (var directory = new DirectoryInfo(path); directory != null; directory = directory.Parent)
            if (Attributes(Path.Combine(directory.FullName, ".git")) != null) return directory.FullName;
        return null;
    }

    private static FileAttributes? Attributes(string path)
    {
        try { return File.GetAttributes(path); }
        catch (Exception error) when (error is FileNotFoundException or DirectoryNotFoundException) { return null; }
    }

    private T Run<T>(Func<T> action)
    {
        lock (Gate) return WithStorageErrors(action);
    }

    private static T WithPathErrors<T>(Func<T> action)
    {
        try { return action(); }
        catch (Exception error) when (error is InvalidDataException or IOException or UnauthorizedAccessException
            or ArgumentException or NotSupportedException or SecurityException)
        { throw new InvalidDataException(PathError); }
    }

    private static T WithStorageErrors<T>(Func<T> action)
    {
        try { return action(); }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or ArgumentException
            or NotSupportedException or SecurityException or Win32Exception or CryptographicException)
        { throw new InvalidDataException("OPENAI_KEY_STORAGE_FAILED"); }
    }

    private static byte[] TransformDpapi(byte[] input, byte[] entropy, bool protect)
    {
        var inputPin = GCHandle.Alloc(input, GCHandleType.Pinned);
        var entropyPin = default(GCHandle);
        var output = default(DataBlob);
        try
        {
            entropyPin = GCHandle.Alloc(entropy, GCHandleType.Pinned);
            var data = new DataBlob { Length = input.Length, Pointer = inputPin.AddrOfPinnedObject() };
            var salt = new DataBlob { Length = entropy.Length, Pointer = entropyPin.AddrOfPinnedObject() };
            // No CRYPTPROTECT_LOCAL_MACHINE flag keeps protection bound to the current Windows user.
            var success = protect
                ? CryptProtectData(ref data, IntPtr.Zero, ref salt, IntPtr.Zero, IntPtr.Zero, NoDpapiPrompt, ref output)
                : CryptUnprotectData(ref data, IntPtr.Zero, ref salt, IntPtr.Zero, IntPtr.Zero, NoDpapiPrompt, ref output);
            V4Rules.Require(success && output.Pointer != IntPtr.Zero && output.Length > 0
                && output.Length <= (protect ? MaxBlobLength : 1024), protect ? "OPENAI_KEY_PROTECT_FAILED" : Unreadable);
            var bytes = new byte[output.Length];
            Marshal.Copy(output.Pointer, bytes, 0, bytes.Length);
            return bytes;
        }
        finally
        {
            if (output.Pointer != IntPtr.Zero)
            {
                for (var index = 0; index < output.Length; index++) Marshal.WriteByte(output.Pointer, index, 0);
                LocalFree(output.Pointer);
            }
            if (entropyPin.IsAllocated) entropyPin.Free();
            inputPin.Free();
        }
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct DataBlob
    {
        public int Length;
        public IntPtr Pointer;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct FileInformation
    {
        public FileAttributes Attributes;
        public FILETIME CreationTime;
        public FILETIME LastAccessTime;
        public FILETIME LastWriteTime;
        public uint Volume;
        public uint SizeHigh;
        public uint SizeLow;
        public uint Links;
        public uint IndexHigh;
        public uint IndexLow;
    }

    [DllImport("crypt32.dll", ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptProtectData(ref DataBlob input, IntPtr description, ref DataBlob entropy,
        IntPtr reserved, IntPtr prompt, uint flags, ref DataBlob output);

    [DllImport("crypt32.dll", ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptUnprotectData(ref DataBlob input, IntPtr description, ref DataBlob entropy,
        IntPtr reserved, IntPtr prompt, uint flags, ref DataBlob output);

    [DllImport("kernel32.dll", ExactSpelling = true)]
    private static extern IntPtr LocalFree(IntPtr memory);

    [DllImport("kernel32.dll", ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetFileInformationByHandle(SafeFileHandle file, out FileInformation information);
}
