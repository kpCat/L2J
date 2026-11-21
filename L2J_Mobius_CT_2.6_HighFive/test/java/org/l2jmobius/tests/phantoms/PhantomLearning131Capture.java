/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;

/** Records the existing native cast/apply receipts for one active TASK131 episode at a time. */
final class PhantomLearning131Capture
{
	record Actor(long profile, int objectId, long epoch) { }
	record Target(int objectId, long epoch) { }
	record Original(String episodeId, String sealSha) { }
	record Settlement(ActionExplorationV4.Plan plan, TacticalEpisodeV3 episode,
		List<TacticalReceiptV3> receipts, Map<Long, ActionExperimentReceiptV4.Intent> intents, List<ActionExplorationV4.Decision> decisions) { }

	static final class Episode
	{
		private final ActionExplorationV4 experiment;
		private final long started;
		private final Map<Long, Actor> roster;
		private final Set<Target> foes;
		private final Set<Target> allies;
		private final Map<Long, ActionExperimentReceiptV4.Intent> intents = new LinkedHashMap<>();
		private final Map<String, TacticalReceiptV3> paid = new LinkedHashMap<>();
		private boolean accepting = true;
		private boolean sealed;
		private boolean fault;

		Episode(ActionExplorationV4 experiment, long started, Collection<Actor> actors, Set<Target> foes)
		{
			this.experiment = experiment;
			this.started = started;
			this.foes = Set.copyOf(foes);
			roster = actors.stream().collect(Collectors.toUnmodifiableMap(Actor::profile, actor -> actor));
			allies = actors.stream().map(actor -> new Target(actor.objectId(), actor.epoch())).collect(Collectors.toUnmodifiableSet());
			require(experiment != null && started > 0 && !roster.isEmpty() && roster.size() <= 9 && !foes.isEmpty() && foes.size() <= 16
				&& actors.stream().map(Actor::objectId).distinct().count() == roster.size() && foes.stream().map(Target::objectId).distinct().count() == foes.size()
				&& actors.stream().allMatch(actor -> actor.profile() > 0 && actor.objectId() > 0 && actor.epoch() > 0)
				&& foes.stream().allMatch(target -> target.objectId() > 0 && target.epoch() > 0
					&& allies.stream().noneMatch(ally -> ally.objectId() == target.objectId())), "EPISODE_SCOPE");
		}

		boolean fits(TacticalObservationV3 observation, List<LegalActionV3> offered)
		{
			final var actor = roster.get(observation.profileId());
			return actor != null && actor.objectId() == observation.actorObjectId() && actor.epoch() == observation.actorEpoch()
				&& observation.capturedNanos() >= started && offered.size() >= 2 && offered.stream().allMatch(action ->
				{
					final Target target = new Target(action.targetObjectId(), action.targetEpoch());
					return switch (action.family())
					{
						case DAMAGE -> foes.contains(target);
						case HEAL -> allies.contains(target);
						default -> false;
					};
				});
		}
	}

	private final AtomicReference<Episode> current = new AtomicReference<>();
	private final TacticalPolicyV3 tactical;
	private final LearningPolicy damage;

