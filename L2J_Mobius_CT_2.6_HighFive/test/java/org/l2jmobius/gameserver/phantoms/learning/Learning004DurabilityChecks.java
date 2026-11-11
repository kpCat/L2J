/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.zip.ZipFile;

/** Actual own-SQL cold/fault checks. Mutations are transaction-local and always rolled back; no fabricated native rows. */
public final class Learning004DurabilityChecks
{
	public static void main(String[] args)
	{
		try { run(args); }
		catch (Exception failure) { System.err.println("LEARNING004_DURABILITY_FAILED " + failure.getClass().getSimpleName()); System.exit(2); }
	}
	private static void run(String[] args) throws Exception
	{
		if (args.length < 2 || !Set.of("inspect","verify","faults","export","transport").contains(args[0])) { throw new IllegalArgumentException("Expected mode and own installation"); }
		final Path root = Path.of(args[1]).toRealPath();
		final var guard = new TrainingStore(root); final var store = new TacticalStoreV3(root);
		if (args[0].equals("inspect")) { inspect(guard); return; }
		store.requireStopped();
		if (args[0].equals("transport")) { transport(guard,store); return; }
		final var optimizer = store.optimizationParent();
		if (optimizer == null || optimizer.state().updates() <= 0) { throw new AssertionError("Committed native optimizer required"); }
		final var candidate = store.candidate(optimizer.modelId());
		final var accepted = store.latestAccepted();
		check(accepted != null && store.accepted(2,"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc") != null,"accepted003 changed");
		final var raw = store.dataset();
		final byte[] contexts = store.replayContexts(candidate.runId(),candidate.datasetSha(),candidate.rows());
		final var samples = TacticalReplayV3.unpackContexts(contexts,candidate.datasetSha());
		check(samples.stream().filter(sample -> sample.kind().equals("EFFECT")).count() == candidate.rows().size(),"replay effect count changed");
		final long count = store.nativeSampleCount(); final var first = raw.getFirst();
		check(!store.record(first.receipt(),first.split(),first.contextKey()) && count == store.nativeSampleCount(),"retry double-credited native event");
		if (args[0].equals("faults"))
		{
			final String director = Files.readString(root.resolve("director.json"));
			check(director.matches("(?s).*\"phase\"\\s*:\\s*\"PAUSED\".*"),"fault checks require paused Director and stopped native boundary");
			try (var connection = guard.openTacticalConnection())
			{
				connection.setAutoCommit(false);
				try
				{
					mutate(connection,"UPDATE phantom_tactical_v3_optimizers SET parent_weights_sha=REPEAT('0',64) WHERE model_id=" + optimizer.modelId());
					reject(() -> store.latestOptimization(connection,false),"forged SQL parent accepted"); connection.rollback();
					mutate(connection,"DELETE FROM phantom_tactical_v3_optimizers WHERE model_id=" + optimizer.modelId());
					reject(() -> store.latestOptimization(connection,false),"deleted latest optimizer silently fell back"); connection.rollback();
					mutate(connection,"UPDATE phantom_tactical_v3_optimizers SET model_id=model_id+1000000 WHERE model_id=" + optimizer.modelId());
					reject(() -> store.latestOptimization(connection,false),"orphan optimizer silently joined away"); connection.rollback();
					mutate(connection,"DELETE FROM phantom_tactical_v3_context_replay WHERE run_id='" + candidate.runId() + "'");
					reject(() -> TacticalStoreV3.requireReplayContexts(connection,candidate.runId(),candidate.datasetSha(),candidate.rows()),"missing frozen replay accepted"); connection.rollback();
					mutate(connection,"DELETE FROM phantom_tactical_v3_events WHERE event_key='" + candidate.rows().getFirst().eventKey() + "'");
					reject(() -> TacticalStoreV3.requireReplayContexts(connection,candidate.runId(),candidate.datasetSha(),candidate.rows()),"deleted raw native effect accepted"); connection.rollback();
				}
				finally { connection.rollback(); }
			}
			check(store.optimizationParent().sha().equals(optimizer.sha()) && store.nativeSampleCount() == count,"fault rollback changed retained checkpoint/native ledger");
			System.out.println("LEARNING004_SQL_FAULTS parent=REJECTED deletedOptimizer=REJECTED orphanOptimizer=REJECTED missingReplay=REJECTED missingRaw=REJECTED rollback=PASS commits=0");
		}
		Path exported = null;
		if (args[0].equals("export"))
		{
			exported = TacticalTrainerV3.export(root);
			try (var zip = new ZipFile(exported.toFile()))
			{
				final byte[] restoredOptimizer = entry(zip,"optimizer.bin",TacticalModelV3.Optimizer.BYTE_SIZE);
				final var restored = TacticalModelV3.Optimizer.restore(restoredOptimizer,optimizer.sha());
				check(Arrays.equals(restored.bytes(),optimizer.state().bytes()) && restored.updates() == optimizer.state().updates(),"cold optimizer export changed");
				check(Arrays.equals(entry(zip,"replay.bin",64*1024*1024),TacticalStoreV3.pack(candidate.rows())),"cold replay export changed");
				check(Arrays.equals(entry(zip,"context-replay.bin",4*1024*1024),contexts),"cold context replay export changed");
				check(Arrays.equals(entry(zip,"working-weights.bin",TacticalModelV3.BYTE_SIZE),candidate.model().bytes()),"selected weights replaced by continuation weights");
				check(Arrays.equals(entry(zip,"weights.bin",TacticalModelV3.BYTE_SIZE),accepted.model().bytes()),"champion changed in export");
				for (var sample : samples)
				{
					if (sample.kind().equals("FAILURE")) { TacticalFailureV3.restore(entry(zip,"failures/"+sample.key()+".bin",TacticalFailureV3.MAX_BYTES),sample.receiptSha()); }
				}
			}
		}
		final var groups = new TreeMap<String,Long>();
		for (var row : raw)
		{
			final var observation = row.receipt().attempt().observation();
			final String key = row.receipt().attempt().selected().family()+"/"+observation.self().classId()+"/"+(observation.partyIdentity().startsWith("solo:") ? "SOLO" : "PARTY")+"/"+row.split();
			groups.merge(key,1L,Long::sum);
		}
		final String coverage = groups.entrySet().stream().map(group -> TrainingStore.quote(group.getKey())+":"+group.getValue()).collect(java.util.stream.Collectors.joining(","));
		System.out.println("LEARNING004_COLD {\"pid\":"+ProcessHandle.current().pid()+",\"nativeEvents\":"+count+",\"workingModel\":"+candidate.id()+",\"workingSha\":"+TrainingStore.quote(candidate.sha())
			+",\"optimizerSha\":"+TrainingStore.quote(optimizer.sha())+",\"updates\":"+optimizer.state().updates()+",\"epochs\":"+optimizer.state().completedEpochs()
			+",\"runId\":"+TrainingStore.quote(candidate.runId())+",\"datasetSha\":"+TrainingStore.quote(candidate.datasetSha())+",\"contextReplaySha\":"+TrainingStore.quote(TacticalObservationV3.hash(contexts))
			+",\"contextSamples\":"+samples.size()+",\"failureSamples\":"+samples.stream().filter(sample -> sample.kind().equals("FAILURE")).count()+",\"coverage\":{"+coverage+"},\"episodes\":"+store.episodeCounts()
			+",\"export\":"+(exported == null ? "null" : TrainingStore.quote(exported.toString()))+"}");
	}
	private static void inspect(TrainingStore guard) throws Exception
	{
		try (var connection = guard.openTacticalConnection(); var query = connection.createStatement())
		{
			try (var rows = query.executeQuery("SELECT run_id,dataset_sha FROM phantom_tactical_v3_runs WHERE state='FROZEN' ORDER BY created_at,run_id"))
			{ while(rows.next()) { System.out.println("LEARNING004_FROZEN run="+rows.getString(1)+" datasetSha="+rows.getString(2)); } }
			try (var rows = query.executeQuery("SELECT model_id,optimizer_sha,updates,completed_epochs,parent_model_id,parent_optimizer_sha FROM phantom_tactical_v3_optimizers ORDER BY model_id"))
			{ while(rows.next()) { System.out.println("LEARNING004_COMMITTED model="+rows.getLong(1)+" optimizerSha="+rows.getString(2)+" updates="+rows.getLong(3)+" epochs="+rows.getLong(4)+" parent="+rows.getLong(5)+" parentOptimizerSha="+rows.getString(6)); } }
			try (var rows = query.executeQuery("SELECT origin,split,COUNT(*) FROM phantom_tactical_v3_events GROUP BY origin,split ORDER BY origin,split"))
			{ while(rows.next()) { System.out.println("LEARNING004_LEDGER origin="+rows.getString(1)+" split="+rows.getString(2)+" count="+rows.getLong(3)); } }
		}
	}
	private static void transport(TrainingStore guard, TacticalStoreV3 store) throws Exception
	{
		final var initial = TacticalModelV3.startOptimization(store.latestAccepted().model(),TacticalStoreV3.SEED);
		final byte[] bytes = initial.bytes();
		try (var connection = guard.openTacticalConnection())
		{
			long packet;
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT @@max_allowed_packet")) { check(rows.next(),"missing packet limit"); packet=rows.getLong(1); }
			try (var query = connection.createStatement()) { query.executeUpdate("CREATE TEMPORARY TABLE learning004_optimizer_transport (payload LONGBLOB NOT NULL) ENGINE=InnoDB"); }
			try
			{
				connection.setAutoCommit(false);
				try (var insert = connection.prepareStatement("INSERT INTO learning004_optimizer_transport(payload) VALUES(?)"))
				{
					check(insert.getClass().getName().equals("com.mysql.cj.jdbc.ServerPreparedStatement"),"V3 optimizer did not use confirmed binary protocol");
					insert.setBytes(1,bytes); check(insert.executeUpdate()==1,"optimizer transport insert failed");
				}
				try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT payload FROM learning004_optimizer_transport"))
				{
					check(rows.next() && Arrays.equals(bytes,TacticalModelV3.Optimizer.restore(rows.getBytes(1),initial.sha256()).bytes()) && !rows.next(),"binary optimizer readback mismatch");
				}
				System.out.println("LEARNING004_OPTIMIZER_TRANSPORT bytes="+bytes.length+" maxAllowedPacket="+packet+" protocol=BINARY exactReadback=PASS temporaryTable=true rollback=true origin=MATH_ONLY nativeCredit=0");
			}
			finally { connection.rollback(); try(var query=connection.createStatement()) { query.executeUpdate("DROP TEMPORARY TABLE learning004_optimizer_transport"); } }
		}
	}
	private static byte[] entry(ZipFile zip, String name, int limit) throws Exception
	{
		final var item = zip.getEntry(name); check(item != null && item.getSize() >= 0 && item.getSize() <= limit,"export entry missing/oversized "+name);
		try (var input = zip.getInputStream(item)) { final byte[] bytes = input.readNBytes(limit+1); check(bytes.length <= limit,"export byte bound"); return bytes; }
	}
	private static void mutate(Connection connection, String sql) throws Exception
	{ try (var statement = connection.createStatement()) { check(statement.executeUpdate(sql)==1,"fault did not affect exactly one own row"); } }
	@FunctionalInterface private interface Checked { Object run() throws Exception; }
	private static void reject(Checked check, String message) throws Exception
	{ try { check.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
