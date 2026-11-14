/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Offline terminal feedback. Historical settlements and action rewards are never rewritten. */
public final class TerminalRewardReceiptV1
{
	public static final int VERSION=1, MAX_BYTES=16*1024*1024;
	public static final String REVISION="006/native-goal-time-exp-sp-useful-heal-observed-paid-mp-death-lb/MC-gamma1/v1";
	public record NativeTiming(String eventKey,int instanceId,long hpStart,long hpEnd,long mpStart,long mpEnd,boolean lethal)
	{
		public TimedAction bind(TacticalStoreV3.Row row)
		{if(!eventKey.equals(row.eventKey())){throw new IllegalArgumentException("Foreign timing receipt");}return new TimedAction(row,instanceId,hpStart,hpEnd,mpStart,mpEnd,lethal);}
	}
	private static volatile java.util.function.Consumer<NativeTiming> NATIVE_TIMING=_ -> { };
	/** Explicit native TEST observer; no storage, scheduler or reward computation on the cast path. */
	public static void nativeTimingObserver(java.util.function.Consumer<NativeTiming> sink){NATIVE_TIMING=Objects.requireNonNull(sink);}
	static void nativeTiming(NativeTiming timing){NATIVE_TIMING.accept(timing);}
	public record Boundary(String installation,String ownerNonce,long ownerGeneration,int instanceId,int targetObjectId,long spawnGeneration,
		long cutNanos,long terminalNanos,long settledNanos,boolean encounterAdmitted)
	{
		public Boundary
		{
			if(installation==null||!installation.matches("[a-f0-9]{64}")||ownerNonce==null||!ownerNonce.matches("[a-f0-9]{32}")||ownerGeneration<=0||instanceId<0
				||targetObjectId<=0||spawnGeneration<=0||cutNanos<=0||terminalNanos<=cutNanos||settledNanos<terminalNanos||!encounterAdmitted)
			{throw new IllegalArgumentException("Unproven native terminal boundary");}
		}
	}
	/** Native write intervals are recorded by the existing exact HP/MP hooks, not inferred from publication latency. */
	public record TimedAction(TacticalStoreV3.Row row,int instanceId,long hpStartedNanos,long hpFinishedNanos,long mpStartedNanos,long mpFinishedNanos,boolean linkedLethalWrite)
	{
		public TimedAction
		{
			if(row==null||instanceId<0||hpStartedNanos<=0||hpFinishedNanos<hpStartedNanos||mpStartedNanos<=0||mpFinishedNanos<mpStartedNanos)
			{throw new IllegalArgumentException("Missing native write timestamps");}
		}
	}
	public record Components(int uniqueKills,long durationNanos,Long creditedExp,Long creditedSp,double usefulHeal,double observedPaidMp,int observedDeathsLowerBound,boolean terminalDead) { }
	public static final class Config
	{
		private final byte[] _bytes;
		private final double goal,time,exp,sp,heal,mp,timeScale,expScale,spScale,healScale,mpScale;
		private Config(byte[] bytes)
		{
			if(bytes==null||bytes.length>16384){throw new IllegalArgumentException("Reward configuration size");}
			_bytes=bytes.clone();final String json=new String(_bytes,StandardCharsets.UTF_8);TrainingStore.validateObservation(json);
			if(number(json,"schemaVersion")!=1||number(json,"confirmedKillGoal")!=1||number(json,"admittedEncounterTimeoutGoal")!=-1||number(json,"observedDeathRewardOverride")!=-1
				||!json.contains("\"GOAL_TIME_EXP_SP_HEAL_OBSERVED_MP_DEATH_LB_V1\"")||!json.contains("\"EXECUTED_ACTION_MONTE_CARLO_GAMMA_1\""))
			{throw new IllegalArgumentException("Unsupported terminal reward schema");}
			goal=weight(json,"goal");time=weight(json,"time");exp=weight(json,"exp");sp=weight(json,"sp");heal=weight(json,"heal");mp=weight(json,"observedMp");
			timeScale=scale(json,"timeNanos");expScale=scale(json,"creditedExp");spScale=scale(json,"creditedSp");healScale=scale(json,"usefulHealHp");mpScale=scale(json,"observedPaidMp");
			if(goal<=time+mp||goal<=exp+sp+heal){throw new IllegalArgumentException("Terminal outcome must dominate positive resource components");}
		}
		public static Config load(Path path) throws IOException {if(!path.toAbsolutePath().normalize().equals(path.toRealPath())||Files.isSymbolicLink(path)){throw new IOException("Physical reward configuration required");}return new Config(Files.readAllBytes(path));}
		public String sha256(){return TacticalObservationV3.hash(_bytes);}
		public byte[] bytes(){return _bytes.clone();}
		private static double number(String json,String key)
		{
			final var match=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)").matcher(json);
			if(!match.find()){throw new IllegalArgumentException("Missing reward configuration field: "+key);}final double result=Double.parseDouble(match.group(1));
			if(!Double.isFinite(result)||match.find()){throw new IllegalArgumentException("Duplicate or invalid reward configuration field: "+key);}return result;
		}
		private static double weight(String json,String key){final double v=number(json,key);if(v<0||v>1){throw new IllegalArgumentException("Reward weight bound");}return v;}
		private static double scale(String json,String key){final double v=number(json,key);if(v<=0||v>1e15){throw new IllegalArgumentException("Reward scale bound");}return v;}
		public static Config defaults()
		{
			return new Config(DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
		}
		private static final String DEFAULT_JSON="""
{
  "schemaVersion": 1,
  "name": "GOAL_TIME_EXP_SP_HEAL_OBSERVED_MP_DEATH_LB_V1",
  "weights": { "goal": 0.65, "time": 0.15, "exp": 0.08, "sp": 0.04, "heal": 0.06, "observedMp": 0.02 },
  "scales": { "timeNanos": 120000000000, "creditedExp": 100000, "creditedSp": 20000, "usefulHealHp": 10000, "observedPaidMp": 2000 },
  "componentClip": [0, 1],
  "rewardClip": [-1, 1],
  "confirmedKillGoal": 1,
  "admittedEncounterTimeoutGoal": -1,
  "observedDeathRewardOverride": -1,
  "completeDeathCount": "UNKNOWN_EXCLUDED",
  "wholePartyMp": "UNKNOWN_EXCLUDED",
  "economy": "UNKNOWN_EXCLUDED",
  "attribution": "EXECUTED_ACTION_MONTE_CARLO_GAMMA_1",
  "incompleteOrInterrupted": "EXCLUDED",
  "lateDrainEffects": "RETAIN_EVIDENCE_EXCLUDE_FIT",
  "trainMinActions": 16,
  "tuningMinActions": 8,
  "modelStatus": "DRAFT"
}
""";
	}
	private final Boundary _boundary;
	private final TacticalEpisodeV3 _episode;
	private final List<TimedAction> _actions;
	private final String _split,_dataset;
	private final Config _config;
	private final Components _components;
	private final float _reward;
	private TerminalRewardReceiptV1(Boundary boundary,TacticalEpisodeV3 episode,List<TimedAction> actions,String split,String dataset,Config config)
	{
		if(boundary==null||episode==null||config==null||!Set.of("TRAIN","TUNING","AUDIT").contains(split)||dataset==null||!dataset.matches("[A-Za-z0-9._-]{1,96}")
			||actions==null||actions.isEmpty()||actions.size()>1024||!boundary.ownerNonce().equals(episode.ownerNonce())||boundary.cutNanos()!=episode.startedNanos()
			||boundary.settledNanos()!=episode.settledNanos()||!Set.of("NATIVE_KILL","NATIVE_ACTOR_DIED","TIMEOUT").contains(episode.reason()))
		{throw new IllegalArgumentException("Incomplete terminal receipt or split contamination");}
		_boundary=boundary;_episode=episode;_actions=List.copyOf(actions);_split=split;_dataset=dataset;_config=config;
		final var events=new HashSet<String>();double usefulHeal=0,paidMp=0;
		for(var action:_actions)
		{
			final var row=action.row();final var receipt=row.receipt();final var selected=receipt.attempt().selected();final var observation=receipt.attempt().observation();
			if(!row.split().equals(split)||!events.add(row.eventKey())||!episode.eventKeys().contains(row.eventKey())||action.instanceId()!=boundary.instanceId()
				||observation.capturedNanos()<boundary.cutNanos()||observation.capturedNanos()>action.hpStartedNanos()
				||action.mpStartedNanos()<boundary.cutNanos()||action.mpFinishedNanos()>boundary.terminalNanos()
				||episode.actors().stream().noneMatch(a->a.profile()==selected.profileId()&&a.objectId()==selected.actorObjectId()&&a.epoch()==selected.actorEpoch()))
			{throw new IllegalArgumentException("Foreign, stale, duplicate or late native action");}
			final boolean damage=selected.family()==LegalActionV3.Family.DAMAGE;
			if(damage&&(selected.targetObjectId()!=boundary.targetObjectId()||selected.targetEpoch()!=boundary.spawnGeneration())
				||!damage&&episode.actors().stream().noneMatch(a->a.objectId()==selected.targetObjectId()&&a.epoch()==selected.targetEpoch()))
			{throw new IllegalArgumentException("Foreign native recipient");}
			final boolean lethal=action.linkedLethalWrite()&&damage&&action.hpStartedNanos()<=boundary.terminalNanos()&&action.hpFinishedNanos()>=boundary.terminalNanos()
				&&episode.kills().stream().anyMatch(k->k.targetObjectId()==boundary.targetObjectId()&&k.spawnGeneration()==boundary.spawnGeneration()&&k.instanceId()==boundary.instanceId()&&k.creditedActorId()==selected.actorObjectId());
			if(action.hpStartedNanos()<boundary.cutNanos()||action.hpStartedNanos()>boundary.terminalNanos()||action.hpFinishedNanos()>boundary.terminalNanos()&&!lethal
				||action.hpFinishedNanos()>boundary.settledNanos()||action.linkedLethalWrite()&&!lethal)
			{throw new IllegalArgumentException("Native effect outside terminal boundary");}
			if(!damage){usefulHeal+=receipt.actualHp();}paidMp+=receipt.actualMp();
		}
		final var kills=new HashSet<String>();
		for(var kill:episode.kills())
		{
			if(kill.targetObjectId()==boundary.targetObjectId()&&kill.spawnGeneration()==boundary.spawnGeneration()&&kill.instanceId()==boundary.instanceId())
			{kills.add(kill.targetObjectId()+"/"+kill.spawnGeneration()+"/"+kill.instanceId());}
		}
		if(episode.reason().equals("NATIVE_KILL")&&kills.isEmpty()){throw new IllegalArgumentException("Unproven target goal");}
		final boolean dead=episode.actors().stream().anyMatch(TacticalEpisodeV3.Actor::dead);
		final Long exp=episode.actors().stream().allMatch(a->a.exp()!=null)?sum(episode.actors().stream().map(TacticalEpisodeV3.Actor::exp).toList()):null;
		final Long sp=episode.actors().stream().allMatch(a->a.sp()!=null)?sum(episode.actors().stream().map(TacticalEpisodeV3.Actor::sp).toList()):null;
		if(!dead&&episode.observedDeaths()==0&&(exp==null||sp==null)){throw new IllegalArgumentException("Canonical credited earnings UNKNOWN");}
		_components=new Components(kills.size(),Math.subtractExact(boundary.terminalNanos(),boundary.cutNanos()),exp,sp,usefulHeal,paidMp,episode.observedDeaths(),dead);
		_reward=dead||episode.observedDeaths()>0?-1:(float)Math.clamp(config.goal*(kills.isEmpty()?-1:1)-config.time*clip(_components.durationNanos()/config.timeScale)
			+config.exp*clip(exp/config.expScale)+config.sp*clip(sp/config.spScale)+config.heal*clip(usefulHeal/config.healScale)-config.mp*clip(paidMp/config.mpScale),-1,1);
	}
	private static long sum(List<Long> values){long result=0;for(long value:values){result=Math.addExact(result,value);}return result;}
	private static double clip(double value){return Math.clamp(value,0,1);}
	public static TerminalRewardReceiptV1 create(Boundary boundary,TacticalEpisodeV3 episode,List<TimedAction> actions,String split,String dataset,Config config){return new TerminalRewardReceiptV1(boundary,episode,actions,split,dataset,config);}
	public Boundary boundary(){return _boundary;}
	public TacticalEpisodeV3 episode(){return _episode;}
	public List<TimedAction> actions(){return _actions;}
	public String split(){return _split;}
	public String dataset(){return _dataset;}
	public Config config(){return _config;}
	public Components components(){return _components;}
	public float reward(){return _reward;}
	public String outcome(){return _components.terminalDead()||_components.observedDeathsLowerBound()>0||_components.uniqueKills()==0?"FAILED":"COMPLETED";}
	public String naturalKey(){return hash("NATIVE_EPISODE_CREDIT/"+_boundary.installation()+"/"+_boundary.ownerNonce()+"/"+_boundary.ownerGeneration()+"/"+_episode.id());}
	public String goalKey(){return _components.uniqueKills()==0?null:hash("NATIVE_KILL/"+_boundary.ownerNonce()+"/"+_boundary.targetObjectId()+"/"+_boundary.spawnGeneration()+"/"+_boundary.instanceId());}
	private static String hash(String value){return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8));}
	private static String q(String value){return TrainingStore.quote(value);}
	public String json()
	{
		return "{\"schemaVersion\":1,\"naturalKey\":"+q(naturalKey())+",\"goalKey\":"+(goalKey()==null?"null":q(goalKey()))+",\"episodeId\":"+q(_episode.id())+",\"ownerNonce\":"+q(_boundary.ownerNonce())
			+",\"installation\":"+q(_boundary.installation())+",\"ownerGeneration\":"+_boundary.ownerGeneration()+",\"instanceId\":"+_boundary.instanceId()+",\"targetObjectId\":"+_boundary.targetObjectId()+",\"spawnGeneration\":"+_boundary.spawnGeneration()
			+",\"cutNanos\":"+_boundary.cutNanos()+",\"terminalNanos\":"+_boundary.terminalNanos()+",\"settledNanos\":"+_boundary.settledNanos()+",\"settlementSha\":"+q(_episode.sha256())+",\"split\":"+q(_split)+",\"dataset\":"+q(_dataset)+",\"configSha\":"+q(_config.sha256())
			+",\"outcome\":"+q(outcome())+",\"uniqueKills\":"+_components.uniqueKills()+",\"durationNanos\":"+_components.durationNanos()+",\"creditedExp\":"+_components.creditedExp()+",\"creditedSp\":"+_components.creditedSp()+",\"observedUsefulHeal\":"+_components.usefulHeal()+",\"observedPaidMp\":"+_components.observedPaidMp()
			+",\"observedDeathsLowerBound\":"+_components.observedDeathsLowerBound()+",\"terminalDead\":"+_components.terminalDead()+",\"completeDeathCount\":null,\"wholePartyMp\":null,\"adenaNet\":null,\"loot\":null,\"consumables\":null,\"reward\":"+_reward
			+",\"normalizedComponents\":{\"goal\":"+(_components.uniqueKills()>0?1:-1)+",\"time\":"+clip(_components.durationNanos()/_config.timeScale)
			+",\"creditedExp\":"+(_components.creditedExp()==null?"null":Double.toString(clip(_components.creditedExp()/_config.expScale)))
			+",\"creditedSp\":"+(_components.creditedSp()==null?"null":Double.toString(clip(_components.creditedSp()/_config.spScale)))
			+",\"usefulHeal\":"+clip(_components.usefulHeal()/_config.healScale)+",\"observedPaidMp\":"+clip(_components.observedPaidMp()/_config.mpScale)+"}"
			+",\"actions\":["+_actions.stream().map(a->"{\"eventKey\":"+q(a.row().eventKey())+",\"receiptSha\":"+q(TacticalObservationV3.hash(TacticalStoreV3.encode(a.row())))+",\"hpStart\":"+a.hpStartedNanos()+",\"hpEnd\":"+a.hpFinishedNanos()+",\"mpStart\":"+a.mpStartedNanos()+",\"mpEnd\":"+a.mpFinishedNanos()+",\"linkedLethalWrite\":"+a.linkedLethalWrite()+"}").collect(java.util.stream.Collectors.joining(","))
			+"],\"applicationState\":\"STAGED\",\"terminalRewardUsedForTraining\":false}";
	}
	public byte[] bytes()
	{
		try
		{
			final var buffer=new ByteArrayOutputStream();final var out=new DataOutputStream(buffer);
			out.writeInt(0x54523631);out.writeInt(VERSION);blob(out,_config.bytes());
			out.writeUTF(_boundary.installation());out.writeUTF(_boundary.ownerNonce());out.writeLong(_boundary.ownerGeneration());out.writeInt(_boundary.instanceId());out.writeInt(_boundary.targetObjectId());out.writeLong(_boundary.spawnGeneration());
			out.writeLong(_boundary.cutNanos());out.writeLong(_boundary.terminalNanos());out.writeLong(_boundary.settledNanos());out.writeBoolean(_boundary.encounterAdmitted());
			out.writeUTF(_episode.id());out.writeUTF(_episode.ownerNonce());out.writeUTF(_episode.scope());out.writeLong(_episode.startedNanos());out.writeLong(_episode.settledNanos());out.writeUTF(_episode.reason());
			out.writeInt(_episode.actors().size());for(var actor:_episode.actors())
			{out.writeLong(actor.profile());out.writeInt(actor.objectId());out.writeLong(actor.epoch());out.writeInt(actor.classId());out.writeInt(actor.level());out.writeDouble(actor.hp());out.writeDouble(actor.mp());out.writeBoolean(actor.dead());nullable(out,actor.exp());nullable(out,actor.sp());}
			out.writeInt(_episode.kills().size());for(var kill:_episode.kills())
			{out.writeInt(kill.targetObjectId());out.writeInt(kill.npcId());out.writeLong(kill.spawnGeneration());out.writeInt(kill.instanceId());out.writeInt(kill.creditedActorId());out.writeLong(kill.nativeSequence());}
			out.writeInt(_episode.eventKeys().size());for(String key:_episode.eventKeys()){out.writeUTF(key);}
			out.writeDouble(_episode.exactObservedDamage());out.writeDouble(_episode.usefulObservedHeal());out.writeDouble(_episode.exactObservedMp());out.writeInt(_episode.observedDeaths());
			out.writeUTF(_split);out.writeUTF(_dataset);out.writeInt(_actions.size());
			for(var action:_actions)
			{
				blob(out,TacticalStoreV3.encode(action.row()));out.writeInt(action.instanceId());out.writeLong(action.hpStartedNanos());out.writeLong(action.hpFinishedNanos());out.writeLong(action.mpStartedNanos());out.writeLong(action.mpFinishedNanos());out.writeBoolean(action.linkedLethalWrite());
				if(buffer.size()>MAX_BYTES){throw new IllegalArgumentException("Terminal receipt byte bound");}
			}
			out.flush();return buffer.toByteArray();
		}
		catch(IOException failure){throw new IllegalArgumentException("Terminal receipt encoding failed",failure);}
	}
	public static TerminalRewardReceiptV1 restore(byte[] bytes,String expectedSha)
	{
		if(bytes==null||bytes.length>MAX_BYTES||expectedSha==null||!expectedSha.matches("[a-f0-9]{64}")||!TacticalObservationV3.hash(bytes).equals(expectedSha)){throw new IllegalArgumentException("Terminal receipt checksum changed");}
		try(var in=new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if(in.readInt()!=0x54523631||in.readInt()!=VERSION){throw new IllegalArgumentException("Terminal receipt codec schema");}
			final var config=new Config(blob(in,16384));
			final var boundary=new Boundary(in.readUTF(),in.readUTF(),in.readLong(),in.readInt(),in.readInt(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readBoolean());
			final String id=in.readUTF(),nonce=in.readUTF(),scope=in.readUTF();final long start=in.readLong(),settle=in.readLong();final String reason=in.readUTF();
			final var actors=new ArrayList<TacticalEpisodeV3.Actor>();final int actorCount=count(in,1,9);
			for(int i=0;i<actorCount;i++){actors.add(new TacticalEpisodeV3.Actor(in.readLong(),in.readInt(),in.readLong(),in.readInt(),in.readInt(),in.readDouble(),in.readDouble(),in.readBoolean(),nullable(in),nullable(in)));}
			final var kills=new ArrayList<TacticalEpisodeV3.Kill>();final int killCount=count(in,0,16);
			for(int i=0;i<killCount;i++){kills.add(new TacticalEpisodeV3.Kill(in.readInt(),in.readInt(),in.readLong(),in.readInt(),in.readInt(),in.readLong()));}
			final var keys=new ArrayList<String>();final int keyCount=count(in,0,1024);for(int i=0;i<keyCount;i++){keys.add(in.readUTF());}
			final var episode=new TacticalEpisodeV3(id,nonce,scope,start,settle,reason,actors,kills,keys,in.readDouble(),in.readDouble(),in.readDouble(),in.readInt());
			final String split=in.readUTF(),dataset=in.readUTF();final int actionCount=count(in,1,1024);final var actions=new ArrayList<TimedAction>();
			for(int i=0;i<actionCount;i++){actions.add(new TimedAction(TacticalStoreV3.decode(blob(in,1024*1024)),in.readInt(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readBoolean()));}
			if(in.available()!=0){throw new IllegalArgumentException("Trailing terminal receipt bytes");}
			final var restored=create(boundary,episode,actions,split,dataset,config);
			if(!Arrays.equals(restored.bytes(),bytes)){throw new IllegalArgumentException("Noncanonical terminal receipt bytes");}return restored;
		}
		catch(IOException failure){throw new IllegalArgumentException("Invalid terminal receipt envelope",failure);}
	}
	private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
	private static byte[] blob(DataInputStream in,int maximum)throws IOException{final int length=count(in,1,maximum);final byte[] bytes=in.readNBytes(length);if(bytes.length!=length){throw new EOFException();}return bytes;}
	private static int count(DataInputStream in,int minimum,int maximum)throws IOException{final int n=in.readInt();if(n<minimum||n>maximum){throw new IllegalArgumentException("Terminal receipt collection bound");}return n;}
	private static void nullable(DataOutputStream out,Long value)throws IOException{out.writeBoolean(value!=null);if(value!=null){out.writeLong(value);}}
	private static Long nullable(DataInputStream in)throws IOException{return in.readBoolean()?in.readLong():null;}
	public String sha256(){return TacticalObservationV3.hash(bytes());}
}
