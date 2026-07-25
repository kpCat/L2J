/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException.Category;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;
import org.l2jmobius.tests.phantoms.StrictSqlScriptRunner.ScriptInfo;

public final class PhantomProfilePersistenceSuite implements PhantomTestSuite
{
	private static final String SCRIPT_PATH = "dist/db_installer/sql/game/phantom_profiles.sql";
	private static final int FIRST_CHARACTER_ID = 900000001;
	private static final int SECOND_CHARACTER_ID = 900000002;
	private PhantomProfileRepository _repository;
	private int _replayCount;

	@Override
	public String id()
	{
		return "profile-persistence";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}

		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		final List<ScriptInfo> scripts = PhantomTestSchemaManifest.inventory(context.moduleRoot()).stream().filter(script -> SCRIPT_PATH.equals(script.relativePath())).toList();
		PhantomAssertions.assertEquals(1, scripts.size(), "Profile schema inventory must contain exactly one installer script.");
		PhantomAssertions.assertEquals(2, scripts.getFirst().statements().size(), "Profile installer must contain exactly two statements.");
		try (Connection connection = DatabaseFactory.getConnection())
		{
			StrictSqlScriptRunner.execute(connection, scripts);
			_replayCount++;
			StrictSqlScriptRunner.execute(connection, scripts);
			_replayCount++;
		}

