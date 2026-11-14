/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain UTF-8 key=value configuration; values are never shell or Unicode escape programs. */
public final class TrainingConfig
{
	private final Map<String, String> _values;
	private final String _sha;

	private TrainingConfig(Map<String, String> values, String sha)
	{
		_values = Map.copyOf(values);
		_sha = sha;
		if (integer("schemaVersion", 1, 1, 1) != 1) { throw new IllegalArgumentException("Неизвестная версия конфигурации."); }
		seed();
		if (text("autonomous009Enabled", "false").equals("true") && !autonomousConfigured()) { throw new IllegalArgumentException("Для автономного opt-in нужна явная версия конфигурации."); }
		if (autonomousConfigured()) { validateAutonomous(); }
		if (_values.containsKey("mode") && !Set.of("SOLO", "PARTY", "BOTH").contains(text("mode", "")))
		{ throw new IllegalArgumentException("Запрошенный режим пока NOT_IMPLEMENTED."); }
		for (String mode : strings(text("modes", text("mode", "SOLO,PARTY"))))
		{
			if (!Set.of("SOLO", "PARTY").contains(mode)) { throw new IllegalArgumentException("Режим " + mode + " пока NOT_IMPLEMENTED."); }
		}
		if (strings(text("modes", text("mode", "SOLO,PARTY"))).isEmpty()) { throw new IllegalArgumentException("Не выбран режим тренировки."); }
		classes(); levels(); grades(); weaponEnchants(); npcIds();
		episodesPerBatch(); maxActiveActors(); episodeSeconds(); preparationSeconds(); scenarioLimit(); trainingThreshold(); retentionEpisodes();
		integer("eventsPerEpisode", 4, 1, 64);
		integer("cpuCount", 4, 1, 16);
		integer("maxHeapMb", 4096, 1024, 16384);
		integer("minFreeDiskMb", 4096, 1024, 1048576);
		integer("startupTimeoutSeconds", 180, 30, 900);
		integer("shutdownTimeoutSeconds", 180, 30, 900);
		integer("batchTimeoutSeconds", 3600, 60, 86400);
		integer("idleSeconds", 10, 1, 3600);
		integer("maxTrainingCohorts", 32, 1, 256);
		integer("maxStoredActors", 4096, 32, 32768);
		integer("maxStoredProfiles", 4096, 64, 32768);
		final int sql = integer("sqlPort", 33327, 33000, 65535);
		final int game = integer("gamePort", 17971, 1024, 65535);
		final int login = integer("loginPort", 19271, 1024, 65535);
		if (sql == game || sql == login || game == login) { throw new IllegalArgumentException("Порты SQL, игры и Login должны различаться."); }
		if (!"127.0.0.1".equals(text("bindAddress", "127.0.0.1"))) { throw new IllegalArgumentException("Тренировка допускает только 127.0.0.1."); }
		if (!Set.of("SHADOW", "ON").contains(text("learningMode", "SHADOW"))) { throw new IllegalArgumentException("Неизвестный режим модели."); }
		final long version = longValue("modelVersion", 0, 0, Long.MAX_VALUE);
		final String modelSha = text("modelSha", "");
		if ("ON".equals(text("learningMode", "SHADOW")) && (version == 0 || !modelSha.matches("[0-9a-f]{64}")))
		{ throw new IllegalArgumentException("Для ON необходимы принятая версия и SHA модели."); }
		if ("true".equals(text("v3Enabled", "false")))
		{
			if (integer("modelAlgorithmV3", 3, 3, 3) != TacticalModelV3.ALGORITHM || !Set.of("TACTICAL_V3", TacticalObservationV3.SCHEMA).contains(text("featureSchemaV3", "TACTICAL_V3"))
				|| !"DAMAGE,HEAL".equals(text("learningActionFamilies", "DAMAGE,HEAL")) || seed() != TacticalStoreV3.SEED)
			{ throw new IllegalArgumentException("Не совпадают версия, схема или seed тактической модели V3."); }
			integer("maxEntities", 20, 3, 20); integer("maxActions", 16, 2, 16); integer("decisionCooldownMillis", 2000, 1000, 60000);
			integer("v3CollectionRound", 0, 0, 99); integer("v3ProofRound", 0, 0, 99); integer("v3TrainContexts", 16, 16, 256);
			integer("v3TuningContexts", 8, 8, 128); integer("v3AuditContexts", 8, 8, 128);
			final String v3Mode = text("v3Mode", "SHADOW"); final long v3Version = longValue("v3ModelVersion", 0, 0, Long.MAX_VALUE);
			if (!Set.of("OFF", "SHADOW", "ON").contains(v3Mode) || "ON".equals(v3Mode) && (v3Version <= 0 || !text("v3ModelSha", "").matches("[a-f0-9]{64}")))
			{ throw new IllegalArgumentException("Для V3 ON необходим явный pin проверенной TEST-модели."); }
		}
	}

