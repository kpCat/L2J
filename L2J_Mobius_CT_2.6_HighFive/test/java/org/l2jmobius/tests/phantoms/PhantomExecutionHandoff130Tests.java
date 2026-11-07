/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Dependency;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase;

/** The same protocol assertions are used by the existing SQL130 runner. No Players or alternate engines. */
public final class PhantomExecutionHandoff130Tests
{
	private PhantomExecutionHandoff130Tests() { }
	public static void register(PhantomTestRegistry registry)
	{
		registry.add("execution-root-planning-cannot-borrow-completion-after-request", _ ->
		{
			final var owner = PhantomExecutionHandoff.solo(1); final var initial = PhantomExecutionHandoff.initial(owner);
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, initial);
			try (var earned = gate.tryAcquire(1, owner))
			{
				gate.publish(1, 1, initial.request(new PhantomDomainRef("execution.party", "group"), "party", 1000, 5000));
				try (var fresh = gate.enterOwner(PhantomExecutionHandoff::solo))
				{ PhantomAssertions.assertEquals(null, gate.tryAcquireGoalContinuation(1, owner), "Fresh scheduler planning borrowed preparation/completion during REQUEST."); }
				try (var captured = earned.enterScope(); var completion = gate.tryAcquireGoalContinuation(1, owner))
				{ PhantomAssertions.assertTrue(completion != null, "An earned command lost its lawful root completion during REQUEST."); }
			}
		});
		registry.add("execution-goal-publication-codec-and-authority-bounds", _ ->
		{
			final var companion = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.Companion(
				org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.COMPONENT_TYPE, 7, 1, "b".repeat(64));
			final var pending = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication(
				org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.Status.PENDING, 11, 1000, 31000, false, "a".repeat(64), List.of(companion), 0);
			PhantomAssertions.assertEquals(pending, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.decode(pending.encode()), "Publication codec lost its exact root/companion authority.");
			PhantomAssertions.assertEquals(pending.requestId(), pending.committed(4).requestId(), "Publication acknowledgement changed request identity.");
			PhantomAssertions.assertTrue(pending.encode().length < org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Publication metadata exceeded the existing component payload limit.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.Companion("commerce.receipt", 0, 1, "c".repeat(64)), "Goal publication gained an independent Commerce writer.");
			final var malformed = java.util.Arrays.copyOf(pending.encode(), pending.encode().length + 1);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.decode(malformed), "Publication accepted trailing bytes.");
		});
		registry.add("execution-production-lifecycle-denies-an-uninstalled-delegate", _ ->
		{
			final var bridge = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge(true);
			PhantomAssertions.assertThrows(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException.class,
				() -> bridge.beforeMaterialize(1, 51), "Early startup bypassed execution ownership before composition was installed.");
			bridge.install(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.none()); bridge.beforeMaterialize(1, 51);
		});
		registry.add("execution-recovery-redirection-preserves-all-original-obligations", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var party = new PhantomDomainRef("execution.party", "group1");
			final var root = new Dependency("goal.runtime", 1, 3, "a".repeat(64));
			final var proof = List.of(root, new Dependency("learning.intent", 1, 11, "b".repeat(64)), new Dependency("commerce.receipt", 0, -1, ""));
			final var transferred = PhantomExecutionHandoff.initial(solo).request(party, "party", 1000, 5000).yield(1001).checkpoint(proof, 1002).transfer(1003);
			final var redirect = transferred.request(solo, "recipient-unavailable", 1004, 5000).yield(1005);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> redirect.checkpoint(List.of(root), 1006), "Recovery redirection discarded the original child/receipt proof.");
			final var returned = redirect.checkpoint(proof, 1006).transfer(1007);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> returned.recovered(List.of(root)), "RECOVER acknowledgement discarded unresolved obligations.");
			PhantomAssertions.assertEquals(proof, returned.recovered(proof).dependencies(), "A lawful redirect lost the original obligations.");
		});
		registry.add("execution-suspended-child-adopts-parent-without-new-progress", _ ->
		{
			final var subject = new PhantomDomainRef("profile", "1");
			final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(9, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
				subject, null, 1, 0, null, List.of(), null, "farm", 1, 0, 0, 9999, java.util.Map.of(), "farm", 4);
			final var child = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(9, "skill.learn", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
				subject, new PhantomDomainRef("skill", "3:1"), 3, 2, null, List.of(), null, "learning.autonomous", 1, 7, 19, 5000,
				java.util.Map.of("owner.revision", 2L, "owner.object", 51L, "journey.started", 1000L, "journey.paused", 1500L, "journey.remaining", 3500L), "learning", 11);
			final var next = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.rebindSuspended(child, root);
			PhantomAssertions.assertEquals(4L, next.constraints().get("owner.revision"), "Suspended child kept an obsolete planning binding.");
			PhantomAssertions.assertTrue(next.revision() == 11 && next.currentAmount() == 2 && next.expenseBudget() == 19 && next.riskBudget() == 7
				&& next.deadlineEpochMillis() == 5000 && next.constraints().get("journey.remaining") == 3500 && next.constraints().get("journey.paused") == 1500,
				"Parent adoption restarted child work or lost its progress/resources.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.rebindSuspended(
				org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(child, 1600), root), "An executing child silently changed parent binding.");
		});
		registry.add("16-execution-request-yield-checkpoint-transfer-recovery", _ ->
		{
			final var solo = new PhantomDomainRef("execution.solo", "profile1");
			final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var initial = PhantomExecutionHandoff.initial(solo);
			final var requested = initial.request(party, "request1", 1000, 5000);
			PhantomAssertions.assertEquals(Phase.REQUESTED, requested.phase(), "REQUEST was not durable protocol state.");
			PhantomAssertions.assertEquals(initial.generation(), requested.generation(), "REQUEST prematurely transferred execution.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> requested.transfer(1001), "Recipient ran before safe YIELD/CHECKPOINT.");
			final var yielded = requested.yield(1001);
			final var dependencies = List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64)), new Dependency(org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE, 0, -1, ""));
			final var checkpointed = yielded.checkpoint(dependencies, 1002);
			final var transferred = checkpointed.transfer(1003);
			PhantomAssertions.assertEquals(party, transferred.owner(), "CAS recipient did not become execution owner.");
			PhantomAssertions.assertEquals(2L, transferred.generation(), "Execution generation did not fence stale source callbacks.");
			PhantomAssertions.assertEquals(dependencies, transferred.dependencies(), "Transfer discarded the resumable source proof.");
			for (var state : List.of(initial, requested, yielded, checkpointed, transferred, transferred.recovered(dependencies)))
			{ PhantomAssertions.assertEquals(state, PhantomExecutionHandoff.decode(state.encode()), "Restart changed ownership/checkpoint."); }
			for (var state : List.of(requested, yielded, checkpointed))
			{
				final var recovered = state.recoverSource();
				PhantomAssertions.assertEquals(solo, recovered.owner(), "Cancellation/timeout stole source rights.");
				PhantomAssertions.assertEquals(Phase.RECOVERING, recovered.phase(), "Cancellation bypassed recovery of a potentially paused child.");
				{
					final var restarted = PhantomExecutionHandoff.decode(recovered.encode());
					final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1);
					gate.publish(1, 0, restarted);
					PhantomAssertions.assertEquals(null, gate.tryAcquire(1, solo), "Restart opened source execution before checkpoint recovery.");
					gate.publish(1, 1, restarted.recovered(dependencies));
					try (var resumed = gate.tryAcquire(1, solo)) { PhantomAssertions.assertTrue(resumed != null && resumed.valid(), "Recovered child could not resume."); }
				}
				PhantomAssertions.assertEquals(2L, recovered.generation(), "Source recovery reused a stale grant.");
			}
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> checkpointed.transfer(5000), "An expired request executed instead of recovering its source.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> requested.request(party, "second", 1002, 6000), "Concurrent requester replaced an accepted transfer.");
		});
		registry.add("17-execution-barrier-drains-and-fences-old-and-unknown-grants", _ ->
		{
			final var solo = new PhantomDomainRef("execution.solo", "profile1"); final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var active = PhantomExecutionHandoff.initial(solo);
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1);
			gate.publish(1, 0, active);
			final var old = gate.tryAcquire(1, solo);
			final var request = active.request(party, "request1", 1000, 5000); gate.publish(1, 1, request);
			PhantomAssertions.assertFalse(old.valid(), "Previously admitted work did not observe REQUEST.");
			PhantomAssertions.assertEquals(null, gate.tryAcquire(1, solo), "Source acquired a fresh conflicting operation after REQUEST.");
			PhantomAssertions.assertEquals(null, gate.tryAcquire(1, party), "Recipient ran before transfer.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> gate.quiescent(1, 1, () -> true), "Handoff ignored a running planning/handler/persistence lease.");
			old.close(); old.close();
			final var checkpoint = request.yield(1001).checkpoint(List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64))), 1002);
			gate.quiescent(1, 1, () -> { gate.publish(1, 2, checkpoint); gate.publish(1, 3, checkpoint.transfer(1003).recovered(checkpoint.dependencies())); return true; });
			PhantomAssertions.assertEquals(null, gate.tryAcquire(1, solo), "Stale source callback reacquired execution after CAS.");
			try (var current = gate.tryAcquire(1, party)) { PhantomAssertions.assertTrue(current != null && current.valid(), "Recipient did not receive exclusive execution."); }
			final var returnRequest = checkpoint.transfer(1003).recovered(checkpoint.dependencies()).request(solo, "return", 1100, 6000); gate.publish(1, 4, returnRequest);
			final var recovered = returnRequest.recoverSource().recovered(checkpoint.dependencies());
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> gate.quiescent(1, 4, () -> { gate.publish(1, 5, recovered); throw new IllegalStateException("lost SQL acknowledgement"); }), "Injected unknown transfer was swallowed.");
			PhantomAssertions.assertEquals(null, gate.tryAcquire(1, party), "Unknown write acknowledgement reopened execution.");
			gate.acknowledgeRecovery(1, 5, recovered);
			try (var resumed = gate.tryAcquire(1, party)) { PhantomAssertions.assertTrue(resumed != null && resumed.valid(), "Durable/native recovery could not resume the actual winning owner."); }
			PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> gate.publish(1, 3, checkpoint.transfer(1003)), "Late restart page restored stale execution.");
		});
		registry.add("18-old-pulse-and-native-stamp-cannot-borrow-returned-source-grant", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1);
			final var initial = PhantomExecutionHandoff.initial(solo); gate.publish(1, 0, initial);
			final var proof = List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64)));
			try (var oldPulse = gate.enterOwner(_ -> solo))
			{
				final org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp captured;
				try (var permit = gate.tryAcquireScoped(1, null)) { captured = permit.stamp(); }
				final var request = initial.request(party, "party", 1000, 5000); gate.publish(1, 1, request);
				final var transferred = request.yield(1001).checkpoint(proof, 1002).transfer(1003).recovered(proof);
				gate.quiescent(1, 1, () -> { gate.publish(1, 4, transferred); return true; });
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, captured), "Old native callback borrowed the recipient grant.");
				final var returning = transferred.request(solo, "return", 1100, 6000); gate.publish(1, 5, returning);
				gate.quiescent(1, 5, () -> { gate.publish(1, 8, returning.yield(1101).checkpoint(proof, 1102).transfer(1103).recovered(proof)); return true; });
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, captured), "Old native stamp survived a round trip back to the same owner.");
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, null), "An old pulse borrowed the source's new generation after round trip.");
			}
			try (var nextPulse = gate.enterOwner(_ -> solo); var permit = gate.tryAcquireScoped(1, null))
			{ PhantomAssertions.assertTrue(permit != null && permit.valid(), "A fresh source pulse could not recover after the legitimate return."); }
		});
		registry.add("19-recipient-must-recover-before-normal-execution-and-cancel-retains-that-gate", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var initial = PhantomExecutionHandoff.initial(solo);
			final var proof = List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64)));
			final var transferred = initial.request(party, "party", 1000, 5000).yield(1001).checkpoint(proof, 1002).transfer(1003);
			PhantomAssertions.assertEquals(Phase.RECOVERING, transferred.phase(), "CAS transfer prematurely granted normal execution before recipient recovery.");
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, transferred);
			PhantomAssertions.assertEquals(null, gate.tryAcquire(1, party), "Recipient executed before RECOVER acknowledgement.");
			final var fallbackRequest = transferred.request(solo, "fallback", 1100, 6000);
			final var fallback = PhantomExecutionHandoff.decode(fallbackRequest.encode()).recoverSource();
			PhantomAssertions.assertEquals(Phase.RECOVERING, fallback.phase(), "Cancelling a recovery fallback bypassed the source's uncompleted recovery.");
			gate.publish(1, 1, fallback);
			try (var oldRecoveryPulse = gate.enterOwner(_ -> party))
			{
				gate.quiescent(1, 1, () -> { gate.publish(1, 2, fallback.recovered(proof)); return true; });
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, null), "A recovery pulse upgraded itself into fresh normal execution after acknowledgement.");
			}
			try (var nextPulse = gate.enterOwner(_ -> party); var current = gate.tryAcquireScoped(1, null))
			{ PhantomAssertions.assertTrue(current != null && current.valid(), "Recovered recipient did not receive normal execution."); }
			final var legacy = initial.encode(); java.nio.ByteBuffer.wrap(legacy).putInt(1);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomExecutionHandoff.decode(legacy), "An older prototype payload implicitly granted the new recovery contract.");
		});
		registry.add("20-request-fences-before-sql-and-unknown-ack-cannot-reopen-old-pulses", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var initial = PhantomExecutionHandoff.initial(solo); final var requested = initial.request(party, "request", 1000, 5000);
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, initial);
			final var fencedBeforeSql = new java.util.concurrent.atomic.AtomicBoolean();
			try (var oldPulse = gate.enterOwner(_ -> solo); var earned = gate.tryAcquireScoped(1, null))
			{
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> gate.requesting(1, 0, () ->
				{
					try (var unexpected = gate.tryAcquireScoped(1, null)) { fencedBeforeSql.set(!earned.valid() && unexpected == null); }
					gate.publish(1, 1, requested);
					throw new IllegalStateException("request acknowledgement lost");
				}), "Injected unknown REQUEST acknowledgement was swallowed.");
				PhantomAssertions.assertTrue(fencedBeforeSql.get(), "REQUEST started SQL before closing conflicting admission.");
				earned.close();
				final var actual = requested.recoverSource().recovered(List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64)))); gate.publish(1, 2, actual);
				PhantomAssertions.assertEquals(null, gate.tryAcquire(1, solo), "Unknown REQUEST acknowledgement reopened its source.");
				try (var duringRecovery = gate.enterOwner(_ -> solo))
				{
					gate.acknowledgeRecovery(1, 2, actual);
					PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, null), "A pulse begun under the unknown fence borrowed the recovered grant.");
				}
			}
			try (var freshPulse = gate.enterOwner(_ -> solo); var current = gate.tryAcquireScoped(1, null))
			{ PhantomAssertions.assertTrue(current != null && current.valid(), "Explicit recovery failed to restore the sole durable owner."); }
		});
		registry.add("21-earned-completion-survives-request-but-not-yield-or-transfer", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var party = new PhantomDomainRef("execution.party", "group1.generation7");
			final var initial = PhantomExecutionHandoff.initial(solo); final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, initial);
			final org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp captured;
			try (var owner = gate.enterOwner(_ -> solo); var original = gate.tryAcquireScoped(1, null)) { captured = original.stamp(); }
			final var requested = initial.request(party, "party", 1000, 5000); gate.publish(1, 1, requested);
			PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, captured), "REQUEST admitted a new normal action.");
			try (var completion = gate.tryAcquireCompletionScoped(1, captured))
			{
				PhantomAssertions.assertTrue(completion != null && completion.valid(), "REQUEST prevented completion of an already accepted operation.");
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> gate.quiescent(1, 1, () -> true), "Transfer ignored the completion lease.");
			}
			final var yielded = requested.yield(1001); gate.publish(1, 2, yielded);
			PhantomAssertions.assertEquals(null, gate.tryAcquireCompletionScoped(1, captured), "YIELD left old completion admission open.");
			final var proof = List.of(new Dependency("goal.runtime", 1, 9, "a".repeat(64)));
			final var received = yielded.checkpoint(proof, 1002).transfer(1003); gate.publish(1, 4, received);
			PhantomAssertions.assertEquals(null, gate.tryAcquireCompletionScoped(1, captured), "Transferred source retained completion rights.");
			try (var recover = gate.enterCompletionOwner(_ -> party); var completion = gate.tryAcquireCompletionScoped(1, null))
			{
				PhantomAssertions.assertTrue(completion != null && completion.valid(), "Recipient could not recover its accepted operation.");
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, null), "Recovery scope acquired normal execution before ACK.");
			}
		});
		registry.add("22-multi-profile-owner-scope-retains-exact-captured-fences", _ ->
		{
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(2);
			gate.publish(1, 0, PhantomExecutionHandoff.initial(PhantomExecutionHandoff.solo(1)));
			gate.publish(2, 0, PhantomExecutionHandoff.initial(PhantomExecutionHandoff.solo(2)));
			try (var owner = gate.enterOwner(PhantomExecutionHandoff::solo); var first = gate.tryAcquireScoped(1, null); var captured = gate.enterCaptured(first.stamp()))
			{
				try (var second = gate.tryAcquireScoped(2, null)) { PhantomAssertions.assertTrue(second != null && second.valid(), "An unrelated captured actor hid the lawful multi-profile owner scope."); }
				final var requested = PhantomExecutionHandoff.initial(PhantomExecutionHandoff.solo(1)).request(new PhantomDomainRef("execution.party", "group1"), "party", 1000, 5000);
				gate.publish(1, 1, requested);
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, null), "Matching stale actor fell back to a broader fresh owner scope.");
			}
		});
		registry.add("23-command-retains-admission-after-actor-lease-close", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var initial = PhantomExecutionHandoff.initial(solo);
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, initial);
			final var actor = gate.tryAcquire(1, solo);
			try (var command = actor.retainScope())
			{
				actor.close();
				gate.publish(1, 1, initial.request(new PhantomDomainRef("execution.party", "group1"), "party", 1000, 5000));
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> gate.quiescent(1, 1, () -> true), "Closing the actor released an executing command before its native work was admitted/drained.");
			}
			gate.quiescent(1, 1, () -> true);
		});
		registry.add("24-durable-obligation-closes-fresh-work-without-revoking-issued-completion", _ ->
		{
			final var solo = PhantomExecutionHandoff.solo(1); final var initial = PhantomExecutionHandoff.initial(solo);
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(1, 0, initial);
			try (var issued = gate.tryAcquire(1, solo))
			{
				gate.blocker(1, "commerce.receipt", 4, true);
				PhantomAssertions.assertTrue(issued.valid(), "Persisting an obligation revoked its own already admitted native operation.");
				PhantomAssertions.assertEquals(null, gate.tryAcquire(1, solo), "A fresh conflicting action ran while an effect was unsettled.");
				PhantomAssertions.assertEquals(null, gate.tryAcquireScoped(1, issued.stamp()), "A queued timer borrowed issued ACTION continuation rights.");
				try (var continuation = gate.tryAcquireContinuationScoped(1, issued.stamp()); var completion = gate.tryAcquireOwnedCompletion(1, solo))
				{ PhantomAssertions.assertTrue(continuation != null && completion != null, "The canonical obligation could not finish under its current owner."); }
				gate.blocker(1, "commerce.receipt", 5, false); gate.blocker(1, "commerce.receipt", 4, true);
				try (var fresh = gate.tryAcquire(1, solo)) { PhantomAssertions.assertTrue(fresh != null && fresh.valid(), "A stale obligation observation closed a settled owner."); }
			}
		});
	}
}
