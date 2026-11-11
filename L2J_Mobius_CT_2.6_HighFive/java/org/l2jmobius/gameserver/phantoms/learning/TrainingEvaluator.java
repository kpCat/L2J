/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.l2jmobius.gameserver.phantoms.learning.TrainingStore.Sample;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore.Snapshot;

/** Explicit offline TEST training. Audit never chooses a seed or an epoch. */
public final class TrainingEvaluator
{
	private TrainingEvaluator() { }

	public static void main(String[] args)
	{
		try
		{
			if (args.length != 2 || !List.of("train", "status", "report", "export", "probe").contains(args[0]))
			{ throw new IllegalArgumentException("Команда: train|status|report|export|probe <собственная установка>."); }
			final var v3Config = TrainingConfig.load(Path.of(args[1]).resolve("TRAIN_CONFIG.ini"));
			if ("true".equals(v3Config.text("v3Enabled", "false")) && List.of("train", "report", "export").contains(args[0]))
			{
				TacticalTrainerV3.main(new String[] { args[1], "train".equals(args[0]) ? "evaluate" : args[0] });
				return;
			}
			final var store = new TrainingStore(Path.of(args[1]));
			store.initialize();
			switch (args[0])
			{
				case "train" -> train(store);
				case "export" -> export(store);
				case "report" -> report(store);
				case "status", "probe" -> status(store);
				default -> throw new IllegalArgumentException("Неизвестная команда тренажёра.");
			}
		}
		catch (Exception failure)
		{
			final String reason = failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
				? failure.getMessage() : "Не удалось подтвердить или сохранить собственный SQL-журнал. Проверьте статус владельца и доступность SQL.";
			System.err.println("Операция тренажёра не завершена: " + reason);
			System.exit(2);
		}
	}

	/** Both comparisons are on the same untouched per-scope AUDIT, in original reward units. */
	public static boolean qualityPasses(double modelMse, double constantMse, Double priorMse, double relativeImprovement)
	{
		return Double.isFinite(modelMse) && modelMse >= 0 && Double.isFinite(constantMse) && constantMse > 0
			&& Double.isFinite(relativeImprovement) && relativeImprovement >= 0 && relativeImprovement < 1
			&& modelMse < constantMse * (1 - relativeImprovement)
			&& (priorMse == null || (Double.isFinite(priorMse) && priorMse > 0 && modelMse < priorMse * (1 - relativeImprovement)));
	}

	private record Metrics(String json, boolean pass, String reason) { }

	private static void train(TrainingStore store) throws SQLException, IOException
	{
		store.requireStopped();
		final var preparation = store.freeze();
		if (preparation.snapshot() == null)
		{
			evaluation(store, "COLLECTING", preparation.reason(), "", "", "null");
			status(store);
			return;
		}
		final Snapshot snapshot = preparation.snapshot();
		boolean storedDraft = false;
		try
		{
			final long started = System.nanoTime();
			final var fit = NeuralPolicy.trainResidual(snapshot.examples("TRAIN"), snapshot.examples("TUNING"), snapshot.seed());
			final var audit = NeuralPolicy.audit(fit, snapshot.examples("AUDIT"), snapshot.seed());
			final var prior = snapshot.priorModel() == 0 ? null : store.learning().accepted(snapshot.priorModel());
			if (snapshot.priorModel() != 0 && (prior == null || !snapshot.priorSha().equals(prior.model().sha256())))
			{ throw new IllegalStateException("Предыдущий принятый checkpoint не прошёл холодную проверку."); }
			final Metrics metrics = metrics(snapshot, fit, audit, prior == null ? null : prior.model(), (System.nanoTime() - started) / 1000000,
				store.config().integer("minimumImprovementBasisPoints", 100, 0, 5000) / 10000.0);
			store.draft(snapshot, audit, metrics.json());
			storedDraft = true;
			String state = "DRAFT";
			String reason = metrics.reason();
			if (metrics.pass())
			{
				try
				{
					final var checkpoint = store.learning().save(audit, snapshot.examples("TRAIN").size(), snapshot.examples("AUDIT").size(), snapshot.datasetHash(), "NATIVE");
					if (store.accept(snapshot, checkpoint))
					{ state = "ACCEPTED"; reason = "Независимая проверка пройдена; checkpoint принят только в собственной TEST-установке."; }
					else { reason = "Активный checkpoint изменился; кандидат сохранён DRAFT без применения."; }
				}
				catch (IllegalArgumentException failure)
				{
					if (!"Checkpoint did not meet validation threshold".equals(failure.getMessage())) { throw failure; }
					reason = "Кандидат сохранён DRAFT: дополнительный штатный порог LearningStore не пройден.";
				}
			}
			if (!"ACCEPTED".equals(state)) { store.decline(snapshot, reason, false); }
			evaluation(store, state, reason, snapshot.id(), audit.model().sha256(), metrics.json());
			status(store);
		}
		catch (SQLException | RuntimeException | IOException failure)
		{
			store.decline(snapshot, storedDraft ? "Кандидат сохранён; операция прервана до подтверждения применения." : "Обучение прервано; audit этой попытки не используется повторно.", !storedDraft);
			throw failure;
		}
	}