	public static TrainingConfig load(Path path) throws IOException
	{
		final byte[] bytes = Files.readAllBytes(path);
		if (bytes.length > 65536) { throw new IllegalArgumentException("Конфигурация превышает допустимый размер."); }
		final var values = new LinkedHashMap<String, String>();
		String body = new String(bytes, StandardCharsets.UTF_8);
		if (body.startsWith("\ufeff")) { body = body.substring(1); }
		for (String line : body.split("\\R"))
		{
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) { continue; }
			final int equal = line.indexOf('=');
			if (equal < 1) { throw new IllegalArgumentException("Ожидается строка key=value."); }
			final String key = line.substring(0, equal).trim();
			if (!key.matches("[A-Za-z][A-Za-z0-9]*") || values.putIfAbsent(key, line.substring(equal + 1).trim()) != null)
			{ throw new IllegalArgumentException("Повторный или неверный параметр: " + key); }
		}
		return new TrainingConfig(values, NeuralPolicy.sha256(bytes));
	}

	public String text(String key, String fallback) { return _values.getOrDefault(key, fallback); }
	public String sha256() { return _sha; }
	public int integer(String key, int fallback, int minimum, int maximum)
	{ return (int) longValue(key, fallback, minimum, maximum); }
	public long longValue(String key, long fallback, long minimum, long maximum)
	{
		final long value;
		try { value = Long.parseLong(text(key, Long.toString(fallback))); }
		catch (NumberFormatException failure) { throw new IllegalArgumentException("Неверное целое число: " + key); }
		if (value < minimum || value > maximum) { throw new IllegalArgumentException("Параметр вне допустимого диапазона: " + key); }
		return value;
	}
	public long seed() { return longValue("seed", 183, Long.MIN_VALUE, Long.MAX_VALUE); }
	public int episodesPerBatch() { return integer("episodesPerBatch", 6, 1, 256); }
	public int maxActiveActors() { return integer("maxActiveActors", 5, 2, 9); }
	public int episodeSeconds() { return integer("episodeSeconds", 120, 15, 900); }
	public int preparationSeconds() { return integer("preparationSeconds", 180, 15, 600); }
	public int scenarioLimit() { return integer("scenarioLimit", 12, 3, 64); }
	public int trainingThreshold() { return integer("trainingThreshold", 40, 32, NeuralPolicy.MAX_EXAMPLES); }
	public int retentionEpisodes() { return integer("retentionEpisodes", 10000, 100, 100000); }
	public List<Integer> classes()
	{
		if (autonomousConfigured() && text("classes", "").equals("auto")) { return org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory.classIds(); }
		final var values = integers("classes", "94", 0, 200, false);
		if (autonomousConfigured() && values.stream().anyMatch(id -> org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(id) == null))
		{ throw new IllegalArgumentException("Неизвестная профессия в автономном фильтре."); }
		return values;
	}
	public List<Integer> levels()
	{
		return autonomousConfigured() && text("levels", "").equals("auto")
			? java.util.stream.IntStream.rangeClosed(integer("autonomous009MinLevel", 1, 1, 85), integer("autonomous009MaxLevel", 85, 1, 85)).boxed().toList()
			: integers("levels", "77,78", 1, 85, false);
	}
	public boolean autonomousConfigured() { return _values.containsKey("autonomous009Version"); }
	private void validateAutonomous()
	{
		integer("autonomous009Version", 1, 1, 1);
		if (!text("autonomous009Comparator", "").equals(AutonomousTrainingV1.COMPARATOR) || !text("v3Enabled", "false").equals("true")
			|| !Set.of("OFF", "PREPARE", "COLLECT", "FIT", "EVAL").contains(text("autonomous009Phase", "OFF")))
		{ throw new IllegalArgumentException("Не совпадают автономная версия, comparator или V3 phase."); }
		for (String flag : List.of("autonomous009Enabled", "autonomous009Exploration", "autonomous009AcceptanceSmoke", "autonomous009GuiHoldBeforeNative", "autonomous009EvaluationOnly"))
		{ if (!Set.of("true", "false").contains(text(flag, flag.equals("autonomous009Exploration") ? "true" : "false"))) { throw new IllegalArgumentException("Неверный автономный флаг: " + flag); } }
		final boolean evaluationOnly = text("autonomous009EvaluationOnly", "false").equals("true");
		if (evaluationOnly && (!Set.of("OFF", "EVAL").contains(text("autonomous009Phase", "OFF")) || !text("autonomous009Exploration", "true").equals("false") || !text("autonomous009AcceptanceSmoke", "false").equals("true")))
		{ throw new IllegalArgumentException("Независимая EVAL допускает только OFF/EVAL без exploration и с отдельным acceptance budget."); }
		if (evaluationOnly && integer("autonomous009EvaluationEveryCycles", 1, 1, 100) != 1)
		{ throw new IllegalArgumentException("Независимая EVAL требует отдельный единственный evaluation cycle."); }
		if (text("autonomous009GuiHoldBeforeNative", "false").equals("true") && !text("autonomous009AcceptanceSmoke", "false").equals("true"))
		{ throw new IllegalArgumentException("Проверка GUI до native разрешена только в acceptance smoke."); }
		if (text("autonomous009Enabled", "false").equals("true"))
		{
			if (!text("configProfile", "").equals("OUTCOME006") || !text("outcome006Enabled", "false").equals("true") || !text("v3Terminal006", "false").equals("true")
				|| text("exploration007Enabled", "false").equals("true") || text("exploration008Enabled", "false").equals("true")
				|| text("autonomous009Phase", "OFF").equals("OFF") || !text("v3DatasetContextPrefix", "").equals("autonomous009|")
				|| !text("autonomous009Campaign", "").matches("autonomous009-[A-Za-z0-9_-]{1,64}") || !text("outcome006DatasetId", "").matches(ActionAdvantageV2.DATASET_PREFIX + "[A-Za-z0-9_-]{1,80}"))
			{ throw new IllegalArgumentException("Не подтверждены отдельный автономный campaign и terminal authority."); }
			if (Set.of("COLLECT", "FIT", "EVAL").contains(text("autonomous009Phase", ""))
				&& (longValue("autonomous009ModelVersion", 0, 0, Long.MAX_VALUE) == 0 || !text("autonomous009ModelSha", "").matches("[a-f0-9]{64}")))
			{ throw new IllegalArgumentException("До запуска native-фазы нужны точные версия и SHA автономной модели."); }
		}
		if (integer("autonomous009MinLevel", 1, 1, 85) > integer("autonomous009MaxLevel", 85, 1, 85)) { throw new IllegalArgumentException("Начало диапазона уровней больше конца."); }
		final int memory = integer("autonomous009MemoryBudgetMb", 8192, 1536, 131072), heap = integer("maxHeapMb", 4096, 1024, 16384);
		if (memory < heap + 512) { throw new IllegalArgumentException("Memory budget должен учитывать heap и минимум512MB native overhead."); }
		integer("autonomous009DurationMinutes", 1440, 1, 10080); integer("autonomous009CheckpointMinutes", 15, 1, 1440);
		integer("autonomous009ReplayCapacity", 128, 48, 512); integer("autonomous009EvaluationEveryCycles", 1, 1, 100);
		integer("autonomous009StagnationLimit", 3, 3, 3); integer("autonomous009NoDataLimit", 3, 1, 16);
		integer("autonomous009PriorStarts", 0, 0, 10); longValue("autonomous009PriorNativeMillis", 0, 0, 5400000);
		if (integer("episodesPerBatch", 48, 48, 256) % 12 != 0 || integer("episodesPerBatch", 48, 48, 256) > integer("autonomous009ReplayCapacity", 128, 48, 512))
		{ throw new IllegalArgumentException("Автономный batch должен вмещаться в replay и состоять из полных12-slot cells."); }
		integer("autonomous009CycleLimit", 0, 0, 1000000); longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE);
		integer("cpuBudgetPercent", 75, 1, 100);
		if (text("autonomous009AcceptanceSmoke", "false").equals("true"))
		{
			if (evaluationOnly) { integer("autonomous009SmokeMaxStarts", 3, 1, 3); integer("autonomous009SmokeMaxNativeMinutes", 45, 1, 45); }
			else { integer("autonomous009SmokeMaxStarts", 6, 6, 10); integer("autonomous009SmokeMaxNativeMinutes", 90, 90, 90); }
		}
		autonomousClassWeights(); autonomousFamilies();
	}
	public Map<Integer, Integer> autonomousClassWeights()
	{
		final var result = new LinkedHashMap<Integer, Integer>();
		for (int id : org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory.classIds()) { result.put(id, 1); }
		final String setting = text("autonomous009ClassWeights", "auto");
		if (!setting.equals("auto"))
		{
			final var seen = new java.util.HashSet<Integer>();
			for (String entry : strings(setting))
			{
				final var parts = entry.split(":", -1);
				try
				{
					if (parts.length != 2) { throw new NumberFormatException(); }
					final int id = Integer.parseInt(parts[0]), weight = Integer.parseInt(parts[1]);
					if (!result.containsKey(id) || !seen.add(id) || weight < 1 || weight > 1000) { throw new NumberFormatException(); }
					result.put(id, weight);
				}
				catch (NumberFormatException invalid) { throw new IllegalArgumentException("Неверное распределение классов: " + entry); }
			}
		}
		return Map.copyOf(result);
	}
	public List<LegalActionV3.Family> autonomousFamilies()
	{
		final var result = new ArrayList<LegalActionV3.Family>();
		for (String name : strings(text("autonomous009Families", "DAMAGE,HEAL")))
		{
			final LegalActionV3.Family family;
			try { family = LegalActionV3.Family.valueOf(name); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Неизвестное семейство автономных действий."); }
			if (!Set.of(LegalActionV3.Family.DAMAGE, LegalActionV3.Family.HEAL).contains(family) || result.contains(family)) { throw new IllegalArgumentException("Неподдерживаемое или повторное семейство автономных действий."); }
			result.add(family);
		}
		if (result.isEmpty()) { throw new IllegalArgumentException("Не выбраны поддерживаемые action families."); }
		return List.copyOf(result);
	}
	public List<Integer> npcIds() { return integers("npcIds", "", 1, 1000000, true); }
	public List<Integer> weaponEnchants() { return integers("weaponEnchants", "0,2,4", 0, 25, false); }
	public List<String> grades()
	{
		final var grades = strings(text("grades", "A,S"));
		if (grades.isEmpty() || !Set.of("NONE", "D", "C", "B", "A", "S").containsAll(grades))
		{ throw new IllegalArgumentException("Неверный список рангов экипировки."); }
		return grades;
	}
	private List<Integer> integers(String key, String fallback, int minimum, int maximum, boolean empty)
	{
		final List<Integer> values = new ArrayList<>();
		for (String item : strings(text(key, fallback)))
		{
			try
			{
				final int value = Integer.parseInt(item);
				if (value < minimum || value > maximum || values.contains(value)) { throw new NumberFormatException(); }
				values.add(value);
			}
			catch (NumberFormatException failure) { throw new IllegalArgumentException("Неверный список: " + key); }
		}
		if ((!empty && values.isEmpty()) || values.size() > 64) { throw new IllegalArgumentException("Недопустимый размер списка: " + key); }
		return List.copyOf(values);
	}
	private static List<String> strings(String value)
	{
		if (value.isBlank()) { return List.of(); }
		final var result = java.util.Arrays.stream(value.split(",", -1)).map(String::trim).toList();
		if (result.stream().anyMatch(String::isEmpty)) { throw new IllegalArgumentException("Пустое значение в списке."); }
		return result;
	}
}
