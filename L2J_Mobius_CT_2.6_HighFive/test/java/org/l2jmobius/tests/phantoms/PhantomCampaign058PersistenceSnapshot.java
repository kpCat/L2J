/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only saved-state witness for the exact persistent058a lane; no World claims. */
public final class PhantomCampaign058PersistenceSnapshot
{
	private PhantomCampaign058PersistenceSnapshot() { }

	public static void main(String[] args) throws Exception
	{
		if (args.length == 2 && args[0].equals("--decode061-components")) { decodeCareer061(Path.of(args[1])); return; }
		if (args.length == 3 && args[0].equals("--decode061-components") && args[2].matches("11|21|22|35|36")) { decodeCareer061(Path.of(args[1]), Integer.parseInt(args[2])); return; }
		if ((args.length != 1) || !args[0].matches("[a-z0-9-]{1,40}") || !PhantomCampaign058PersistentLane.enabled())
		{
			throw new IllegalArgumentException("Explicit persistent058a snapshot label required.");
		}
		final Path lane = PhantomCampaign058PersistentLane.LANE;
		final var settings = PhantomCampaign058PersistentLane.validateFiles(PhantomCampaign058PersistentLane.MODULE, Path.of(System.getProperty("phantom.test.config", "")), true);
		PhantomAssertions.assertTrue(Files.isRegularFile(lane.resolve("scene-setup.complete")), "Prepared scene receipt is required.");
		final Map<String, String> queries = new LinkedHashMap<>();
		queries.put("identities", "SELECT charId,account_name,char_name,level,classid,base_class,accesslevel FROM characters ORDER BY charId");
		queries.put("skills", "SELECT charId,skill_id,skill_level,class_index FROM character_skills ORDER BY charId,class_index,skill_id");
		queries.put("items", "SELECT owner_id,object_id,item_id,count,enchant_level,loc,loc_data FROM items ORDER BY object_id");
		queries.put("profiles", "SELECT profile_id,character_object_id FROM phantom_profiles ORDER BY profile_id");
		queries.put("stable-identities", "SELECT charId,account_name,char_name,base_class,accesslevel FROM characters ORDER BY charId");
		final String prepared = "(char_name='TestAdmin' OR char_name LIKE 'T058%')";
		queries.put("prepared-state", "SELECT charId,char_name,level,classid,base_class,x,y,z,exp,sp,curHp,curMp FROM characters WHERE " + prepared + " ORDER BY charId");
		queries.put("prepared-skills", "SELECT s.charId,s.skill_id,s.skill_level,s.class_index FROM character_skills s JOIN characters c ON c.charId=s.charId WHERE " + prepared + " ORDER BY s.charId,s.class_index,s.skill_id");
		queries.put("prepared-items", "SELECT i.owner_id,i.object_id,i.item_id,i.count,i.enchant_level,i.loc,i.loc_data FROM items i JOIN characters c ON c.charId=i.owner_id WHERE " + prepared + " ORDER BY i.object_id");
		final StringBuilder output = new StringBuilder("kind\tcount\tsha256\n");
		Class.forName(settings.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			PhantomAssertions.assertEquals(PhantomCampaign058PersistentLane.DATABASE, connection.getCatalog(), "Snapshot catalog differs.");
			for (var query : queries.entrySet())
			{
				final MessageDigest digest = MessageDigest.getInstance("SHA-256");
				int count = 0;
				try (var statement = connection.createStatement(); var rows = statement.executeQuery(query.getValue()))
				{
					while (rows.next())
					{
						for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
						{
							final String value = String.valueOf(rows.getObject(column));
							digest.update((value.length() + ":" + value + "|").getBytes(StandardCharsets.UTF_8));
						}
						digest.update((byte) '\n');
						count++;
					}
				}
				if (query.getKey().equals("identities")) { PhantomAssertions.assertEquals(33, count, "Prepared character count changed."); }
				if (query.getKey().equals("profiles")) { PhantomAssertions.assertEquals(32, count, "Prepared population count changed."); }
				output.append(query.getKey()).append('\t').append(count).append('\t').append(HexFormat.of().formatHex(digest.digest())).append('\n');
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT charId,char_name,level,classid,x,y,z,exp,sp,curHp,curMp FROM characters WHERE char_name='TestAdmin' OR char_name LIKE 'T058%' ORDER BY charId"))
			{
				int count = 0;
				while (rows.next())
				{
					System.out.println("SAVED_CHARACTER " + rows.getInt(1) + " " + rows.getString(2) + " level=" + rows.getInt(3) + " class=" + rows.getInt(4) + " xyz=" + rows.getInt(5) + "," + rows.getInt(6) + "," + rows.getInt(7) + " exp=" + rows.getLong(8) + " sp=" + rows.getLong(9) + " hp=" + rows.getInt(10) + " mp=" + rows.getInt(11));
					count++;
				}
				PhantomAssertions.assertEquals(7, count, "Exact named TEST scene differs.");
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT charId,char_name,level,classid,exp,sp FROM characters WHERE char_name<>'TestAdmin' AND char_name NOT LIKE 'T058%' ORDER BY charId"))
			{
				while (rows.next()) { System.out.println("OTHER_SAVED_CHARACTER " + rows.getInt(1) + " " + rows.getString(2) + " level=" + rows.getInt(3) + " class=" + rows.getInt(4) + " exp=" + rows.getLong(5) + " sp=" + rows.getLong(6)); }
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT owner_id,object_id,item_id,count,loc FROM items ORDER BY object_id DESC LIMIT 10"))
			{
				while (rows.next()) { System.out.println("LATEST_SAVED_ITEM owner=" + rows.getInt(1) + " object=" + rows.getInt(2) + " item=" + rows.getInt(3) + " count=" + rows.getLong(4) + " loc=" + rows.getString(5)); }
			}
		}
		final Path directory = lane.resolve("checkpoints");
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(args[0] + ".tsv"), output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.print(output);
		System.out.println("SNAPSHOT_READ_ONLY=true; WORLD_PRESENCE_NOT_CLAIMED; label=" + args[0]);
	}

	/** Offline decoder for the bounded SELECT receipt: no JDBC, Player, World or fixture startup. */
	private static void decodeCareer061(Path input) throws Exception
	{
		decodeCareer061(input, 35);
	}

	private static void decodeCareer061(Path input, int profile) throws Exception
	{
		final Path directory = PhantomCampaign058PersistentLane.LANE.resolve("task061").toRealPath();
		if (Files.isSymbolicLink(input) || !input.toRealPath().getParent().equals(directory) || !input.getFileName().toString().matches("career061-components-[a-z0-9-]{1,30}\\.tsv") || Files.size(input) > 262_144) { throw new IllegalArgumentException("Exact bounded TASK061 saved-component receipt required."); }
		final var lines = Files.readAllLines(input, StandardCharsets.UTF_8);
		if (lines.size() < 2 || lines.size() > 25 || !lines.getFirst().replace("\uFEFF", "").equals("component_type\trow_version\tHEX(payload)")) { throw new IllegalArgumentException("Unknown saved-component receipt shape."); }
		System.out.println("OFFLINE_DECODE_ONLY; JDBC=0; WORLD=0; WRITES=0; declaredProfile=" + profile + "; input=" + input.getFileName());
		for (String line : lines.subList(1, lines.size()))
		{
			final var columns = line.split("\t", -1);
			if (columns.length != 3 || !columns[0].matches("[a-z][a-z0-9_.-]{0,63}") || Long.parseLong(columns[1]) < 0 || columns[2].length() > 8192) { throw new IllegalArgumentException("Invalid component receipt row."); }
			final byte[] payload = HexFormat.of().parseHex(columns[2]);
			System.out.println("COMPONENT=" + columns[0] + "; row=" + columns[1] + "; bytes=" + payload.length + "; sha=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
			switch (columns[0])
			{
				case "background.state" -> printBackground061("BACKGROUND", new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(payload));
				case "background.owned-store" ->
				{
					final var intent = org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.decode(payload);
					System.out.println("OWNED_STORE target=" + intent.targetState() + "; preparedRow=" + intent.preparedRowVersion() + "; nativeEpoch=" + intent.materializedAtNanos() + "; previous=" + intent.previousState() + "; previousHash=" + intent.previousPayloadHash() + "; skillsHash=" + intent.skillsHash());
					printBackground061("BEFORE", intent.before()); printBackground061("AFTER", intent.after());
				}
				case "background.catchup" -> System.out.println("CATCHUP=" + new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().decode(payload));
				case "background.native-context" -> System.out.println("NATIVE_CONTEXT=" + org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.decode(payload));
				default -> { }
			}
		}
	}

	private static void printBackground061(String label, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState state)
	{
		System.out.println(label + " state=" + state.state() + "; identity=" + state.identity() + "; progress=" + state.progress() + "; vitals=" + state.vitals() + "; position=" + state.position() + "; autoSkills=" + state.autoGetSkills() + "; inventory=" + state.inventory());
	}
}
