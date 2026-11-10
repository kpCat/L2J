/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import org.l2jmobius.tests.phantoms.PhantomLearning001Suite;

/** Explicit isolated DB check. Write and read run in different JVMs. */
public final class LearningSql001Check
{
	public static void main(String[] args) throws Exception
	{
		if (!Boolean.getBoolean("phantom.learning.train") || args.length < 2)
		{
			throw new IllegalArgumentException("Isolated learning DB opt-in required");
		}
		final String url = System.getenv("PHANTOM_LEARNING_JDBC");
		final String password = System.getenv("PHANTOM_LEARNING_PASSWORD");
		final var store = new LearningStore(url, "learning001", password, Path.of(args[1]));
		if ("write".equals(args[0]) && args.length == 2)
		{
			final var fixture = PhantomLearning001Suite.fixture();
			final var trained = NeuralPolicy.audit(NeuralPolicy.train(fixture.train(), fixture.tuning(), 183), fixture.heldOut(), 183);
			final var saved = store.save(trained, fixture.train().size(), fixture.heldOut().size(), "f".repeat(64), "SYNTHETIC");
			final var source = fixture.train().getFirst();
			final var evidence = new LearningStore.Evidence("a".repeat(64), "b".repeat(64), "SOLO", 0, 1339, 1, 0, source.reward(), source.features());
			store.persistFixture(List.of(evidence));
			store.persistFixture(List.of(evidence));
			check(!store.promote(saved.id()) && store.accepted(saved.id()) == null, "synthetic model promoted");
			try
			{
				store.trainNative(64, 183);
				throw new AssertionError("Synthetic evidence entered native trainer");
			}
			catch (IllegalStateException expected)
			{
				check(expected.getMessage().startsWith("Native training requires"), "unexpected training refusal");
			}
			System.out.printf("learning001 SQL write fixture id=%d sha256=%s bytes=%d synthetic=true promotion=REFUSED%n",
				saved.id(), saved.model().sha256(), NeuralPolicy.BYTE_SIZE);
			return;
		}
		if (!"read".equals(args[0]) || args.length != 4)
		{
			throw new IllegalArgumentException("Expected write <gameRoot> or read <gameRoot> <version> <sha256>");
		}
		final long id = Long.parseLong(args[2]);
		final var read = store.fixture(id);
		check(read != null && read.model().sha256().equals(args[3]), "new JVM read a different model");
		PhantomLearning001Suite.assertDispatch(read.model());
		try (var connection = DriverManager.getConnection(url, "learning001", password))
		{
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT origin,COUNT(*) FROM phantom_ai_training_examples GROUP BY origin"))
			{
				check(rows.next() && "SYNTHETIC".equals(rows.getString(1)) && rows.getInt(2) == 1 && !rows.next(), "sample key or provenance failed");
			}
			// Observe the bad row through the actual loader and roll it back.
			connection.setAutoCommit(false);
			try (var update = connection.prepareStatement("UPDATE phantom_ai_model_versions SET sha256=? WHERE model_id=?"))
			{
				update.setString(1, "0".repeat(64));
				update.setLong(2, id);
				check(update.executeUpdate() == 1, "fault injection missed its fixture");
			}
			try
			{
				store.read(connection, id, "DRAFT", "SYNTHETIC");
				throw new AssertionError("Corrupted SQL model loaded");
			}
			catch (IllegalArgumentException expected)
			{
				check(expected.getMessage().contains("checksum"), "unexpected model rejection");
			}
			connection.rollback();
			try (var update = connection.prepareStatement("UPDATE phantom_ai_model_versions SET schema_fingerprint=? WHERE model_id=?"))
			{
				update.setString(1, "0".repeat(64));
				update.setLong(2, id);
				check(update.executeUpdate() == 1, "schema injection missed its fixture");
			}
			check(store.read(connection, id, "DRAFT", "SYNTHETIC") == null, "stale schema loaded");
			connection.rollback();
		}
		check(store.fixture(id).model().sha256().equals(args[3]), "rollback changed the checkpoint");
		System.out.printf("learning001 SQL fresh JVM id=%d sha256=%s decisions=SOLO+PARTY fixtureExamples=1 corrupt=REFUSED stale=REFUSED rollback=PASS%n", id, read.model().sha256());
	}

	private static void check(boolean ok, String message)
	{
		if (!ok) { throw new AssertionError(message); }
	}
}
