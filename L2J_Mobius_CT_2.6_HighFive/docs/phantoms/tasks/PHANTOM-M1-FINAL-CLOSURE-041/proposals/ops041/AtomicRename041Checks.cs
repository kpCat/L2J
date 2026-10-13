using System;
using System.ComponentModel;
using System.IO;
using System.Threading;

public static class AtomicRename041Checks
{
    public static int Main(string[] args)
    {
        if (args.Length != 1 || Directory.Exists(args[0])) throw new ArgumentException("Fresh explicit fixture directory required.");
        Directory.CreateDirectory(args[0]);
        string root = Path.GetFullPath(args[0]);
        int failed = 0;
        for (int test = 0; test < 3; test++)
        {
            string source = Path.Combine(root, "source" + test), target = Path.Combine(root, "target" + test);
            File.WriteAllText(source, "new" ); File.WriteAllText(target, "old");
            try
            {
                if (test == 0)
                {
                    var reader = File.Open(target, FileMode.Open, FileAccess.Read, FileShare.Read);
                    var close = new Thread(() => { Thread.Sleep(20); reader.Dispose(); });
                    close.Start();
                    try { LocalPlayAtomicRename.Replace(source, target); }
                    finally { close.Join(); reader.Dispose(); }
                    if (File.ReadAllText(target) != "new" || File.Exists(source)) throw new Exception("Atomic publication differs.");
                }
                else if (test == 1)
                {
                    bool rejected = false;
                    using (var reader = File.Open(target, FileMode.Open, FileAccess.Read, FileShare.Read))
                        try { LocalPlayAtomicRename.Replace(source, target); } catch (Win32Exception) { rejected = true; }
                    if (!rejected || File.ReadAllText(target) != "old" || File.ReadAllText(source) != "new") throw new Exception("Persistent conflict failed open.");
                }
                else
                {
                    bool rejected = false;
                    try { LocalPlayAtomicRename.Replace(source, Path.Combine(root, "other", "target")); } catch (ArgumentException) { rejected = true; }
                    if (!rejected || File.ReadAllText(target) != "old" || !File.Exists(source)) throw new Exception("Cross-directory publication accepted.");
                }
                Console.WriteLine("PASS " + test);
            }
            catch (Exception error) { failed++; Console.WriteLine("FAIL " + test + " " + error.Message); }
        }
        Console.WriteLine("ATOMIC_RENAME041 total=3 failed=" + failed);
        return failed == 0 ? 0 : 1;
    }
}