	private static Metrics metrics(Snapshot snapshot, NeuralPolicy.Training fit, NeuralPolicy.Training audit, NeuralPolicy prior, long duration, double improvement)
	{
		boolean pass = audit.step() > 0;
		final var reasons = new ArrayList<String>();
		if (!pass) { reasons.add("TUNING не выбрал обученную эпоху"); }
		final var scopes = new StringBuilder("{");
		for (String scope : List.of("SOLO", "PARTY"))
		{
			final List<Sample> training = snapshot.samples().stream().filter(row -> "TRAIN".equals(row.split()) && scope.equals(row.scope())).toList();
			final List<Sample> heldOut = snapshot.samples().stream().filter(row -> "AUDIT".equals(row.split()) && scope.equals(row.scope())).toList();
			final double mean = training.stream().mapToDouble(Sample::reward).average().orElseThrow();
			final double constant = loss(heldOut, _ -> mean);
			final double candidate = loss(heldOut, vector -> audit.model().score(vector));
			final Double previous = prior == null ? null : loss(heldOut, prior::score);
			final boolean good = qualityPasses(candidate, constant, previous, improvement);
			pass &= good;
			if (!good) { reasons.add("качество " + scope + " не превосходит независимый constant/prior"); }
			if (scopes.length() > 1) { scopes.append(','); }
			scopes.append(TrainingStore.quote(scope)).append(":{\"rows\":").append(heldOut.size()).append(",\"actors\":").append(heldOut.stream().map(Sample::actor).distinct().count())
				.append(",\"trainMean\":").append(mean).append(",\"constantMse\":").append(constant).append(",\"modelMse\":").append(candidate)
				.append(",\"priorMse\":").append(previous == null ? "null" : previous).append(",\"pass\":").append(good).append('}');
		}
		scopes.append('}');
		final String json = "{\"schemaVersion\":1,\"datasetHash\":" + TrainingStore.quote(snapshot.datasetHash()) + ",\"seed\":" + snapshot.seed()
			+ ",\"train\":" + snapshot.examples("TRAIN").size() + ",\"tuning\":" + snapshot.examples("TUNING").size() + ",\"audit\":" + snapshot.examples("AUDIT").size()
			+ ",\"auditFresh\":true,\"step\":" + audit.step() + ",\"tuningMse\":" + fit.loss() + ",\"auditMse\":" + audit.loss() + ",\"randomInitialMse\":" + audit.initialLoss()
			+ ",\"minimumRelativeImprovement\":" + improvement + ",\"trainingMillis\":" + duration + ",\"scopes\":" + scopes
			+ ",\"unsafeActionRate\":null,\"nativeActionReorderingRate\":null,\"unknownMetricsReason\":\"Нужна отдельная проверка холодного native-сеанса; offline MSE её не заменяет.\"}";
		return new Metrics(json, pass, pass ? "Независимое качество подтверждено." : "Кандидат сохранён DRAFT: " + String.join("; ", reasons) + ".");
	}

