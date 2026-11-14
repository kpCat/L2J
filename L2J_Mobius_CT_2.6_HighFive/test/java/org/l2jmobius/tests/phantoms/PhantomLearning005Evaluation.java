/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;

/** TEST evaluator contracts; no game mechanics, random damage or training here. */
public final class PhantomLearning005Evaluation
{
	public static final String SPLIT="EVAL005";
	public static final List<String> REQUIRED_FACTS=List.of("classLevel","learnedSkills","equipment","buffs","hpMpCp","positionInstance","partyRoles","npcTemplateLevel","npcHpPosition","geoLos","objectiveTimeout","configRates");
	public enum Mode { SOLO_DAMAGE_V1, PARTY_HEAL_V3 }
	public enum Side { STOCK, LEARNED }
	public enum Outcome { COMPLETED, FAILED, INCOMPLETE, SKIP }
	public enum Family { DAMAGE, HEAL }
	public record DraftAuthority(long modelId,String modelSha,String terminalDatasetSha,String optimizerSha,String runId,String namespace)
	{
		public DraftAuthority(long modelId,String modelSha,String terminalDatasetSha,String optimizerSha,String runId)
		{this(modelId,modelSha,terminalDatasetSha,optimizerSha,runId,"EVAL006R1");}
		public DraftAuthority
		{
			if(!Set.of("EVAL006R1","EVAL007","EVAL008").contains(namespace)||modelId<=(namespace.equals("EVAL008")?11:namespace.equals("EVAL007")?9:8)||runId==null||!runId.matches("[a-f0-9]{32}")){throw new IllegalArgumentException("New frozen DRAFT authority required");}
			PhantomLearning005Evaluation.sha(modelSha);PhantomLearning005Evaluation.sha(terminalDatasetSha);PhantomLearning005Evaluation.sha(optimizerSha);
		}
		String json(){return "{\"namespace\":"+q(namespace)+",\"modelId\":"+modelId+",\"modelSha\":"+q(modelSha)+","+q(namespace.equals("EVAL008")?"residualDatasetSha":namespace.equals("EVAL007")?"advantageDatasetSha":"terminalDatasetSha")+":"+q(terminalDatasetSha)+",\"optimizerSha\":"+q(optimizerSha)+",\"runId\":"+q(runId)+"}";}
		int schemaVersion(){return namespace.equals("EVAL008")?8:namespace.equals("EVAL007")?7:6;}
		String fingerprint(){return hash(json());}
	}
	public record Pin(Mode mode,long id,String status,String sha,String schema,int algorithm,DraftAuthority authority)
	{
		public Pin(Mode mode,long id,String status,String sha,String schema,int algorithm){this(mode,id,status,sha,schema,algorithm,null);}
	}
	public record Incarnation(int objectId,long epoch,int instance)
	{
		public Incarnation(int objectId,long epoch){this(objectId,epoch,0);}
		public Incarnation { if(objectId<=0||epoch<=0||instance<0){throw new IllegalArgumentException("Native incarnation required");} }
	}
	public record Kill(int target,long spawn,int instance,int creditedActor)
	{ public Kill { if(target<=0||spawn<=0||instance<0||creditedActor<=0){throw new IllegalArgumentException("Native kill identity required");} } }
	public record Effect(String key,int actor,long actorEpoch,int target,long targetEpoch,int instance,String selected,String baseline,List<String> legal,Family family,double hp,Double mp,String sourceSha,String sourceJson,Pin evaluationPin)
	{
		public Effect(String key,int actor,long actorEpoch,int target,long targetEpoch,int instance,String selected,String baseline,List<String> legal,Family family,double hp,Double mp,String sourceSha,String sourceJson)
		{this(key,actor,actorEpoch,target,targetEpoch,instance,selected,baseline,legal,family,hp,mp,sourceSha,sourceJson,null);}
		public Effect
		{
			legal=List.copyOf(legal); sha(key);sha(sourceSha);
			if(actor<=0||actorEpoch<=0||target<=0||targetEpoch<=0||instance<0||!positive(hp)||mp!=null&&!nonnegative(mp)||legal.isEmpty()||legal.size()>16
				||new HashSet<>(legal).size()!=legal.size()||!legal.contains(selected)||!legal.contains(baseline)) {throw new IllegalArgumentException("Invalid executed native effect");}
			if(family==null||sourceJson==null||sourceJson.length()>32768||!hash(sourceJson).equals(sourceSha)){throw new IllegalArgumentException("Missing or corrupt native source receipt");}
			TrainingStore.validateObservation(sourceJson);
			requireRaw(sourceJson,"eventKey",q(key));requireRaw(sourceJson,"actor",Integer.toString(actor));requireRaw(sourceJson,"actorEpoch",Long.toString(actorEpoch));
			requireRaw(sourceJson,"target",Integer.toString(target));requireRaw(sourceJson,"targetEpoch",Long.toString(targetEpoch));requireRaw(sourceJson,"instance",Integer.toString(instance));
			requireRaw(sourceJson,"selected",q(selected));requireRaw(sourceJson,"baseline",q(baseline));requireRaw(sourceJson,"legalSha",q(hash(arrayJson(legal))));
			if(evaluationPin==null)
			{
				requireRaw(sourceJson,"modelSha",q(approved(family==Family.DAMAGE?Mode.SOLO_DAMAGE_V1:Mode.PARTY_HEAL_V3).sha()));
				requireRaw(sourceJson,"modelVersion",family==Family.DAMAGE?"1":"2");
				requireRaw(sourceJson,"origin",q(family==Family.DAMAGE?"NATIVE_V1_EXACT_CAST_HP":"NATIVE_V3_EXACT_HEAL_HP"));
			}
			else
			{
				admitDraft(evaluationPin,evaluationPin.authority());
				requireRaw(sourceJson,"modelSha",q(evaluationPin.sha()));requireRaw(sourceJson,"modelVersion",Long.toString(evaluationPin.id()));
				requireRaw(sourceJson,"origin",q(family==Family.DAMAGE?"NATIVE_V3_EXACT_CAST_HP":"NATIVE_V3_EXACT_HEAL_HP"));
				requireRaw(sourceJson,"authoritySha",q(evaluationPin.authority().fingerprint()));requireRaw(sourceJson,"trainingCredit","false");
			}
			if(Double.parseDouble(rawField(sourceJson,family==Family.DAMAGE?"actualDamage":"usefulHp"))!=hp){throw new IllegalArgumentException("Source HP differs");}
			if(mp!=null&&Double.parseDouble(rawField(sourceJson,"actualMp"))!=mp){throw new IllegalArgumentException("Source MP differs");}
			if(family==Family.HEAL&&(mp==null||mp<=0||Double.parseDouble(rawField(sourceJson,"elapsedNanos"))<=0
				||Math.abs(Double.parseDouble(rawField(sourceJson,"afterHp"))-Double.parseDouble(rawField(sourceJson,"beforeHp"))-hp)>1e-6))
			{throw new IllegalArgumentException("Unproven exact heal delta or MP");}
		}
		public boolean changed() {return legal.size()>=2&&!selected.equals(baseline);}
	}
	public record Arm(String pair,Side side,Mode mode,long seed,int position,String buildSha,String configSha,String scenario,
		Map<String,String> facts,List<Incarnation> actors,Incarnation target,Pin pin,Outcome outcome,String reason,double elapsedMillis,double preparationMillis,
		Map<String,Double> metrics,List<Effect> effects,List<Kill> kills,boolean nativeSource)
	{
		public Arm
		{
			if(pair==null||!pair.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")||side==null||mode==null||outcome==null||pin==null||position<0||position>1
				||scenario==null||scenario.isBlank()||scenario.length()>256||reason==null||reason.isBlank()||reason.length()>256||!nonnegative(elapsedMillis)||!nonnegative(preparationMillis))
			{throw new IllegalArgumentException("Invalid arm envelope");}
			sha(buildSha);sha(configSha); if(pin.authority()==null){admit(pin);}else{admitDraft(pin,pin.authority());}
			if(pin.mode()!=mode){throw new IllegalArgumentException("Wrong model family");}
			facts=Collections.unmodifiableMap(new TreeMap<>(facts));actors=List.copyOf(actors);effects=List.copyOf(effects);kills=List.copyOf(kills);
			metrics=Collections.unmodifiableMap(new TreeMap<>(metrics));
			if(actors.size()>9||effects.size()>2048||kills.size()>32||metrics.size()>64||facts.size()>64||actors.stream().distinct().count()!=actors.size()
				||effects.stream().map(Effect::key).distinct().count()!=effects.size()||metrics.values().stream().anyMatch(v->v!=null&&!Double.isFinite(v)))
			{throw new IllegalArgumentException("Arm integrity or capacity");}
			if(outcome!=Outcome.SKIP && (actors.isEmpty()||target==null||!facts.keySet().containsAll(REQUIRED_FACTS))) {throw new IllegalArgumentException("Missing pre-CUT facts");}
			for(var event:effects)
			{
				if(pin.authority()!=null&&!pin.equals(event.evaluationPin())||pin.authority()==null&&event.evaluationPin()!=null){throw new IllegalArgumentException("Foreign evaluation effect authority");}
				if(actors.stream().noneMatch(a->a.objectId()==event.actor()&&a.epoch()==event.actorEpoch()&&a.instance()==event.instance())){throw new IllegalArgumentException("Foreign native actor");}
				if(event.family()==Family.DAMAGE&&(target==null||target.objectId()!=event.target()||target.epoch()!=event.targetEpoch()||target.instance()!=event.instance())){throw new IllegalArgumentException("Foreign damage recipient");}
				if(event.family()==Family.HEAL&&(mode!=Mode.PARTY_HEAL_V3||actors.stream().noneMatch(a->a.objectId()==event.target()&&a.epoch()==event.targetEpoch()&&a.instance()==event.instance()))){throw new IllegalArgumentException("Foreign heal recipient");}
				if(mode==Mode.PARTY_HEAL_V3&&event.family()==Family.DAMAGE&&event.changed()){throw new IllegalArgumentException("Damage side policy changed in HEAL comparison");}
			}
			for(var kill:kills) {if(target==null||target.objectId()!=kill.target()||target.epoch()!=kill.spawn()||target.instance()!=kill.instance()||actors.stream().noneMatch(a->a.objectId()==kill.creditedActor())){throw new IllegalArgumentException("Foreign kill");}}
			if(outcome==Outcome.COMPLETED && (!nativeSource||elapsedMillis<=0||kills.isEmpty()||effects.isEmpty()||!reason.equals("NATIVE_KILL"))) {throw new IllegalArgumentException("Unproven completed encounter");}
		}
		public String id(){return hash(pair+'/'+side);}
		public String modeLabel(){return pin.authority()!=null&&mode==Mode.SOLO_DAMAGE_V1?"SOLO_DAMAGE_V3":mode.name();}
		public String fingerprint(){return hash(modeLabel()+"/"+q(scenario)+"/"+mapJson(facts));}
		public String json()
		{
			return "{\"schemaVersion\":"+(pin.authority()==null?5:pin.authority().schemaVersion())+",\"split\":"+q(pin.authority()==null?"EVAL005":pin.authority().namespace())+",\"armId\":"+q(id())+",\"pairId\":"+q(pair)+",\"side\":"+q(side.name())+",\"mode\":"+q(modeLabel())
				+",\"seed\":"+seed+",\"position\":"+position+",\"buildSha\":"+q(buildSha)+",\"configSha\":"+q(configSha)+",\"scenario\":"+q(scenario)+",\"fingerprint\":"+q(fingerprint())
				+",\"facts\":"+mapJson(facts)+",\"actors\":["+actors.stream().map(a->"{\"id\":"+a.objectId()+",\"epoch\":"+a.epoch()+",\"instance\":"+a.instance()+"}").collect(java.util.stream.Collectors.joining(","))+"]"
				+",\"target\":"+(target==null?"null":"{\"id\":"+target.objectId()+",\"epoch\":"+target.epoch()+",\"instance\":"+target.instance()+"}")+",\"modelId\":"+pin.id()+",\"modelSha\":"+q(pin.sha())+",\"modelStatus\":"+q(pin.status())
				+(pin.authority()==null?"":",\"evaluationAuthority\":"+pin.authority().json())+",\"outcome\":"+q(outcome.name())+",\"reason\":"+q(reason)+",\"elapsedMillis\":"+elapsedMillis+",\"preparationMillis\":"+preparationMillis+",\"metrics\":"+mapJson(metrics)
				+",\"effects\":["+effects.stream().map(e->"{\"key\":"+q(e.key())+",\"actor\":"+e.actor()+",\"actorEpoch\":"+e.actorEpoch()+",\"target\":"+e.target()+",\"targetEpoch\":"+e.targetEpoch()+",\"instance\":"+e.instance()
					+",\"selected\":"+q(e.selected())+",\"stock\":"+q(e.baseline())+",\"legal\":["+e.legal().stream().map(PhantomLearning005Evaluation::q).collect(java.util.stream.Collectors.joining(","))+"]"
					+",\"family\":"+q(e.family().name())+",\"hp\":"+e.hp()+",\"mp\":"+e.mp()+",\"sourceSha\":"+q(e.sourceSha())+",\"sourceReceipt\":"+e.sourceJson()+"}").collect(java.util.stream.Collectors.joining(","))+"]"
				+",\"kills\":["+kills.stream().map(k->"{\"target\":"+k.target()+",\"spawn\":"+k.spawn()+",\"instance\":"+k.instance()+",\"creditedActor\":"+k.creditedActor()+"}").collect(java.util.stream.Collectors.joining(","))+"]"
				+",\"nativeSource\":"+nativeSource+",\"terminalRewardUsedForTraining\":false}";
		}
	}
	public record Pair(String id,String status,String reason,Arm stock,Arm learned,Map<String,Double> deltas,int divergences) { }
	public record Summary(int n,int attempts,int incomplete,int unmatched,int divergences,String label,Double median,Double minimum,Double maximum,Double ciLow,Double ciHigh) { }
	public static Pin approved(Mode mode)
	{
		return mode==Mode.SOLO_DAMAGE_V1 ? new Pin(mode,1,"ACCEPTED","e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2","53a0b97cd4c771a2d2a93b46829273f312e0dfd77626989e92b9b52ae2dc1d34",2)
			:new Pin(mode,2,"ACCEPTED_TEST","6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc",TacticalObservationV3.SCHEMA,3);
	}
	public static void admit(Pin pin) {if(pin==null||pin.mode()==null||!approved(pin.mode()).equals(pin)){throw new IllegalArgumentException("Only exact accepted model1/model2 admitted; DRAFT8 forbidden");}}
	public static void admitDraft(Pin pin,DraftAuthority authority)
	{
		if(pin==null||pin.mode()==null||authority==null||!authority.equals(pin.authority())||pin.id()!=authority.modelId()||!pin.sha().equals(authority.modelSha())
			||!pin.status().equals("DRAFT")||pin.algorithm()!=3||!pin.schema().equals(TacticalObservationV3.SCHEMA)){throw new IllegalArgumentException("Exact EVAL006R1 terminal DRAFT authority required");}
	}
	public static Side order(long seed,int pairOrdinal) {return ((seed+pairOrdinal)&1)==0?Side.STOCK:Side.LEARNED;}
	public static Pair compare(Arm first,Arm second)
	{
		final var a=first.side()==Side.STOCK?first:second; final var b=first.side()==Side.STOCK?second:first;
		String reason=null;
		if(a.side()!=Side.STOCK||b.side()!=Side.LEARNED||!a.pair().equals(b.pair())){reason="PAIR_ID_OR_SIDE";}
		else if(!Objects.equals(a.pin().authority(),b.pin().authority())){reason="EVALUATION_AUTHORITY";}
		else if(a.mode()!=b.mode()||a.seed()!=b.seed()||a.position()==b.position()||!a.buildSha().equals(b.buildSha())||!a.configSha().equals(b.configSha())){reason="CAMPAIGN_OR_ORDER";}
		else if(!a.nativeSource()||!b.nativeSource()){reason="NON_NATIVE_SOURCE";}
		else if(!a.fingerprint().equals(b.fingerprint())){reason="PRE_CUT_FACTS_DIFFER";}
		else if(a.target()==null||b.target()==null||a.target().equals(b.target())||a.actors().stream().anyMatch(b.actors()::contains)
			||a.actors().stream().anyMatch(x->b.actors().stream().anyMatch(y->x.objectId()==y.objectId()))){reason="REUSED_INCARNATION";}
		else if(a.effects().stream().anyMatch(x->b.effects().stream().anyMatch(y->x.key().equals(y.key())))){reason="DUPLICATED_NATIVE_EVENT";}
		if(reason!=null){return new Pair(a.pair(),"UNMATCHED",reason,a,b,Map.of(),0);}
		final int changed=(int)b.effects().stream().filter(e->e.family()==(b.mode()==Mode.SOLO_DAMAGE_V1?Family.DAMAGE:Family.HEAL)).filter(Effect::changed).count();
		if(a.outcome()!=Outcome.COMPLETED||b.outcome()!=Outcome.COMPLETED)
		{return new Pair(a.pair(),"MATCHED_INCOMPLETE",a.outcome()+"/"+b.outcome(),a,b,Map.of(),changed);}
		final var deltas=new TreeMap<String,Double>();
		final var keys=new TreeSet<String>(List.of("elapsedMillis","exp","sp","hp","mp","usefulHeal","deaths","loot","consumables","adenaNet","wholePartyDamage"));
		keys.addAll(a.metrics().keySet());keys.addAll(b.metrics().keySet());
		if(a.pin().authority()!=null){keys.removeIf(key->key.startsWith("rawObserved"));}
		for(String key:keys){final var av=a.metrics().get(key);final var bv=b.metrics().get(key);deltas.put(key,av==null||bv==null?null:bv-av);}
		return new Pair(a.pair(),"MATCHED_COMPLETED","PHYSICAL_FACTS_EQUAL_NATIVE_RANDOMNESS_INDEPENDENT",a,b,Collections.unmodifiableMap(deltas),changed);
	}
	public static int distinctKills(List<Kill> kills) {return (int)kills.stream().map(k->k.target()+"/"+k.spawn()+"/"+k.instance()).distinct().count();}
	public static Summary summarize(List<Pair> pairs,long seed)
	{
		if(pairs.stream().map(Pair::id).distinct().count()!=pairs.size()){throw new IllegalArgumentException("Duplicate pair in statistics");}
		final var valid=pairs.stream().filter(p->p.status().equals("MATCHED_COMPLETED")).toList();
		final var times=valid.stream().map(p->p.deltas().get("elapsedMillis")).filter(Objects::nonNull).sorted().toList();
		final int n=valid.size(), incomplete=(int)pairs.stream().filter(p->p.status().equals("MATCHED_INCOMPLETE")).count(),unmatched=pairs.size()-n-incomplete;
		final int changed=valid.stream().mapToInt(Pair::divergences).sum();
		Double low=null,high=null,median=times.isEmpty()?null:median(times);
		if(!times.isEmpty())
		{
			// Analysis RNG is offline only; never installed in GameServer or used for native outcomes.
			final var random=new Random(seed^0x4556414c303035L);final var bootstrap=new ArrayList<Double>();
			for(int sample=0;sample<2000;sample++){final var resampled=new ArrayList<Double>();for(int i=0;i<times.size();i++){resampled.add(times.get(random.nextInt(times.size())));}Collections.sort(resampled);bootstrap.add(median(resampled));}
			Collections.sort(bootstrap);low=bootstrap.get(49);high=bootstrap.get(1949);
		}
		String label=n==0?"INCONCLUSIVE":changed==0?"NO_POLICY_DIVERGENCE":"INCONCLUSIVE";
		if(n>=20&&times.size()==n&&changed>0)
		{
			final boolean safe=valid.stream().allMatch(p->p.deltas().get("deaths")!=null&&p.deltas().get("deaths")<=0);
			label=high<0&&safe&&incomplete==0&&unmatched==0?"ADVANTAGE_SUPPORTED":low>0?"REGRESSION_OBSERVED":"NO_ADVANTAGE_OBSERVED";
		}
		return new Summary(n,pairs.size()*2,incomplete,unmatched,changed,label,median,times.isEmpty()?null:times.getFirst(),times.isEmpty()?null:times.getLast(),low,high);
	}
	private static double median(List<Double> sorted){int n=sorted.size();return n%2==1?sorted.get(n/2):(sorted.get(n/2-1)+sorted.get(n/2))/2;}
	static String q(String s)
	{
		if(s==null){return "null";}
		if(s.chars().anyMatch(c->c<32&&c!='\r'&&c!='\n'&&c!='\t')){throw new IllegalArgumentException("Control character in JSON");}
		return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";
	}
	static String mapJson(Map<String,?> map) {return "{"+map.entrySet().stream().map(e->q(e.getKey())+":"+(e.getValue() instanceof String s?q(s):e.getValue())).collect(java.util.stream.Collectors.joining(","))+"}";}
	static String arrayJson(List<String> values){return "["+values.stream().map(PhantomLearning005Evaluation::q).collect(java.util.stream.Collectors.joining(","))+"]";}
	static String rawField(String json,String key)
	{
		final var pattern=java.util.regex.Pattern.compile("(?:^|[,{])\\s*\""+java.util.regex.Pattern.quote(key)+"\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null)");
		final var matcher=pattern.matcher(json);String value=null;
		while(matcher.find()){if(value!=null){throw new IllegalArgumentException("Duplicate source field: "+key);}value=matcher.group(1);}
		if(value==null){throw new IllegalArgumentException("Missing source field: "+key);}return value;
	}
	static void requireRaw(String json,String key,String expected){if(!rawField(json,key).equals(expected)){throw new IllegalArgumentException("Source field mismatch: "+key);}}
	static void sha(String value){if(value==null||!value.matches("[a-f0-9]{64}")){throw new IllegalArgumentException("SHA256 required");}}
	static boolean positive(double value){return Double.isFinite(value)&&value>0;}
	static boolean nonnegative(double value){return Double.isFinite(value)&&value>=0;}
	public static String hash(String value)
	{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
