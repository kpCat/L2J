/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;

/** An individual authenticated paid action. Terminal EXP/SP and success are deliberately not labels. */
public final class ActionFeedbackV4
{
	private final TacticalStoreV3.Row _row;
	private final TacticalEpisodeV3 _episode;
	private final String _sourceSha, _fingerprint;
	private ActionFeedbackV4(TacticalStoreV3.Row row, TacticalEpisodeV3 episode, String sourceSha, String rowSha)
	{
		_row = row; _episode = episode; _sourceSha = sourceSha;
		_fingerprint = TacticalObservationV3.hash((ActionValueV4.SCHEMA + "/NATIVE/" + rowSha + "/" + episode.sha256() + "/" + sourceSha).getBytes(StandardCharsets.UTF_8));
	}
	/** Caller authenticates source bytes against its frozen native ledger before supplying this envelope. */
	public static ActionFeedbackV4 nativeAction(byte[] bytes, String expectedSha, TacticalEpisodeV3 episode, HealEvidenceV3.SourceOwner owner, String sourceSha)
	{
		if (expectedSha == null || !expectedSha.matches("[a-f0-9]{64}") || bytes == null || !TacticalObservationV3.hash(bytes).equals(expectedSha)
			|| sourceSha == null || !sourceSha.matches("[a-f0-9]{64}") || sourceSha.equals("0".repeat(64)) || episode == null || owner == null)
		{ throw new IllegalArgumentException("V4 requires authenticated native bytes"); }
		final var row = TacticalStoreV3.decode(bytes);
		final var receipt = row.receipt(); final var attempt = receipt.attempt(); final var observation = attempt.observation();
		if (!receipt.owner().equals(owner) || owner.operationKey().contains("CONTRACT") || owner.operationKey().contains("FIXTURE")
			|| !episode.eventKeys().contains(row.eventKey()) || observation.capturedNanos() < episode.startedNanos() || observation.capturedNanos() > episode.settledNanos()
			|| episode.actors().stream().noneMatch(a -> a.profile() == observation.profileId() && a.objectId() == observation.actorObjectId() && a.epoch() == observation.actorEpoch())
			|| !row.contextKey().contains(episode.id()))
		{ throw new IllegalArgumentException("V4 native actor/owner/episode mismatch"); }
		ActionValueV4.features(observation, attempt.selected());
		return new ActionFeedbackV4(row, episode, sourceSha, expectedSha);
	}
	public TacticalStoreV3.Row row() { return _row; }
	public TacticalEpisodeV3 episode() { return _episode; }
	public String episodeId() { return _episode.id(); }
	public String sourceSha() { return _sourceSha; }
	public String fingerprint() { return _fingerprint; }
	public double label() { return _row.receipt().reward(); }
}
