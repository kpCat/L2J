[CmdletBinding()]
param([string]$SnapshotRoot = '', [string]$OutputRoot = '')
$ErrorActionPreference='Stop'
$studio=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$SnapshotRoot) { $SnapshotRoot=Split-Path $studio -Parent }
$SnapshotRoot=[IO.Path]::GetFullPath($SnapshotRoot)
$preimage=Join-Path $SnapshotRoot 'preimage/PhantomSemanticStudio'
$manifestPath=Join-Path $SnapshotRoot 'SOURCE_MANIFEST.json'
if (!(Test-Path -LiteralPath $manifestPath) -or !(Test-Path -LiteralPath $preimage)) { throw 'Нужны исходный SHA manifest и физический preimage snapshot.' }
$allowed=@(
 'src/PhantomSemanticStudio.Core/LmStudioClient.cs','src/PhantomSemanticStudio.Core/Models.cs','src/PhantomSemanticStudio.Core/BatchGeneration.cs','src/PhantomSemanticStudio.Core/WorkspaceStore.cs',
 'src/PhantomSemanticStudio.Core/PhantomSemanticStudio.Core.csproj','src/PhantomSemanticStudio.Cli/Program.cs','tests/PhantomSemanticStudio.Tests/Program.cs','README_RU.md',
 'src/PhantomSemanticStudio.Core/PromptProfiles.cs','src/PhantomSemanticStudio.Core/LmStudioClient.Corpus.cs','src/PhantomSemanticStudio.Core/V4CorpusGeneration.cs',
 'src/PhantomSemanticStudio.Core/CorpusCampaign.cs','src/PhantomSemanticStudio.Core/CorpusQuality.cs','tests/PhantomSemanticStudio.Tests/SemanticCorpus001.cs',
 'prompts/corpus001/common.v1.md','prompts/corpus001/situation-first.v1.md','prompts/corpus001/persona-first.v1.md','prompts/corpus001/dialogue-context.v1.md',
 'prompts/corpus001/mature-rough.v1.md','prompts/corpus001/styles.v1.json','prompts/corpus001/pilot-cases.v1.json','prompts/corpus001/samplers.v1.json',
 'scripts/Invoke-CorpusLab.ps1','scripts/Start-CorpusLab.cmd','scripts/Verify-CorpusOverlay.ps1','docs/SEMANTIC_CORPUS_RU.md',
 'src/PhantomSemanticStudio.Core/PromptLab.cs','src/PhantomSemanticStudio.Core/PromptLabStore.cs','src/PhantomSemanticStudio.Core/PromptLabSession.cs','src/PhantomSemanticStudio.Core/LmStudioClient.PromptLab.cs',
 'src/PhantomSemanticStudio.WinForms/MainForm.cs','src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs','src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj','src/PhantomSemanticStudio.WinForms/Program.cs',
 'src/PhantomSemanticStudio.WinForms/PromptLabControl.cs','src/PhantomSemanticStudio.WinForms/PromptLabControl.Designer.cs','src/PhantomSemanticStudio.WinForms/PromptLabControl.resx',
 'tests/PhantomSemanticStudio.Tests/PromptLab001.cs','tests/PhantomSemanticStudio.Tests/PromptLab001Controls.cs','docs/PROMPT_LAB_RU.md','prompts/corpus001/refiner.v1.md',
 'src/PhantomSemanticStudio.Core/PromptLabAuthoring.cs','src/PhantomSemanticStudio.Core/PromptLabRouting.cs',
 'scripts/Verify-Designer.ps1','tests/PhantomSemanticStudio.Tests/Batch081Controls.cs','tests/PhantomSemanticStudio.Tests/Pss005.cs',
 'src/PhantomSemanticStudio.Core/PromptHierarchy.cs','prompts/corpus001/families.v1.json','prompts/corpus001/user-task.v1.md','src/PhantomSemanticStudio.Core/CorpusCandidateIndex.cs',
 'prompts/corpus001/semantic-conservative.v1.md','prompts/corpus001/semantic-act-contrast.v1.md',
 'src/PhantomSemanticStudio.Core/CorpusCampaignJournal.cs','src/PhantomSemanticStudio.Core/CorpusCampaignWorker.cs',
 'tests/PhantomSemanticStudio.Tests/CorpusCampaign001.cs','scripts/Start-CorpusCampaign.ps1',
 'src/PhantomSemanticStudio.Core/CorpusCampaignPolicy.cs','src/PhantomSemanticStudio.Core/CorpusCampaignEditorial.cs',
 'src/PhantomSemanticStudio.Core/LmStudioClient.CorpusEditorial.cs','src/PhantomSemanticStudio.Core/CorpusCampaignExport.cs','prompts/corpus001/editorial.v1.md'
)
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
$manifestEntries=Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$baseline=@{}; foreach ($entry in $manifestEntries) { $baseline[$entry.Path]=$entry.Sha256 }
$changed=[Collections.Generic.List[object]]::new()
foreach ($entry in $baseline.GetEnumerator()) {
    if ((Hash (Join-Path $preimage $entry.Key)) -ne $entry.Value) { throw "PREIMAGE_CHANGED: $($entry.Key)" }
    if (!(Test-Path -LiteralPath (Join-Path $studio $entry.Key))) { throw "SOURCE_DELETED: $($entry.Key)" }
}
foreach ($file in Get-ChildItem -LiteralPath $studio -File -Recurse | Where-Object { $_.FullName -notmatch '[\\/](bin|obj|artifacts|\.vs|\.git)[\\/]' }) {
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'LINKED_SOURCE' }
    $relative=$file.FullName.Substring($studio.Length+1).Replace('\','/'); $sha=Hash $file.FullName
    if ($baseline.ContainsKey($relative) -and $sha -eq $baseline[$relative]) { continue }
    if ($relative -notin $allowed) { throw "OUT_OF_SCOPE: $relative" }
    $changed.Add([pscustomobject]@{ Path=$relative; Before=$baseline[$relative]; After=$sha })
}
if ($changed.Count -gt $allowed.Count) { throw 'SOURCE_FILE_BUDGET' }
# Numeric codepoints represent diagnostic patterns, never user-facing escaped text.
$markerCodes='1056,1119;1056,1116;1056,1115;1056,8226;1056,1038;1056,8250;1056,164;1056,1114;1056,1032;1056,1113;1056,1169;1056,181;1056,176;1056,187;1056,1029;1056,1109;1057,1039;1057,8364;1057,1026;1057,8249;1057,1034;1057,8218;1057,1107;1057,8225;1057,8230;1057,8224;65533'
$moji=($markerCodes.Split(';') | ForEach-Object { [regex]::Escape((-join ($_.Split(',') | ForEach-Object { [char][int]$_ }))) }) -join '|'
$escaped='\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};'
foreach ($entry in $changed) {
    $text=[IO.File]::ReadAllText((Join-Path $studio $entry.Path),[Text.UTF8Encoding]::new($false,$true))
    if ($text -cmatch $moji) { throw "MOJIBAKE_MARKER: $($entry.Path)" }
    if ($text -cmatch $escaped) { throw "ESCAPED_CYRILLIC: $($entry.Path)" }
}
Write-Output "SCOPE_PASS: $($changed.Count)/$($allowed.Count) exact allowed files; preimage SHA verified."
Write-Output 'MOJIBAKE_MARKERS_PASS'
Write-Output 'ESCAPED_CYRILLIC_PASS'
if ($OutputRoot) {
    $OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
    if (Test-Path -LiteralPath $OutputRoot) { throw 'OVERLAY_OUTPUT_MUST_BE_NEW' }
    if ($OutputRoot.StartsWith($studio+'\',[StringComparison]::OrdinalIgnoreCase) -or $OutputRoot.StartsWith($preimage+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'OVERLAY_INTERSECTS_SOURCE' }
    New-Item -ItemType Directory -Path $OutputRoot | Out-Null
    [IO.File]::WriteAllText((Join-Path $OutputRoot 'manifest.json'),($changed | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
    foreach ($entry in $changed) {
        $target=Join-Path $OutputRoot ('postimage/'+$entry.Path); New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $studio $entry.Path) -Destination $target
        if ((Hash $target) -ne $entry.After) { throw 'OVERLAY_COPY_SHA_MISMATCH' }
    }
    $patchCheck=Join-Path $SnapshotRoot ('patch-check-'+[guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $patchCheck | Out-Null
    $appliedSource=Join-Path $patchCheck 'PhantomSemanticStudio'
    Copy-Item -LiteralPath $preimage -Destination $appliedSource -Recurse
    $registryRelative='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/semantic/v4'
    $registryTarget=Join-Path $patchCheck $registryRelative
    New-Item -ItemType Directory -Path $registryTarget -Force | Out-Null
    foreach ($name in @('registry.tsv','voices.tsv','events.tsv')) {
        Copy-Item -LiteralPath (Join-Path (Join-Path $SnapshotRoot $registryRelative) $name) -Destination (Join-Path $registryTarget $name)
    }
    # Standard library only; applies unified hunks to a physical preimage copy and checks bytes/SHA.
    $patchProgram=@'
import difflib,hashlib,json,pathlib,re,sys
old,new,out,applied=map(pathlib.Path,sys.argv[1:])
entries=json.loads((out/'manifest.json').read_text(encoding='utf-8-sig'))
patches=[]
for entry in entries:
    name=entry['Path']; a=(old/name).read_bytes().decode('utf-8').splitlines(keepends=True) if (old/name).exists() else []
    b=(new/name).read_bytes().decode('utf-8').splitlines(keepends=True)
    diff=list(difflib.unified_diff(a,b,fromfile='a/PhantomSemanticStudio/'+name if (old/name).exists() else '/dev/null',tofile='b/PhantomSemanticStudio/'+name,n=3))
    rebuilt=[]; cursor=0; index=2
    while index<len(diff):
        match=re.match(r'@@ -([0-9]+)(?:,([0-9]+))? \+([0-9]+)(?:,([0-9]+))? @@',diff[index]); assert match, name
        start=int(match[1]); count=int(match[2] or '1'); start=start-1 if count else start
        rebuilt+=a[cursor:start]; cursor=start; index+=1
        while index<len(diff) and not diff[index].startswith('@@ '):
            line=diff[index]; kind=line[0]; value=line[1:]
            if kind in ' -': assert cursor<len(a) and a[cursor]==value, name; cursor+=1
            if kind in ' +': rebuilt.append(value)
            index+=1
    rebuilt+=a[cursor:]; assert rebuilt==b, name
    target=applied/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(''.join(rebuilt).encode('utf-8'))
    assert hashlib.sha256(target.read_bytes()).hexdigest()==entry['After'], name
    patches.extend(diff)
(out/'studio.patch').write_text(''.join(patches),encoding='utf-8',newline='\n')
(out/'patch-proof.json').write_text(json.dumps({'Files':len(entries),'AppliedSource':str(applied),'Status':'UNIFIED_HUNKS_APPLIED_EXACT_SHA','Build':'NOT_RUN'},indent=2),encoding='utf-8')
print('UNIFIED_PATCH_HUNKS_VERIFIED files='+str(len(entries)))
'@
    & python -c $patchProgram $preimage $studio $OutputRoot $appliedSource
    if ($LASTEXITCODE -ne 0) { throw 'UNIFIED_PATCH_VERIFICATION_FAILED' }
    Write-Output ('PATCH_SHA256='+ (Hash (Join-Path $OutputRoot 'studio.patch')))
    Write-Output ('PATCH_CHECK_SOURCE='+$appliedSource)
}
