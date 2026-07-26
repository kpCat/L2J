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
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

public final class PhantomDecisionPersistenceSuite implements PhantomTestSuite
{
	private final List<Long> _ownedProfileIds = new ArrayList<>();
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();
	private PhantomProfileRepository _repository;

	@Override
	public String id()
	{
		return "decision-persistence";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		_repository = PhantomProfileRepository.open();
		context.record("decisionPersistence.componentType", PhantomGoalStateStore.COMPONENT_TYPE);
		context.record("decisionPersistence.componentSchema", PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
				{
					for (long profileId : _ownedProfileIds)
					{
						statement.setLong(1, profileId);
						statement.executeUpdate();
					}
				}
			}
		}
		finally
		{
			DatabaseFactory.close();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-codec-deterministic-round-trip", _ -> testDeterministicRoundTrip());
		registry.add("02-codec-rejects-magic", _ -> testUnknownMagic());
		registry.add("03-codec-rejects-format-version", _ -> testUnknownFormatVersion());
		registry.add("04-codec-rejects-goal-version", _ -> testUnknownGoalVersion());
		registry.add("05-codec-rejects-truncation", _ -> testTruncation());
		registry.add("06-codec-rejects-trailing-bytes", _ -> testTrailingBytes());
		registry.add("07-codec-rejects-length-before-allocation", _ -> testOversizedLength());
		registry.add("08-codec-rejects-unknown-status", _ -> testUnknownStatus());
		registry.add("09-store-insert-load-envelope", _ -> testStoreInsertLoad());
		registry.add("10-store-optimistic-replace", _ -> testStoreReplace());
		registry.add("11-store-optimistic-delete", _ -> testStoreDelete());
		registry.add("12-store-rejects-component-schema", _ -> testComponentSchema());
		registry.add("13-restart-active-goal-needs-replan", _ -> testRestartNeedsReplan());
		registry.add("14-payload-contains-goal-only", _ -> testGoalOnlyPayload());
	}

	private void testDeterministicRoundTrip()
	{
		final PhantomGoal goal = goal(0);
		final byte[] first = _codec.encode(goal);
		final byte[] second = _codec.encode(goal);
		PhantomAssertions.assertTrue(Arrays.equals(first, second), "Identical goals produced different binary payloads.");
		PhantomAssertions.assertEquals(goal, _codec.decode(first), "Goal binary round-trip changed immutable state.");
		PhantomAssertions.assertTrue(first.length <= PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Goal payload exceeded component envelope.");
	}

	private void testUnknownMagic()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[0] ^= 1;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown goal magic was accepted.");
	}

	private void testUnknownFormatVersion()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[5] = 2;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown binary format version was accepted.");
	}

	private void testUnknownGoalVersion()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[7] = 2;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown goal schema version was accepted.");
	}

	private void testTruncation()
	{
		final byte[] payload = _codec.encode(goal(0));
		for (int length : List.of(0, 4, 24, payload.length - 1))
		{
			final byte[] truncated = Arrays.copyOf(payload, length);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(truncated), "Truncated goal payload was accepted at length " + length + ".");
		}
	}

	private void testTrailingBytes()
	{
		final byte[] payload = Arrays.copyOf(_codec.encode(goal(0)), _codec.encode(goal(0)).length + 1);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Trailing goal payload bytes were accepted.");
	}

	private void testOversizedLength()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[25] = 0x7f;
		payload[26] = (byte) 0xff;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Oversized string length reached allocation.");
	}

	private void testUnknownStatus()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[24] = 99;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown persisted goal status was accepted.");
	}

	private void testStoreInsertLoad()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal inserted = store.insert(profile.profileId(), goal(0));
		final StoredGoal loaded = store.load(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(inserted, loaded, "goal.runtime load changed inserted state.");
		final PhantomProfileComponent component = _repository.findComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(1, component.componentSchemaVersion(), "goal.runtime used a non-v1 component envelope.");
		PhantomAssertions.assertTrue(component.payload().length <= 4096, "goal.runtime exceeded 4096 bytes.");
	}

	private void testStoreReplace()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal initial = store.insert(profile.profileId(), goal(0));
		final StoredGoal replaced = store.replace(profile.profileId(), initial.rowVersion(), goal(1));
		PhantomAssertions.assertEquals(1L, replaced.goal().revision(), "Optimistic goal replacement lost revision.");
		PhantomAssertions.assertEquals(initial.rowVersion() + 1, replaced.rowVersion(), "Goal component row version did not advance.");
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> store.replace(profile.profileId(), initial.rowVersion(), goal(2)), "Stale goal replacement was accepted.");
	}

	private void testStoreDelete()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal inserted = store.insert(profile.profileId(), goal(0));
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> store.delete(profile.profileId(), inserted.rowVersion() + 1), "Stale goal delete was accepted.");
		store.delete(profile.profileId(), inserted.rowVersion());
		PhantomAssertions.assertTrue(store.load(profile.profileId()).isEmpty(), "Optimistic goal delete retained component.");
	}

	private void testComponentSchema()
	{
		final PhantomProfile profile = createProfile();
		_repository.insertComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE, 2, _codec.encode(goal(0)));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomGoalStateStore(_repository).load(profile.profileId()), "Unknown goal.runtime component schema was accepted.");
	}

	private void testRestartNeedsReplan()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		store.insert(profile.profileId(), goal(0));
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4);
		engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "Restart engine did not attach persisted goal.");
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = engine.find(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "Restart restored execution state instead of NEEDS_REPLAN.");
		PhantomAssertions.assertEquals(0L, snapshot.planId(), "Restart restored a persisted plan.");
		engine.beginStop();
		PhantomAssertions.assertTrue(engine.finishStop(), "Restart engine did not stop.");
	}

	private void testGoalOnlyPayload()
	{
		final byte[] payload = _codec.encode(goal(0));
		final String ascii = new String(payload, java.nio.charset.StandardCharsets.ISO_8859_1);
		PhantomAssertions.assertFalse(ascii.contains("action.test"), "Goal payload serialized a plan action.");
		PhantomAssertions.assertFalse(ascii.contains("candidate.test"), "Goal payload serialized candidate evaluation.");
		PhantomAssertions.assertFalse(ascii.contains("handler"), "Goal payload serialized handler state.");
	}

	private PhantomProfile createProfile()
	{
		final PhantomProfile profile = _repository.create(null);
		_ownedProfileIds.add(profile.profileId());
		return profile;
	}

	private static PhantomGoal goal(long revision)
	{
		return new PhantomGoal(7, "goal.test", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("subject", "A"), new PhantomDomainRef("target", "B"), 100, 25, "method.test", List.of(new PhantomDomainRef("source", "A"), new PhantomDomainRef("source", "B")), new PhantomDomainRef("anchor", "C"), "purpose.test", 700, 50, 75, 123456789, Map.of("constraint.a", 1L, "constraint.b", -2L), "reason.test", revision);
	}
}
