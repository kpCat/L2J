$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$private = Join-Path $module '.phantom-local/observe014'
$option = Join-Path $private 'secrets/client.cnf'
$rows = @(& 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' "--defaults-extra-file=$option" --batch --skip-column-names --database=l2jmobiush5_localplay_observe014 -e "SELECT charId,char_name,online,level,exp,sp,x,y,z FROM characters WHERE charId=268492939 AND char_name='TestAdmin'; SELECT COUNT(*) FROM characters WHERE online=1;")
if ($LASTEXITCODE -ne 0 -or $rows.Count -ne 2 -or $rows[1] -cne '0' -or $rows[0] -cne "268492939`tTestAdmin`t0`t12`t138026`t13880`t44131`t42673`t-3488") { throw 'Post-stop online/save verification failed.' }
$original = 'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'
$preserved = @(Import-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t")
foreach ($entry in $preserved) { if ((Get-FileHash (Join-Path $original $entry.path)).Hash -cne $entry.sha256) { throw 'Original PLAY hash changed.' } }
Write-Output "POST_STOP_SAVED $($rows[0]); total_online=0; original_PLAY_hashes_preserved=$($preserved.Count)"
& (Join-Path $private 'runtime/Check-LocalPlay.ps1') | Where-Object { $_ -notmatch 'PersonalQoLAccount' }