	private static double loss(List<Sample> rows, java.util.function.ToDoubleFunction<float[]> predict)
	{
		return rows.stream().mapToDouble(row ->
		{
			final double error = predict.applyAsDouble(LearningFeatures.decode(row.features())) - row.reward();
			return error * error;
		}).average().orElseThrow();
	}

	private static LearningStore.Checkpoint active(TrainingStore store) throws SQLException
	{
		final var current = store.active();
		if (current.model() == 0) { return null; }
		final var checkpoint = store.learning().accepted(current.model());
		if (checkpoint == null || !current.sha().equals(checkpoint.model().sha256()))
		{ throw new IllegalStateException("Активная TEST-модель не прошла проверку формата, каталога или SHA."); }
		return checkpoint;
	}

	private static void evaluation(TrainingStore store, String state, String reason, String attempt, String candidateSha, String metrics) throws SQLException, IOException
	{
		final var current = active(store);
		final String json = "{\"schemaVersion\":1,\"updatedAt\":" + TrainingStore.quote(Instant.now().toString()) + ",\"state\":" + TrainingStore.quote(state)
			+ ",\"modelVersion\":" + (current == null ? 0 : current.id()) + ",\"modelSha\":" + TrainingStore.quote(current == null ? "" : current.model().sha256())
			+ ",\"reason\":" + TrainingStore.quote(reason) + ",\"candidateAttempt\":" + TrainingStore.quote(attempt) + ",\"candidateSha\":" + TrainingStore.quote(candidateSha)
			+ ",\"metrics\":" + metrics + "}\n";
		write(store.installation().resolve("evaluation.json"), json);
		System.out.println(reason);
	}

	private static void status(TrainingStore store) throws SQLException, IOException
	{
		final var counts = store.counts();
		final var checkpoint = active(store);
		final var coverage = store.coverage();
		final String json = "{\"schemaVersion\":1,\"updatedAt\":" + TrainingStore.quote(Instant.now().toString()) + ",\"committed\":" + counts.committed()
			+ ",\"incomplete\":" + counts.incomplete() + ",\"events\":" + counts.events() + ",\"attempts\":" + counts.attempts() + ",\"accepted\":" + counts.accepted()
			+ ",\"nextOrdinal\":" + counts.nextOrdinal() + ",\"activeModelVersion\":" + (checkpoint == null ? 0 : checkpoint.id())
			+ ",\"activeModelSha\":" + TrainingStore.quote(checkpoint == null ? "" : checkpoint.model().sha256()) + ",\"schema\":" + TrainingStore.quote(LearningFeatures.SCHEMA)
			+ ",\"catalog\":" + TrainingStore.quote(store.catalog()) + ",\"counterWindow\":\"LIFETIME\",\"coverage\":" + coverage.json()
			+ ",\"fullNativeDecisionLatency\":null,\"unsupportedModes\":[\"PVP\",\"PK\",\"RAID\",\"SIEGE\",\"TW\",\"OLYMPIAD\",\"SUMMON\",\"HEAL\",\"CONTROL\",\"SPOIL/SWEEP\",\"MOVE\"]}\n";
		write(store.installation().resolve("status.json"), json);
		System.out.printf(Locale.ROOT, "Подтверждено эпизодов: %d; неполных: %d; точных событий: %d; попыток обучения: %d; принятых моделей: %d.%n",
			counts.committed(), counts.incomplete(), counts.events(), counts.attempts(), counts.accepted());
	}

