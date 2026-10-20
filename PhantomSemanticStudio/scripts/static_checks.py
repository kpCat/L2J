from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
root=Path(sys.argv[1])
checks=[]
def check(name, ok):
 checks.append((name,bool(ok))); print(('PASS ' if ok else 'FAIL ')+name)
f=root/'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs'
check('Designer exists', f.exists())
if f.exists():
 s=f.read_text(encoding='utf-8-sig'); body=s.split('private void InitializeComponent()',1)[1]
 check('No loops/LINQ/await/lambdas in InitializeComponent', not re.search(r'\b(for|foreach|while|await|if|switch)\s*\(|=>|\.Select\(|\.Where\(',body))
 check('Six designed tabs',len(re.findall(r'= new (?:System.Windows.Forms.)?TabPage\(\);',body))==6)
 check('No I/O in designer', not re.search(r'File\.|Directory\.|HttpClient|Process\.|Task\.',body))
for p in root.rglob('*.csproj'):
 ET.parse(p); check('XML '+p.name,True)
check('Solution exists',(root/'PhantomSemanticStudio.sln').exists())
check('Core runtime tests present',(root/'tests/PhantomSemanticStudio.Tests/Program.cs').exists())
check('No DLL/EXE represented as tested builds',not list(root.rglob('*.exe')) and not list(root.rglob('*.dll')))
print(f'{sum(v for _,v in checks)}/{len(checks)} PASS')
sys.exit(not all(v for _,v in checks))
