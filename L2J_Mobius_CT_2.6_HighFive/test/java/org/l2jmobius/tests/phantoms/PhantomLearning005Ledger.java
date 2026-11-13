/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;
import org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.*;

/** Atomic immutable receipt frames plus verified canonical JSON; no SQL or training. */
public final class PhantomLearning005Ledger implements AutoCloseable
{
	private static final int MAGIC=0x45563035,MAX=4*1024*1024;
	private final Path root;
	private final FileChannel channel;
	private final FileLock lock;
	private final String build;
	private final DraftAuthority authority;
	public PhantomLearning005Ledger(Path directory,String campaign,String build) throws IOException
	{this(directory,campaign,build,build);}
	public PhantomLearning005Ledger(Path directory,String campaign,String build,String protocolSha) throws IOException
	{this(directory,campaign,build,protocolSha,null);}
	public PhantomLearning005Ledger(Path directory,String campaign,String build,String protocolSha,DraftAuthority authority) throws IOException
	{
		this.authority=authority;
		PhantomLearning005Evaluation.sha(build);
		PhantomLearning005Evaluation.sha(protocolSha);
		if(campaign==null||!campaign.matches("[A-Za-z0-9._-]{1,96}")){throw new IllegalArgumentException("Campaign identity required");}
		Files.createDirectories(directory);root=directory.toAbsolutePath().normalize();
		if(!root.equals(root.toRealPath())){throw new IOException("Physical evaluation directory required");}
		this.build=build;
		final var lockPath=root.resolve("campaign.lock");physicalIfExists(lockPath);
		channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
		FileLock acquired=null;
		try
		{
			try{acquired=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Campaign already locked",busy);}
			if(acquired==null){throw new IOException("Campaign already locked");}
			final String identity="schemaVersion="+(authority==null?5:authority.schemaVersion())+"\ncampaign="+campaign+"\nbuild="+build+"\nprotocol="+protocolSha+"\nsplit="+(authority==null?"EVAL005":authority.namespace())+"\n"+(authority==null?"":"authority="+authority.fingerprint()+"\n");
			final Path manifest=root.resolve("campaign.ini");
			if(Files.exists(manifest)){physicalIfExists(manifest);if(!Files.readString(manifest).equals(identity)){throw new IOException("Campaign pins changed");}}
			else{atomic(manifest,identity.getBytes(StandardCharsets.UTF_8));}
			lock=acquired;
		}
		catch(IOException|RuntimeException failure){if(acquired!=null){acquired.close();}channel.close();throw failure;}
	}
	public boolean put(Arm arm) throws IOException
	{
		requireOpen();if(!arm.buildSha().equals(build)||!Objects.equals(authority,arm.pin().authority())){throw new IOException("Arm build or authority differs from campaign");}
		TrainingStore.validateObservation(arm.json());
		final var payload=pack(arm);final Path path=root.resolve(arm.id()+".bin");
		if(Files.exists(path))
		{
			final var old=read(path);if(!Arrays.equals(payload,pack(old))){throw new IOException("Conflicting immutable arm replay");}
			return false;
		}
		final var keys=new HashSet<String>();for(var old:arms()){for(var event:old.effects()){keys.add(event.key());}}
		if(arm.effects().stream().anyMatch(e->keys.contains(e.key()))){throw new IOException("Native event credited by another arm");}
		atomic(path,frame(payload));
		atomic(root.resolve(arm.id()+".json"),(arm.json()+"\n").getBytes(StandardCharsets.UTF_8));
		pairs();
		return true;
	}
	public List<Arm> arms() throws IOException
	{
		requireOpen();final var arms=new ArrayList<Arm>();final var keys=new HashSet<String>();
		try(var paths=Files.list(root))
		{
			for(var path:paths.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.bin")).sorted().toList())
			{
				if(arms.size()>=8192){throw new IOException("Campaign capacity");}
				final var arm=read(path);if(!arm.buildSha().equals(build)){throw new IOException("Foreign build");}
				for(var effect:arm.effects()){if(!keys.add(effect.key())){throw new IOException("Duplicated native source event");}}
				arms.add(arm);
			}
		}
		return List.copyOf(arms);
	}
	public List<Pair> pairs() throws IOException
	{
		final var result=new ArrayList<Pair>();
		for(var group:arms().stream().collect(java.util.stream.Collectors.groupingBy(Arm::pair,TreeMap::new,java.util.stream.Collectors.toList())).values())
		{
			if(group.size()!=2){continue;}final var pair=PhantomLearning005Evaluation.compare(group.get(0),group.get(1));
			final String json="{\"schemaVersion\":"+(authority==null?5:authority.schemaVersion())+",\"pairId\":"+PhantomLearning005Evaluation.q(pair.id())+",\"status\":"+PhantomLearning005Evaluation.q(pair.status())+",\"reason\":"+PhantomLearning005Evaluation.q(pair.reason())
				+",\"stockArm\":"+PhantomLearning005Evaluation.q(pair.stock().id())+",\"stockSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(pair.stock().json()))
				+",\"learnedArm\":"+PhantomLearning005Evaluation.q(pair.learned().id())+",\"learnedSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(pair.learned().json()))
				+",\"deltas\":"+PhantomLearning005Evaluation.mapJson(pair.deltas())+",\"divergences\":"+pair.divergences()+"}\n";
			final Path path=root.resolve(PhantomLearning005Evaluation.hash(pair.id())+".pair.json");
			if(Files.exists(path)){physicalIfExists(path);if(!Files.readString(path).equals(json)){throw new IOException("Immutable pair result differs");}}
			else{atomic(path,json.getBytes(StandardCharsets.UTF_8));}
			result.add(pair);
		}return List.copyOf(result);
	}
	private Arm read(Path path) throws IOException
	{return read(path,true).arm();}
	private record ReadFrame(Arm arm,String nonce) { }
	private ReadFrame read(Path path,boolean complete) throws IOException
	{
		physicalIfExists(path);if(Files.size(path)>MAX){throw new IOException("Oversized receipt");}
		try(var in=new DataInputStream(Files.newInputStream(path)))
		{
			if(in.readInt()!=MAGIC||in.readInt()!=(authority==null?5:authority.schemaVersion())){throw new IOException("Unknown evaluator schema");}
			int length=in.readInt();if(length<1||length>MAX){throw new IOException("Invalid frame length");}
			String sha=in.readUTF();byte[] bytes=in.readNBytes(length);
			if(bytes.length!=length||in.read()!=-1||!sha.equals(digest(bytes))){throw new IOException("Receipt digest or truncation");}
			String nonce=null;
			if(!complete)
			{
				try(var start=new DataInputStream(new ByteArrayInputStream(bytes)))
				{
					nonce=start.readUTF();int size=count(start,MAX);byte[] payload=start.readNBytes(size);
					if(payload.length!=size||start.read()!=-1||!nonce.matches("[a-f0-9]{32}")){throw new IOException("Invalid atomic START");}
					bytes=payload;
				}
			}
			final var arm=unpack(bytes,authority);
			if(!path.getFileName().toString().equals(arm.id()+(complete?".bin":".started.bin"))){throw new IOException("Receipt filename mismatch");}
			if(!complete){return new ReadFrame(arm,nonce);}
			final var json=root.resolve(arm.id()+".json");
			if(Files.exists(json))
			{
				physicalIfExists(json);if(Files.size(json)>MAX){throw new IOException("Oversized JSON");}
				final String body=Files.readString(json);TrainingStore.validateObservation(body);
				if(!body.equals(arm.json()+"\n")){throw new IOException("Canonical JSON disagrees with immutable frame");}
			}
			else{atomic(json,(arm.json()+"\n").getBytes(StandardCharsets.UTF_8));}
			return new ReadFrame(arm,null);
		}
	}
	public record Started(Arm arm,String nonce) { }
	public void begin(Arm arm,String nonce) throws IOException
	{
		if(!Objects.equals(authority,arm.pin().authority())){throw new IOException("START evaluation authority differs");}
		requireOpen();if(nonce==null||!nonce.matches("[a-f0-9]{32}")||arm.outcome()!=Outcome.INCOMPLETE||!arm.effects().isEmpty()||!arm.kills().isEmpty()){throw new IOException("Invalid START journal");}
		if(Files.exists(root.resolve(arm.id()+".bin"))||Files.exists(root.resolve(arm.id()+".started.bin"))){throw new IOException("Arm already started; no fresh credit");}
		final var bytes=new ByteArrayOutputStream();final byte[] payload=pack(arm);
		try(var out=new DataOutputStream(bytes)){out.writeUTF(nonce);out.writeInt(payload.length);out.write(payload);}
		atomic(root.resolve(arm.id()+".started.bin"),frame(bytes.toByteArray()));
	}
	public List<Started> pending() throws IOException
	{
		requireOpen();final var starts=new ArrayList<Started>();
		try(var paths=Files.list(root))
		{
			for(var path:paths.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.started\\.bin")).sorted().toList())
			{
				final var start=read(path,false);final var arm=start.arm();if(Files.exists(root.resolve(arm.id()+".bin"))){continue;}
				starts.add(new Started(arm,start.nonce()));
			}
		}return List.copyOf(starts);
	}
	private byte[] frame(byte[] payload) throws IOException
	{
		final var output=new ByteArrayOutputStream();try(var out=new DataOutputStream(output)){out.writeInt(MAGIC);out.writeInt(authority==null?5:authority.schemaVersion());out.writeInt(payload.length);out.writeUTF(digest(payload));out.write(payload);}return output.toByteArray();
	}
	private void atomic(Path target,byte[] bytes) throws IOException
	{
		if(bytes.length>MAX||!target.getParent().equals(root)){throw new IOException("Owned bounded path required");}
		if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){throw new FileAlreadyExistsException(target.toString());}
		final Path pending=root.resolve("pending-"+UUID.randomUUID());
		try(var file=FileChannel.open(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE))
		{final var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()){file.write(buffer);}file.force(true);}
		try{Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(pending);}
	}
	private void physicalIfExists(Path path) throws IOException
	{if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&(!path.equals(path.toRealPath())||Files.isSymbolicLink(path))){throw new IOException("Reparse path refused");}}
	private void requireOpen() throws IOException {if(!lock.isValid()){throw new IOException("Ledger is closed");}}
	@Override public void close() throws IOException {try{lock.close();}finally{channel.close();}}
	private static String digest(byte[] bytes)
	{try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
	private static void incarnation(DataOutputStream out,Incarnation i) throws IOException {out.writeInt(i.objectId());out.writeLong(i.epoch());out.writeInt(i.instance());}
	private static Incarnation incarnation(DataInputStream in) throws IOException {return new Incarnation(in.readInt(),in.readLong(),in.readInt());}
	private static int count(DataInputStream in,int max) throws IOException {int n=in.readInt();if(n<0||n>max){throw new IOException("Codec count bound");}return n;}
	private static byte[] pack(Arm a) throws IOException
	{
		var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes))
		{
			out.writeUTF(a.pair());out.writeUTF(a.side().name());out.writeUTF(a.mode().name());out.writeLong(a.seed());out.writeInt(a.position());out.writeUTF(a.buildSha());out.writeUTF(a.configSha());out.writeUTF(a.scenario());
			out.writeInt(a.facts().size());for(var e:a.facts().entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
			out.writeInt(a.actors().size());for(var i:a.actors()){incarnation(out,i);}out.writeBoolean(a.target()!=null);if(a.target()!=null){incarnation(out,a.target());}
			out.writeLong(a.pin().id());out.writeUTF(a.pin().status());out.writeUTF(a.pin().sha());out.writeUTF(a.pin().schema());out.writeInt(a.pin().algorithm());
			if(a.pin().authority()!=null){final var pin=a.pin().authority();out.writeLong(pin.modelId());out.writeUTF(pin.modelSha());out.writeUTF(pin.terminalDatasetSha());out.writeUTF(pin.optimizerSha());out.writeUTF(pin.runId());if(pin.namespace().equals("EVAL007")){out.writeUTF(pin.namespace());}}
			out.writeUTF(a.outcome().name());out.writeUTF(a.reason());out.writeDouble(a.elapsedMillis());out.writeDouble(a.preparationMillis());
			out.writeInt(a.metrics().size());for(var e:a.metrics().entrySet()){out.writeUTF(e.getKey());out.writeBoolean(e.getValue()!=null);if(e.getValue()!=null){out.writeDouble(e.getValue());}}
			out.writeInt(a.effects().size());for(var e:a.effects())
			{
				out.writeUTF(e.key());out.writeInt(e.actor());out.writeLong(e.actorEpoch());out.writeInt(e.target());out.writeLong(e.targetEpoch());out.writeInt(e.instance());out.writeUTF(e.selected());out.writeUTF(e.baseline());
				out.writeInt(e.legal().size());for(var s:e.legal()){out.writeUTF(s);}out.writeUTF(e.family().name());out.writeDouble(e.hp());out.writeBoolean(e.mp()!=null);if(e.mp()!=null){out.writeDouble(e.mp());}out.writeUTF(e.sourceSha());out.writeUTF(e.sourceJson());
			}
			out.writeInt(a.kills().size());for(var k:a.kills()){out.writeInt(k.target());out.writeLong(k.spawn());out.writeInt(k.instance());out.writeInt(k.creditedActor());}out.writeBoolean(a.nativeSource());
		}return bytes.toByteArray();
	}
	private static Arm unpack(byte[] bytes,DraftAuthority expected) throws IOException
	{
		try(var in=new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			String pair=in.readUTF();Side side=Side.valueOf(in.readUTF());Mode mode=Mode.valueOf(in.readUTF());long seed=in.readLong();int position=in.readInt();String build=in.readUTF(),config=in.readUTF(),scenario=in.readUTF();
			var facts=new TreeMap<String,String>();for(int n=count(in,64);n>0;n--){if(facts.put(in.readUTF(),in.readUTF())!=null){throw new IOException("Duplicate fact key");}}
			var actors=new ArrayList<Incarnation>();for(int n=count(in,9);n>0;n--){actors.add(incarnation(in));}var target=in.readBoolean()?incarnation(in):null;
			var pin=new Pin(mode,in.readLong(),in.readUTF(),in.readUTF(),in.readUTF(),in.readInt());
			if(expected!=null){long id=in.readLong();String sha=in.readUTF(),targetSha=in.readUTF(),optimizer=in.readUTF(),run=in.readUTF();final var decoded=new DraftAuthority(id,sha,targetSha,optimizer,run,expected.namespace().equals("EVAL007")?in.readUTF():"EVAL006R1");if(!expected.equals(decoded)){throw new IOException("Cold DRAFT authority changed");}pin=new Pin(mode,pin.id(),pin.status(),pin.sha(),pin.schema(),pin.algorithm(),decoded);}
			var outcome=Outcome.valueOf(in.readUTF());String reason=in.readUTF();double elapsed=in.readDouble(),preparation=in.readDouble();
			var metrics=new TreeMap<String,Double>();for(int n=count(in,64);n>0;n--){String key=in.readUTF();if(metrics.containsKey(key)){throw new IOException("Duplicate metric key");}metrics.put(key,in.readBoolean()?in.readDouble():null);}
			var effects=new ArrayList<Effect>();
			for(int n=count(in,2048);n>0;n--)
			{
				String key=in.readUTF();int actor=in.readInt();long epoch=in.readLong();int recipient=in.readInt();long targetEpoch=in.readLong();int instance=in.readInt();String selected=in.readUTF(),baseline=in.readUTF();
				var legal=new ArrayList<String>();for(int m=count(in,16);m>0;m--){legal.add(in.readUTF());}Family family=Family.valueOf(in.readUTF());double hp=in.readDouble();Double mp=in.readBoolean()?in.readDouble():null;effects.add(new Effect(key,actor,epoch,recipient,targetEpoch,instance,selected,baseline,legal,family,hp,mp,in.readUTF(),in.readUTF(),expected==null?null:pin));
			}
			var kills=new ArrayList<Kill>();for(int n=count(in,32);n>0;n--){kills.add(new Kill(in.readInt(),in.readLong(),in.readInt(),in.readInt()));}
			var result=new Arm(pair,side,mode,seed,position,build,config,scenario,facts,actors,target,pin,outcome,reason,elapsed,preparation,metrics,effects,kills,in.readBoolean());
			if(in.read()!=-1){throw new IOException("Trailing receipt data");}return result;
		}
		catch(IllegalArgumentException invalid){throw new IOException("Invalid typed receipt",invalid);}
	}
}
