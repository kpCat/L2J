using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using System.IO;
using System.Collections.Generic;
using System.Security.AccessControl;
using System.Security.Principal;
using Microsoft.Win32.SafeHandles;

// Read-only Win32 queries; optional debug privilege affects only the elevated helper's own token.
public static class Win32SqlIdentity
{
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool QueryFullProcessImageNameW(IntPtr handle, uint flags, StringBuilder path, ref uint size);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool GetProcessTimes(IntPtr handle, out long created, out long exited, out long kernel, out long user);
    [DllImport("kernel32.dll")] static extern IntPtr GetCurrentProcess();
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool GetTokenInformation(IntPtr token, int kind, out int value, uint size, out uint returned);
    [DllImport("advapi32.dll", EntryPoint="GetTokenInformation", SetLastError=true)] static extern bool GetTokenBuffer(IntPtr token, int kind, IntPtr value, uint size, out uint returned);
    [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr OpenSCManagerW(string machine, string database, uint access);
    [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr OpenServiceW(IntPtr manager, string name, uint access);
    [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool QueryServiceConfigW(IntPtr service, IntPtr config, uint size, out uint required);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool QueryServiceStatusEx(IntPtr service, int kind, out ServiceStatus status, uint size, out uint required);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool CloseServiceHandle(IntPtr handle);
    [DllImport("iphlpapi.dll",SetLastError=true)] static extern uint GetExtendedTcpTable(IntPtr table,ref uint size,bool order,uint family,int kind,uint reserved);
    [StructLayout(LayoutKind.Sequential)] struct Privilege { public uint Count,Low; public int High; public uint Attributes; }
    [StructLayout(LayoutKind.Sequential)] struct Luid { public uint Low; public int High; }
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool LookupPrivilegeValueW(string system,string name,out Luid luid);
    [DllImport("advapi32.dll",SetLastError=true)] static extern bool AdjustTokenPrivileges(IntPtr token,bool disable,ref Privilege value,uint length,out Privilege previous,out uint returned);
    [DllImport("kernel32.dll")] static extern void SetLastError(uint error);

    [StructLayout(LayoutKind.Sequential)] struct ServiceStatus
    {
        public uint Type, State, Controls, Win32Exit, ServiceExit, Checkpoint, WaitHint, Pid, Flags;
    }
    [StructLayout(LayoutKind.Sequential)] struct ServiceConfig
    {
        public uint Type, StartType, ErrorControl;
        public IntPtr BinaryPath, LoadOrder;
        public uint Tag;
        public IntPtr Dependencies, Account, DisplayName;
    }
    public sealed class ProcessResult
    {
        public uint Pid, RequestedAccess;
        public int Error;
        public string ImagePath;
        public long CreatedFileTime;
        public bool Success;
    }
    public sealed class ServiceResult
    {
        public string Name, BinaryPath, Account;
        public uint Pid, State, Type;
    }
    public sealed class ListenerResult { public string LocalAddress; public int LocalPort; public uint OwningProcess; }
    public static ListenerResult[] Listeners(int port)
    {
        if(port<1 || port>65535) throw new InvalidOperationException("G0_TCP_PORT");
        var result=new List<ListenerResult>();
        foreach(uint family in new uint[] {2,23}) {
            uint size=0; uint code=GetExtendedTcpTable(IntPtr.Zero,ref size,false,family,3,0);
            if((code!=122 && code!=0) || size<4 || size>16777216) throw new InvalidOperationException("G0_TCP_READ_FAILED");
            IntPtr table=Marshal.AllocHGlobal((int)size);
            try {
                if(GetExtendedTcpTable(table,ref size,false,family,3,0)!=0) throw new InvalidOperationException("G0_TCP_READ_CHANGED");
                int count=Marshal.ReadInt32(table); int rowSize=family==2?24:56;
                if(count<0 || (long)count*rowSize+4>size) throw new InvalidOperationException("G0_TCP_TABLE_SIZE");
                for(int i=0;i<count;i++) {
                    IntPtr row=IntPtr.Add(table,4+i*rowSize);int rawPort=Marshal.ReadInt32(row,family==2?8:20);
                    int localPort=((rawPort&255)<<8)|((rawPort>>8)&255);
                    if(localPort!=port) continue;
                    byte[] address=new byte[family==2?4:16];Marshal.Copy(IntPtr.Add(row,family==2?4:0),address,0,address.Length);
                    string text=family==2?new System.Net.IPAddress(address).ToString():new System.Net.IPAddress(address,(uint)Marshal.ReadInt32(row,16)).ToString();
                    result.Add(new ListenerResult {LocalAddress=text,LocalPort=localPort,OwningProcess=unchecked((uint)Marshal.ReadInt32(row,family==2?20:52))});
                }
            } finally {Marshal.FreeHGlobal(table);}
        }
        return result.ToArray();
    }
    public static ProcessResult Process(uint pid, uint access)
    {
        var result = new ProcessResult { Pid=pid, RequestedAccess=access };
        IntPtr handle=OpenProcess(access, false, pid);
        if(handle==IntPtr.Zero) { result.Error=Marshal.GetLastWin32Error(); return result; }
        try
        {
            uint size=32768; var path=new StringBuilder((int)size);
            if(!QueryFullProcessImageNameW(handle,0,path,ref size)) { result.Error=Marshal.GetLastWin32Error(); return result; }
            long created, exited, kernel, user;
            if(!GetProcessTimes(handle,out created,out exited,out kernel,out user)) { result.Error=Marshal.GetLastWin32Error(); return result; }
            result.ImagePath=path.ToString(); result.CreatedFileTime=created; result.Success=true;
            return result;
        }
        finally { CloseHandle(handle); }
    }
    public static SafeFileHandle Hold(uint pid)
    {
        IntPtr handle=OpenProcess(0x1000, false, pid);
        if(handle==IntPtr.Zero) throw new InvalidOperationException("G0_PROCESS_QUERY_DENIED");
        return new SafeFileHandle(handle,true);
    }
    public static ProcessResult Read(SafeFileHandle handle, uint pid)
    {
        uint size=32768; var path=new StringBuilder((int)size);
        long created, exited, kernel, user;
        if(!QueryFullProcessImageNameW(handle.DangerousGetHandle(),0,path,ref size) ||
           !GetProcessTimes(handle.DangerousGetHandle(),out created,out exited,out kernel,out user))
            throw new InvalidOperationException("G0_PROCESS_QUERY_FAILED");
        if(exited!=0) throw new InvalidOperationException("G0_PROCESS_EXITED");
        return new ProcessResult { Pid=pid, Success=true, ImagePath=path.ToString(), CreatedFileTime=created };
    }
    public static bool CurrentTokenElevated()
    {
        IntPtr token;
        if(!OpenProcessToken(GetCurrentProcess(),8,out token)) throw new Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            int elevated; uint returned;
            if(!GetTokenInformation(token,20,out elevated,4,out returned)) throw new Win32Exception(Marshal.GetLastWin32Error());
            return elevated!=0;
        }
        finally { CloseHandle(token); }
    }
    static IntPtr TokenBuffer(IntPtr token,int kind)
    {
        uint size;
        GetTokenBuffer(token,kind,IntPtr.Zero,0,out size);
        if(size<4 || size>65536) throw new InvalidOperationException("G0_TOKEN_QUERY_FAILED");
        IntPtr buffer=Marshal.AllocHGlobal((int)size);
        if(!GetTokenBuffer(token,kind,buffer,size,out size)) {Marshal.FreeHGlobal(buffer);throw new InvalidOperationException("G0_TOKEN_QUERY_FAILED");}
        return buffer;
    }
    public static int CurrentIntegrityRid()
    {
        IntPtr token;
        if(!OpenProcessToken(GetCurrentProcess(),8,out token)) throw new InvalidOperationException("G0_TOKEN_QUERY_FAILED");
        try {
            IntPtr buffer=TokenBuffer(token,25);
            try {
                string sid=new SecurityIdentifier(Marshal.ReadIntPtr(buffer)).Value;
                if(!sid.StartsWith("S-1-16-",StringComparison.Ordinal)) throw new InvalidOperationException("G0_TOKEN_INTEGRITY");
                return Int32.Parse(sid.Substring(7),System.Globalization.CultureInfo.InvariantCulture);
            } finally {Marshal.FreeHGlobal(buffer);}
        } finally {CloseHandle(token);}
    }
    public static bool CurrentDebugEnabled()
    {
        IntPtr token;
        if(!OpenProcessToken(GetCurrentProcess(),8,out token)) throw new InvalidOperationException("G0_TOKEN_QUERY_FAILED");
        try {
            Luid debug;if(!LookupPrivilegeValueW(null,"SeDebugPrivilege",out debug)) throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_UNAVAILABLE");
            IntPtr buffer=TokenBuffer(token,3);
            try {
                int count=Marshal.ReadInt32(buffer);
                if(count<0 || count>1024) throw new InvalidOperationException("G0_TOKEN_QUERY_FAILED");
                for(int i=0;i<count;i++) {
                    IntPtr entry=IntPtr.Add(buffer,4+i*12);
                    if(unchecked((uint)Marshal.ReadInt32(entry))==debug.Low && Marshal.ReadInt32(entry,4)==debug.High) return (Marshal.ReadInt32(entry,8)&2)!=0;
                }
                return false;
            } finally {Marshal.FreeHGlobal(buffer);}
        } finally {CloseHandle(token);}
    }
    public static IDisposable EnableDebug()
    {
        if(!CurrentTokenElevated()) throw new InvalidOperationException("G0_HELPER_ELEVATION_REQUIRED");
        IntPtr token;
        if(!OpenProcessToken(GetCurrentProcess(),0x28,out token)) throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_UNAVAILABLE");
        try {
            Luid luid; if(!LookupPrivilegeValueW(null,"SeDebugPrivilege",out luid)) throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_UNAVAILABLE");
            var value=new Privilege {Count=1,Low=luid.Low,High=luid.High,Attributes=2};Privilege previous;uint returned;
            SetLastError(0);
            if(!AdjustTokenPrivileges(token,false,ref value,(uint)Marshal.SizeOf(typeof(Privilege)),out previous,out returned) || Marshal.GetLastWin32Error()!=0) throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_UNAVAILABLE");
            var lease=new DebugLease {Token=token,Previous=previous};
            if(!CurrentDebugEnabled()) {lease.Dispose();token=IntPtr.Zero;throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_UNAVAILABLE");}
            return lease;
        } catch {CloseHandle(token);throw;}
    }
    sealed class DebugLease : IDisposable
    {
        public IntPtr Token;public Privilege Previous;
        public void Dispose() {
            if(Token==IntPtr.Zero)return;
            try { if(Previous.Count!=0) {Privilege ignored;uint returned;SetLastError(0);if(!AdjustTokenPrivileges(Token,false,ref Previous,(uint)Marshal.SizeOf(typeof(Privilege)),out ignored,out returned) || Marshal.GetLastWin32Error()!=0) throw new InvalidOperationException("G0_DEBUG_PRIVILEGE_RESTORE_FAILED");} }
            finally {CloseHandle(Token);Token=IntPtr.Zero;}
        }
    }
    public static ServiceResult Service(string name)
    {
        IntPtr manager=OpenSCManagerW(null,null,1);
        if(manager==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            IntPtr service=OpenServiceW(manager,name,1|4);
            if(service==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
            try
            {
                uint required; QueryServiceConfigW(service,IntPtr.Zero,0,out required);
                if(required==0 || required>8192) throw new InvalidOperationException("SCM_CONFIG_SIZE");
                IntPtr buffer=Marshal.AllocHGlobal((int)required);
                try
                {
                    if(!QueryServiceConfigW(service,buffer,required,out required)) throw new Win32Exception(Marshal.GetLastWin32Error());
                    var config=(ServiceConfig)Marshal.PtrToStructure(buffer,typeof(ServiceConfig));
                    ServiceStatus status;
                    if(!QueryServiceStatusEx(service,0,out status,(uint)Marshal.SizeOf(typeof(ServiceStatus)),out required)) throw new Win32Exception(Marshal.GetLastWin32Error());
                    return new ServiceResult {Name=name, Pid=status.Pid, State=status.State, Type=status.Type,
                        BinaryPath=Marshal.PtrToStringUni(config.BinaryPath), Account=Marshal.PtrToStringUni(config.Account)};
                }
                finally { Marshal.FreeHGlobal(buffer); }
            }
            finally { CloseServiceHandle(service); }
        }
        finally { CloseServiceHandle(manager); }
    }
}

// A receipt is read and irreversibly consumed through the same exclusive append-only handle.
public sealed class G0AttestationFile : IDisposable
{
    public SafeFileHandle Handle { get; private set; }
    string path;
    const uint ReadAppend=0x12008d;
    [StructLayout(LayoutKind.Sequential)] struct Attributes { public int Length; public IntPtr Descriptor; public int Inherit; }
    [StructLayout(LayoutKind.Sequential)] struct Time { public uint Low,High; }
    [StructLayout(LayoutKind.Sequential)] struct Info
    {
        public uint Attributes; public Time Created,Accessed,Written;
        public uint Volume,SizeHigh,SizeLow,Links,IndexHigh,IndexLow;
    }
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern SafeFileHandle CreateFileW(string path,uint access,uint share,IntPtr attributes,uint mode,uint flags,IntPtr template);
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool CreateDirectoryW(string path,ref Attributes attributes);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetFileInformationByHandle(SafeFileHandle handle,out Info info);
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern uint GetFinalPathNameByHandleW(SafeFileHandle handle,StringBuilder path,uint size,uint flags);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool ReadFile(SafeFileHandle handle,byte[] buffer,uint count,out uint read,IntPtr overlapped);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool WriteFile(SafeFileHandle handle,byte[] buffer,uint count,out uint written,IntPtr overlapped);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetFilePointerEx(SafeFileHandle handle,long distance,out long position,uint origin);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool FlushFileBuffers(SafeFileHandle handle);
    [DllImport("advapi32.dll",SetLastError=true)] static extern uint GetSecurityInfo(SafeFileHandle handle,int type,uint flags,out IntPtr owner,out IntPtr group,out IntPtr dacl,out IntPtr sacl,out IntPtr descriptor);
    [DllImport("advapi32.dll")] static extern uint GetSecurityDescriptorLength(IntPtr descriptor);
    [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr memory);

    public static string Canonical(string value)
    {
        if(String.IsNullOrEmpty(value) || !System.Text.RegularExpressions.Regex.IsMatch(value,@"^[A-Za-z]:\\") || value.IndexOf(':',2)>=0 || value.Length>240)
            throw new InvalidOperationException("G0_LOCAL_CANONICAL_PATH_REQUIRED");
        string full=Path.GetFullPath(value);
        if(!String.Equals(full.TrimEnd('\\'),value.TrimEnd('\\'),StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("G0_LOCAL_CANONICAL_PATH_REQUIRED");
        return full;
    }
    static SafeFileHandle OpenRaw(string value,uint access,uint share,uint flags)
    {
        var handle=CreateFileW(Canonical(value),access,share,IntPtr.Zero,3,flags|0x00200000,IntPtr.Zero);
        if(handle.IsInvalid) { handle.Dispose(); throw new InvalidOperationException("G0_FILE_OPEN_DENIED"); }
        return handle;
    }
    static Info Physical(SafeFileHandle handle,string value,bool directory)
    {
        Info info;
        if(!GetFileInformationByHandle(handle,out info)) throw new InvalidOperationException("G0_FILE_METADATA_FAILED");
        if((info.Attributes&0x400)!=0 || ((info.Attributes&16)!=0)!=directory || (!directory && info.Links!=1)) throw new InvalidOperationException("G0_REPARSE_OR_ALIAS");
        var final=new StringBuilder(32768);
        uint length=GetFinalPathNameByHandleW(handle,final,32768,0);
        if(length==0 || length>=32768 || !String.Equals(final.ToString().Replace(@"\\?\",String.Empty).TrimEnd('\\'),Canonical(value).TrimEnd('\\'),StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("G0_PHYSICAL_PATH_MISMATCH");
        return info;
    }
    static RawSecurityDescriptor Security(SafeFileHandle handle)
    {
        IntPtr owner,group,dacl,sacl,descriptor;
        if(GetSecurityInfo(handle,1,5,out owner,out group,out dacl,out sacl,out descriptor)!=0) throw new InvalidOperationException("G0_SECURITY_READ_FAILED");
        try { int size=checked((int)GetSecurityDescriptorLength(descriptor)); var bytes=new byte[size]; Marshal.Copy(descriptor,bytes,0,size); return new RawSecurityDescriptor(bytes,0); }
        finally { LocalFree(descriptor); }
    }
    static void ExactAcl(SafeFileHandle handle,string owner,string reader,int readerMask)
    {
        ValidateAcl(Security(handle),owner,reader,readerMask);
    }
    static void ValidateAcl(RawSecurityDescriptor sd,string owner,string reader,int readerMask)
    {
        if(sd.Owner==null || sd.Owner.Value!=owner || (sd.ControlFlags&ControlFlags.DiscretionaryAclProtected)==0 || sd.DiscretionaryAcl==null || sd.DiscretionaryAcl.Count!=3)
            throw new InvalidOperationException("G0_RECEIPT_ACL");
        var expected=new Dictionary<string,int> { {"S-1-5-32-544",0x1f01ff},{"S-1-5-18",0x1f01ff},{reader,readerMask} };
        foreach(GenericAce ace in sd.DiscretionaryAcl)
        {
            var allowed=ace as CommonAce; int mask;
            if(allowed==null || allowed.AceFlags!=AceFlags.None || allowed.AceQualifier!=AceQualifier.AccessAllowed || !expected.TryGetValue(allowed.SecurityIdentifier.Value,out mask) || allowed.AccessMask!=mask)
                throw new InvalidOperationException("G0_RECEIPT_ACL");
            expected.Remove(allowed.SecurityIdentifier.Value);
        }
        if(expected.Count!=0) throw new InvalidOperationException("G0_RECEIPT_ACL");
    }
    public static void AssertDescriptor(string sddl,string reader)
    {
        ValidateAcl(new RawSecurityDescriptor(sddl),"S-1-5-32-544",reader,(int)ReadAppend);
    }
    public static string ReceiptSddl(string sid) { new SecurityIdentifier(sid); return "O:BAG:BAD:P(A;;FA;;;BA)(A;;FA;;;SY)(A;;0x12008d;;;"+sid+")"; }
    static string DirectorySddl(string sid,int mask) { new SecurityIdentifier(sid); return "O:BAG:BAD:P(A;;FA;;;BA)(A;;FA;;;SY)(A;;0x"+mask.ToString("x")+";;;"+sid+")"; }
    public static G0AttestationFile Open(string value)
    {
        var file=new G0AttestationFile { path=Canonical(value),Handle=OpenRaw(value,ReadAppend,0,0) };
        try { Physical(file.Handle,value,false); return file; } catch {file.Dispose();throw;}
    }
    public static IDisposable PinFile(string value)
    {
        var file=new G0AttestationFile {path=Canonical(value),Handle=OpenRaw(value,0x120089,1,0)};
        try { Physical(file.Handle,value,false);return file; } catch {file.Dispose();throw;}
    }
    public static void Create(string value,string text,string sid)
    {
        if(File.Exists(Canonical(value))) throw new InvalidOperationException("G0_RECEIPT_REPLAY");
        string pending=Canonical(value)+".pending";
        var sd=new RawSecurityDescriptor(ReceiptSddl(sid)); var bytes=new byte[sd.BinaryLength]; sd.GetBinaryForm(bytes,0);
        var pinned=GCHandle.Alloc(bytes,GCHandleType.Pinned);
        IntPtr attrs=IntPtr.Zero;
        bool created=false,published=false;
        try {
            var attributes=new Attributes { Length=Marshal.SizeOf(typeof(Attributes)),Descriptor=pinned.AddrOfPinnedObject() };
            attrs=Marshal.AllocHGlobal(attributes.Length); Marshal.StructureToPtr(attributes,attrs,false);
            using(var handle=CreateFileW(pending,0x40020000,0,attrs,1,0x00200000,IntPtr.Zero)) {
                if(handle.IsInvalid) throw new InvalidOperationException("G0_RECEIPT_CREATE_NEW_FAILED");
                created=true;
                Physical(handle,pending,false); ExactAcl(handle,"S-1-5-32-544",sid,(int)ReadAppend);
                byte[] content=new UTF8Encoding(false,true).GetBytes(text); uint written;
                if(content.Length>16384 || !WriteFile(handle,content,(uint)content.Length,out written,IntPtr.Zero) || written!=content.Length || !FlushFileBuffers(handle)) throw new InvalidOperationException("G0_RECEIPT_WRITE_FAILED");
            }
            File.Move(pending,Canonical(value));
            published=true;
        } finally {
            if(attrs!=IntPtr.Zero)Marshal.FreeHGlobal(attrs);pinned.Free();
            // Never delete someone else's pending file or a published replay tombstone.
            if(created && !published) File.Delete(pending);
        }
    }
    public static string ReadRequest(string value,string sid)
    {
        using(var file=new G0AttestationFile {path=Canonical(value),Handle=OpenRaw(value,0x120089,5,0)}) {
            Physical(file.Handle,value,false); var sd=Security(file.Handle);
            if(sd.Owner==null || sd.Owner.Value!=sid || (sd.ControlFlags&ControlFlags.DiscretionaryAclProtected)==0 || sd.DiscretionaryAcl==null || sd.DiscretionaryAcl.Count!=2) throw new InvalidOperationException("G0_REQUEST_ACL");
            var seen=new HashSet<string>();
            foreach(GenericAce ace in sd.DiscretionaryAcl) {
                var a=ace as CommonAce;
                if(a==null || a.AceFlags!=AceFlags.None || a.AceQualifier!=AceQualifier.AccessAllowed || a.AccessMask!=0x1f01ff || (a.SecurityIdentifier.Value!=sid && a.SecurityIdentifier.Value!="S-1-5-18") || !seen.Add(a.SecurityIdentifier.Value)) throw new InvalidOperationException("G0_REQUEST_ACL");
            }
            return file.ReadText();
        }
    }
    public static IDisposable EnsureDirectory(string value,string reader,bool top)
    {
        string sid=top?"S-1-5-11":reader; int mask=top?0x1200a0:0x1200a9;
        var sd=new RawSecurityDescriptor(DirectorySddl(sid,mask)); var bytes=new byte[sd.BinaryLength]; sd.GetBinaryForm(bytes,0);
        var pinned=GCHandle.Alloc(bytes,GCHandleType.Pinned);
        try {
            var attributes=new Attributes {Length=Marshal.SizeOf(typeof(Attributes)),Descriptor=pinned.AddrOfPinnedObject()};
            if(!CreateDirectoryW(Canonical(value),ref attributes) && Marshal.GetLastWin32Error()!=183) throw new InvalidOperationException("G0_DIRECTORY_CREATE_FAILED");
        } finally {pinned.Free();}
        return CheckDirectory(value,reader,top);
    }
    public static IDisposable CheckDirectory(string value,string reader,bool top)
    {
        var handle=OpenRaw(value,0x20080,3,0x02000000);
        try { Physical(handle,value,true);ExactAcl(handle,"S-1-5-32-544",top?"S-1-5-11":reader,top?0x1200a0:0x1200a9);return handle; } catch {handle.Dispose();throw;}
    }
    public static IDisposable PinParents(string value,bool trusted)
    {
        var pins=new Pins(); var directories=new List<string>();
        for(var part=new DirectoryInfo(Path.GetDirectoryName(Canonical(value)));part!=null;part=part.Parent) directories.Add(part.FullName);
        directories.Reverse();
        try {
            foreach(string directory in directories) {
                var handle=OpenRaw(directory,0x20080,3,0x02000000);pins.Handles.Add(handle); Physical(handle,directory,true);
                if(trusted) {
                    var sd=Security(handle);
                    string owner=sd.Owner==null?"":sd.Owner.Value;
                    if(owner!="S-1-5-32-544" && owner!="S-1-5-18" && owner!="S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464") throw new InvalidOperationException("G0_PARENT_OWNER");
                    if(sd.DiscretionaryAcl==null) throw new InvalidOperationException("G0_PARENT_ACL");
                    foreach(GenericAce ace in sd.DiscretionaryAcl) {
                        var a=ace as CommonAce;
                        if(a==null) throw new InvalidOperationException("G0_PARENT_ACL");
                        if((a.AceFlags&AceFlags.InheritOnly)!=0 || a.AceQualifier!=AceQualifier.AccessAllowed) continue;
                        string who=a.SecurityIdentifier.Value;
                        if(who!="S-1-5-32-544" && who!="S-1-5-18" && who!="S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464" && (a.AccessMask&0xd0040)!=0) throw new InvalidOperationException("G0_PARENT_ACL");
                    }
                }
            }
            return pins;
        } catch {pins.Dispose();throw;}
    }
    sealed class Pins : IDisposable { public List<SafeFileHandle> Handles=new List<SafeFileHandle>(); public void Dispose(){for(int i=Handles.Count-1;i>=0;i--)Handles[i].Dispose();} }
    public void AssertAcl(string sid) { Physical(Handle,path,false);ExactAcl(Handle,"S-1-5-32-544",sid,(int)ReadAppend); }
    public string ReadText()
    {
        Info info=Physical(Handle,path,false);long position;
        if(info.SizeHigh!=0 || info.SizeLow==0 || info.SizeLow>16384 || !SetFilePointerEx(Handle,0,out position,0)) throw new InvalidOperationException("G0_RECEIPT_SIZE");
        var bytes=new byte[info.SizeLow];uint read;
        if(!ReadFile(Handle,bytes,(uint)bytes.Length,out read,IntPtr.Zero) || read!=bytes.Length) throw new InvalidOperationException("G0_RECEIPT_READ_FAILED");
        return new UTF8Encoding(false,true).GetString(bytes);
    }
    public void Consume()
    {
        byte[] trailer=Encoding.ASCII.GetBytes("USED\n");uint written;
        if(!WriteFile(Handle,trailer,(uint)trailer.Length,out written,IntPtr.Zero) || written!=trailer.Length || !FlushFileBuffers(Handle)) throw new InvalidOperationException("G0_CONSUME_FAILED");
    }
    public void Dispose() { if(Handle!=null)Handle.Dispose(); }
}
