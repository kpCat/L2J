/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalFailureV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;

/** Existing GameServer/actors/owners; one shared policy and no replacement combat simulation. */
public final class PhantomLearning004Native
{
	static void run(Path root, Path attempt, TrainingConfig config, LearningPolicy legacy) throws Exception
	{
		final var store = new TacticalStoreV3(root); store.initialize();
		store.recoverEpisodes(attempt.getFileName().toString());
		PhantomLearning003Native.run(root, attempt, config, legacy, (authority, runtime, shared) ->
		{
			final long evaluationsBefore = shared.evaluated.sum();
			final int round = config.integer("v3CollectionRound", 4, 4, 99);
			final int train = config.integer("v3DamageTrainPerScope", 16, 16, 128);
			final int tuning = config.integer("v3DamageTuningPerScope", 8, 8, 64);
			final int audit = config.integer("v3DamageAuditPerScope", 4, 4, 32);
			final var receipts = new ConcurrentLinkedQueue<TacticalReceiptV3>();
			final var failures = new ConcurrentLinkedQueue<TacticalFailureV3>();
			shared.failureObserver(failure -> { if (failures.size() >= 1024) { throw new IllegalStateException("004 native failure queue capacity"); } failures.add(failure); });
			shared.damageObserver(receipt ->
			{
				if (receipts.size() >= 1024) { throw new IllegalStateException("004 native queue capacity"); }
				receipts.add(receipt);
			});
			final var seen = store.recordedContextHashes();
			final var catalog = PhantomTrainingScenarios.discover(config,authority,config.seed()+round,256).stream()
				.filter(scene -> java.util.Set.of(21798,21799).contains(scene.npc()))
				.filter(scene -> java.util.stream.Stream.of("SOLO","PARTY").noneMatch(scope -> seen.contains(TacticalObservationV3.hash(context(scene,scope).getBytes(StandardCharsets.UTF_8)))))
				.toList();
			final var curriculum = new java.util.ArrayList<PhantomTrainingScenarios.Scene>();
			for (String split : List.of("TRAIN","TUNING","AUDIT"))
			{
				final int needed = split.equals("TRAIN") ? train : split.equals("TUNING") ? tuning : audit;
				final var selected = catalog.stream().filter(scene -> PhantomTrainingScenarios.sceneSplit(scene.id()).equals(split)).limit(needed).map(scene -> scene.withSplit(split)).toList();
				if (selected.size() != needed) { throw new IllegalStateException("004 fresh validated native contexts insufficient: " + split + " available=" + selected.size() + " required=" + needed); }
				curriculum.addAll(selected);
			}
			final var segment = segment(curriculum,config.episodesPerBatch());
			int attempted=0, completed=0, failedEpisodes=0, incomplete=0, preCutRejected=0;
			System.out.println("LEARNING004_SEGMENT_PLAN catalogPairs=" + curriculum.size() + " segmentPairs=" + segment.size() + " episodeBudget=" + config.episodesPerBatch() + " qualityThresholds=UNCHANGED");
			for (int index = 0; index < segment.size() && !requested(attempt); index++)
			{
				final var solo = segment.get(index); final var party = solo;
				final String split = solo.split();
				final Path directory = attempt.resolve("damage004").resolve(split.toLowerCase(java.util.Locale.ROOT) + '-' + index);
				Files.createDirectories(directory);
				final var actors = PhantomLearning003Damage.prepare(authority, solo, party, directory, config);
				for (boolean grouped : new boolean[] {false, true})
				{
					if (requested(attempt)) { break; }
					final var scene = grouped ? party : solo;
					final String scope = grouped ? "PARTY" : "SOLO";
					final String context = context(scene,scope);
					final String id = TacticalObservationV3.hash((attempt.getFileName() + "/" + context + "/" + actors.get(grouped ? 1 : 0)[2]).getBytes(StandardCharsets.UTF_8));
					receipts.clear();
					failures.clear();
					String after;
					String refusal=null; boolean cut=false; attempted++;
					TacticalEpisodeV3 episode = null;
					java.util.function.Supplier<TacticalEpisodeV3> finalSnapshot = null;
					try
					{
						// Independent pre-CUT actors: four fresh Party members, no reuse/reset of the completed Solo participant.
						final var roster = grouped ? actors.stream().filter(row -> !row[1].equals("SOLO")).toList() : actors;
						final String before = runtime.prepareTraining(scene, roster, grouped, config.preparationSeconds(), () -> requested(attempt));
						for (var row : roster)
						{
							final var object=org.l2jmobius.gameserver.model.World.getInstance().findObject(Integer.parseInt(row[3]));
							if(object instanceof org.l2jmobius.gameserver.model.actor.Player player && player.getCurrentSkillWorldPosition()==null)
							{
								for(var skill : player.getAllSkills())
								{
									if(skill.isChanneling() && skill.getChannelingSkillId()>0 && skill.getTargetType()==org.l2jmobius.gameserver.model.skill.targets.TargetType.GROUND)
									{
										if(player.checkDoCastConditions(skill)){throw new IllegalStateException("004 ground skill without native point was admitted");}
										System.out.println("LEARNING004_GROUND_NO_POINT_UNAVAILABLE actor="+player.getObjectId()+" level="+player.getLevel()+" skill="+skill.getId()+" nativeCheck=false inputUnchanged=true");
									}
								}
							}
						}
						store.beginEpisode(id, attempt.getFileName().toString(), scope, before);
						cut=true;
						Files.writeString(directory.resolve(scope.toLowerCase(java.util.Locale.ROOT) + ".start.json"), before + "\n", StandardOpenOption.CREATE_NEW);
						System.out.println("LEARNING004_CUT episode=" + id + " scope=" + scope + " split=" + split + " context=" + context + " setup=PRE_CUT_NATIVE_ACTORS");
						finalSnapshot = runtime.runLearning004Encounter(id, attempt.getFileName().toString(), config.episodeSeconds(), () -> requested(attempt), () -> List.copyOf(receipts));
					}
					catch (IllegalStateException unavailable)
					{
						if(cut || !preCutRejection(unavailable)){throw unavailable;}
						refusal=unavailable.getMessage();
					}
					finally
					{
						if (finalSnapshot == null) { after = runtime.settleTraining(); }
						else
						{
							final var settled = runtime.settleLearning004(finalSnapshot);
							episode = settled.episode(); after = settled.facts();
						}
					}
					if(refusal!=null)
					{
						preCutRejected++;
						final String rejected="{\"schemaVersion\":4,\"scope\":\""+scope+"\",\"split\":\""+split+"\",\"context\":\""+context+"\",\"reason\":\""+refusal
							+"\",\"nativeEpisodeStarted\":false,\"outcome\":\"SKIP\",\"reward\":null,\"nativeCredit\":0,\"observedAfterDrain\":"+after+"}";
						Files.writeString(directory.resolve(scope.toLowerCase(java.util.Locale.ROOT)+".rejected.json"),rejected+"\n",StandardOpenOption.CREATE_NEW);
						System.out.println("LEARNING004_PRE_CUT_REJECTED "+rejected);
						continue;
					}
					if (episode == null) { throw new IllegalStateException("004 native settlement not captured"); }
					for (var receipt : receipts)
					{
						if (!store.record(receipt, split, context)) { throw new IllegalStateException("004 duplicate fresh native DAMAGE receipt"); }
						final var action = receipt.attempt().selected();
						System.out.println("LEARNING004_NATIVE_DAMAGE eventKey=" + receipt.eventKey() + " scope=" + scope + " split=" + split + " model=" + receipt.attempt().modelVersion()
							+ " skill=" + action.skillId() + ':' + action.skillLevel() + " offered=" + receipt.attempt().offered().size() + " target=" + action.targetObjectId()
							+ " spawn=" + action.targetEpoch() + " beforeHp=" + receipt.beforeHp() + " afterHp=" + receipt.afterHp() + " exactHp=" + receipt.actualHp()
							+ " actualMp=" + receipt.actualMp() + " elapsedNanos=" + receipt.elapsedNanos() + " reward=" + receipt.reward());
					}
					for (var failure : failures)
					{
						store.recordFailure(failure,split,context,id); System.out.println("LEARNING004_NATIVE_FAILURE " + failure.json());
					}
					store.settleEpisode(episode);
					Files.writeString(directory.resolve(scope.toLowerCase(java.util.Locale.ROOT) + ".settlement.json"), episode.json() + "\n", StandardOpenOption.CREATE_NEW);
					Files.writeString(directory.resolve(scope.toLowerCase(java.util.Locale.ROOT) + ".drained.json"), after + "\n", StandardOpenOption.CREATE_NEW);
					System.out.println("LEARNING004_EPISODE " + episode.json());
					if(episode.outcome().equals("COMPLETED")){completed++;}
					else if(episode.outcome().equals("FAILED")){failedEpisodes++;}
					else{incomplete++;}
				}
			}
			if (requested(attempt)) { throw new IllegalStateException("004 native segment interrupted; retained events have no terminal segment marker"); }
			if (shared.evaluated.sum() <= evaluationsBefore) { throw new IllegalStateException("004 shared neural scorer was not called for DAMAGE"); }
			Files.writeString(attempt.resolve("damage004.segment.json"),"{\"episodeBudget\":" + config.episodesPerBatch() + ",\"attempted\":"+attempted+",\"completedNativeEpisodes\":"+completed
				+",\"failedNativeEpisodes\":"+failedEpisodes+",\"incompleteNativeEpisodes\":"+incomplete+",\"preCutRejected\":"+preCutRejected
				+ ",\"catalogPairs\":" + curriculum.size() + ",\"allCatalogPairsCompleted\":" + (segment.size()==curriculum.size()) + ",\"qualityThresholdsChanged\":false}\n",StandardOpenOption.CREATE_NEW);
			System.out.println("LEARNING004_SHARED_MODEL families=DAMAGE,HEAL onePolicy=true damageEvaluations=" + (shared.evaluated.sum() - evaluationsBefore) + " timing=" + shared.timing());
		});
	}
	private static boolean requested(Path attempt)
	{ return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	static boolean preCutRejection(Throwable failure)
	{
		return failure instanceof IllegalStateException && failure.getMessage()!=null && java.util.Set.of("LEARNING001_STOCK_PARTY_PREFLIGHT_TARGET","LEARNING001_STOCK_PARTY_TEAM_ADMISSION",
			"LEARNING001_TRAIN_NATURAL_RECOVERY_DEADLINE","LEARNING001_TRAIN_PREPARATION_NATIVE_THREAT").contains(failure.getMessage());
	}
	private static String context(PhantomTrainingScenarios.Scene scene, String scope)
	{ return "004.DAMAGE." + scope + "|spot=" + scene.npc() + ':' + scene.x() + ':' + scene.y() + ':' + scene.z() + "|scene=" + scene.id() + (scope.equals("PARTY") ? "|party=4" : ""); }
	/** Same existing Director episode budget; balanced stable splits, remaining raw/candidate contexts retained for later segments. */
	static List<PhantomTrainingScenarios.Scene> segment(List<PhantomTrainingScenarios.Scene> catalog, int episodeBudget)
	{
		if (episodeBudget < 12) { throw new IllegalArgumentException("004 fresh4-context per-scope audit plus TRAIN/TUNING needs episode budget>=12"); }
		final var groups = new java.util.LinkedHashMap<String,List<PhantomTrainingScenarios.Scene>>();
		for (String split : List.of("TRAIN","TUNING","AUDIT")) { groups.put(split,catalog.stream().filter(scene -> scene.split().equals(split)).toList()); }
		final var result = new java.util.ArrayList<PhantomTrainingScenarios.Scene>();
		if(groups.get("TRAIN").isEmpty() || groups.get("TUNING").isEmpty() || groups.get("AUDIT").size()<4)
		{throw new IllegalArgumentException("004 independent split coverage unavailable before native");}
		result.add(groups.get("TRAIN").getFirst()); result.add(groups.get("TUNING").getFirst()); result.addAll(groups.get("AUDIT").subList(0,4));
		final var next = new java.util.HashMap<String,Integer>(); next.put("TRAIN",1); next.put("TUNING",1); next.put("AUDIT",4);
		while (result.size()<episodeBudget/2)
		{
			boolean added=false;
			for (var entry : groups.entrySet())
			{
				if (result.size()>=episodeBudget/2) { break; }
				final int index=next.get(entry.getKey());
				if (index<entry.getValue().size()) { result.add(entry.getValue().get(index)); next.put(entry.getKey(),index+1); added=true; }
			}
			if (!added) { break; }
		}
		return List.copyOf(result);
	}
}
