/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Binds the paid native effect to a versioned experiment decision. */
public final class ActionExperimentReceiptV4
{
	private ActionExperimentReceiptV4() { }
	/** Captured by existing LearningPolicy.Observer.issued before nativeDispatch.run(). */
	public record Intent(ActionExplorationV4.Decision decision, HealEvidenceV3.SourceOwner owner, long capturedNanos)
	{
		public Intent
		{
			if (decision == null || owner == null || capturedNanos < decision.capturedNanos()) { throw new IllegalArgumentException("Invalid before-native intent"); }
		}
		public byte[] bytes()
		{
			try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes))
				{ out.writeInt(0x494e3231); byte[] d = decision.bytes(); out.writeInt(d.length); out.write(d); out.writeLong(owner.generation()); out.writeUTF(owner.operationKey()); out.writeLong(capturedNanos); } return bytes.toByteArray(); }
			catch (IOException impossible) { throw new IllegalStateException(impossible); }
		}
		public static Intent restore(byte[] bytes)
		{
			if (bytes == null || bytes.length > 501000) { throw new IllegalArgumentException("Intent bound"); }
			try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
			{
				if (in.readInt() != 0x494e3231) { throw new IllegalArgumentException("Intent schema"); }
				int n = in.readInt(); if (n < 1 || n > 500000) { throw new IllegalArgumentException("Intent decision bound"); }
				var result = new Intent(ActionExplorationV4.Decision.restore(in.readNBytes(n)), new HealEvidenceV3.SourceOwner(in.readLong(), in.readUTF()), in.readLong());
				if (in.available() != 0) { throw new IllegalArgumentException("Trailing intent bytes"); } return result;
			}
			catch (IOException e) { throw new IllegalArgumentException("Incomplete intent", e); }
		}
	}
	public static void validateSummary(Map<String, Object> summary, ActionExplorationV4.Decision decision)
	{
		if (!decision.mode().equals(summary.get("mode")) || !(summary.get("propensity") instanceof Number p) || p.doubleValue() != decision.propensity()
			|| !decision.sha256().equals(summary.get("decisionSha")) || !(summary.get("stock") instanceof Number stock) || stock.intValue() != decision.stock().skillId()
			|| !(summary.get("chosen") instanceof Number chosen) || chosen.intValue() != decision.selected().skillId())
		{ throw new IllegalArgumentException("Experiment summary contradicts immutable decision"); }
	}
	public record Effect(int schemaVersion, String family, String decisionSha, String nativeSha, String rowSha,
		String episodeSha, String eventKey, String feedbackFingerprint, double usefulHpFraction, double paidHp, double paidMp, long elapsedNanos)
	{
		public Effect
		{
			final boolean supportedFamily = schemaVersion == 1 && "DAMAGE".equals(family) || schemaVersion == 2 && "HEAL".equals(family);
			if (!supportedFamily || List.of(decisionSha, nativeSha, rowSha, episodeSha, eventKey, feedbackFingerprint).stream().anyMatch(s -> !s.matches("[a-f0-9]{64}"))
				|| !Double.isFinite(usefulHpFraction) || usefulHpFraction <= 0 || usefulHpFraction > 1 || !Double.isFinite(paidHp) || paidHp <= 0 || !Double.isFinite(paidMp) || paidMp <= 0 || elapsedNanos <= 0)
			{ throw new IllegalArgumentException("Invalid native effect v1"); }
		}
		public byte[] bytes()
		{
			try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes))
				{ out.writeInt(0x4e453231); out.writeInt(schemaVersion); for (String s : List.of(family, decisionSha, nativeSha, rowSha, episodeSha, eventKey, feedbackFingerprint)) { out.writeUTF(s); }
					out.writeDouble(usefulHpFraction); out.writeDouble(paidHp); out.writeDouble(paidMp); out.writeLong(elapsedNanos); } return bytes.toByteArray(); }
			catch (IOException impossible) { throw new IllegalStateException(impossible); }
		}
	}
	/** The caller must authenticate the original raw native writer before invoking this adapter. */
	public static Effect bind(Intent intent, String frozenPlanSha, byte[] rowBytes, TacticalEpisodeV3 episode, String nativeSha)
	{
		var decision = intent.decision();
		if (decision == null || !decision.plan().sha256().equals(frozenPlanSha) || !decision.plan().episode().equals(episode.id()))
		{ throw new IllegalArgumentException("Unfrozen experiment decision"); }
		var row = TacticalStoreV3.decode(rowBytes); var receipt = row.receipt(); var a = receipt.attempt();
		final var family = a.selected().family();
		if (!row.split().equals(decision.plan().split()) || !Arrays.equals(a.observation().bytes(), decision.observation().bytes()) || !a.offered().equals(decision.offered())
			|| !a.baseline().equals(decision.stock()) || !a.selected().equals(decision.selected()) || !receipt.owner().equals(intent.owner())
			|| decision.capturedNanos() < episode.startedNanos() || intent.capturedNanos() > episode.settledNanos()
			|| family == LegalActionV3.Family.HEAL && (receipt.elapsedNanos() > episode.settledNanos() - intent.capturedNanos()
				|| episode.actors().stream().noneMatch(actor -> actor.objectId() == a.selected().targetObjectId() && actor.epoch() == a.selected().targetEpoch())))
		{ throw new IllegalArgumentException("Decision/native selection, owner, split or time mismatch"); }
		String rowSha = TacticalObservationV3.hash(rowBytes);
		var feedback = ActionFeedbackV4.nativeAction(rowBytes, rowSha, episode, intent.owner(), nativeSha);
		double max = a.observation().entity(a.selected().targetObjectId()).fact(TacticalObservationV3.Field.MAX_HP).value();
		return new Effect(family == LegalActionV3.Family.HEAL ? 2 : 1, family.name(), decision.sha256(), nativeSha, rowSha, episode.sha256(), row.eventKey(),
			feedback.fingerprint(), receipt.actualHp() / max, receipt.actualHp(), receipt.actualMp(), receipt.elapsedNanos());
	}
	/** Bounded post-drain publication, never called by the native selection hot path. */
	public static void publish(Path target, byte[] bytes) throws IOException
	{
		if (!target.isAbsolute() || !target.getParent().equals(target.getParent().toRealPath()) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Fresh physical metadata path required"); }
		Path part = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".part");
		try
		{
			try (var channel = FileChannel.open(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) { channel.write(buffer); } channel.force(true); }
			Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
		}
		finally { Files.deleteIfExists(part); }
	}
}