	PhantomLearning131Capture(String manifestSha, ActionValueV4 model)
	{
		tactical = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, 0, null, 20, 16, 1000, heal -> paid(TacticalReceiptV3.fromHeal(heal)));
		tactical.actionValueTest(TacticalPolicyV3.Mode.ON, model, _ -> { });
		tactical.actionEpisodes131(this::selectExperiment, this::before);
		tactical.damageObserver(this::paid);
		damage = LearningPolicy.ownedEpisodes131(tactical, manifestSha);
		damage.observer(new LearningPolicy.Observer()
		{
			@Override
			public void issued(LearningPolicy.Attempt attempt, Player actor, Monster target)
			{
				beforeDamage(attempt, actor, target);
			}

			@Override
			public void recorded(LearningPolicy.Attempt attempt, LearningStore.Evidence row) { }
		});
	}

	TacticalPolicyV3 tactical() { return tactical; }
	LearningPolicy damage() { return damage; }

	void open(Episode episode)
	{
		synchronized (episode)
		{
			require(episode.accepting && current.compareAndSet(null, episode), "EPISODE_OVERLAP");
		}
	}

	boolean eligible(long profile, Player player)
	{
		final var episode = current.get();
		if (episode == null) { return false; }
		synchronized (episode)
		{
			final var actor = episode.roster.get(profile);
			final var owner = player.getNativeWorkOwner();
			return episode.accepting && !episode.fault && actor != null && actor.objectId() == player.getObjectId()
				&& owner != null && owner.isCurrent() && owner.player() == player && owner.epoch() == actor.epoch();
		}
	}

	boolean permitsSkill(long profile, Skill skill)
	{
		final var episode = current.get();
		if (episode == null) { return false; }
		synchronized (episode)
		{
			return episode.accepting && !episode.fault && episode.roster.containsKey(profile)
				&& episode.experiment.plan().skills().contains(skill.getId() + ":" + skill.getLevel());
		}
	}

	ActionExplorationV4 selectExperiment(TacticalObservationV3 observation, List<LegalActionV3> offered)
	{
		final var episode = current.get();
		if (episode == null) { return null; }
		synchronized (episode)
		{
			return episode.accepting && !episode.fault && episode.fits(observation, offered) ? episode.experiment : null;
		}
	}

	private void beforeDamage(LearningPolicy.Attempt attempt, Player actor, Monster target)
	{
		final var episode = current.get();
		final var selected = attempt.tactical();
		if (episode == null || selected == null || !eligible(attempt.request().profileId(), actor)
			|| target.getObjectId() != selected.selected().targetObjectId() || TacticalSnapshotV3.epoch(target) != selected.selected().targetEpoch()
			|| World.getInstance().findObject(target.getObjectId()) != target) { return; }
		episode.experiment.decisions().stream().filter(decision -> decision.observation().decisionId() == selected.observation().decisionId()
			&& decision.selected().equals(selected.selected())
			&& decision.stock().equals(selected.baseline()) && decision.offered().equals(selected.offered()))
			.findFirst().ifPresent(decision -> before(new ActionExperimentReceiptV4.Intent(decision, new SourceOwner(attempt.generation(),
				"Combat/" + attempt.request().profileId() + "/" + attempt.generation() + "/" + attempt.sequence()), System.nanoTime())));
	}

	private void before(ActionExperimentReceiptV4.Intent intent)
	{
		final var episode = current.get();
		if (episode == null) { return; }
		synchronized (episode)
		{
			final var decision = intent.decision();
			if (!episode.accepting || !decision.plan().equals(episode.experiment.plan()) || !episode.fits(decision.observation(), decision.offered())) { return; }
			if (episode.intents.size() >= 64 || episode.intents.putIfAbsent(decision.observation().decisionId(), intent) != null)
			{
				episode.fault = true;
			}
		}
	}

	private void paid(TacticalReceiptV3 receipt)
	{
		final var episode = current.get();
		if (episode == null) { return; }
		synchronized (episode)
		{
			final var attempt = receipt.attempt();
			if (episode.sealed || !episode.fits(attempt.observation(), attempt.offered())) { return; }
			final var intent = episode.intents.get(attempt.observation().decisionId());
			if (intent == null || !receipt.owner().equals(intent.owner()) || !attempt.selected().equals(intent.decision().selected())
				|| episode.paid.size() >= 64 || episode.paid.putIfAbsent(receipt.eventKey(), receipt) != null)
			{
				episode.fault = true;
			}
		}
	}

	void closeChoices(Episode episode)
	{
		require(current.get() == episode, "FOREIGN_EPISODE");
		synchronized (episode)
		{
			episode.accepting = false;
		}
	}

	List<TacticalReceiptV3> receipts(Episode episode)
	{
		synchronized (episode)
		{
			require(current.get() == episode && !episode.accepting, "EPISODE_ADMISSION_OPEN");
			return List.copyOf(episode.paid.values());
		}
	}

	/** Freezes the observed events at the native episode drain boundary. */
	Settlement settle(Episode episode, TacticalEpisodeV3 settled, String nonce)
	{
		final List<TacticalReceiptV3> receipts;
		final Map<Long, ActionExperimentReceiptV4.Intent> intents;
		synchronized (episode)
		{
			require(!episode.accepting && !episode.sealed && !episode.fault && current.get() == episode, "EPISODE_DRAIN");
			episode.sealed = true;
			receipts = List.copyOf(episode.paid.values());
			intents = Map.copyOf(episode.intents);
		}
		final var plan = episode.experiment.plan();
		require(Set.of("TRAIN", "TUNING").contains(plan.split()) && plan.episode().equals(settled.id()) && settled.ownerNonce().equals(nonce)
			&& settled.startedNanos() == episode.started && !receipts.isEmpty()
			&& Set.copyOf(settled.eventKeys()).equals(receipts.stream().map(TacticalReceiptV3::eventKey).collect(Collectors.toSet()))
			&& episode.roster.values().stream().allMatch(actor -> settled.actors().stream().anyMatch(actual ->
				actual.profile() == actor.profile() && actual.objectId() == actor.objectId() && actual.epoch() == actor.epoch()))
			&& settled.actors().size() == episode.roster.size(), "SETTLED_EPISODE_SCOPE");
		final var decisions = episode.experiment.decisions();
		if (plan.split().equals("TUNING")) { episode.experiment.verifyFrozenLog(decisions); }
		else { ActionExplorationV4.validateLog(decisions); }
		require(current.compareAndSet(episode, null), "EPISODE_REPLACED_BEFORE_SEAL");
		return new Settlement(plan, settled, receipts, intents, decisions);
	}

	/** The boot publishes frozen originals after canonical runtime drain and STORE. */
	static Original publishEpisode(Path episodes, String bootId, Settlement drained) throws Exception
	{
		final var settled = drained.episode();
		final var plan = drained.plan();
		final String split = plan.split();
		require(bootId.matches("[0-9]{5}") && episodes.isAbsolute() && episodes.equals(episodes.toRealPath()) && Files.isDirectory(episodes), "EPISODE_OUTPUT");
		final Path output = Files.createDirectory(episodes.resolve(settled.id()));
		final byte[] settlement = settled.json().getBytes(StandardCharsets.UTF_8);
		ActionExperimentReceiptV4.publish(output.resolve("episode131.json"), settlement);
		final var seal = new StringBuilder()
			.append(row("meta", "schema", "NATIVE_" + split + "_EPISODE131_V1")).append(row("meta", "status", "DRAINED"))
			.append(row("meta", "split", split)).append(row("meta", "bootId", bootId)).append(row("meta", "nonce", settled.ownerNonce()))
			.append(row("meta", "episodeId", settled.id())).append(row("meta", "sourceSha", plan.sourceSha()))
			.append(row("meta", "policySha", plan.sha256())).append(row("meta", "startedNanos", settled.startedNanos()))
			.append(row("meta", "settledNanos", settled.settledNanos())).append(row("meta", "episodeSha", hash(settlement)));
		if (split.equals("TUNING"))
		{
			final var encoder = java.util.Base64.getEncoder();
			final byte[] decisions = drained.decisions().stream().map(decision -> encoder.encodeToString(decision.bytes()))
				.collect(Collectors.joining("\n", "", "\n")).getBytes(StandardCharsets.US_ASCII);
			require(decisions.length <= 4 * 1024 * 1024, "FROZEN_LOG_BOUND");
			ActionExperimentReceiptV4.publish(output.resolve("decisions131.tsv"), decisions);
			seal.append(row("meta", "decisionsSha", hash(decisions)));
		}
		for (var receipt : drained.receipts().stream().sorted(Comparator.comparing(TacticalReceiptV3::eventKey)).toList())
		{
			final String event = receipt.eventKey();
			final var intent = drained.intents().get(receipt.attempt().observation().decisionId());
			require(receipt.elapsedNanos() <= settled.settledNanos() - intent.capturedNanos(), "NATIVE_EFFECT_AFTER_DRAIN");
			final byte[] intentBytes = intent.bytes();
			final byte[] nativeBytes = PhantomLearning005Native.nativeReceiptJson(receipt).getBytes(StandardCharsets.UTF_8);
			final byte[] row = TacticalStoreV3.encode(new TacticalStoreV3.Row(split, "task131|" + split + "|" + settled.id(), receipt));
			final byte[] effect = ActionExperimentReceiptV4.bind(intent, plan.sha256(), row, settled, hash(nativeBytes)).bytes();
			ActionExperimentReceiptV4.publish(output.resolve(event + ".intent.bin"), intentBytes);
			ActionExperimentReceiptV4.publish(output.resolve(event + ".row.bin"), row);
			ActionExperimentReceiptV4.publish(output.resolve(event + ".native.json"), nativeBytes);
			ActionExperimentReceiptV4.publish(output.resolve(event + ".effect.bin"), effect);
			seal.append(row("event", event, hash(intentBytes), hash(row), hash(nativeBytes), hash(effect)));
		}
		final byte[] sealed = seal.toString().getBytes(StandardCharsets.UTF_8);
		ActionExperimentReceiptV4.publish(output.resolve("seal131.tsv"), sealed);
		return new Original(settled.id(), hash(sealed));
	}

	private static String row(Object... fields) { return Arrays.stream(fields).map(Object::toString).collect(Collectors.joining("\t")) + "\n"; }
	private static String hash(byte[] bytes) { return TacticalObservationV3.hash(bytes); }
	private static void require(boolean condition, String reason) { if (!condition) { throw new IllegalStateException("TASK131_CAPTURE_" + reason); } }
}
