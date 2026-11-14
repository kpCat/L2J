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
		if(store.terminalMode()){runOutcome006(root,attempt,config,legacy,store);return;}
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
	static final class OutcomeClock implements AutoCloseable
	{
		static final long LIMIT=java.time.Duration.ofMinutes(90).toNanos();
		private final Path state;private final long previous,started,bootstrap;private final java.util.function.LongSupplier now;private boolean closed;
		OutcomeClock(Path campaign,long bootstrapNanos,java.util.function.LongSupplier now) throws java.io.IOException
		{
			if(bootstrapNanos<0||!campaign.equals(campaign.toRealPath())){throw new IllegalArgumentException("Physical outcome clock required");}
			state=campaign.resolve("active.nanos");if(Files.exists(state)&&!state.equals(state.toRealPath())){throw new java.io.IOException("Physical clock state required");}
			previous=Files.exists(state)?Long.parseLong(Files.readString(state)):0;if(previous<0||previous>LIMIT){throw new IllegalArgumentException("Invalid retained outcome time");}
			this.now=now;bootstrap=bootstrapNanos;started=now.getAsLong();
			// Unclosed/crashed segments retain the full charge. Only a controlled close refunds unused time.
			persist(LIMIT);
		}
		long spentNanos(){return Math.min(LIMIT,previous+bootstrap+Math.max(0,now.getAsLong()-started));}
		boolean expired(){return spentNanos()>=LIMIT;}
		boolean canStart(long maximumSlotAndDrainNanos){return maximumSlotAndDrainNanos>=0&&spentNanos()<LIMIT-maximumSlotAndDrainNanos;}
		private void persist(long value) throws java.io.IOException
		{
			final Path pending=state.resolveSibling("clock-"+java.util.UUID.randomUUID()+".pending");
			try
			{
				try(var channel=java.nio.channels.FileChannel.open(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE))
				{final var bytes=java.nio.ByteBuffer.wrap(Long.toString(value).getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining()){channel.write(bytes);}channel.force(true);}
				Files.move(pending,state,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			finally{Files.deleteIfExists(pending);}
		}
		@Override public void close() throws java.io.IOException{if(!closed){persist(spentNanos());closed=true;}}
	}
	static Path outcomeCampaign(Path root,String id) throws java.io.IOException
	{
		if(id==null||!id.isEmpty()&&!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")){throw new IllegalArgumentException("Invalid outcome campaign identity");}
		final Path path=id.isEmpty()?root.resolve("outcome006/collection"):root.resolve("outcome006/campaigns").resolve(id);
		Files.createDirectories(path);
		if(!path.toAbsolutePath().normalize().equals(path.toRealPath())){throw new java.io.IOException("Physical outcome campaign required");}
		return path;
	}
	static boolean reserveOutcomeSlot(Path campaign,int slot,String json) throws java.io.IOException
	{
		if(slot<0||slot>=48){throw new IllegalArgumentException("Outcome slot ceiling exceeded");}
		final Path work=campaign.resolve("slot-"+slot);
		if(Files.exists(work,java.nio.file.LinkOption.NOFOLLOW_LINKS))
		{
			if(!work.equals(work.toRealPath())||!Files.isDirectory(work)){throw new java.io.IOException("Physical consumed slot required");}
			return false;
		}
		Files.createDirectory(work);
		Files.writeString(work.resolve("reserved.json"),json,StandardOpenOption.CREATE_NEW);
		return true;
	}
	private static List<org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1> outcomeReceipts(Path campaign) throws java.io.IOException
	{
		final var result=new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1>();
		for(int slot=0;slot<48;slot++)
		{
			final Path path=campaign.resolve("slot-"+slot).resolve("terminal.bin");
			if(!Files.exists(path)){continue;}
			if(!path.equals(path.toRealPath())||Files.size(path)>org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.MAX_BYTES){throw new java.io.IOException("Physical bounded terminal receipt required");}
			final byte[] bytes=Files.readAllBytes(path);
			result.add(org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.restore(bytes,TacticalObservationV3.hash(bytes)));
		}
		return List.copyOf(result);
	}
	private static boolean outcomeNeeded(Path campaign,String split) throws java.io.IOException
	{
		final var receipts=outcomeReceipts(campaign).stream().filter(r->r.split().equals(split)).toList();
		final long actions=receipts.stream().mapToLong(r->r.actions().size()).sum();
		return switch(split)
		{
			case "TRAIN" -> actions<16||receipts.stream().flatMap(r->r.actions().stream()).map(a->a.row().contextKey()).distinct().count()<2
				||receipts.stream().filter(r->r.episode().scope().equals("SOLO")&&r.episode().outcome().equals("COMPLETED")).count()<2
				||receipts.stream().filter(r->r.episode().scope().equals("PARTY")&&r.episode().outcome().equals("COMPLETED")).count()<2;
			case "TUNING" -> actions<8;
			case "AUDIT" -> receipts.stream().flatMap(r->r.actions().stream()).map(a->a.row().actorProfile()).distinct().count()<2
				||receipts.stream().flatMap(r->r.actions().stream()).map(a->a.row().contextKey()).distinct().count()<2;
			default -> throw new IllegalArgumentException("Outcome split required");
		};
	}
	private static boolean outcomeQuota(Path campaign) throws java.io.IOException
	{return !outcomeNeeded(campaign,"TRAIN")&&!outcomeNeeded(campaign,"TUNING")&&!outcomeNeeded(campaign,"AUDIT");}
	private static void runOutcome006(Path root,Path attempt,TrainingConfig config,LearningPolicy legacy,TacticalStoreV3 store) throws Exception
	{
		final var rewardConfig=store.terminalConfig();
		final String campaignId=config.text("outcome006Campaign","");final boolean r1=!campaignId.isEmpty();
		final Path campaign=outcomeCampaign(root,campaignId);
		if(r1&&(!campaignId.equals("learning006r1")||!config.text("outcome006DatasetId","").equals("outcome006r1-v1")||!config.text("v3DatasetContextPrefix","").equals("outcome006r1|"))){throw new IllegalArgumentException("Exact R1 campaign/dataset/context pins required");}
		if(!campaign.equals(campaign.toRealPath())){throw new IllegalStateException("Physical006 collection required");}
		if(r1)
		{
			final Path pin=campaign.resolve("config.sha256");
			if(Files.exists(pin)){if(!pin.equals(pin.toRealPath())||!Files.readString(pin).equals(config.sha256())){throw new IllegalArgumentException("Outcome campaign config changed");}}
			else{Files.writeString(pin,config.sha256(),StandardOpenOption.CREATE_NEW);}
		}
		if(Files.exists(campaign.resolve("native.complete"))){Files.writeString(attempt.resolve("batch.complete"),"schemaVersion=6\ncollectionOnly=true\nreusedCompleteBudget=true\n",StandardOpenOption.CREATE_NEW);System.out.println("OUTCOME006_COLLECTION_ALREADY_SETTLED noRepeat=true");return;}
		final OutcomeClock clock=r1?new OutcomeClock(campaign,java.time.Duration.ofMillis(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()+1).toNanos(),System::nanoTime):null;
		final String installation=TacticalObservationV3.hash(Files.readString(root.resolve("owner.txt")).getBytes(StandardCharsets.UTF_8));
		final String director=Files.readString(root.resolve("director.json"));
		final var generationMatch=java.util.regex.Pattern.compile("\"generation\"\\s*:\\s*([0-9]+)").matcher(director);
		if(!generationMatch.find()){throw new IllegalStateException("006 director generation missing");}final long generation=Long.parseLong(generationMatch.group(1));
		final var receipts=new ConcurrentLinkedQueue<TacticalReceiptV3>();final var failures=new ConcurrentLinkedQueue<TacticalFailureV3>();
		final var timings=new java.util.concurrent.ConcurrentHashMap<String,org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.NativeTiming>();
		org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.nativeTimingObserver(t->{if(timings.size()>=1024){throw new IllegalStateException("006 timing bound");}timings.put(t.eventKey(),t);});
		final var checkpoint=store.latestAccepted();
		final var shared=new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW,checkpoint.id(),checkpoint.model(),20,16,2000,r->{if(receipts.size()>=1024){throw new IllegalStateException("006 effect bound");}receipts.add(TacticalReceiptV3.fromHeal(r));});
		shared.damageObserver(r->{if(receipts.size()>=1024){throw new IllegalStateException("006 effect bound");}receipts.add(r);});
		shared.failureObserver(f->{if(failures.size()>=1024){throw new IllegalStateException("006 failure bound");}failures.add(f);});
		try(var authority=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final Path plan=campaign.resolve("scenes.tsv");final List<PhantomTrainingScenarios.Scene> scenes;
			if(Files.exists(plan)){scenes=Files.readAllLines(plan).stream().map(PhantomTrainingScenarios.Scene::parse).toList();}
			else
			{
				final var historical=r1?store.recordedSceneIds():java.util.Set.<String>of();
				final var catalog=PhantomTrainingScenarios.discover(config,authority,config.seed()+6,256).stream().filter(s->java.util.Set.of(21798,21799).contains(s.npc())).filter(s->!historical.contains(s.id())).toList();
				final var selected=new java.util.ArrayList<PhantomTrainingScenarios.Scene>();
				for(String split:List.of("TRAIN","TUNING","AUDIT"))
				{
					final int required=r1?(split.equals("TRAIN")?16:4):(split.equals("TRAIN")?4:2);
					final var group=catalog.stream().filter(s->PhantomTrainingScenarios.sceneSplit(s.id()).equals(split)).limit(required).map(s->s.withSplit(split)).toList();
					if(group.size()!=required){throw new IllegalStateException("006 native split catalog insufficient");}selected.addAll(group);
				}
				scenes=List.copyOf(selected);Files.writeString(plan,scenes.stream().map(PhantomTrainingScenarios.Scene::row).collect(java.util.stream.Collectors.joining("\n","","\n")),StandardOpenOption.CREATE_NEW);
			}
			if(scenes.size()!=(r1?24:8)){throw new IllegalStateException("006 declared attempt budget changed");}
			int consumed=0;for(int slot=0;slot<48;slot++){if(Files.exists(campaign.resolve("slot-"+slot))){consumed++;}}
			final int segmentLimit=r1&&consumed==0?4:48;
			int ordinal=0,segmentAttempts=0;boolean quota=false,timeBudget=false;
			collection:for(var scene:scenes){for(boolean grouped:new boolean[]{false,true})
			{
				final int slot=ordinal++;if(requested(attempt)){throw new IllegalStateException("006 requested boundary; unfinished slots retained");}
				if(r1&&outcomeQuota(campaign)){quota=true;break collection;}
				if(r1&&!clock.canStart(java.time.Duration.ofSeconds((long)config.preparationSeconds()+config.episodeSeconds()+config.integer("shutdownTimeoutSeconds",180,30,900)).toNanos())){timeBudget=true;break collection;}
				if(r1&&!outcomeNeeded(campaign,scene.split())){continue;}
				final Path work=campaign.resolve("slot-"+slot);
				if(!reserveOutcomeSlot(campaign,slot,"{\"slot\":"+slot+",\"attempt\":\""+attempt.getFileName()+"\",\"split\":\""+scene.split()+"\",\"party\":"+grouped+"}\n")){System.out.println("OUTCOME006_SLOT_RETAINED slot="+slot+" rerun=false");continue;}
				segmentAttempts++;
				receipts.clear();failures.clear();timings.clear();
				final String scope=grouped?"PARTY":"SOLO",context=(r1?"outcome006r1|":"outcome006|")+scope+"|spot="+scene.npc()+":"+scene.x()+":"+scene.y()+":"+scene.z()+"|scene="+scene.id();
				final String id=TacticalObservationV3.hash((attempt.getFileName()+"/"+context+"/"+slot).getBytes(StandardCharsets.UTF_8));
				org.l2jmobius.gameserver.model.actor.instance.Monster target=null;TacticalEpisodeV3 episode=null;boolean cut=false;
				String exclusion=null;
				try(var runtime=new PhantomLearning001ShadowNative.Runtime(authority,root.resolve("game"),legacy,attempt.resolve("stop.request"),false,5,shared,true))
				{
					java.util.function.Supplier<TacticalEpisodeV3> snapshot=null;
					try
					{
						final var actors=PhantomLearning003Damage.prepare(authority,scene,scene,work,config);
						runtime.prepareTraining(scene,grouped?actors.stream().filter(r->!r[1].equals("SOLO")).toList():actors,grouped,config.preparationSeconds(),()->requested(attempt));
						final var origin=org.l2jmobius.gameserver.model.World.getInstance().getVisibleObjects().stream().filter(org.l2jmobius.gameserver.model.actor.instance.Monster.class::isInstance).map(org.l2jmobius.gameserver.model.actor.instance.Monster.class::cast)
							.filter(m->m.getId()==scene.npc()&&!m.isAlikeDead()&&Math.hypot(m.getX()-scene.x(),m.getY()-scene.y())<1600).min(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.instance.Monster::getObjectId)).orElseThrow();
						for(int candidate=0;candidate<4;candidate++)
						{
							final var spawn=new org.l2jmobius.gameserver.model.spawns.Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc()));spawn.setXYZ(origin.getX(),origin.getY(),origin.getZ());
							final var npc=(org.l2jmobius.gameserver.model.actor.instance.Monster)spawn.doSpawn(false);spawn.stopRespawn();
							Files.writeString(work.resolve("admissions.jsonl"),"{\"candidate\":"+candidate+",\"objectId\":"+npc.getObjectId()+",\"champion\":"+npc.isChampion()+"}\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
							if(!npc.isChampion()&&!npc.isRaid()&&!npc.isAggressive()&&!npc.isAlikeDead()&&npc.getCurrentHp()==npc.getMaxHp()){target=npc;break;}npc.deleteMe();
						}
						if(target==null){throw new IllegalStateException("006 stock spawn admissions exhausted");}
						runtime.prepareOutcome006(target,slot);
						final String before=runtime.outcomeBeforeFacts();
						final var targetIdentity=target.getNativeEvidenceTarget();
						Files.writeString(work.resolve("before.json"),before+"\n",StandardOpenOption.CREATE_NEW);
						cut=true;
						System.out.println("OUTCOME006_CUT slot="+slot+" scope="+scope+" split="+scene.split()+" episode="+id);
						snapshot=runtime.runLearning006Encounter(id,attempt.getFileName().toString(),config.episodeSeconds(),()->requested(attempt),()->List.copyOf(receipts),stamp->{try{store.beginTerminalEpisode(id,attempt.getFileName().toString(),scope,before,generation,targetIdentity.instanceId(),targetIdentity.objectId(),targetIdentity.spawnGeneration(),stamp);}catch(Exception failure){throw new IllegalStateException("006 durable CUT failed",failure);}});
					}
					catch(IllegalStateException unavailable)
					{
						if(!cut&&!preCutRejection(unavailable)&&!java.util.Set.of("OUTCOME006_PRE_CUT_GEO","OUTCOME006_FULL_SOLO_ADMISSION","006 stock spawn admissions exhausted").contains(unavailable.getMessage())){throw unavailable;}
						exclusion=unavailable.getMessage();
					}
					finally
					{
						if(snapshot==null){runtime.settleTraining();}else{episode=runtime.settleLearning004(snapshot).episode();}
						runtime.releaseOutcome006();
					}
					if(episode!=null)
					{
						for(var receipt:receipts){store.record(receipt,scene.split(),context);}
						for(var failure:failures){store.recordFailure(failure,scene.split(),context,id);}
						store.settleEpisode(episode);Files.writeString(work.resolve("settlement.json"),episode.json()+"\n",StandardOpenOption.CREATE_NEW);
						if(receipts.isEmpty()||!runtime.outcomeEncounterAdmitted()||!java.util.Set.of("NATIVE_KILL","NATIVE_ACTOR_DIED","TIMEOUT").contains(episode.reason())){exclusion="NO_ELIGIBLE_TERMINAL_ACTIONS_OR_ENCOUNTER";}
						else
						{
						final var identity=target.getNativeEvidenceTarget();
						final var boundary=new org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.Boundary(installation,attempt.getFileName().toString(),generation,identity.instanceId(),identity.objectId(),identity.spawnGeneration(),runtime.outcomeCutNanos(),runtime.outcomeTerminalNanos(),episode.settledNanos(),!receipts.isEmpty());
						final var eligible=new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction>();
						for(var receipt:receipts)
						{
							try
							{
								final var timing=timings.get(receipt.eventKey());if(timing==null){throw new IllegalArgumentException("Native timing missing");}
								final var timed=timing.bind(new TacticalStoreV3.Row(scene.split(),context,receipt));
								org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.create(boundary,episode,List.of(timed),scene.split(),config.text("outcome006DatasetId",""),rewardConfig);eligible.add(timed);
							}
							catch(IllegalArgumentException invalid){Files.writeString(work.resolve("excluded-actions.jsonl"),"{\"eventKey\":\""+receipt.eventKey()+"\",\"reason\":"+PhantomLearning005Evaluation.q(invalid.getMessage())+"}\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
						}
						if(!eligible.isEmpty())
						{
							final var terminal=org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.create(boundary,episode,eligible,scene.split(),config.text("outcome006DatasetId",""),rewardConfig);store.stageTerminal(terminal);
							Files.write(work.resolve("terminal.bin"),terminal.bytes(),StandardOpenOption.CREATE_NEW);Files.writeString(work.resolve("terminal.json"),terminal.json()+"\n",StandardOpenOption.CREATE_NEW);
							System.out.println("OUTCOME006_STAGED slot="+slot+" split="+scene.split()+" outcome="+terminal.outcome()+" actions="+eligible.size()+" reward="+terminal.reward());
						}else{exclusion="NO_ELIGIBLE_TERMINAL_ACTIONS";}
						}
					}
				}
				finally{if(target!=null){target.deleteMe();}}
				Files.writeString(work.resolve("result.json"),"{\"slot\":"+slot+",\"afterCut\":"+cut+",\"nativeOutcome\":"+(episode==null?"null":PhantomLearning005Evaluation.q(episode.outcome()))+",\"exclusion\":"+(exclusion==null?"null":PhantomLearning005Evaluation.q(exclusion))+"}\n",StandardOpenOption.CREATE_NEW);
				System.out.println("OUTCOME006_SLOT_END slot="+slot+" exclusion="+exclusion);
				if(r1&&segmentAttempts>=segmentLimit){break collection;}
			}}
			Files.writeString((r1?attempt:campaign).resolve("diagnostics.json"),shared.diagnostics()+"\n",StandardOpenOption.CREATE_NEW);
			Files.writeString((r1?attempt:campaign).resolve("timing.txt"),shared.timing()+"\n",StandardOpenOption.CREATE_NEW);
			if(r1){quota=outcomeQuota(campaign);
				Files.writeString(attempt.resolve("campaign.segment.json"),"{\"campaign\":\""+campaignId+"\",\"attempted\":"+segmentAttempts+",\"quota\":"+quota+",\"timeBudget\":"+timeBudget+",\"allSlotsVisited\":"+(ordinal>=48)+"}\n",StandardOpenOption.CREATE_NEW);}
			if(!r1||quota||timeBudget||ordinal>=48){Files.writeString(campaign.resolve("native.complete"),r1?(quota?"QUOTA_REACHED\n":timeBudget?"TIME_BUDGET_EXHAUSTED\n":"SLOT_BUDGET_EXHAUSTED\n"):"Fixed16 slots consumed; no acceptance or advantage implied.\n",StandardOpenOption.CREATE_NEW);}
			Files.writeString(attempt.resolve("batch.complete"),"schemaVersion=6\ncollectionOnly=true\n",StandardOpenOption.CREATE_NEW);
		}
		finally{try{if(clock!=null){clock.close();}}finally{org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.nativeTimingObserver(_ -> { });}}
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
