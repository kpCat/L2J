using System;
using System.Runtime.InteropServices;

// Read-only runtime/probe queries. NULL job checks membership in any Windows Job Object.
// https://learn.microsoft.com/en-us/windows/win32/api/jobapi/nf-jobapi-isprocessinjob
public static class TrainingJobProbe
{
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool IsProcessInJob(IntPtr process, IntPtr job, [MarshalAs(UnmanagedType.Bool)] out bool result);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryInformationJobObject(IntPtr job, int kind, IntPtr data, uint length, out uint returned);

    // Native LARGE_INTEGER / DWORD / SIZE_T / ULONG_PTR order, default Windows packing.
    // https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_basic_limit_information
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

    public sealed class JobSnapshot
    {
        public string Scope = "CURRENT_IMMEDIATE_JOB_ONLY";
        public int PointerBytes, BasicLimitStructBytes, LimitsError, ProcessListError;
        public bool LimitsSucceeded, ProcessListSucceeded, ProcessListValid, ProcessListComplete;
        public uint LimitFlags, AssignedProcesses, ReturnedProcessIds, LimitsReturnedBytes, ProcessListReturnedBytes;
        public bool KillOnJobClose, BreakawayOk, SilentBreakawayOk;
        public ulong[] ProcessIds = new ulong[0];
    }

    // NULL reads only this process's immediate job; it cannot identify unnamed ancestors.
    // https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-queryinformationjobobject
    public static JobSnapshot CurrentJob()
    {
        var result = new JobSnapshot();
        result.PointerBytes = IntPtr.Size;
        result.BasicLimitStructBytes = Marshal.SizeOf(typeof(BasicLimits));
        if (result.BasicLimitStructBytes != (IntPtr.Size == 8 ? 64 : 48))
            throw new InvalidOperationException("TRAIN002_JOB_LIMIT_LAYOUT");
        IntPtr limits = Marshal.AllocHGlobal(result.BasicLimitStructBytes);
        try
        {
            uint returned;
            result.LimitsSucceeded = QueryInformationJobObject(IntPtr.Zero, 2, limits, (uint)result.BasicLimitStructBytes, out returned);
            result.LimitsError = result.LimitsSucceeded ? 0 : Marshal.GetLastWin32Error();
            result.LimitsReturnedBytes = returned;
            if (result.LimitsSucceeded)
            {
                var value = (BasicLimits)Marshal.PtrToStructure(limits, typeof(BasicLimits));
                result.LimitFlags = value.LimitFlags;
                result.KillOnJobClose = (value.LimitFlags & 0x2000) != 0;
                result.BreakawayOk = (value.LimitFlags & 0x0800) != 0;
                result.SilentBreakawayOk = (value.LimitFlags & 0x1000) != 0;
            }
        }
        finally { Marshal.FreeHGlobal(limits); }

        const int maximumIds = 4096;
        int bytes = 8 + maximumIds * IntPtr.Size;
        IntPtr list = Marshal.AllocHGlobal(bytes);
        try
        {
            uint returned;
            result.ProcessListSucceeded = QueryInformationJobObject(IntPtr.Zero, 3, list, (uint)bytes, out returned);
            result.ProcessListError = result.ProcessListSucceeded ? 0 : Marshal.GetLastWin32Error();
            result.ProcessListReturnedBytes = returned;
            if (result.ProcessListSucceeded)
            {
                result.AssignedProcesses = unchecked((uint)Marshal.ReadInt32(list, 0));
                result.ReturnedProcessIds = unchecked((uint)Marshal.ReadInt32(list, 4));
                result.ProcessListValid = result.ReturnedProcessIds <= maximumIds && result.AssignedProcesses >= result.ReturnedProcessIds;
                result.ProcessListComplete = result.ProcessListValid && result.AssignedProcesses == result.ReturnedProcessIds;
                if (result.ProcessListValid)
                {
                    result.ProcessIds = new ulong[result.ReturnedProcessIds];
                    for (int index = 0; index < result.ProcessIds.Length; index++)
                    {
                        int offset = 8 + index * IntPtr.Size;
                        result.ProcessIds[index] = IntPtr.Size == 8 ? unchecked((ulong)Marshal.ReadInt64(list, offset)) : unchecked((uint)Marshal.ReadInt32(list, offset));
                    }
                }
            }
        }
        finally { Marshal.FreeHGlobal(list); }
        return result;
    }
}