	private static void report(TrainingStore store) throws SQLException, IOException
	{
		status(store);
		final var counts = store.counts();
		final var checkpoint = active(store);
		final var coverage = store.coverage();
		final String body = "# Отчёт собственного тренажёра\n\nСформирован: " + Instant.now() + ".\n\nПодтверждённых эпизодов: " + counts.committed()
			+ "; неполных: " + counts.incomplete() + "; точных NATIVE-событий: " + counts.events() + ".\n\nАктивная TEST-модель: "
			+ (checkpoint == null ? "пока отсутствует" : "v" + checkpoint.id() + ", SHA " + checkpoint.model().sha256())
			+ ". Применение в рабочей игре не выполнялось.\n\n" + coverageMarkdown(coverage) + "\n" + store.historyMarkdown()
			+ (checkpoint == null ? "" : "\nНезависимое качество принятой модели по Solo и Party:\n\n```json\n" + store.exportModel().metrics() + "\n```\n")
			+ "\nPVP, PK, RAID, SIEGE, TW, OLYMPIAD, SUMMON, HEAL, CONTROL, SPOIL/SWEEP, MOVE: **NOT_IMPLEMENTED**.\n"
			+ "\nSQL-подтверждение direct HP не доказывает прирост DPS, native выбор модели или полную безопасность непринятых действий. Эти показатели проверяются отдельным холодным native-сеансом.\n"
			+ "\nRich-наблюдения старше retentionEpisodes удаляются с явной отметкой richPruned. Immutable числовые доказательства, frozen datasets и история моделей сохраняются до явного предела ёмкости; при его достижении новые попытки не создаются.\n";
		write(store.installation().resolve("report.md"), body);
		System.out.println("Русский отчёт сохранён в report.md.");
	}

