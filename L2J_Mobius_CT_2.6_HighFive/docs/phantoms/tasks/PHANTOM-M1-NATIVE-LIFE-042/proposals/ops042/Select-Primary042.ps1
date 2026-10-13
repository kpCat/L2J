function Select-Primary042 {
 param([Parameter(Mandatory)][object[]]$Suitable,[string[]]$PreviousIds=@())
 # These are captured native capability facts, never future damage/reward results.
 $casters042=@($Suitable | Where-Object {[double]$_.liveResourceCost -gt 0 -and [int]$_.lastCastSkillId -gt 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 $melee042=@($Suitable | Where-Object {[double]$_.liveResourceCost -eq 0 -and [int]$_.lastCastSkillId -eq 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 if($casters042.Count -eq 0 -or $melee042.Count -eq 0){throw 'PRIMARY_NATIVE_MELEE_CASTER_PRECONDITION: whole cohort retained.'}
 return @($melee042[0],$casters042[0])
}
function Select-LifePrimary042 {
 param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Suitable)
 $life042=@($Suitable | Where-Object {$_.dead -ceq 'false' -and [int]$_.npcId -gt 0 -and $_.inventorySlots -and [int]$_.inventorySlots -lt 200} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
 if($life042.Count -ne 1){throw 'LIFE_PRIMARY042_INPUT_REQUIRED: whole cohort retained.'}
 return $life042
}