		_repository = PhantomProfileRepository.open();
		cleanupRows();
		context.record("profile.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("profile.schemaScripts", bootstrap.schemaSnapshot().scriptCount());
		context.record("profile.schemaStatements", bootstrap.schemaSnapshot().statementCount());
		context.record("profile.schemaAggregateSha256", bootstrap.schemaSnapshot().aggregateSha256());
		context.record("profile.installerReplayCount", _replayCount);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				cleanupRows();
				assertResidueZero();
			}
		}
		finally
		{
			DatabaseFactory.close();
		}

		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while ((System.nanoTime() < deadline) && hasLivePoolThread())
		{
			Thread.sleep(25);
		}
		PhantomAssertions.assertFalse(hasLivePoolThread(), "Profile suite left a Hikari non-daemon thread.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-schema-exact-and-repository-open", _ -> testSchemaExactAndRepositoryOpen());
		registry.add("02-installer-replay-twice", _ -> PhantomAssertions.assertEquals(2, _replayCount, "Profile installer was not replayed twice before data tests."));
		registry.add("03-create-unlinked-find-and-missing", _ -> testCreateUnlinked());
		registry.add("04-character-link-round-trip", _ -> testCharacterLinkRoundTrip());
		registry.add("05-unique-character-link-conflict", _ -> testUniqueCharacterLinkConflict());
		registry.add("06-stale-core-update-rejected", _ -> testStaleCoreUpdate());
		registry.add("07-concurrent-core-exactly-one-winner", _ -> testConcurrentCoreUpdate());
		registry.add("08-component-input-validation", _ -> testComponentInputValidation());
		registry.add("09-component-payload-boundaries", _ -> testPayloadBoundaries());
		registry.add("10-component-defensive-copies", _ -> testDefensiveCopies());
		registry.add("11-component-insert-read-update", _ -> testComponentInsertReadUpdate());
		registry.add("12-stale-component-update-rejected", _ -> testStaleComponentUpdate());
		registry.add("13-component-list-binary-order-immutable", _ -> testComponentListOrder());
		registry.add("14-optimistic-component-delete", _ -> testOptimisticComponentDelete());
		registry.add("15-profile-delete-cascades-components", _ -> testProfileDeleteCascade());
		registry.add("16-new-repository-reloads-same-state", _ -> testRepositoryRestart());
		registry.add("17-stale-profile-delete-rejected", _ -> testStaleProfileDelete());
		registry.add("18-final-owned-row-residue-zero", _ ->
		{
			cleanupRows();
			assertResidueZero();
		});
	}

	private void testSchemaExactAndRepositoryOpen() throws Exception
	{
		PhantomAssertions.assertTrue(_repository != null, "Repository did not open.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'phantom_profiles'"), "Profile table is missing.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'phantom_profile_components'"), "Component table is missing.");
		PhantomAssertions.assertEquals(4096L, scalar("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'phantom_profile_components' AND column_name = 'payload'"), "Component payload schema bound is wrong.");
	}

	private void testCreateUnlinked() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		PhantomAssertions.assertTrue(profile.profileId() > 0, "Generated profile ID is not positive.");
		PhantomAssertions.assertEquals(null, profile.characterObjectId(), "Unlinked profile unexpectedly has a character link.");
		PhantomAssertions.assertEquals(1, profile.schemaVersion(), "New profile schema version is wrong.");
		PhantomAssertions.assertEquals(0L, profile.rowVersion(), "New profile row version is wrong.");
		PhantomAssertions.assertTrue(profile.createdAt() != null && profile.updatedAt() != null, "New profile timestamps are missing.");
		PhantomAssertions.assertEquals(profile, _repository.find(profile.profileId()).orElseThrow(), "Profile round-trip changed the snapshot.");
		PhantomAssertions.assertTrue(_repository.find(Long.MAX_VALUE).isEmpty(), "Missing profile lookup returned a row.");
	}

	private void testCharacterLinkRoundTrip() throws Exception
	{
		cleanupRows();
		final PhantomProfile created = _repository.create(null);
		final PhantomProfile linked = _repository.updateCharacterLink(created.profileId(), created.rowVersion(), FIRST_CHARACTER_ID);
		PhantomAssertions.assertEquals(FIRST_CHARACTER_ID, linked.characterObjectId(), "Character link was not stored.");
		PhantomAssertions.assertEquals(1L, linked.rowVersion(), "Character link did not increment row version.");
		PhantomAssertions.assertEquals(linked, _repository.findByCharacterObjectId(FIRST_CHARACTER_ID).orElseThrow(), "Character lookup returned another profile.");
		final PhantomProfile unlinked = _repository.updateCharacterLink(linked.profileId(), linked.rowVersion(), null);
		PhantomAssertions.assertEquals(null, unlinked.characterObjectId(), "Character link was not cleared.");
		PhantomAssertions.assertEquals(2L, unlinked.rowVersion(), "Character unlink did not increment row version.");
		PhantomAssertions.assertTrue(_repository.findByCharacterObjectId(FIRST_CHARACTER_ID).isEmpty(), "Cleared character link remained queryable.");
	}

	private void testUniqueCharacterLinkConflict() throws Exception
	{
		cleanupRows();
		final PhantomProfile first = _repository.create(FIRST_CHARACTER_ID);
		final PhantomProfile second = _repository.create(SECOND_CHARACTER_ID);
		final PhantomProfilePersistenceException failure = PhantomAssertions.assertThrows(PhantomProfilePersistenceException.class, () -> _repository.updateCharacterLink(second.profileId(), second.rowVersion(), FIRST_CHARACTER_ID), "Duplicate character link must fail.");
		PhantomAssertions.assertEquals(Category.CONSTRAINT_VIOLATION, failure.category(), "Unique character conflict has the wrong category.");
		PhantomAssertions.assertEquals(first, _repository.find(first.profileId()).orElseThrow(), "Unique conflict changed the existing owner.");
		PhantomAssertions.assertEquals(second, _repository.find(second.profileId()).orElseThrow(), "Unique conflict changed the losing profile.");
	}

	private void testStaleCoreUpdate() throws Exception
	{
		cleanupRows();
		final PhantomProfile created = _repository.create(null);
		final PhantomProfile updated = _repository.updateCharacterLink(created.profileId(), created.rowVersion(), FIRST_CHARACTER_ID);
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> _repository.updateCharacterLink(created.profileId(), created.rowVersion(), SECOND_CHARACTER_ID), "Stale profile update must fail.");
		PhantomAssertions.assertEquals(updated, _repository.find(created.profileId()).orElseThrow(), "Stale profile update changed the winner.");
	}

	private void testConcurrentCoreUpdate() throws Exception
	{
		cleanupRows();
		final PhantomProfile created = _repository.create(null);
		final PhantomProfileRepository leftRepository = PhantomProfileRepository.open();
		final PhantomProfileRepository rightRepository = PhantomProfileRepository.open();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		final AtomicInteger winners = new AtomicInteger();
		final AtomicInteger conflicts = new AtomicInteger();
		final AtomicReference<Throwable> unexpected = new AtomicReference<>();

		final Thread left = contender(leftRepository, created, FIRST_CHARACTER_ID, ready, start, winners, conflicts, unexpected, "t005-profile-left");
		final Thread right = contender(rightRepository, created, SECOND_CHARACTER_ID, ready, start, winners, conflicts, unexpected, "t005-profile-right");
		left.start();
		right.start();
		PhantomAssertions.assertTrue(ready.await(2, TimeUnit.SECONDS), "Concurrent profile writers did not reach the barrier.");
		start.countDown();
		left.join(4000);
		right.join(4000);
		PhantomAssertions.assertFalse(left.isAlive() || right.isAlive(), "Concurrent profile writers did not terminate.");
		PhantomAssertions.assertEquals(null, unexpected.get(), "Concurrent profile writer failed unexpectedly.");
		PhantomAssertions.assertEquals(1, winners.get(), "Concurrent profile update did not have exactly one winner.");
		PhantomAssertions.assertEquals(1, conflicts.get(), "Concurrent profile update did not have exactly one optimistic conflict.");
		final PhantomProfile result = _repository.find(created.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(1L, result.rowVersion(), "Concurrent profile update incremented the row version more than once.");
		PhantomAssertions.assertTrue((result.characterObjectId() == FIRST_CHARACTER_ID) || (result.characterObjectId() == SECOND_CHARACTER_ID), "Concurrent winner stored an unexpected character link.");
	}

	private static Thread contender(PhantomProfileRepository repository, PhantomProfile profile, int characterObjectId, CountDownLatch ready, CountDownLatch start, AtomicInteger winners, AtomicInteger conflicts, AtomicReference<Throwable> unexpected, String name)
	{
		return new Thread(() ->
		{
			ready.countDown();
			try
			{
				start.await();
				repository.updateCharacterLink(profile.profileId(), profile.rowVersion(), characterObjectId);
				winners.incrementAndGet();
			}
			catch (ConcurrentModificationException expected)
			{
				conflicts.incrementAndGet();
			}
			catch (Throwable throwable)
			{
				unexpected.compareAndSet(null, throwable);
			}
		}, name);
	}

	private void testComponentInputValidation() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		for (String invalid : new String[]
		{
			"", "Test.opaque", "1test", "test opaque", "a".repeat(65)
		})
		{
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _repository.insertComponent(profile.profileId(), invalid, 1, new byte[0]), "Invalid component type was accepted: " + invalid);
		}
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _repository.insertComponent(profile.profileId(), null, 1, new byte[0]), "Null component type was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _repository.insertComponent(profile.profileId(), "test.opaque", 0, new byte[0]), "Zero component schema version was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _repository.insertComponent(profile.profileId(), "test.opaque", 65536, new byte[0]), "Oversized component schema version was accepted.");
		PhantomAssertions.assertThrows(NullPointerException.class, () -> _repository.insertComponent(profile.profileId(), "test.opaque", 1, null), "Null component payload was accepted.");
	}

	private void testPayloadBoundaries() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		PhantomAssertions.assertEquals(0, _repository.insertComponent(profile.profileId(), "test.empty", 1, new byte[0]).payload().length, "Empty payload did not round-trip.");
		PhantomAssertions.assertEquals(4096, _repository.insertComponent(profile.profileId(), "test.max", 1, new byte[4096]).payload().length, "4096-byte payload did not round-trip.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _repository.insertComponent(profile.profileId(), "test.too-large", 1, new byte[4097]), "4097-byte payload was accepted.");
	}

	private void testDefensiveCopies() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		final byte[] input = new byte[]
		{
			1, 2, 3
		};
		final PhantomProfileComponent component = _repository.insertComponent(profile.profileId(), "test.opaque", 1, input);
		input[0] = 9;
		PhantomAssertions.assertTrue(Arrays.equals(new byte[]
		{
			1, 2, 3
		}, component.payload()), "Component retained its input payload array.");
		final byte[] output = component.payload();
		output[1] = 9;
		PhantomAssertions.assertTrue(Arrays.equals(new byte[]
		{
			1, 2, 3
		}, component.payload()), "Component exposed its stored payload array.");
		PhantomAssertions.assertTrue(Arrays.equals(new byte[]
		{
			1, 2, 3
		}, _repository.findComponent(profile.profileId(), "test.opaque").orElseThrow().payload()), "Payload mutation escaped into persistence.");
	}

	private void testComponentInsertReadUpdate() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		final PhantomProfileComponent inserted = _repository.insertComponent(profile.profileId(), "test.opaque", 1, new byte[]
		{
			4
		});
		PhantomAssertions.assertEquals(0L, inserted.rowVersion(), "New component row version is wrong.");
		PhantomAssertions.assertTrue(Arrays.equals(new byte[]
		{
			4
		}, _repository.findComponent(profile.profileId(), "test.opaque").orElseThrow().payload()), "Inserted component did not round-trip.");
		final PhantomProfileComponent updated = _repository.updateComponent(profile.profileId(), "test.opaque", inserted.rowVersion(), 2, new byte[]
		{
			5, 6
		});
		PhantomAssertions.assertEquals(2, updated.componentSchemaVersion(), "Component schema version did not update.");
		PhantomAssertions.assertEquals(1L, updated.rowVersion(), "Component row version did not increment.");
		PhantomAssertions.assertTrue(Arrays.equals(new byte[]
		{
			5, 6
		}, updated.payload()), "Component payload did not update.");
		final PhantomProfilePersistenceException duplicate = PhantomAssertions.assertThrows(PhantomProfilePersistenceException.class, () -> _repository.insertComponent(profile.profileId(), "test.opaque", 1, new byte[0]), "Duplicate component insert must fail.");
		PhantomAssertions.assertEquals(Category.CONSTRAINT_VIOLATION, duplicate.category(), "Duplicate component insert has the wrong category.");
	}

	private void testStaleComponentUpdate() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		final PhantomProfileComponent inserted = _repository.insertComponent(profile.profileId(), "test.opaque", 1, new byte[0]);
		final PhantomProfileComponent updated = _repository.updateComponent(profile.profileId(), "test.opaque", inserted.rowVersion(), 2, new byte[]
		{
			1
		});
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> _repository.updateComponent(profile.profileId(), "test.opaque", inserted.rowVersion(), 3, new byte[]
		{
			2
		}), "Stale component update must fail.");
		PhantomAssertions.assertEquals(updated.rowVersion(), _repository.findComponent(profile.profileId(), "test.opaque").orElseThrow().rowVersion(), "Stale component update changed the winner.");
	}

	private void testComponentListOrder() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		_repository.insertComponent(profile.profileId(), "test.z", 1, new byte[0]);
		_repository.insertComponent(profile.profileId(), "test.a-", 1, new byte[0]);
		_repository.insertComponent(profile.profileId(), "test.a", 1, new byte[0]);
		final List<PhantomProfileComponent> components = _repository.listComponents(profile.profileId());
		PhantomAssertions.assertEquals(List.of("test.a", "test.a-", "test.z"), components.stream().map(PhantomProfileComponent::componentType).toList(), "Component list is not in database binary order.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> components.add(components.getFirst()), "Component list is mutable.");
	}

	private void testOptimisticComponentDelete() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(null);
		final PhantomProfileComponent inserted = _repository.insertComponent(profile.profileId(), "test.opaque", 1, new byte[0]);
		final PhantomProfileComponent updated = _repository.updateComponent(profile.profileId(), "test.opaque", inserted.rowVersion(), 2, new byte[0]);
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> _repository.deleteComponent(profile.profileId(), "test.opaque", inserted.rowVersion()), "Stale component delete must fail.");
		_repository.deleteComponent(profile.profileId(), "test.opaque", updated.rowVersion());
		PhantomAssertions.assertTrue(_repository.findComponent(profile.profileId(), "test.opaque").isEmpty(), "Component delete left a row.");
	}

	private void testProfileDeleteCascade() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(FIRST_CHARACTER_ID);
		_repository.insertComponent(profile.profileId(), "test.opaque", 1, new byte[]
		{
			7
		});
		_repository.delete(profile.profileId(), profile.rowVersion());
		PhantomAssertions.assertTrue(_repository.find(profile.profileId()).isEmpty(), "Profile delete left a core row.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Profile delete did not cascade component rows.");
	}

	private void testRepositoryRestart() throws Exception
	{
		cleanupRows();
		final PhantomProfile profile = _repository.create(FIRST_CHARACTER_ID);
		final PhantomProfileComponent component = _repository.insertComponent(profile.profileId(), "test.opaque", 17, new byte[]
		{
			8, 9
		});
		final PhantomProfileRepository restarted = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(profile, restarted.find(profile.profileId()).orElseThrow(), "New repository instance changed the profile snapshot.");
		final PhantomProfileComponent reloaded = restarted.findComponent(profile.profileId(), "test.opaque").orElseThrow();
		PhantomAssertions.assertEquals(component.profileId(), reloaded.profileId(), "New repository instance changed component identity.");
		PhantomAssertions.assertEquals(component.componentType(), reloaded.componentType(), "New repository instance changed component type.");
		PhantomAssertions.assertEquals(component.componentSchemaVersion(), reloaded.componentSchemaVersion(), "New repository instance changed component schema version.");
		PhantomAssertions.assertEquals(component.rowVersion(), reloaded.rowVersion(), "New repository instance changed component row version.");
		PhantomAssertions.assertTrue(Arrays.equals(component.payload(), reloaded.payload()), "New repository instance changed component payload.");
	}

	private void testStaleProfileDelete() throws Exception
	{
		cleanupRows();
		final PhantomProfile created = _repository.create(null);
		final PhantomProfile updated = _repository.updateCharacterLink(created.profileId(), created.rowVersion(), FIRST_CHARACTER_ID);
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> _repository.delete(created.profileId(), created.rowVersion()), "Stale profile delete must fail.");
		_repository.delete(updated.profileId(), updated.rowVersion());
		PhantomAssertions.assertTrue(_repository.find(updated.profileId()).isEmpty(), "Current profile delete left a row.");
	}

	private static void cleanupRows() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			Statement statement = connection.createStatement())
		{
			statement.executeUpdate("DELETE FROM phantom_profiles");
		}
	}

	private static void assertResidueZero() throws Exception
	{
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Owned component row residue remains.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Owned profile row residue remains.");
	}

	private static long scalar(String sql) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery(sql))
		{
			PhantomAssertions.assertTrue(result.next(), "Scalar query returned no row.");
			return result.getLong(1);
		}
	}

	private static boolean hasLivePoolThread()
	{
		return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.isAlive() && !thread.isDaemon() && thread.getName().startsWith("L2JMobiusPool"));
	}
}
