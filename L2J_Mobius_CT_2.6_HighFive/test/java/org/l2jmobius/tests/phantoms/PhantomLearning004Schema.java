/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/** Exact additive upgrade of this task's previously attested own TEST schema, never an arbitrary stale manifest repair. */
final class PhantomLearning004Schema
{
	private static final String SCRIPT = "dist/db_installer/sql/game/phantom_tactical_v3.sql";
	private static final String OLD_SCRIPT = "771923AC49ABAA60BA08D0149F7A9842A6E3AA174D2EED3CFD336FC791ADAFE6";
	private static final String OLD_ALL = "DB97B49B23193D4A3A75528F6378D12FEDEC0600D9E01276FC3E872FF4A5B0AA";
	private static final Set<String> ADDED = Set.of("failures","event_episodes","context_replay");
	private static final String BEFORE_TERMINAL = "79B176A2F596D9ED77258315AC203867B2D1127B24DEE53D6FB0B4482B299521";
	private static final String BEFORE_ADVANTAGE="71F7ACE0E54399B510F021A33F49BF5A5FC10DE853E47E5DE2299FC0FF819833";
	private static List<String> beforeAdvantage(Path module) throws Exception
	{
		String text=Files.readString(module.resolve(SCRIPT));int at=text.indexOf("CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_advantage_snapshots`");
		if(at<0){throw new IllegalArgumentException("007 additive snapshot table missing");}
		String prefix=text.substring(0,at);boolean verified=false;
		for(int trim=0;trim<3;trim++)
		{
			String lf=prefix.replace("\r\n","\n");
			if(sha(lf.getBytes(StandardCharsets.UTF_8)).equals(BEFORE_ADVANTAGE)||sha(lf.replace("\n","\r\n").getBytes(StandardCharsets.UTF_8)).equals(BEFORE_ADVANTAGE)){verified=true;break;}
			if(prefix.endsWith("\r\n")){prefix=prefix.substring(0,prefix.length()-2);}else if(prefix.endsWith("\n")){prefix=prefix.substring(0,prefix.length()-1);}
		}
		if(!verified){throw new IllegalArgumentException("007 changed historical SQL prefix");}
		return StrictSqlScriptRunner.splitStatements(module.resolve(SCRIPT),prefix);
	}
	static List<String> advantageDelta(List<String> before,List<String> after)
	{
		var remaining=new ArrayList<>(after);for(String sql:before){if(!remaining.remove(sql)){throw new IllegalArgumentException("007 historical SQL changed");}}
		var names=new HashSet<String>();
		for(String sql:remaining)
		{
			var match=java.util.regex.Pattern.compile("(?s)^CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_(advantage_snapshots|advantage_applications)` \\(.*").matcher(sql.strip());
			if(!match.matches()||!names.add(match.group(1))||sql.matches("(?is).*\\b(?:ALTER|DROP|DELETE|UPDATE|INSERT|DATABASE|USE|GRANT|USER)\\b.*")){throw new IllegalArgumentException("007 must add only exact advantage tables");}
		}
		if(!names.equals(Set.of("advantage_snapshots","advantage_applications"))){throw new IllegalArgumentException("007 additive table set changed");}
		return List.copyOf(remaining);
	}
	static List<String> terminalDelta(List<String> before,List<String> after)
	{
		final var remaining=new ArrayList<>(after);
		for(String sql:before){if(!remaining.remove(sql)){throw new IllegalArgumentException("006 changed a historical schema statement");}}
		final var names=new HashSet<String>();
		for(String sql:remaining)
		{
			final var match=java.util.regex.Pattern.compile("(?s)^CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_(terminal_rewards|terminal_applications)` \\(.*").matcher(sql.strip());
			if(!match.matches()||!names.add(match.group(1))||sql.matches("(?is).*\\b(?:ALTER|DROP|DELETE|UPDATE|INSERT|DATABASE|USE|GRANT|USER)\\b.*")){throw new IllegalArgumentException("006 migration is not the exact additive allowlist");}
		}
		if(!names.equals(Set.of("terminal_rewards","terminal_applications"))){throw new IllegalArgumentException("006 terminal table set changed");}
		return List.copyOf(remaining);
	}
	private static Path terminalFixture(Path module)throws Exception
	{
		final Path fixture=module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/fixtures/schema-before-terminal.sql");
		if(!fixture.equals(fixture.toRealPath())||Files.isSymbolicLink(fixture)||!sha(Files.readAllBytes(fixture)).equals(BEFORE_TERMINAL)){throw new IllegalStateException("TRAIN002_006_SCHEMA_FIXTURE");}
		return fixture;
	}
	static List<String> delta(List<String> before, List<String> after)
	{
		final var remaining = new ArrayList<>(after);
		for (String sql : before) { if (!remaining.remove(sql)) { throw new IllegalArgumentException("004 migration changed an existing statement"); } }
		final var names = new HashSet<String>();
		for (String sql : remaining)
		{
			final var match = java.util.regex.Pattern.compile("(?s)^CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_(failures|event_episodes|context_replay)` \\(.*").matcher(sql.strip());
			if (!match.matches() || !names.add(match.group(1)) || sql.matches("(?is).*\\b(?:ALTER|DROP|DELETE|UPDATE|INSERT|DATABASE|USE|GRANT|USER)\\b.*"))
			{ throw new IllegalArgumentException("004 migration is not the exact additive table allowlist"); }
		}
		if (!names.equals(ADDED)) { throw new IllegalArgumentException("004 migration table set changed"); }
		return List.copyOf(remaining);
	}
	static void upgrade(Path root, Path module, Path config, int port) throws Exception
	{
		final var inventory = PhantomTestSchemaManifest.inventory(module);
		final var current = PhantomTestSchemaManifest.fromScripts(inventory);
		final Path local = root.resolve("sql/schema-manifest.properties");
		final var localState = PhantomTestSchemaManifest.read(local);
		if (localState.equals(current)) { return; } // Canonical probe below still validates DB identity and manifest.
		if("true".equals(PhantomTrainingSql.read(root.resolve("TRAIN_CONFIG.ini")).getProperty("exploration007Enabled")))
		{upgradeAdvantage(root,module,config,port,inventory,current,localState);return;}
		if("OUTCOME006".equals(PhantomTrainingSql.read(root.resolve("TRAIN_CONFIG.ini")).getProperty("configProfile")))
		{upgradeTerminal(root,module,config,port,inventory,current,localState);return;}
		final Path fixture = module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-004/fixtures/schema-before-failure-ledger.sql");
		if (!fixture.equals(fixture.toRealPath()) || !sha(Files.readAllBytes(fixture)).equals(OLD_SCRIPT)) { throw new IllegalStateException("TRAIN002_004_MIGRATION_FIXTURE"); }
		final var oldStatements = StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
		final var oldInventory = new ArrayList<StrictSqlScriptRunner.ScriptInfo>();
		List<String> additions = null;
		for (var script : inventory)
		{
			if (script.relativePath().equals(SCRIPT))
			{
				additions = delta(oldStatements,script.statements());
				oldInventory.add(new StrictSqlScriptRunner.ScriptInfo(fixture,SCRIPT,OLD_SCRIPT,oldStatements));
			}
			else { oldInventory.add(script); }
		}
		final var previous = PhantomTestSchemaManifest.fromScripts(oldInventory);
		if (additions == null || !previous.aggregateSha256().equals(OLD_ALL) || !localState.equals(previous))
		{ throw new IllegalStateException("TRAIN002_004_MIGRATION_UNRECOGNIZED_SCOPE"); }
		final var settings = PhantomTrainingSql.read(config);
		final String endpoint = "jdbc:mysql://127.0.0.1:" + port + "/phantom_learning001_train002";
		if (!endpoint.equals(settings.getProperty("URL")) || !"learning001".equals(settings.getProperty("Login")))
		{ throw new IllegalStateException("TRAIN002_004_MIGRATION_ENDPOINT"); }
		try (var connection = DriverManager.getConnection(endpoint,"learning001",settings.getProperty("Password")))
		{
			PhantomTrainingSql.requireIdentity(connection,root,port,"phantom_learning001_train002","learning001@127.0.0.1");
			boolean alreadyPublished = false;
			try { PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,previous); }
			catch (PhantomTestConfigurationException changed)
			{ PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current); alreadyPublished = true; }
			final String before = retainedSha(connection);
			if (!alreadyPublished)
			{
				try (var statement = connection.createStatement()) { for (String sql : additions) { statement.executeUpdate(sql); } }
				if (!before.equals(retainedSha(connection))) { throw new IllegalStateException("TRAIN002_004_MIGRATION_RETAINED_DATA"); }
				PhantomTestSchemaManifest.writeDatabaseMetadata(connection,current);
			}
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);
			final byte[] prior = Files.readAllBytes(local);
			final Path backup = Path.of(System.getProperty("java.io.tmpdir")).resolve("phantom-learning004-manifest-" + sha(prior));
			if (Files.exists(backup)) { if (!Arrays.equals(prior,Files.readAllBytes(backup))) { throw new IllegalStateException("TRAIN002_004_MIGRATION_BACKUP"); } }
			else { Files.write(backup,prior,StandardOpenOption.CREATE_NEW); }
			PhantomTestSchemaManifest.writeAtomic(local,current);
			System.out.println("LEARNING004_SCHEMA_UPGRADE tables=3 retainedSha=" + before + " previous=" + OLD_ALL + " current=" + current.aggregateSha256() + " recoveredPublication=" + alreadyPublished);
		}
	}
	private static void upgradeTerminal(Path root,Path module,Path config,int port,List<StrictSqlScriptRunner.ScriptInfo> inventory,
		PhantomTestSchemaManifest.Snapshot current,PhantomTestSchemaManifest.Snapshot localState)throws Exception
	{
		final Path fixture=terminalFixture(module);
		final var before=StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
		final var oldInventory=new ArrayList<StrictSqlScriptRunner.ScriptInfo>();List<String> additions=null;
		for(var script:inventory)
		{
			if(script.relativePath().equals(SCRIPT)){additions=terminalDelta(before,script.statements());oldInventory.add(new StrictSqlScriptRunner.ScriptInfo(fixture,SCRIPT,BEFORE_TERMINAL,before));}
			else{oldInventory.add(script);}
		}
		final var previous=PhantomTestSchemaManifest.fromScripts(oldInventory);
		if(additions==null||!localState.equals(previous)){throw new IllegalStateException("TRAIN002_006_SCHEMA_UNRECOGNIZED_SCOPE");}
		final var settings=PhantomTrainingSql.read(config);final String endpoint="jdbc:mysql://127.0.0.1:"+port+"/phantom_learning001_train002";
		if(!endpoint.equals(settings.getProperty("URL"))||!"learning001".equals(settings.getProperty("Login"))){throw new IllegalStateException("TRAIN002_006_SCHEMA_ENDPOINT");}
		try(var connection=DriverManager.getConnection(endpoint,"learning001",settings.getProperty("Password")))
		{
			PhantomTrainingSql.requireIdentity(connection,root,port,"phantom_learning001_train002","learning001@127.0.0.1");
			boolean published=false;
			try{PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,previous);}
			catch(PhantomTestConfigurationException mismatch){PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);published=true;}
			final var retained=List.of("events","optimizers","episodes","failures","event_episodes","context_replay","splits","runs","audit_used","models");
			final String retainedBefore=retainedSha(connection,retained);
			if(!published)
			{
				try(var statement=connection.createStatement()){for(String sql:additions){statement.executeUpdate(sql);}}
				if(!retainedBefore.equals(retainedSha(connection,retained))){throw new IllegalStateException("TRAIN002_006_SCHEMA_RETAINED_DATA_CHANGED");}
				PhantomTestSchemaManifest.writeDatabaseMetadata(connection,current);
			}
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);
			final Path local=root.resolve("sql/schema-manifest.properties");final byte[] prior=Files.readAllBytes(local);
			final Path backup=root.resolve("archives/schema-before-terminal-"+sha(prior)+".properties");
			if(Files.exists(backup)){if(!Arrays.equals(prior,Files.readAllBytes(backup))){throw new IllegalStateException("TRAIN002_006_SCHEMA_BACKUP_CHANGED");}}
			else{Files.write(backup,prior,StandardOpenOption.CREATE_NEW);}
			PhantomTestSchemaManifest.writeAtomic(local,current);
			System.out.println("LEARNING006_SCHEMA_UPGRADE exactAdditive=2 retainedTenTablesSha="+retainedBefore+" previous="+previous.aggregateSha256()+" current="+current.aggregateSha256()+" recovered="+published);
		}
	}
	private static void upgradeAdvantage(Path root,Path module,Path config,int port,List<StrictSqlScriptRunner.ScriptInfo> inventory,
		PhantomTestSchemaManifest.Snapshot current,PhantomTestSchemaManifest.Snapshot localState) throws Exception
	{
		var localConfig=PhantomTrainingSql.read(root.resolve("TRAIN_CONFIG.ini"));
		if(!"learning007-causal-v1".equals(localConfig.getProperty("exploration007Campaign"))||!"learning007-advantage-v1".equals(localConfig.getProperty("exploration007Dataset")))
		{throw new IllegalStateException("TRAIN002_007_EXACT_CAMPAIGN_REQUIRED");}
		var priorStatements=beforeAdvantage(module);var previousInventory=new ArrayList<StrictSqlScriptRunner.ScriptInfo>();List<String> additions=null;
		for(var script:inventory)
		{
			if(script.relativePath().equals(SCRIPT)){additions=advantageDelta(priorStatements,script.statements());previousInventory.add(new StrictSqlScriptRunner.ScriptInfo(module.resolve(SCRIPT),SCRIPT,BEFORE_ADVANTAGE,priorStatements));}
			else{previousInventory.add(script);}
		}
		var previous=PhantomTestSchemaManifest.fromScripts(previousInventory);
		if(additions==null||!previous.aggregateSha256().equals("AD67809BC80755A4FB5B7E52844F63D50534F329D82FED1424F217F6CB6427BD")||!previous.equals(localState))
		{throw new IllegalStateException("TRAIN002_007_UNRECOGNIZED_PREVIOUS_SCHEMA");}
		var settings=PhantomTrainingSql.read(config);String endpoint="jdbc:mysql://127.0.0.1:"+port+"/phantom_learning001_train002";
		if(!endpoint.equals(settings.getProperty("URL"))||!"learning001".equals(settings.getProperty("Login"))){throw new IllegalStateException("TRAIN002_007_ENDPOINT");}
		try(var connection=DriverManager.getConnection(endpoint,"learning001",settings.getProperty("Password")))
		{
			PhantomTrainingSql.requireIdentity(connection,root,port,"phantom_learning001_train002","learning001@127.0.0.1");boolean recovered=false;
			try{PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,previous);}catch(PhantomTestConfigurationException changed){PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);recovered=true;}
			var retained=List.of("events","optimizers","episodes","failures","event_episodes","context_replay","splits","runs","audit_used","models","terminal_rewards","terminal_applications");
			String before=retainedSha(connection,retained);
			if(!recovered)
			{
				try(var statement=connection.createStatement()){for(String sql:additions){statement.executeUpdate(sql);}}
				if(!before.equals(retainedSha(connection,retained))){throw new IllegalStateException("TRAIN002_007_RETAINED_HISTORY_CHANGED");}
				PhantomTestSchemaManifest.writeDatabaseMetadata(connection,current);
			}
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);
			byte[] bytes=Files.readAllBytes(root.resolve("sql/schema-manifest.properties"));String pin=sha(bytes);
			for(Path backup:List.of(root.resolve("archives/schema-before-advantage-"+pin+".properties"),Path.of(System.getProperty("java.io.tmpdir")).resolve("phantom-learning007-manifest-"+pin+".properties")))
			{if(Files.exists(backup)){if(!Arrays.equals(bytes,Files.readAllBytes(backup))){throw new IllegalStateException("TRAIN002_007_MANIFEST_BACKUP_CHANGED");}}else{Files.write(backup,bytes,StandardOpenOption.CREATE_NEW);}if(!sha(Files.readAllBytes(backup)).equals(pin)){throw new IllegalStateException("TRAIN002_007_BACKUP_SHA");}}
			PhantomTestSchemaManifest.writeAtomic(root.resolve("sql/schema-manifest.properties"),current);
			System.out.println("LEARNING007_SCHEMA_UPGRADE exactAdditive=2 retainedTwelveTablesSha="+before+" previous="+previous.aggregateSha256()+" current="+current.aggregateSha256()+" recovered="+recovered);
		}
	}
	private static String retainedSha(Connection connection) throws Exception
	{return retainedSha(connection,List.of("events","optimizers","episodes","splits","runs","audit_used","models"));}
	private static String retainedSha(Connection connection,List<String> tables) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		for (String table : tables)
		{
			digest.update(table.getBytes(StandardCharsets.US_ASCII));
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM phantom_tactical_v3_" + table + " ORDER BY 1"))
			{
				while (rows.next())
				{
					for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++)
					{
						final byte[] value = rows.getBytes(i); digest.update(java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
						if (value != null) { digest.update(value); }
					}
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}
	private static String sha(byte[] bytes) throws Exception { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(args[0]).toRealPath();
		final var before = StrictSqlScriptRunner.splitStatements(module.resolve(SCRIPT),Files.readString(module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-004/fixtures/schema-before-failure-ledger.sql")));
		var after = StrictSqlScriptRunner.splitStatements(module.resolve(SCRIPT),Files.readString(module.resolve(SCRIPT)));
		if(after.stream().anyMatch(sql->sql.contains("`phantom_tactical_v3_advantage_snapshots`")))
		{
			var original006=beforeAdvantage(module);if(advantageDelta(original006,after).size()!=2){throw new AssertionError("Missing007 additive tables");}
			var mutated=new ArrayList<>(after);mutated.set(0,mutated.getFirst().replace("MEDIUMBLOB","LONGBLOB"));reject(()->advantageDelta(original006,mutated));after=original006;
			System.out.println("LEARNING007_SCHEMA_CONTRACT exactAdditive=2 historicalPrefixSha=PASS changedOld=REJECTED DB_CONNECTIONS=0");
		}
		if(after.stream().anyMatch(sql->sql.contains("`phantom_tactical_v3_terminal_rewards`")))
		{
			final Path fixture=terminalFixture(module);final var original004=StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
			if(terminalDelta(original004,after).size()!=2){throw new AssertionError("Missing006 additive tables");}after=original004;
		}
		if (delta(before,after).size() != 3) { throw new AssertionError("Missing additive migration"); }
		final var changed = new ArrayList<>(after); changed.set(0,changed.getFirst().replace("MEDIUMBLOB","LONGBLOB")); reject(() -> delta(before,changed));
		final var dropped = new ArrayList<>(after); dropped.add("DROP TABLE phantom_tactical_v3_events"); reject(() -> delta(before,dropped));
		System.out.println("LEARNING004_SCHEMA_CONTRACT exactAdditive=PASS changedOld=REJECTED extraDDL=REJECTED DB_CONNECTIONS=0");
	}
	private static void reject(Runnable work) { try { work.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Unsafe migration accepted"); }
}
