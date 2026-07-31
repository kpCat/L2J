/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemoryRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.RelationshipRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.StoredState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStateCodec;
import org.l2jmobius.tests.phantoms.PhantomSocialTestDoubles.MemoryStore;

public final class PhantomSocialSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG,
		CODEC,
		PERSONALITY,
		DECAY,
		EVENTS,
		MODIFIERS,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 18001801L;
	private final Mode _mode;

	public PhantomSocialSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "social-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG -> catalog(registry);
			case CODEC -> codec(registry);
			case PERSONALITY -> personality(registry);
			case DECAY -> decay(registry);
			case EVENTS -> events(registry);
			case MODIFIERS -> modifiers(registry);
			case LIFECYCLE_PERFORMANCE -> lifecyclePerformance(registry);
		}
	}

	private static void catalog(PhantomTestRegistry registry)
	{
		registry.add("01-current-xml-is-strict-hashed-and-complete", context ->
		{
			final PhantomSocialCatalog first = catalog(context);
			final PhantomSocialCatalog second = catalog(context);
			PhantomAssertions.assertEquals(first.hash(), second.hash(), "Social catalog hash changed across identical loads.");
			PhantomAssertions.assertEquals(64, first.hash().length(), "Social catalog is not SHA-256 addressed.");
			PhantomAssertions.assertEquals(24, first.limits().relationships(), "Relationship limit changed.");
			PhantomAssertions.assertEquals(24, first.limits().memories(), "Memory limit changed.");
			for (String event : List.of("party.invite.accepted.outbound", "party.invite.accepted.inbound", "party.invite.refused.outbound", "party.invite.refused.inbound", "party.invite.expired.outbound", "party.member.joined", "party.member.left", "party.member.expelled", "party.leader.transferred", "party.support.received", "agreement.fulfilled", "agreement.broken", "debt.incurred", "debt.repaid"))
			{
				PhantomAssertions.assertEquals(event, first.requireEvent(event).key(), "Required social event is absent.");
			}
			for (String modifier : List.of("goal.persistence", "risk.tolerance", "party.invite.preference", "party.support.priority", "conversation.warmth", "conflict.escalation"))
			{
				PhantomAssertions.assertEquals(modifier, first.requireModifier(modifier).key(), "Required social modifier is absent.");
			}
			context.record("social.catalogHash", first.hash());
		});
		registry.add("02-xxe-and-structural-invalid-controls-fail-closed", context ->
		{
			final String source = Files.readString(catalogPath(context), StandardCharsets.UTF_8);
			rejectCatalog(context, "xxe", source.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE socialCatalog [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"));
			rejectCatalog(context, "duplicate-code", source.replace("<trait code=\"2\" key=\"caution\"/>", "<trait code=\"1\" key=\"caution\"/>"));
			rejectCatalog(context, "duplicate-key", source.replace("key=\"caution\"", "key=\"persistence\""));
			rejectCatalog(context, "missing-dimension", source.replace("\n\t\t<dimension code=\"107\" key=\"debt\" decayPerDay=\"0\"/>", ""));
			rejectCatalog(context, "invalid-ttl", source.replace("ttlMinutes=\"43200\"", "ttlMinutes=\"0\""));
			rejectCatalog(context, "invalid-weight", source.replace("value=\"2400\"", "value=\"4000\""));
			rejectCatalog(context, "excessive-limit", source.replace("relationships=\"24\"", "relationships=\"25\""));
			rejectCatalog(context, "unknown-source", source.replace("source=\"trait.persistence\"", "source=\"trait.unknown\""));
			rejectCatalog(context, "zero-event-delta", source.replace("<delta source=\"relationship.respect\" value=\"50\"/>", "<delta source=\"relationship.respect\" value=\"0\"/>"));
			rejectCatalog(context, "duplicate-zero-agreement", source.replace("<agreement key=\"accepted\" value=\"1\"/>", "<agreement key=\"accepted\" value=\"1\"/>\n\t\t\t<agreement key=\"accepted\" value=\"0\"/>"));
		});
		registry.add("03-data-is-explicit-tuning-policy", context ->
		{
			final String xml = Files.readString(catalogPath(context), StandardCharsets.UTF_8);
			PhantomAssertions.assertFalse(xml.toLowerCase(java.util.Locale.ROOT).contains("retail"), "Social tuning data claims unsupported retail authority.");
			PhantomAssertions.assertTrue(xml.contains("memoryDecayPerDay") && xml.contains("expired-lowest-salience-oldest-hash"), "Memory decay/eviction policy is not data driven.");
		});
	}

	private static void codec(PhantomTestRegistry registry)
	{
		registry.add("01-worst-case-roundtrip-remains-within-4096", context ->
		{
			final SocialState state = worstCaseState(catalog(context).hash());
			final PhantomSocialStateCodec codec = new PhantomSocialStateCodec();
			final byte[] payload = codec.encode(state);
			PhantomAssertions.assertTrue(payload.length <= 4096, "Worst-case social state exceeds 4096 bytes.");
			PhantomAssertions.assertEquals(state, codec.decode(payload), "Social state binary roundtrip changed durable truth.");
			context.record("social.worstCasePayloadBytes", payload.length);
		});
		registry.add("02-corruption-trailing-truncation-and-version-fail-closed", context ->
		{
			final PhantomSocialStateCodec codec = new PhantomSocialStateCodec();
			final byte[] payload = codec.encode(worstCaseState(catalog(context).hash()));
			final byte[] version = payload.clone();
			version[4] = 2;
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(version), "Unknown social codec version was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(payload, payload.length - 1)), "Truncated social payload was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(payload, payload.length + 1)), "Trailing social payload byte was accepted.");
			final byte[] invalidValue = payload.clone();
			final int relationshipStart = 56 + (PhantomSocialModel.MAX_TRAITS * 4);
			ByteBuffer.wrap(invalidValue).putShort(relationshipStart + 9, (short) 12000);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidValue), "Out-of-range relationship value was accepted.");
		});
		registry.add("03-duplicate-and-invalid-order-fail-closed", context ->
		{
			final PhantomSocialStateCodec codec = new PhantomSocialStateCodec();
			final byte[] payload = codec.encode(worstCaseState(catalog(context).hash()));
			final int relationshipStart = 56 + (PhantomSocialModel.MAX_TRAITS * 4);
			final byte[] duplicateSubject = payload.clone();
			System.arraycopy(duplicateSubject, relationshipStart + 1, duplicateSubject, relationshipStart + 57 + 1, Long.BYTES);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicateSubject), "Duplicate relationship subject was accepted.");
			final int memoryStart = relationshipStart + (PhantomSocialModel.MAX_RELATIONSHIPS * 57);
			final byte[] duplicateEvent = payload.clone();
			System.arraycopy(duplicateEvent, memoryStart, duplicateEvent, memoryStart + 95, 32);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicateEvent), "Duplicate memory event ID was accepted.");
			final byte[] invalidOrder = payload.clone();
			ByteBuffer.wrap(invalidOrder).putLong(relationshipStart + 57 + 1, 0L);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidOrder), "Invalid relationship ordering was accepted.");
		});
	}

	private static void personality(PhantomTestRegistry registry)
	{
		registry.add("01-same-profile-catalog-and-seed-is-byte-identical", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService first = service(catalog, store, 16);
			PhantomAssertions.assertEquals(Status.INITIALIZED, first.ensurePersonality(1).status(), "First personality access did not initialize state.");
			final byte[] before = new PhantomSocialStateCodec().encode(store.require(1).state());
			first.beginStop();
			PhantomAssertions.assertTrue(first.finishStop(), "First personality service did not stop.");
			final PhantomSocialService restarted = service(catalog, store, 16);
			PhantomAssertions.assertEquals(Status.READY, restarted.ensurePersonality(1).status(), "Restarted personality was not loaded.");
			final byte[] after = new PhantomSocialStateCodec().encode(store.require(1).state());
			PhantomAssertions.assertTrue(Arrays.equals(before, after), "Restart changed deterministic personality bytes.");
			restarted.beginStop();
			PhantomAssertions.assertTrue(restarted.finishStop(), "Restarted personality service did not stop.");
		});
		registry.add("02-ten-thousand-profiles-are-bounded-and-diverse", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfiles(1, 10000);
			final PhantomSocialService service = service(catalog, store, 32);
			final Set<String> signatures = new java.util.HashSet<>();
			for (long profileId = 1; profileId <= 10000; profileId++)
			{
				final var result = service.ensurePersonality(profileId);
				PhantomAssertions.assertTrue(result.available(), "Deterministic personality initialization failed.");
				PhantomAssertions.assertTrue(result.value().traits().values().stream().allMatch(value -> (value >= -10000) && (value <= 10000)), "Trait value is outside bounds.");
				signatures.add(result.value().traits().values().toString());
			}
			PhantomAssertions.assertTrue(signatures.size() > 9000, "Deterministic personality diversity collapsed.");
			PhantomAssertions.assertTrue(service.snapshot().cacheEntries() <= 32, "Personality cache exceeded its fixed bound.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Personality diversity service did not stop.");
		});
		registry.add("03-personality-input-has-no-stereotype-fields", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java"));
			final int method = source.indexOf("private SocialState createState");
			final int next = source.indexOf("private SocialState apply", method);
			final String body = source.substring(method, next);
			PhantomAssertions.assertFalse(body.contains("classId") || body.contains("race") || body.contains("name"), "Personality generation uses a class/race/name stereotype.");
			PhantomAssertions.assertTrue(body.contains("_catalog.hash()") && body.contains("_personalitySeed") && body.contains("profileId") && body.contains("trait.code()"), "Personality hash inputs are incomplete.");
		});
	}

	private static void decay(PhantomTestRegistry registry)
	{
		registry.add("01-exact-positive-negative-zero-and-clock-rollback", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfiles(1, 3);
			final PhantomSocialService service = service(catalog, store, 16);
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(1, "accepted", "party.invite.accepted.outbound", SubjectRef.phantom(2), 1000, 1000)).status(), "Positive decay fixture was not recorded.");
			PhantomAssertions.assertEquals(120, relationship(service, 1, SubjectRef.phantom(2), 1000, "trust"), "Elapsed zero changed trust.");
			PhantomAssertions.assertEquals(120, relationship(service, 1, SubjectRef.phantom(2), 1001, "trust"), "One-minute floor changed trust.");
			PhantomAssertions.assertEquals(101, relationship(service, 1, SubjectRef.phantom(2), 2439, "trust"), "1439-minute decay is wrong.");
			PhantomAssertions.assertEquals(100, relationship(service, 1, SubjectRef.phantom(2), 2440, "trust"), "One-day decay is wrong.");
			PhantomAssertions.assertEquals(100, relationship(service, 1, SubjectRef.phantom(2), 2441, "trust"), "1441-minute decay is wrong.");
			PhantomAssertions.assertEquals(120, relationship(service, 1, SubjectRef.phantom(2), 900, "trust"), "Clock rollback resurrected or changed trust.");
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(2, "broken", "agreement.broken", SubjectRef.phantom(3), 1000, 1000)).status(), "Negative decay fixture was not recorded.");
			PhantomAssertions.assertEquals(-480, relationship(service, 2, SubjectRef.phantom(3), 2440, "trust"), "Negative one-day decay is wrong.");
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(3, "debt", "debt.incurred", SubjectRef.phantom(1), 1000, 1000)).status(), "Zero-rate debt fixture was not recorded.");
			PhantomAssertions.assertEquals(500, relationship(service, 3, SubjectRef.phantom(1), Long.MAX_VALUE, "debt"), "Zero-rate debt decayed.");
			PhantomAssertions.assertEquals(0, relationship(service, 1, SubjectRef.phantom(2), Long.MAX_VALUE, "trust"), "Huge elapsed time did not decay exactly to zero.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Decay service did not stop.");
		});
		registry.add("02-query-frequency-independent-and-write-free", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			service.record(event(1, "frequency", "party.invite.accepted.outbound", SubjectRef.character(77), 1000, 1000));
			final int writes = store.writes();
			for (long minute : List.of(1001L, 1100L, 1500L, 2000L))
			{
				service.snapshot(1, SubjectRef.character(77), 24, minute);
			}
			final int stepped = relationship(service, 1, SubjectRef.character(77), 2440, "trust");
			final int direct = relationship(service, 1, SubjectRef.character(77), 2440, "trust");
			PhantomAssertions.assertEquals(direct, stepped, "Intermediate queries changed final decay.");
			PhantomAssertions.assertEquals(writes, store.writes(), "Query-only decay wrote persistence.");
			PhantomAssertions.assertEquals(1000L, store.require(1).state().logicalMinute(), "Query changed durable monotonic boundary.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Frequency service did not stop.");
		});
		registry.add("03-ttl-expiry-is-exact-at-boundary", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			service.record(event(1, "ttl", "party.invite.accepted.outbound", SubjectRef.phantom(2), 1000, 1000));
			PhantomAssertions.assertEquals(1, service.snapshot(1, SubjectRef.phantom(2), 24, 44199).value().memories().size(), "Memory expired before exact TTL boundary.");
			PhantomAssertions.assertEquals(0, service.snapshot(1, SubjectRef.phantom(2), 24, 44200).value().memories().size(), "Memory survived exact TTL boundary.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "TTL service did not stop.");
		});
	}

	private static void events(PhantomTestRegistry registry)
	{
		registry.add("01-idempotent-distinct-out-of-order-and-concurrent-events", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			final SocialEvent first = event(1, "idempotent", "party.invite.accepted.outbound", SubjectRef.character(77), 2000, 1000);
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(first).status(), "First social event was not recorded.");
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, service.record(first).status(), "Duplicate social event was applied twice.");
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(1, "distinct", first.eventKey(), first.subject(), 2000, 1000)).status(), "Distinct event ID was collapsed.");
			PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(1, "out-of-order", "party.member.joined", first.subject(), 1000, 1000)).status(), "Out-of-order event was rejected.");
			PhantomAssertions.assertEquals(2000L, store.require(1).state().logicalMinute(), "Out-of-order event reversed monotonic time.");
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			final Thread left = eventThread(service, event(1, "concurrent-left", "party.member.joined", first.subject(), 2001, 1000), failure);
			final Thread right = eventThread(service, event(1, "concurrent-right", "party.member.joined", first.subject(), 2001, 1000), failure);
			left.start();
			right.start();
			left.join(3000);
			right.join(3000);
			PhantomAssertions.assertFalse(left.isAlive() || right.isAlive(), "Concurrent social writers did not terminate.");
			PhantomAssertions.assertEquals(null, failure.get(), "Concurrent social writer failed.");
			PhantomAssertions.assertEquals(5, service.snapshot(1, first.subject(), 24, 2001).value().memories().size(), "Concurrent social events lost a durable update.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Event service did not stop.");
		});
		registry.add("02-optimistic-reload-insert-race-and-three-attempt-bound", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore conflictStore = new MemoryStore();
			conflictStore.addProfile(1);
			final PhantomSocialService conflictService = service(catalog, conflictStore, 16);
			conflictService.ensurePersonality(1);
			conflictStore.conflictNext(1);
			PhantomAssertions.assertEquals(Status.RECORDED, conflictService.record(event(1, "retry", "party.member.joined", SubjectRef.character(10), 1000, 1000)).status(), "One optimistic conflict did not reload and retry.");
			conflictStore.alwaysConflict();
			PhantomAssertions.assertEquals(Status.CONFLICT, conflictService.record(event(1, "fourth", "party.member.joined", SubjectRef.character(11), 1001, 1000)).status(), "Three-attempt optimistic bound was not enforced.");
			conflictService.beginStop();
			PhantomAssertions.assertTrue(conflictService.finishStop(), "Conflict service did not stop.");

			final MemoryStore insertStore = new MemoryStore();
			insertStore.addProfile(2);
			insertStore.insertCollision();
			final PhantomSocialService insertService = service(catalog, insertStore, 16);
			final Status insertStatus = insertService.record(event(2, "insert-race", "party.member.joined", SubjectRef.character(12), 1000, 1000)).status();
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, insertStatus, "Insert collision did not reload the durable winner.");
			PhantomAssertions.assertEquals(1, insertService.snapshot(2, SubjectRef.character(12), 24, 1000).value().memories().size(), "Insert-race event was duplicated.");
			insertService.beginStop();
			PhantomAssertions.assertTrue(insertService.finishStop(), "Insert-race service did not stop.");
		});
		registry.add("03-capacity-eviction-asymmetry-agreements-and-debt", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfiles(1, 6);
			final PhantomSocialService service = service(catalog, store, 16);
			for (int subject = 1; subject <= 24; subject++)
			{
				PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(1, "important-" + subject, "debt.incurred", SubjectRef.character(1000 + subject), 1000, 1000)).status(), "Important relationship fixture failed.");
			}
			PhantomAssertions.assertEquals(Status.CAPACITY_REACHED, service.record(event(1, "important-25", "debt.incurred", SubjectRef.character(1025), 1000, 1000)).status(), "Important relationship was silently evicted.");

			for (int subject = 1; subject <= 25; subject++)
			{
				PhantomAssertions.assertEquals(Status.RECORDED, service.record(event(2, "neutral-" + subject, "debt.repaid", SubjectRef.character(2000 + subject), 1000, 1)).status(), "Neutral relationship fixture failed.");
			}
			PhantomAssertions.assertEquals(24, store.require(2).state().relationships().size(), "Neutral relationship eviction did not preserve the declared bound.");
			PhantomAssertions.assertTrue(store.require(2).state().relationship(SubjectRef.character(2025)) != null, "New neutral relationship was not retained deterministically.");

			final List<String> memoryIds = new ArrayList<>();
			for (int index = 0; index < 25; index++)
			{
				final SocialEvent event = event(3, "memory-" + index, "party.member.joined", SubjectRef.character(3001), 1000, 1000);
				memoryIds.add(event.eventId());
				service.record(event);
			}
			PhantomAssertions.assertEquals(24, store.require(3).state().memories().size(), "Memory eviction did not preserve the declared bound.");
			PhantomAssertions.assertFalse(store.require(3).state().containsEvent(memoryIds.stream().min(String::compareTo).orElseThrow()), "Memory lexical tie-break did not evict the lowest hash.");

			service.record(event(4, "a-to-b", "party.invite.accepted.outbound", SubjectRef.phantom(5), 1000, 1000));
			service.record(event(5, "b-to-a", "agreement.broken", SubjectRef.phantom(4), 1000, 1000));
			PhantomAssertions.assertTrue(relationship(service, 4, SubjectRef.phantom(5), 1000, "trust") > 0, "A-to-B subjective trust is not positive.");
			PhantomAssertions.assertTrue(relationship(service, 5, SubjectRef.phantom(4), 1000, "trust") < 0, "B-to-A subjective trust is not independently negative.");

			service.record(event(6, "fulfilled", "agreement.fulfilled", SubjectRef.character(6001), 1000, 1000));
			service.record(event(6, "broken", "agreement.broken", SubjectRef.character(6001), 1000, 1000));
			service.record(event(6, "debt-in", "debt.incurred", SubjectRef.character(6001), 1000, 1000));
			service.record(event(6, "debt-out", "debt.repaid", SubjectRef.character(6001), 1000, 1000));
			final var snapshot = service.snapshot(6, SubjectRef.character(6001), 24, 1000).value();
			PhantomAssertions.assertEquals(1, snapshot.relationship().agreements().get("fulfilled"), "Fulfilled agreement counter changed.");
			PhantomAssertions.assertEquals(1, snapshot.relationship().agreements().get("broken"), "Broken agreement counter changed.");
			PhantomAssertions.assertEquals(0, snapshot.relationship().relationship().get("debt"), "Debt repayment did not preserve signed owner perspective.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Capacity/asymmetry service did not stop.");
		});
		registry.add("04-catalog-drift-fails-closed-without-mutation", context ->
		{
			final PhantomSocialCatalog first = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService writer = service(first, store, 16);
			writer.ensurePersonality(1);
			writer.beginStop();
			PhantomAssertions.assertTrue(writer.finishStop(), "Catalog-drift writer did not stop.");
			final String source = Files.readString(catalogPath(context), StandardCharsets.UTF_8);
			final Path changed = Files.createTempFile("social-catalog-drift-", ".xml");
			try
			{
				Files.writeString(changed, source + System.lineSeparator(), StandardCharsets.UTF_8);
				final PhantomSocialCatalog second = PhantomSocialCatalog.load(changed);
				PhantomAssertions.assertFalse(first.hash().equals(second.hash()), "Catalog drift fixture retained the old content hash.");
				final int writes = store.writes();
				final PhantomSocialService reader = service(second, store, 16);
				PhantomAssertions.assertEquals(Status.AUTHORITY_STALE, reader.snapshot(1, SubjectRef.character(1), 1, 1000).status(), "Catalog drift was reinterpreted.");
				PhantomAssertions.assertEquals(writes, store.writes(), "Catalog drift mutated the existing payload.");
				reader.beginStop();
				PhantomAssertions.assertTrue(reader.finishStop(), "Catalog-drift reader did not stop.");
			}
			finally
			{
				Files.deleteIfExists(changed);
			}
		});
	}

	private static void modifiers(PhantomTestRegistry registry)
	{
		registry.add("01-all-six-modifiers-are-generic-explainable-and-pure", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			service.ensurePersonality(1);
			final int writes = store.writes();
			for (String key : List.of("goal.persistence", "risk.tolerance", "party.invite.preference", "party.support.priority", "conversation.warmth", "conflict.escalation"))
			{
				final var modifier = service.modifier(1, SubjectRef.character(99), key, 1000);
				PhantomAssertions.assertTrue(modifier.available(), "Required modifier query failed.");
				PhantomAssertions.assertEquals(key, modifier.value().modifierKey(), "Modifier key changed.");
				PhantomAssertions.assertTrue(modifier.value().evidenceKeys().size() <= 8, "Modifier evidence exceeded eight keys.");
			}
			PhantomAssertions.assertFalse(service.modifier(1, SubjectRef.character(99), "goal.persistence", 1000).value().traitContributions().isEmpty(), "Personality does not explain persistence.");
			PhantomAssertions.assertFalse(service.modifier(1, SubjectRef.character(99), "risk.tolerance", 1000).value().traitContributions().isEmpty(), "Personality does not explain risk.");
			PhantomAssertions.assertEquals(0, service.modifier(1, SubjectRef.character(99), "party.invite.preference", 1000).value().deltaBasisPoints(), "Unknown subject is not neutral for party preference.");
			PhantomAssertions.assertEquals(writes, store.writes(), "Pure modifier query wrote persistence.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Modifier purity service did not stop.");
		});
		registry.add("02-relationships-agreements-and-debt-have-configured-direction", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			final SubjectRef subject = SubjectRef.character(77);
			service.record(event(1, "accepted", "party.invite.accepted.outbound", subject, 1000, 1000));
			service.record(event(1, "joined", "party.member.joined", subject, 1000, 1000));
			service.record(event(1, "fulfilled", "agreement.fulfilled", subject, 1000, 1000));
			final int positive = service.modifier(1, subject, "party.invite.preference", 1000).value().deltaBasisPoints();
			PhantomAssertions.assertTrue(positive > 0, "Trust/friendship/reliability/fulfilled agreement did not raise party preference.");
			final int supportBeforeDebt = service.modifier(1, subject, "party.support.priority", 1000).value().deltaBasisPoints();
			service.record(event(1, "debt", "debt.incurred", subject, 1000, 1000));
			final int supportAfterDebt = service.modifier(1, subject, "party.support.priority", 1000).value().deltaBasisPoints();
			PhantomAssertions.assertTrue(supportAfterDebt < supportBeforeDebt, "Positive subject debt did not lower support in the configured direction.");

			final SubjectRef hostile = SubjectRef.character(78);
			for (int index = 0; index < 30; index++)
			{
				service.record(event(1, "broken-" + index, "agreement.broken", hostile, 1000, 1000));
			}
			final var negative = service.modifier(1, hostile, "party.invite.preference", 1000).value();
			PhantomAssertions.assertEquals(-3000, negative.deltaBasisPoints(), "Negative social modifier did not clamp at -3000.");
			PhantomAssertions.assertTrue(negative.relationshipContributions().stream().anyMatch(value -> value.sourceKey().equals("relationship.anger") && (value.deltaBasisPoints() < 0)), "Anger contribution direction is absent.");
			PhantomAssertions.assertTrue(negative.agreementContributions().stream().anyMatch(value -> value.sourceKey().equals("agreement.broken") && (value.deltaBasisPoints() < 0)), "Broken agreement contribution direction is absent.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Directional modifier service did not stop.");
		});
		registry.add("03-positive-modifier-clamps-at-3000", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			final SubjectRef subject = SubjectRef.character(77);
			for (int index = 0; index < 200; index++)
			{
				service.record(event(1, "accepted-out-" + index, "party.invite.accepted.outbound", subject, 1000, 1000));
				service.record(event(1, "accepted-in-" + index, "party.invite.accepted.inbound", subject, 1000, 1000));
			}
			for (int index = 0; index < 20; index++)
			{
				service.record(event(1, "fulfilled-" + index, "agreement.fulfilled", subject, 1000, 1000));
				service.record(event(1, "debt-" + index, "debt.incurred", subject, 1000, 1000));
			}
			PhantomAssertions.assertEquals(3000, service.modifier(1, subject, "party.invite.preference", 1000).value().deltaBasisPoints(), "Positive social modifier did not clamp at 3000.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Positive clamp service did not stop.");
		});
	}

	private static void lifecyclePerformance(PhantomTestRegistry registry)
	{
		registry.add("01-disabled-system-is-socially-inert", _ ->
		{
			final PhantomSystem disabled = new PhantomSystem(PhantomPlayersConfig.Settings.disabled());
			PhantomAssertions.assertFalse(disabled.start(), "Disabled PhantomSystem started.");
			PhantomAssertions.assertEquals(PhantomSocialService.ServiceState.STOPPED, disabled.snapshot().social().state(), "Disabled PhantomSystem created a social service.");
		});
		registry.add("02-stop-waits-for-blocked-write-and-finishes-with-zero-claims", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore delegate = new MemoryStore();
			delegate.addProfile(1);
			final BlockingStore store = new BlockingStore(delegate);
			final PhantomSocialService service = service(catalog, store, 16);
			service.ensurePersonality(1);
			store.block();
			final AtomicReference<Status> outcome = new AtomicReference<>();
			final Thread writer = new Thread(() -> outcome.set(service.record(event(1, "blocked", "party.member.joined", SubjectRef.character(77), 1000, 1000)).status()), "social-blocked-write-test");
			writer.start();
			PhantomAssertions.assertTrue(store.awaitBlocked(), "Social write did not reach the injected block.");
			service.beginStop();
			PhantomAssertions.assertFalse(service.finishStop(), "Social service stopped with an active write.");
			store.release();
			writer.join(3000);
			PhantomAssertions.assertFalse(writer.isAlive(), "Blocked social writer did not terminate.");
			PhantomAssertions.assertEquals(Status.RECORDED, outcome.get(), "Blocked social write lost its durable result.");
			PhantomAssertions.assertTrue(service.finishStop(), "Social service did not finish after write drain.");
			PhantomAssertions.assertEquals(0, service.snapshot().operationClaims(), "Stopped social service retained operation claims.");
			PhantomAssertions.assertEquals(0, service.snapshot().writeClaims(), "Stopped social service retained write claims.");
		});
		registry.add("03-100k-pure-evaluations-write-zero", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfile(1);
			final PhantomSocialService service = service(catalog, store, 16);
			service.ensurePersonality(1);
			final int writes = store.writes();
			final long started = System.nanoTime();
			for (int index = 0; index < 100000; index++)
			{
				final var modifier = service.modifier(1, SubjectRef.character(77), "goal.persistence", 1000 + index);
				if (!modifier.available())
				{
					throw new AssertionError("Pure social modifier evaluation failed.");
				}
			}
			final long elapsed = System.nanoTime() - started;
			PhantomAssertions.assertEquals(writes, store.writes(), "100000 pure social evaluations wrote persistence.");
			context.record("socialPerformance.pureEvaluations", 100000);
			context.record("socialPerformance.pureElapsedNanos", elapsed);
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Pure-evaluation service did not stop.");
		});
		registry.add("04-10k-profile-cache-is-bounded-and-no-worker-exists", context ->
		{
			final PhantomSocialCatalog catalog = catalog(context);
			final MemoryStore store = new MemoryStore();
			store.addProfiles(1, 10000);
			final PhantomSocialService service = service(catalog, store, 16);
			for (long profileId = 1; profileId <= 10000; profileId++)
			{
				PhantomAssertions.assertTrue(service.ensurePersonality(profileId).available(), "Synthetic social profile initialization failed.");
			}
			PhantomAssertions.assertTrue(service.snapshot().cacheEntries() <= 16, "Social cache exceeded 16 entries.");
			final Path directory = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/social");
			final String source;
			try (var files = Files.walk(directory))
			{
				source = files.filter(path -> path.toString().endsWith(".java")).map(path ->
				{
					try
					{
						return Files.readString(path);
					}
					catch (Exception e)
					{
						throw new IllegalStateException(e);
					}
				}).reduce("", String::concat);
			}
			PhantomAssertions.assertFalse(source.contains("new Thread") || source.contains("ExecutorService") || source.contains("ScheduledFuture") || source.contains("CompletableFuture"), "Social production owns a worker, executor or future.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "10k-profile social service did not stop.");
			context.record("socialPerformance.syntheticProfiles", 10000);
		});
	}

	private static PhantomSocialCatalog catalog(PhantomTestContext context)
	{
		return PhantomSocialCatalog.load(catalogPath(context));
	}

	private static Path catalogPath(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml");
	}

	private static PhantomSocialService service(PhantomSocialCatalog catalog, PersistencePort store, int cacheLimit)
	{
		final PhantomSocialService service = new PhantomSocialService(catalog, store, SEED, cacheLimit);
		PhantomAssertions.assertTrue(service.start(), "Social service did not start.");
		return service;
	}

	private static SocialEvent event(long owner, String identity, String key, SubjectRef subject, long minute, int magnitude)
	{
		return new SocialEvent(owner, PhantomSocialModel.sha256("test.event|" + owner + '|' + identity), key, subject, minute, magnitude, PhantomSocialModel.sha256("test.evidence|" + identity));
	}

	private static int relationship(PhantomSocialService service, long owner, SubjectRef subject, long minute, String key)
	{
		final var result = service.snapshot(owner, subject, 24, minute);
		PhantomAssertions.assertTrue(result.available(), "Social relationship snapshot is unavailable.");
		return result.value().relationship().relationship().get(key);
	}

	private static Thread eventThread(PhantomSocialService service, SocialEvent event, AtomicReference<Throwable> failure)
	{
		return new Thread(() ->
		{
			try
			{
				if (service.record(event).status() != Status.RECORDED)
				{
					throw new AssertionError("Concurrent event was not recorded.");
				}
			}
			catch (Throwable throwable)
			{
				failure.compareAndSet(null, throwable);
			}
		}, "social-event-" + event.eventId().substring(0, 8));
	}

	private static SocialState worstCaseState(String authorityHash)
	{
		final NavigableMap<Integer, Integer> traits = new TreeMap<>();
		for (int code = 1; code <= PhantomSocialModel.MAX_TRAITS; code++)
		{
			traits.put(code, (code & 1) == 0 ? 10000 : -10000);
		}
		final List<RelationshipRecord> relationships = new ArrayList<>();
		final List<MemoryRecord> memories = new ArrayList<>();
		for (int index = 1; index <= PhantomSocialModel.MAX_RELATIONSHIPS; index++)
		{
			relationships.add(new RelationshipRecord(SubjectRef.phantom(index), Collections.nCopies(PhantomSocialModel.DIMENSION_COUNT, (index & 1) == 0 ? 10000 : -10000), Collections.nCopies(PhantomSocialModel.AGREEMENT_COUNT, 65535), 1000, 1000));
			memories.add(new MemoryRecord(PhantomSocialModel.sha256("worst.event." + index), 1001, SubjectRef.phantom(index), 999, 10000, 10000, 10000, PhantomSocialModel.sha256("worst.evidence." + index)));
		}
		return new SocialState(authorityHash, SEED, 1000, traits, relationships, memories);
	}

	private static void rejectCatalog(PhantomTestContext context, String name, String content) throws Exception
	{
		final Path path = Files.createTempFile("social-invalid-" + name + '-', ".xml");
		try
		{
			Files.writeString(path, content, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSocialCatalog.load(path), "Invalid social catalog control was accepted: " + name);
		}
		finally
		{
			Files.deleteIfExists(path);
		}
	}

	private static final class BlockingStore implements PersistencePort
	{
		private final MemoryStore _delegate;
		private final CountDownLatch _blocked = new CountDownLatch(1);
		private final CountDownLatch _release = new CountDownLatch(1);
		private final AtomicBoolean _blocking = new AtomicBoolean();

		private BlockingStore(MemoryStore delegate)
		{
			_delegate = delegate;
		}

		private void block()
		{
			_blocking.set(true);
		}

		private boolean awaitBlocked() throws InterruptedException
		{
			return _blocked.await(2, TimeUnit.SECONDS);
		}

		private void release()
		{
			_release.countDown();
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return _delegate.profileExists(profileId);
		}

		@Override
		public Optional<StoredState> load(long profileId)
		{
			return _delegate.load(profileId);
		}

		@Override
		public StoredState save(long profileId, long expectedRowVersion, SocialState state)
		{
			if (_blocking.get())
			{
				_blocked.countDown();
				try
				{
					if (!_release.await(3, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Injected social write release timed out.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Injected social write was interrupted.", e);
				}
			}
			return _delegate.save(profileId, expectedRowVersion, state);
		}
	}
}
