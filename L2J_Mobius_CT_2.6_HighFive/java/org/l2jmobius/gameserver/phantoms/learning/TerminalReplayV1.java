/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded offline outcome projection. Native action reward bytes and legacy replay are unchanged. */
public final class TerminalReplayV1
{
	private static final int MAGIC=0x54523644,MAX_BYTES=64*1024*1024,MAX_RECEIPTS=1024;
	public static final String REVISION="006/terminal-mc-gamma1/episode-split/native-credit-once/v1";
	private TerminalReplayV1(){ }
	public record Frozen(String runId,String datasetSha,List<TerminalRewardReceiptV1> receipts)
	{
		public Frozen
		{
			if(runId==null||!runId.matches("[a-f0-9]{32}")){throw new IllegalArgumentException("Terminal run identity required");}
			receipts=validate(receipts);
			if(datasetSha==null||!TacticalObservationV3.hash(pack(receipts)).equals(datasetSha)){throw new IllegalArgumentException("Terminal frozen dataset checksum changed");}
		}
		public List<TacticalModelV3.Example> examples(String split)
		{
			if(!Set.of("TRAIN","TUNING","AUDIT").contains(split)){throw new IllegalArgumentException("EVAL cannot become an outcome fit split");}
			final var result=new ArrayList<TacticalModelV3.Example>();
			for(var receipt:receipts)
			{
				if(!receipt.split().equals(split)){continue;}
				for(var action:receipt.actions())
				{
					final var attempt=action.row().receipt().attempt();
					result.add(new TacticalModelV3.Example(attempt.observation().state(),attempt.selected().features(attempt.observation()),receipt.reward()));
				}
			}
			return List.copyOf(result);
		}
		public List<TacticalStoreV3.Row> sourceRows(){return receipts.stream().flatMap(r->r.actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList();}
		public String configSha(){return receipts.getFirst().config().sha256();}
		public String datasetId(){return receipts.getFirst().dataset();}
		public byte[] bytes(){return pack(receipts);}
		public boolean sufficient(){return TacticalStoreV3.sufficient(sourceRows());}
		public double constantLoss()
		{
			final double mean=examples("TRAIN").stream().mapToDouble(TacticalModelV3.Example::reward).average().orElseThrow();
			return examples("AUDIT").stream().mapToDouble(e->Math.pow(e.reward()-mean,2)).average().orElseThrow();
		}
	}
	private static List<TerminalRewardReceiptV1> validate(List<TerminalRewardReceiptV1> input)
	{
		if(input==null||input.isEmpty()||input.size()>MAX_RECEIPTS||input.stream().anyMatch(Objects::isNull)){throw new IllegalArgumentException("Terminal replay receipt capacity");}
		final var receipts=input.stream().sorted(Comparator.comparing(TerminalRewardReceiptV1::naturalKey)).toList();
		final var natural=new HashSet<String>();final var goals=new HashSet<String>();final var actions=new HashSet<String>();
		final String config=receipts.getFirst().config().sha256(),installation=receipts.getFirst().boundary().installation(),dataset=receipts.getFirst().dataset();
		final var rows=new ArrayList<TacticalStoreV3.Row>();
		for(var receipt:receipts)
		{
			if(!natural.add(receipt.naturalKey())||receipt.goalKey()!=null&&!goals.add(receipt.goalKey())||!config.equals(receipt.config().sha256())
				||!installation.equals(receipt.boundary().installation())||!dataset.equals(receipt.dataset())){throw new IllegalArgumentException("Duplicate native terminal credit or foreign dataset/config/installation");}
			for(var action:receipt.actions())
			{
				if(!actions.add(action.row().actionKey())){throw new IllegalArgumentException("Native action credited by two episodes");}
				rows.add(action.row());if(rows.size()>TacticalModelV3.MAX_EXAMPLES){throw new IllegalArgumentException("Terminal replay example capacity");}
			}
		}
		TacticalStoreV3.validateDataset(rows);
		return List.copyOf(receipts);
	}
	public static Frozen freeze(String runId,List<TerminalRewardReceiptV1> receipts)
	{final var valid=validate(receipts);return new Frozen(runId,TacticalObservationV3.hash(pack(valid)),valid);}
	public static boolean isSnapshot(byte[] bytes){return bytes!=null&&bytes.length>=8&&ByteBuffer.wrap(bytes).getInt()==MAGIC;}
	private static byte[] pack(List<TerminalRewardReceiptV1> receipts)
	{
		try
		{
			final var buffer=new ByteArrayOutputStream();final var out=new DataOutputStream(buffer);out.writeInt(MAGIC);out.writeInt(1);out.writeInt(receipts.size());
			for(var receipt:receipts){final byte[] bytes=receipt.bytes();out.writeUTF(receipt.sha256());out.writeInt(bytes.length);out.write(bytes);if(buffer.size()>MAX_BYTES){throw new IllegalArgumentException("Terminal frozen byte capacity");}}
			out.flush();return buffer.toByteArray();
		}
		catch(IOException failure){throw new IllegalArgumentException("Terminal dataset encoding failed",failure);}
	}
	public static Frozen restore(String runId,byte[] bytes,String expectedSha)
	{
		if(bytes==null||bytes.length>MAX_BYTES||!TacticalObservationV3.hash(bytes).equals(expectedSha)){throw new IllegalArgumentException("Terminal frozen checksum changed");}
		try(var in=new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if(in.readInt()!=MAGIC||in.readInt()!=1){throw new IllegalArgumentException("Terminal replay schema");}
			final int count=in.readInt();if(count<1||count>MAX_RECEIPTS){throw new IllegalArgumentException("Terminal replay receipt count");}
			final var receipts=new ArrayList<TerminalRewardReceiptV1>();
			for(int index=0;index<count;index++)
			{
				final String sha=in.readUTF();final int length=in.readInt();if(length<1||length>TerminalRewardReceiptV1.MAX_BYTES){throw new IllegalArgumentException("Terminal replay receipt length");}
				final byte[] payload=in.readNBytes(length);if(payload.length!=length){throw new EOFException();}receipts.add(TerminalRewardReceiptV1.restore(payload,sha));
			}
			if(in.available()!=0){throw new IllegalArgumentException("Trailing terminal replay bytes");}
			final var frozen=new Frozen(runId,expectedSha,receipts);if(!Arrays.equals(frozen.bytes(),bytes)){throw new IllegalArgumentException("Noncanonical terminal replay");}return frozen;
		}
		catch(IOException failure){throw new IllegalArgumentException("Invalid terminal replay",failure);}
	}
}
