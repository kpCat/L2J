function Select-Primary041 {
 param([Parameter(Mandatory)][object[]]$Suitable,[string[]]$PreviousIds=@())
 # These are captured native capability facts, never future damage/reward results.
 $casters041=@($Suitable | Where-Object {[double]$_.liveResourceCost -gt 0 -and [int]$_.lastCastSkillId -gt 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 $melee041=@($Suitable | Where-Object {[double]$_.liveResourceCost -eq 0 -and [int]$_.lastCastSkillId -eq 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 if($casters041.Count -eq 0 -or $melee041.Count -eq 0){throw 'PRIMARY_NATIVE_MELEE_CASTER_PRECONDITION: whole cohort retained.'}
 return @($melee041[0],$casters041[0])
}
function Select-LifePrimary041 {
 param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Suitable)
 $life041=@($Suitable | Where-Object {$_.dead -ceq 'false' -and [int]$_.npcId -gt 0 -and $_.inventorySlots -and [int]$_.inventorySlots -lt 200} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
 if($life041.Count -ne 1){throw 'LIFE_PRIMARY041_INPUT_REQUIRED: whole cohort retained.'}
 return $life041
}
