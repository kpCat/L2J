using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

// Test fixture only: every mutable job API receives this NEW unnamed owned job.
// https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-createjobobjectw
public sealed class TrainingOwnedJobFixture : IDisposable
{
    [StructLayout(LayoutKind.Sequential)]
    private struct BasicLimits
    {
        public long PerProcessUserTimeLimit, PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize, MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass, SchedulingClass;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct IoCounters
    {
        public ulong ReadOperationCount, WriteOperationCount, OtherOperationCount;
        public ulong ReadTransferCount, WriteTransferCount, OtherTransferCount;
    }
    // https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information
    [StructLayout(LayoutKind.Sequential)]
    private struct ExtendedLimits
    {
        public BasicLimits BasicLimitInformation;
        public IoCounters IoInfo;
        public UIntPtr ProcessMemoryLimit, JobMemoryLimit, PeakProcessMemoryUsed, PeakJobMemoryUsed;
    }
    private sealed class FixtureHandle : SafeHandleZeroOrMinusOneIsInvalid
    {
        internal FixtureHandle(IntPtr value) : base(true) { SetHandle(value); }
        protected override bool ReleaseHandle() { return CloseHandle(handle); }
    }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, ExactSpelling = true, SetLastError = true)]
    private static extern IntPtr CreateJobObjectW(IntPtr attributes, string name);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetInformationJobObject(FixtureHandle job, int kind, ref ExtendedLimits value, uint length);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryInformationJobObject(FixtureHandle job, int kind, out BasicLimits value, uint length, out uint returned);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool AssignProcessToJobObject(FixtureHandle job, IntPtr process);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool IsProcessInJob(IntPtr process, FixtureHandle job, [MarshalAs(UnmanagedType.Bool)] out bool inside);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    private readonly FixtureHandle job;
    private bool assigned;
    public uint ConfirmedLimitFlags { get; private set; }

    public TrainingOwnedJobFixture()
    {
        int basicBytes = Marshal.SizeOf(typeof(BasicLimits));
        int extendedBytes = Marshal.SizeOf(typeof(ExtendedLimits));
        if (basicBytes != (IntPtr.Size == 8 ? 64 : 48) || extendedBytes != (IntPtr.Size == 8 ? 144 : 112))
            throw new InvalidOperationException("TRAIN002_OWN_JOB_LAYOUT");
        // NULL attributes prohibit inheritance; NULL name always creates a new job.
        IntPtr created = CreateJobObjectW(IntPtr.Zero, null);
        if (created == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error(), "TRAIN002_OWN_JOB_CREATE");
        job = new FixtureHandle(created);
        try
        {
            var limits = new ExtendedLimits();
            limits.BasicLimitInformation.LimitFlags = 0x2000;
            if (!SetInformationJobObject(job, 9, ref limits, (uint)extendedBytes))
                throw new Win32Exception(Marshal.GetLastWin32Error(), "TRAIN002_OWN_JOB_LIMIT");
            BasicLimits observed; uint returned;
            if (!QueryInformationJobObject(job, 2, out observed, (uint)basicBytes, out returned))
                throw new Win32Exception(Marshal.GetLastWin32Error(), "TRAIN002_OWN_JOB_QUERY");
            ConfirmedLimitFlags = observed.LimitFlags;
            if (ConfirmedLimitFlags != 0x2000) throw new InvalidOperationException("TRAIN002_OWN_JOB_LIMIT_MISMATCH");
        }
        catch { job.Dispose(); throw; }
    }

    private static IntPtr Verify(Process process, int pid, long fileTime)
    {
        if (process == null || process.Id != pid || process.StartTime.ToUniversalTime().ToFileTimeUtc() != fileTime || process.HasExited)
            throw new InvalidOperationException("TRAIN002_OWN_JOB_PROCESS_IDENTITY");
        return process.Handle;
    }
    public void Assign(Process process, int pid, long fileTime)
    {
        if (assigned) throw new InvalidOperationException("TRAIN002_OWN_JOB_ALREADY_ASSIGNED");
        IntPtr held = Verify(process, pid, fileTime);
        if (!AssignProcessToJobObject(job, held))
            throw new Win32Exception(Marshal.GetLastWin32Error(), "TRAIN002_OWN_JOB_ASSIGN");
        assigned = true;
        if (Verify(process, pid, fileTime) != held) throw new InvalidOperationException("TRAIN002_OWN_JOB_PROCESS_HANDLE");
    }
    public bool Contains(Process process, int pid, long fileTime)
    {
        IntPtr held = Verify(process, pid, fileTime); bool inside;
        if (!IsProcessInJob(held, job, out inside))
            throw new Win32Exception(Marshal.GetLastWin32Error(), "TRAIN002_OWN_JOB_MEMBERSHIP");
        if (Verify(process, pid, fileTime) != held) throw new InvalidOperationException("TRAIN002_OWN_JOB_PROCESS_HANDLE");
        return inside;
    }
    public void Dispose() { job.Dispose(); }
}
