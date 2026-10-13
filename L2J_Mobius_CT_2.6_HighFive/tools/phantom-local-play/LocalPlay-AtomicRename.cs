using System.ComponentModel;
using System.Runtime.InteropServices;

public static class LocalPlayAtomicRename
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool MoveFileEx(string source, string destination, uint flags);

    public static void Replace(string source, string destination)
    {
        // Same-directory publication only; no COPY_ALLOWED or deferred operation.
        if (System.IO.Path.GetDirectoryName(source) != System.IO.Path.GetDirectoryName(destination))
            throw new System.ArgumentException("Same-directory rename required.");
        if (!MoveFileEx(source, destination, 0x1 | 0x8))
            throw new Win32Exception(Marshal.GetLastWin32Error());
    }
}
