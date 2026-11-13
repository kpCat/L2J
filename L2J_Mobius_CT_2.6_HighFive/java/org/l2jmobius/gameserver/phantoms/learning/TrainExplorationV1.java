/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One explicitly owned TRAIN decision opportunity. It never casts, schedules, or changes native readiness. */
public final class TrainExplorationV1
{
	public static final String CAMPAIGN="learning007-causal-v1", DATASET="learning007-advantage-v1";
	public static final double EPSILON=.25;
	public static final long TEACHER_VERSION=9;
	public static final String TEACHER_SHA="5eacf0b7619c82de3950c0203fcf599c149156d13d31d67cfc9e408362b416dc";
	public record Pick(int index,double propensity) { }
	public static void validatePermit(boolean isolated,boolean optIn,String phase,String campaign,String split)
	{
		if(!isolated||!optIn||!"COLLECT".equals(phase)||!CAMPAIGN.equals(campaign)||!"TRAIN".equals(split))
		{throw new IllegalArgumentException("TRAIN007 exploration requires isolated explicit TRAIN campaign authority");}
	}
	/** Distribution mathematics only; runtime draws come directly from ThreadLocalRandom, never from a seed override. */
	public static Pick pick(int candidates,int stock,double draw,boolean experimental)
	{
		if(candidates<2||candidates>16||stock<0||stock>=candidates||!Double.isFinite(draw)||draw<0||draw>=1)
		{throw new IllegalArgumentException("Invalid canonical alternative distribution");}
		if(!experimental){return new Pick(stock,1);}
		if(draw<1-EPSILON){return new Pick(stock,1-EPSILON);}
		final int alternative=Math.min(candidates-2,(int)((draw-(1-EPSILON))/EPSILON*(candidates-1)));
		return new Pick(alternative>=stock?alternative+1:alternative,EPSILON/(candidates-1));
	}
	public record Selection(String episode,String block,String cell,String split,boolean experimental,
		TacticalObservationV3 observation,List<LegalActionV3> offered,LegalActionV3 stock,LegalActionV3 selected,
		double propensity,double draw,String reason,String fallback,long modelVersion,String modelSha)
	{
		public Selection
		{
			offered=List.copyOf(offered);
			if(episode==null||!episode.matches("[a-f0-9]{64}")||block==null||!block.matches("[A-Za-z0-9_-]{1,64}")||cell==null||!cell.matches("[a-f0-9]{64}")
				||!Set.of("TRAIN","TUNING","AUDIT").contains(split)||experimental&&!split.equals("TRAIN")||observation==null
				||reason==null||fallback==null||fallback.length()>64||modelVersion<=0||modelSha==null||!modelSha.matches("[a-f0-9]{64}"))
			{throw new IllegalArgumentException("Invalid007 selection identity");}
			new TacticalReceiptV3.Attempt(observation,offered,selected,stock,modelVersion);
			var expected=pick(offered.size(),offered.indexOf(stock),draw,experimental);
			if(!offered.get(expected.index()).equals(selected)||Math.abs(expected.propensity()-propensity)>1e-12)
			{throw new IllegalArgumentException("Selection does not match recorded propensity");}
		}
		public Selection failed(String why)
		{return new Selection(episode,block,cell,split,experimental,observation,offered,stock,selected,propensity,draw,reason,why,modelVersion,modelSha);}
		public boolean matches(TacticalReceiptV3 receipt)
		{
			var a=receipt.attempt();
			return fallback.isEmpty()&&a.observation().equals(observation)&&a.offered().equals(offered)&&a.selected().equals(selected)
				&&a.baseline().equals(stock)&&a.modelVersion()==modelVersion;
		}
		public String sha256(){return TacticalObservationV3.hash(bytes());}
		public byte[] bytes()
		{
			try
			{
				var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);
				out.writeInt(0x45583037);out.writeInt(1);out.writeUTF(episode);out.writeUTF(block);out.writeUTF(cell);out.writeUTF(split);out.writeBoolean(experimental);
				var obs=observation.bytes();out.writeInt(obs.length);out.write(obs);out.writeInt(offered.size());for(var a:offered){a.write(out);}
				stock.write(out);selected.write(out);out.writeDouble(propensity);out.writeDouble(draw);out.writeUTF(reason);out.writeUTF(fallback);out.writeLong(modelVersion);out.writeUTF(modelSha);out.flush();return buffer.toByteArray();
			}
			catch(IOException impossible){throw new IllegalStateException(impossible);}
		}
		public static Selection restore(byte[] bytes)
		{
			if(bytes==null||bytes.length>1000000){throw new IllegalArgumentException("Selection size");}
			try(var in=new DataInputStream(new ByteArrayInputStream(bytes)))
			{
				if(in.readInt()!=0x45583037||in.readInt()!=1){throw new IllegalArgumentException("Selection schema");}
				String episode=in.readUTF(),block=in.readUTF(),cell=in.readUTF(),split=in.readUTF();boolean experimental=in.readBoolean();
				int size=in.readInt();if(size<1||size>600000){throw new IllegalArgumentException("Selection observation size");}
				var observation=TacticalObservationV3.restore(in.readNBytes(size));int count=in.readInt();if(count<2||count>16){throw new IllegalArgumentException("Selection action count");}
				var offered=new ArrayList<LegalActionV3>();for(int i=0;i<count;i++){offered.add(LegalActionV3.read(in));}
				var result=new Selection(episode,block,cell,split,experimental,observation,offered,LegalActionV3.read(in),LegalActionV3.read(in),in.readDouble(),in.readDouble(),in.readUTF(),in.readUTF(),in.readLong(),in.readUTF());
				if(in.available()!=0||!Arrays.equals(result.bytes(),bytes)){throw new IllegalArgumentException("Noncanonical selection");}return result;
			}
			catch(IOException failure){throw new IllegalArgumentException("Invalid selection bytes",failure);}
		}
	}
	private final String _episode,_block,_cell,_split,_modelSha;
	private final long _modelVersion;
	private final boolean _experimental;
	private final LegalActionV3.Family _family;
	private final Consumer<Selection> _sink;
	private final AtomicBoolean _spent=new AtomicBoolean();
	private TrainExplorationV1(String episode,String block,String cell,String split,boolean experimental,LegalActionV3.Family family,long version,String sha,Consumer<Selection> sink)
	{_episode=episode;_block=block;_cell=cell;_split=split;_experimental=experimental;_family=family;_modelVersion=version;_modelSha=sha;_sink=Objects.requireNonNull(sink);}
	/** Called before CUT by the existing native TEST adapter, never by an ordinary PLAY/MAIN policy. */
	public static TrainExplorationV1 owned(Path installation,String episode,String block,String cell,String split,boolean experimental,LegalActionV3.Family family,long version,String sha,Consumer<Selection> sink) throws IOException
	{
		var guard=new TrainingStore(installation);var config=guard.config();
		Path root=guard.installation(),clone=root.getParent().getParent().getParent();
		boolean isolated=Boolean.getBoolean("phantom.learning.train")&&root.equals(root.toRealPath())
			&&root.equals(clone.resolve(".phantom-local/learning002/train"))&&Files.isDirectory(clone.resolve("L2J_Mobius_CT_2.6_HighFive"));
		boolean optIn=config.text("exploration007Enabled","false").equals("true")&&config.text("outcome006Enabled","false").equals("true")
			&&config.text("configProfile","").equals("OUTCOME006")&&config.text("exploration007Dataset","").equals(DATASET);
		if(!Set.of("TRAIN","TUNING","AUDIT").contains(split)||family!=LegalActionV3.Family.DAMAGE&&family!=LegalActionV3.Family.HEAL||experimental&&!split.equals("TRAIN"))
		{throw new IllegalArgumentException("Exploration007 split/family rejected");}
		validatePermit(isolated,optIn,config.text("exploration007Phase",""),config.text("exploration007Campaign",""),"TRAIN");
		if(version!=TEACHER_VERSION||!TEACHER_SHA.equals(sha)){throw new IllegalArgumentException("007 collection requires exact frozen DRAFT9 teacher");}
		return new TrainExplorationV1(episode,block,cell,split,experimental,family,version,sha,sink);
	}
	Selection offer(TacticalObservationV3 observation,List<LegalActionV3> offered,LegalActionV3 stock,boolean current,boolean rescueClear)
	{
		if(!current||!rescueClear||stock.family()!=_family||_spent.get()){return null;}
		new TacticalReceiptV3.Attempt(observation,offered,stock,stock,_modelVersion);
		if(!_spent.compareAndSet(false,true)){return null;}
		double draw=_experimental?ThreadLocalRandom.current().nextDouble():0;
		var picked=pick(offered.size(),offered.indexOf(stock),draw,_experimental);var selected=offered.get(picked.index());
		String reason=!_split.equals("TRAIN")?"HOLDOUT_STOCK":!_experimental?"CONTROL_STOCK":selected.equals(stock)?"EXPLORATION_COIN_STOCK":"EXPLORATION_ALTERNATIVE";
		return new Selection(_episode,_block,_cell,_split,_experimental,observation,offered,stock,selected,picked.propensity(),draw,reason,"",_modelVersion,_modelSha);
	}
	void record(Selection selection){_sink.accept(selection);}
	public boolean spent(){return _spent.get();}
	public long modelVersion(){return _modelVersion;}
	public String modelSha(){return _modelSha;}
}
