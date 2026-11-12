/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Focused outcome006 contracts; existing public owner functions, no server/SQL bootstrap. */
public final class PhantomLearning006Checks
{
	private static final String BASELINE = "246ac12cd718e698b18e6ab2b338622b24d505f75ba492eb1cd348bca2e335cd";
	public static void main(String[] args) throws Exception
	{
		if(args.length==1&&args[0].equals("boundaries"))
		{
			final var window=new PhantomLearning001ShadowNative.Runtime.OutcomeDeaths006();
			window.observe(1,100);window.observe(2,90);
			final var first=window.snapshot(120,true);
			check(first.first()==90&&first.firstDeath(2)&&!first.firstDeath(1),"First terminal used CAS winner or included later actor death");
			window.observe(3,80);check(window.snapshot(200,false).equals(first),"Drain changed frozen terminal facts");
			final var timeout=new PhantomLearning001ShadowNative.Runtime.OutcomeDeaths006();timeout.observe(2,110);
			check(timeout.snapshot(100,true).first()==0,"Postdeadline kill promoted timeout");
			timeout.observe(2,90);check(timeout.snapshot(200,false).first()==0,"Late publication changed closed timeout");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_LEGAL_TARGET:test",true,false,false).startsWith("NO_LEGAL_TARGET"),"HEAL substituted for encounter admission");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_EXACT_NATIVE_EVIDENCE",false,false,false).startsWith("NO_LEGAL_TARGET"),"Unadmitted encounter became timeout");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_EXACT_NATIVE_EVIDENCE",true,false,false).equals("TIMEOUT"),"Admitted timeout lost");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("REQUESTED_DRAIN",true,true,false).equals("REQUESTED_DRAIN"),"STOP became positive training credit");
			System.out.println("LEARNING006_BOUNDARIES noAdmission=EXCLUDED noLegalTarget=EXCLUDED stop=EXCLUDED admittedTimeout=TIMEOUT");return;
		}
		if(args.length==2&&args[0].equals("schema")){schema(Path.of(args[1]).toRealPath());return;}
		if (args.length != 2 || !args[0].equals("baseline")) { throw new IllegalArgumentException("Expected baseline and module root"); }
		final Path module = Path.of(args[1]).toRealPath();
		final String owner = module.resolve("tools/phantom-runtime/training/TrainingOwner.ps1").toString().replace("'", "''");
		final String prefix = ". '" + owner + "'; $ErrorActionPreference='Stop'; ";
		check(run(prefix + "Get-TrainProtectedPin @{configProfile='OUTCOME006';outcome006ProtectedSha='" + BASELINE + "'}").equalsIgnoreCase(BASELINE), "R12 new exact baseline pin was not selected");
		check(run(prefix + "Get-TrainProtectedPin @{configProfile='V3'}").equalsIgnoreCase("9777ab48bc8d14bdee77d4aca8e7e650cb099f03ca33fea108d5cdb941c59dcd"), "R12 legacy V3 baseline changed");
		check(run(prefix + "try { Get-TrainProtectedPin @{configProfile='OUTCOME006';outcome006ProtectedSha=('0'*64)}; exit 0 } catch { Write-Output 'REJECTED' }").equals("REJECTED"), "R12 wrong baseline pin accepted");
		System.out.println("LEARNING006_BASELINE R12 PASS new=EXACT legacy=UNCHANGED wrongPin=REJECTED");
	}
	private static void schema(Path module) throws Exception
	{
		final Path fixture=module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/fixtures/schema-before-terminal.sql");
		final Path script=module.resolve("dist/db_installer/sql/game/phantom_tactical_v3.sql");
		final var before=StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
		final var after=StrictSqlScriptRunner.splitStatements(script,Files.readString(script));
		check(PhantomLearning004Schema.terminalDelta(before,after).size()==2,"R06 exact two terminal tables missing");
		final var changed=new ArrayList<>(after);changed.set(0,changed.getFirst().replace("MEDIUMBLOB","LONGBLOB"));
		reject(()->PhantomLearning004Schema.terminalDelta(before,changed),"Existing table schema mutation accepted");
		final var dropped=new ArrayList<>(after);dropped.add("DROP TABLE phantom_tactical_v3_events");
		reject(()->PhantomLearning004Schema.terminalDelta(before,dropped),"Extra terminal migration DDL accepted");
		System.out.println("LEARNING006_SCHEMA exactAdditive2=PASS changedOld=REJECTED extraDDL=REJECTED DB_CONNECTIONS=0");
	}
	private static void reject(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError(message);}
	private static String run(String command) throws Exception
	{
		final var process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)).redirectErrorStream(true).start();
		final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
		if (process.waitFor() != 0) { throw new AssertionError("Owner contract process failed: " + output); }
		return output;
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
}
