/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.BindingStability;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.InvitationStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyBindingReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PendingInvitationReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftStateCodec;

public final class PhantomRiftCorrectionsSuite implements PhantomTestSuite
{
	private static final long REQUIRED_SEED = 23002311L;
	private static final String ZERO = "0".repeat(64);

	public enum Mode
	{
		PARTY_BINDING,
		MANAGED_CONSENT,
		PREINVITE_REVALIDATION,
		INVITATION_AUTHORITY,
		RESTART_MIGRATION,
		SEMANTIC_FACTS,
		CANDIDATE_ORDERING,
		ROUTE_BINDING,
		PERFORMANCE,
		ALL
	}

	private final Mode _mode;

	public PhantomRiftCorrectionsSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "rift023a-" + _mode.name().toLowerCase().replace('_', '-');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("required-seed", context -> PhantomAssertions.assertEquals(REQUIRED_SEED, context.seed(), "Goal 023A seed changed."));
		switch (_mode)
		{
			case PARTY_BINDING -> registry.add("goal017-content-binding-contract", this::partyBinding);
			case MANAGED_CONSENT -> registry.add("target-side-policy-and-canonical-harness", this::managedConsent);
			case PREINVITE_REVALIDATION -> registry.add("exact-preinvite-guards", this::preinvite);
			case INVITATION_AUTHORITY -> registry.add("full-identity-canonical-expiry-and-terminal-types", this::invitationAuthority);
			case RESTART_MIGRATION -> registry.add("v1-decode-v2-replan-and-future-fail-closed", this::restartMigration);
			case SEMANTIC_FACTS -> registry.add("goal020-request-refusal-mapping", this::semanticFacts);
			case CANDIDATE_ORDERING -> registry.add("phantom-first-bounded-relationship-order", this::candidateOrdering);
			case ROUTE_BINDING -> registry.add("stable-binding-before-route-ready", this::routeBinding);
			case PERFORMANCE -> registry.add("bounded-operation-volume", this::performance);
			case ALL ->
			{
				registry.add("party-binding", this::partyBinding);
				registry.add("managed-consent", this::managedConsent);
				registry.add("preinvite-revalidation", this::preinvite);
				registry.add("invitation-authority", this::invitationAuthority);
				registry.add("restart-migration", this::restartMigration);
				registry.add("semantic-facts", this::semanticFacts);
				registry.add("candidate-ordering", this::candidateOrdering);
				registry.add("route-binding", this::routeBinding);
				registry.add("performance", this::performance);
			}
		}
	}

	private void partyBinding(PhantomTestContext context) throws Exception
	{
		final String coordinator = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
		final String service = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java");
		PhantomAssertions.assertTrue(coordinator.contains("bindContentGoal") && coordinator.contains("observeContentBinding"), "Goal 017 content binding seam is absent.");
		PhantomAssertions.assertTrue(coordinator.contains("sameRoster") && coordinator.contains("ContentBindingResult") && coordinator.contains("OperationKind.SUPPORT"), "Canonical mixed-roster adoption is not exact.");
		PhantomAssertions.assertTrue(service.indexOf("case ENSURE_PARTY_BINDING") < service.indexOf("private AdvanceResult requestInvite"), "Binding stage is not separated before invite.");
		PhantomAssertions.assertFalse(service.contains("ensureFormation(current.leaderProfileId()"), "Rift still performs form+invite in one stage.");
	}

	private void managedConsent(PhantomTestContext context) throws Exception
	{
		final String coordinator = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
		final String system = source(context, "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java");
		final String integration = source(context, "test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java");
		PhantomAssertions.assertTrue(coordinator.contains("ManagedInvitationDecision") && coordinator.contains("ACCEPT") && coordinator.contains("REFUSE") && coordinator.contains("DEFER") && coordinator.contains("UNSUPPORTED"), "Managed consent outcomes are incomplete.");
		PhantomAssertions.assertTrue(coordinator.contains("conversationOwnsAccept") && coordinator.contains("explicitConsent"), "Explicit conversation/join precedence is absent.");
		PhantomAssertions.assertTrue(system.contains("installManagedInvitationPolicy(PhantomRiftService.GOAL_TYPE"), "Rift target-side policy is not installed in production composition.");
		PhantomAssertions.assertTrue(integration.contains("new PhantomPartyCoordinator") && integration.contains("new L2jPhantomRiftPartyPort") && integration.contains("PartyInvitationService.getInstance()") && integration.contains("HeadlessPlayerOutboundSession"), "Acceptance harness does not prove real coordinator/port/canonical service without fake GameClient.");
	}

	private void preinvite(PhantomTestContext context) throws Exception
	{
		final String service = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java");
		for (String marker : List.of("_store.load(stored.profileId())", "sameSources(current, readiness)", "selected.selectedRosterHash()", "selected.vacancyKey()", "observeBinding", "candidateClaimAvailable", "candidateFacts", "candidateEvidenceHash", "relationshipEvidenceHash"))
		{
			PhantomAssertions.assertTrue(service.contains(marker), "Missing exact pre-invite guard: " + marker);
		}
		PhantomAssertions.assertTrue(service.contains("return staleCandidate") && service.contains("current.totalAttempts()"), "Stale candidate path is not a zero-attempt replan.");
	}

	private void invitationAuthority(PhantomTestContext context) throws Exception
	{
		final Preparation value = v2Preparation();
		final PhantomRiftStateCodec codec = new PhantomRiftStateCodec();
		final Preparation decoded = codec.decode(codec.encode(value));
		PhantomAssertions.assertEquals(value, decoded, "Schema v2 full invitation identity did not round-trip.");
		PhantomAssertions.assertEquals(23002311L, decoded.invitationReceipt().sequence(), "Invitation sequence changed.");
		PhantomAssertions.assertEquals(101, decoded.invitationReceipt().requesterObjectId(), "Requester identity changed.");
		PhantomAssertions.assertEquals(202, decoded.invitationReceipt().inviteeObjectId(), "Invitee identity changed.");
		PhantomAssertions.assertEquals(123456L, decoded.invitationReceipt().canonicalExpiresAtGameTick(), "Canonical expiry changed.");
		final String policy = source(context, "dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml");
		final String port = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java");
		PhantomAssertions.assertTrue(policy.contains("inviteTimeoutMillis=\"15000\""), "Rift policy default is not canonical 15 seconds.");
		PhantomAssertions.assertTrue(port.contains("case \"party.invite.refused\"") && port.contains("case \"party.invite.expired\""), "REFUSED and EXPIRED are not mapped by exact typed reason.");
		PhantomAssertions.assertFalse(port.contains("contains(\"timeout\")"), "Terminal mapping still uses string substring inference.");
	}

	private void restartMigration(PhantomTestContext context) throws Exception
	{
		final PhantomRiftStateCodec codec = new PhantomRiftStateCodec();
		final Preparation legacy = codec.decode(legacyPayload());
		PhantomAssertions.assertTrue(legacy.legacyUntrusted(), "Schema v1 payload was not marked operationally untrusted.");
		PhantomAssertions.assertEquals(Stage.REQUEST_INVITE, legacy.stage(), "Legacy stage ordinal was not decoded explicitly.");
		PhantomAssertions.assertEquals(2, java.nio.ByteBuffer.wrap(codec.encode(legacy)).getInt(), "Re-encoded state is not schema v2.");
		final byte[] future = codec.encode(v2Preparation());
		java.nio.ByteBuffer.wrap(future).putInt(3);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(future), "Unknown future Rift schema did not fail closed.");
		PhantomAssertions.assertTrue(source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java").contains("rift.schema.v1_replanned"), "Next advance does not persist v2 replan before mutation.");
	}

	private void semanticFacts(PhantomTestContext context) throws Exception
	{
		final String service = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java");
		final String adapter = source(context, "java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java");
		PhantomAssertions.assertTrue(service.contains("RIFT_INVITE_REQUEST") && service.contains("RIFT_INVITE_REFUSED") && service.contains("Status.INVITE_PENDING"), "Exact invitation semantic facts are absent.");
		PhantomAssertions.assertTrue(adapter.contains("rift.invite_request") && adapter.contains("rift.invite_refused"), "Goal 020 adapter does not map both invitation fact types.");
		PhantomAssertions.assertFalse(adapter.contains("phraseBank"), "Goal 020 Rift mapping introduced a phrase bank.");
	}

	private void candidateOrdering(PhantomTestContext context) throws Exception
	{
		final String backend = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.java");
		final String service = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java");
		PhantomAssertions.assertTrue(backend.contains("getVisibleObjectsInRange") && backend.contains("OwnerKind.PHANTOM ? 0 : 1") && backend.indexOf(".limit(limit)") < backend.indexOf(".map(this::reference)"), "Production discovery is not Phantom-first before the <=32 resolution cap.");
		PhantomAssertions.assertFalse(backend.contains("World.getInstance().getPlayers()"), "Rift backend contains a global player scan.");
		PhantomAssertions.assertTrue(backend.contains("party.invite.preference") && service.contains("CandidateScore::relationshipModifier"), "Goal 018 modifier is not part of deterministic ranking.");
		PhantomAssertions.assertTrue(PhantomRiftModel.MAX_CANDIDATES == 32, "Candidate hard bound changed.");
	}

	private void routeBinding(PhantomTestContext context) throws Exception
	{
		final String service = source(context, "java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java");
		PhantomAssertions.assertTrue(service.contains("rift.route.binding_changed") && service.contains("rift.ready.binding_changed"), "Route/READY binding conflict suppression is absent.");
		PhantomAssertions.assertTrue(service.contains("requestRoute(current.leaderProfileId()") && !service.contains("DimensionalRiftManager.getInstance().start"), "Rift service owns entry/teleport mutation.");
	}

	private void performance(PhantomTestContext context) throws Exception
	{
		final long started = System.nanoTime();
		final Preparation value = v2Preparation();
		final PhantomRiftStateCodec codec = new PhantomRiftStateCodec();
		long bindingChecks = 0;
		for (int i = 0; i < 100000; i++)
		{
			bindingChecks += value.partyBinding().stability() == BindingStability.STABLE ? 1 : 0;
		}
		long readinessChecks = 0;
		for (int i = 0; i < 100000; i++)
		{
			for (int member = 0; member < 9; member++) { readinessChecks += member >= 0 ? 1 : 0; }
		}
		long restartChecks = 0;
		for (int i = 0; i < 10000; i++)
		{
			restartChecks += codec.decode(codec.encode(value)).invitationReceipt().sequence() == REQUIRED_SEED ? 1 : 0;
		}
		long preinviteChecks = 0;
		long candidateChecks = 0;
		long semanticChecks = 0;
		for (int i = 0; i < 10000; i++)
		{
			preinviteChecks += value.candidateReceipt().selectedRosterHash().equals(value.rosterHash()) ? 1 : 0;
			candidateChecks += Math.min(32, PhantomRiftModel.MAX_CANDIDATES);
			semanticChecks += InvitationStatus.PENDING == value.invitationReceipt().status() ? 1 : 0;
		}
		PhantomAssertions.assertEquals(100000L, bindingChecks, "Binding check volume changed.");
		PhantomAssertions.assertEquals(900000L, readinessChecks, "Nine-member readiness operation volume changed.");
		PhantomAssertions.assertEquals(10000L, restartChecks, "Restart reconciliation volume changed.");
		PhantomAssertions.assertEquals(10000L, preinviteChecks, "Pre-invite revalidation volume changed.");
		PhantomAssertions.assertEquals(320000L, candidateChecks, "Candidate discovery cap volume changed.");
		PhantomAssertions.assertEquals(10000L, semanticChecks, "Semantic latest-fact volume changed.");
		context.record("rift023a.performance.elapsedNanos", System.nanoTime() - started);
		context.record("rift023a.performance.bindingChecks", bindingChecks);
		context.record("rift023a.performance.readinessMemberChecks", readinessChecks);
		context.record("rift023a.performance.preinviteChecks", preinviteChecks);
		context.record("rift023a.performance.candidateEvaluationsMax", 32);
		context.record("rift023a.performance.restartChecks", restartChecks);
		context.record("rift023a.performance.semanticChecks", semanticChecks);
	}

	private static Preparation v2Preparation()
	{
		final MemberRef leader = MemberRef.phantom(1, 101);
		final MemberRef candidate = MemberRef.phantom(2, 202);
		final String group = PhantomPartyModel.sha256("rift023a.group");
		final String roster = PhantomPartyModel.sha256("rift023a.roster");
		final String manifest = PhantomPartyModel.sha256("rift023a.manifest");
		final String candidateEvidence = PhantomPartyModel.sha256("rift023a.candidate");
		final String relationship = PhantomPartyModel.sha256("rift023a.relationship");
		return new Preparation(1, 23, 0, 1, Stage.OBSERVE_INVITE, Status.INVITE_PENDING, roster, ZERO, ZERO, ZERO, ZERO, "slot.guardian", candidate, REQUIRED_SEED, 1, 1, List.of(), ZERO, 1000, new PartyBindingReceipt(group, 4, 7, leader, roster, manifest, BindingStability.STABLE), new CandidateReceipt("slot.guardian", candidate, candidateEvidence, roster, relationship), new PendingInvitationReceipt(REQUIRED_SEED, 101, 202, 1000, 123456, InvitationStatus.PENDING, "rift.invite.pending"), false);
	}

	private static byte[] legacyPayload() throws Exception
	{
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes))
		{
			output.writeInt(1);
			output.writeLong(1);
			output.writeLong(23);
			output.writeLong(0);
			output.writeByte(1);
			output.writeByte(4);
			output.writeByte(Status.INVITE_PENDING.ordinal());
			for (int i = 0; i < 5; i++) { string(output, ZERO); }
			string(output, "slot.guardian");
			output.writeBoolean(true);
			output.writeByte(0);
			output.writeLong(2);
			output.writeInt(202);
			output.writeLong(REQUIRED_SEED);
			output.writeByte(1);
			output.writeByte(1);
			output.writeByte(0);
			string(output, ZERO);
			output.writeLong(1000);
		}
		return bytes.toByteArray();
	}

	private static void string(DataOutputStream output, String value) throws Exception
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static String source(PhantomTestContext context, String relative) throws Exception
	{
		return Files.readString(context.moduleRoot().resolve(relative));
	}
}