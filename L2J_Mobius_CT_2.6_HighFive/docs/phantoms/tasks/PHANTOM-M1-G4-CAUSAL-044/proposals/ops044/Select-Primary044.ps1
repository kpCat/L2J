function Select-Primary044 {
 param([Parameter(Mandatory)][object[]]$Suitable,[string[]]$PreviousIds=@())
 # These are captured native capability facts, never future damage/reward results.
 $casters044=@($Suitable | Where-Object {[double]$_.liveResourceCost -gt 0 -and [int]$_.lastCastSkillId -gt 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 $melee044=@($Suitable | Where-Object {[double]$_.liveResourceCost -eq 0 -and [int]$_.lastCastSkillId -eq 0} | Sort-Object @{Expression={$_.profileId -in $PreviousIds}},@{Expression={[long]$_.profileId}})
 if($casters044.Count -eq 0 -or $melee044.Count -eq 0){throw 'PRIMARY_NATIVE_MELEE_CASTER_PRECONDITION: whole cohort retained.'}
 return @($melee044[0],$casters044[0])
}
function Select-CurrentLifeInputs044 {
 param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Suitable,
       [Parameter(Mandatory)][object[]]$FarmInputs,[bool]$RequireCurrent=$true)
 if(-not $RequireCurrent){return $Suitable}
 $current=@($FarmInputs | Where-Object {$_.readinessComplete -ceq 'true' -and [long]$_.committedCursorMinute -eq [long]$_.requestedHorizonMinute} | ForEach-Object {$_.profileId})
 return @($Suitable | Where-Object {$_.profileId -cin $current})
}
function Select-LifePrimary044 {
 param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Suitable,[switch]$CurrentReadyOrdinary)
 $life044=@($Suitable | Where-Object {$_.dead -ceq 'false' -and [int]$_.npcId -gt 0 -and $_.inventorySlots -and [int]$_.inventorySlots -lt 200 -and ($CurrentReadyOrdinary -or [double]$_.liveResourceCost -gt 0)} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
 if($life044.Count -ne 1){throw 'LIFE_PRIMARY044_INPUT_REQUIRED: whole cohort retained.'}
 return $life044
}
function Test-LifeBoundary044 {
 param([Parameter(Mandatory)][object]$Actor,[Parameter(Mandatory)][string]$Epoch,[int]$OtherNativeHumans=0)
 return $OtherNativeHumans -eq 0 -and $Actor.worldPresent -ceq 'true' -and $Actor.dead -ceq 'false' -and
  $Actor.materializedAtNanos -ceq $Epoch -and $Actor.attacking -ceq 'false' -and $Actor.casting -ceq 'false' -and $Actor.moving -ceq 'false' -and
  $Actor.PSObject.Properties['current.nativeOwnerOutstanding'].Value -ceq '0' -and $Actor.incomingTargetCount044 -ceq '0' -and
  $Actor.operatorAdmission -match 'retentionPins=\[' -and $Actor.operatorAdmission -notmatch 'retentionPins=\[[^\]]*REAL_PARTY'
}