	private static String coverageMarkdown(TrainingStore.Coverage coverage)
	{
		final var text = new StringBuilder("Счётчики выше относятся ко всей истории установки. Следующее покрытие ограничено последними ")
			.append(coverage.boundedToEpisodes()).append(" эпизодами; ordinal [").append(coverage.firstOrdinal()).append(", ").append(coverage.nextOrdinal()).append(").\n\n")
			.append("Окно UTC: ").append(coverage.windowStartUtc() == null ? "UNKNOWN (эпизодов пока нет)" : coverage.windowStartUtc()).append(" → ")
			.append(coverage.windowEndUtc()).append("; длительность ").append(coverage.windowMillis()).append(" мс. Пропускная способность включает простой и остановки между эпизодами в этом окне. ")
			.append(coverage.olderEpisodesExcluded() ? "Более старые эпизоды исключены из этого покрытия." : "Более старых эпизодов за пределами окна нет.")
			.append("\n\n| Режим | COMMITTED | INCOMPLETE | Активных | Exact events | Актёров | Навыков | Actual scenes | Recorded seeds | Exact HP | Эпизодов/ч | Events/ч |\n")
			.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
		for (String name : List.of("SOLO", "PARTY"))
		{
			final var scope = coverage.scopes().get(name);
			text.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %d | %d | %d | %.2f | %.3f | %.3f |%n", name,
				scope.committed(), scope.incomplete(), scope.active(), scope.exactEvents(), scope.distinctActors(), scope.distinctSkills(), scope.distinctScenarios(),
				scope.distinctSeeds(), scope.exactHp(), scope.episodesPerHour(), scope.eventsPerHour()));
		}
		text.append("\nАктёры и навыки считаются только по SQL-подтверждённым NATIVE/EXACT_CAST_HP событиям COMMITTED эпизодов. Recorded seeds — metadata запуска, не доказательство независимости AUDIT.\n");
		for (String name : List.of("SOLO", "PARTY"))
		{
			final var scope = coverage.scopes().get(name);
			text.append("\n").append(name).append(": категории подтверждённых эпизодов (значение = число эпизодов):\n\n")
				.append("- NPC templates: ").append(coverageCounts(scope.npcTemplates())).append(".\n")
				.append("- Классы обучаемых персонажей: ").append(coverageCounts(scope.learnerClasses())).append(".\n")
				.append("- Уровни обучаемых персонажей: ").append(coverageCounts(scope.levels())).append(".\n")
				.append("- Ранги экипировки обучаемых персонажей: ").append(coverageCounts(scope.equipmentGrades())).append(".\n")
				.append("- Поколения реального каталога актёров: ").append(coverageCounts(scope.catalogGenerations())).append(".\n")
				.append("- Исходы с terminal state: ").append(coverageCounts(scope.outcomes())).append(".\n")
				.append("- Причины неполных/отклонённых эпизодов: ").append(coverageCounts(scope.rejectionReasons())).append(".\n")
				.append("- Суммарное время подготовки/боя/завершения эпизодов: ").append(scope.elapsedMillis()).append(" мс.\n");
		}
		return text.append("\nСтарые/отсутствующие/pruned факты отмечены UNKNOWN. Классы помощников не подменяют классы обучаемых персонажей. Полная native decision latency, CPU/RAM и отдельные времена запуска/SQL: UNKNOWN в этом SQL-отчёте; первичные native.timings и supervisor logs проверяются отдельно.\n").toString();
	}

	private static String coverageCounts(java.util.Map<String, Long> counts)
	{
		if (counts.isEmpty()) { return "нет наблюдений"; }
		return new java.util.TreeMap<>(counts).entrySet().stream().map(entry -> entry.getKey().replace('\n', ' ').replace('\r', ' ').replace('|', '/') + "=" + entry.getValue())
			.collect(java.util.stream.Collectors.joining(", "));
	}

	private static void export(TrainingStore store) throws SQLException, IOException
	{
		final var model = store.exportModel();
		final var checkpoint = model.checkpoint();
		TrainingStore.validateObservation(model.metrics());
		final Path directory = store.installation().resolve("exports");
		Files.createDirectories(directory);
		if (!directory.toRealPath().equals(directory)) { throw new IllegalStateException("Каталог экспорта не должен содержать перенаправлений."); }
		final Path pending = directory.resolve("model-" + checkpoint.id() + "-" + TrainingStore.id() + ".pending");
		final Path target = pending.resolveSibling(pending.getFileName().toString().replace(".pending", ".zip"));
		try
		{
			try (var zip = new ZipOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW), StandardCharsets.UTF_8))
			{
				zip.putNextEntry(new ZipEntry("weights.bin")); zip.write(checkpoint.model().bytes()); zip.closeEntry();
				final String manifest = "{\"schemaVersion\":1,\"origin\":\"NATIVE\",\"status\":\"ACCEPTED_TEST\",\"modelVersion\":" + checkpoint.id()
					+ ",\"algorithm\":" + checkpoint.model().algorithm() + ",\"weightsSha256\":" + TrainingStore.quote(checkpoint.model().sha256())
					+ ",\"featureSchema\":" + TrainingStore.quote(LearningFeatures.SCHEMA) + ",\"skillCatalog\":" + TrainingStore.quote(store.catalog())
					+ ",\"datasetHash\":" + TrainingStore.quote(model.datasetHash()) + ",\"quality\":" + model.metrics() + ",\"productionPromotion\":false}\n";
				TrainingStore.validateObservation(manifest);
				zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
				final var writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
				zip.putNextEntry(new ZipEntry("dataset.jsonl")); store.writeDataset(writer, model.attempt()); writer.flush(); zip.closeEntry();
				zip.putNextEntry(new ZipEntry("episodes.jsonl")); store.writeEpisodes(writer); writer.flush(); zip.closeEntry();
			}
			try (var readback = new java.util.zip.ZipFile(pending.toFile(), StandardCharsets.UTF_8))
			{
				try (var input = readback.getInputStream(readback.getEntry("weights.bin")))
				{ NeuralPolicy.restore(input.readAllBytes(), checkpoint.model().sha256()); }
			}
			Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
			System.out.println("Принятая TEST-модель и проверяемый опыт сохранены: exports/" + target.getFileName());
		}
		finally { Files.deleteIfExists(pending); }
	}

	private static void write(Path target, String content) throws IOException
	{
		if (Files.exists(target) && !target.toRealPath().equals(target)) { throw new IOException("Путь результата перенаправлен."); }
		final Path pending = target.resolveSibling(target.getFileName() + "." + TrainingStore.id() + ".pending");
		try
		{
			Files.writeString(pending, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException failure) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
		}
		finally { Files.deleteIfExists(pending); }
	}
}
