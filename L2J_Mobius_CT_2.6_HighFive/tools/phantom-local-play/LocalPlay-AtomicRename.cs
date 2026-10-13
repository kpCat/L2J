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
        for (int attempt = 0; attempt < 5; attempt++)
        {
            if (MoveFileEx(source, destination, 0x1 | 0x8))
                return;
            int error = Marshal.GetLastWin32Error();
            if (attempt == 4 || (error != 5 && error != 32))
                throw new Win32Exception(error);
            // A short-lived reader can temporarily deny replacement on Windows.
            System.Threading.Thread.Sleep(10);
        }
    }
}
