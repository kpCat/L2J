/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable 128-coefficient contextual action scorer; no dependency on the V3 optimizer or SQL. */
public final class ActionValueV4
{
	public static final int COEFFICIENTS = 128, MAX_EVENTS = 4096;
	public static final String SCHEMA = TacticalObservationV3.hash(("ActionValueV4/1/action64+action64*stateFirst64/paired-known-mask/Huber.25/SGD.05-norm/clipWeights4/"
		+ TacticalObservationV3.SCHEMA + "/" + LegalActionV3.FINGERPRINT).getBytes(StandardCharsets.UTF_8));
	private final double[] _weights;
	private final SortedMap<String, String> _events, _episodes;
	private final String _parent, _training;
	private final long _updates;
	private final String _sha;
	ActionValueV4(double[] weights, Map<String, String> events, Map<String, String> episodes, String parent, String training, long updates)
	{
		if (weights.length != COEFFICIENTS || events.size() > MAX_EVENTS || episodes.size() > MAX_EVENTS || updates < 0 || updates > events.size()
			|| !parent.matches("[a-f0-9]{64}") || !training.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("V4 snapshot bounds"); }
		for (double weight : weights) { if (!Double.isFinite(weight) || Math.abs(weight) > 4) { throw new IllegalArgumentException("V4 nonfinite weights"); } }
		_weights = weights.clone(); _events = Collections.unmodifiableSortedMap(new TreeMap<>(events)); _episodes = Collections.unmodifiableSortedMap(new TreeMap<>(episodes));
		_parent = parent; _training = training; _updates = updates;
		_sha = TacticalObservationV3.hash(bytes());
	}
	public static ActionValueV4 empty() { return new ActionValueV4(new double[COEFFICIENTS], Map.of(), Map.of(), "0".repeat(64), "0".repeat(64), 0); }
	public long updates() { return _updates; }
	double[] weights() { return _weights.clone(); }
	Map<String, String> events() { return _events; }
	Map<String, String> episodes() { return _episodes; }
	public static double[] features(TacticalObservationV3 observation, LegalActionV3 action)
	{
		if (observation == null || action == null || !action.trainable() || action.criticalSafety() || action.generation() != observation.decisionId())
		{ throw new IllegalArgumentException("V4 noncritical lawful action required"); }
		final float[] state = observation.state(), a = action.features(observation);
		final double[] x = new double[COEFFICIENTS];
		for (int i = 0; i < 64; i++)
		{
			if (!Float.isFinite(a[i]) || !Float.isFinite(state[i]) || Math.abs(a[i]) > 1 || Math.abs(state[i]) > 1) { throw new IllegalArgumentException("V4 feature bounds"); }
			x[i] = a[i]; x[64 + i] = a[i] * state[i];
		}
		return x;
	}
	public double score(TacticalObservationV3 observation, LegalActionV3 action) { return score(features(observation, action)); }
	double score(double[] x)
	{
		double value = 0; for (int i = 0; i < COEFFICIENTS; i++) { value += _weights[i] * x[i]; }
		if (!Double.isFinite(value)) { throw new IllegalArgumentException("V4 nonfinite score"); }
		return Math.clamp(value, -1, 1);
	}
	public byte[] bytes()
	{
		try
		{
			final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
			out.writeInt(0x41563431); out.writeUTF(SCHEMA); out.writeUTF(_parent); out.writeUTF(_training); out.writeLong(_updates);
			for (double weight : _weights) { out.writeDouble(weight); }
			out.writeInt(_events.size()); for (var event : _events.entrySet()) { out.writeUTF(event.getKey()); out.writeUTF(event.getValue()); }
			out.writeInt(_episodes.size()); for (var episode : _episodes.entrySet()) { out.writeUTF(episode.getKey()); out.writeUTF(episode.getValue()); }
			out.flush(); return buffer.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	public String sha256() { return _sha; }
	public static ActionValueV4 restore(byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length > 1_000_000 || expectedSha == null || !expectedSha.matches("[a-f0-9]{64}") || !TacticalObservationV3.hash(bytes).equals(expectedSha))
		{ throw new IllegalArgumentException("V4 checkpoint SHA/size"); }
		try
		{
			final var in = new DataInputStream(new ByteArrayInputStream(bytes));
			if (in.readInt() != 0x41563431 || !in.readUTF().equals(SCHEMA)) { throw new IllegalArgumentException("V4 checkpoint schema"); }
			final String parent = in.readUTF(), training = in.readUTF(); final long updates = in.readLong();
			final double[] weights = new double[COEFFICIENTS]; for (int i = 0; i < weights.length; i++) { weights[i] = in.readDouble(); }
			final var events = readMap(in, false); final var episodes = readMap(in, true);
			final var model = new ActionValueV4(weights, events, episodes, parent, training, updates);
			if (in.available() != 0 || !Arrays.equals(bytes, model.bytes())) { throw new IllegalArgumentException("V4 noncanonical checkpoint"); }
			return model;
		}
		catch (IOException failure) { throw new IllegalArgumentException("V4 damaged checkpoint", failure); }
	}
	private static Map<String, String> readMap(DataInputStream in, boolean episodes) throws IOException
	{
		final int count = in.readInt(); if (count < 0 || count > MAX_EVENTS) { throw new IllegalArgumentException("V4 ledger bounds"); }
		final var result = new TreeMap<String, String>();
		for (int i = 0; i < count; i++)
		{
			final String key = in.readUTF(), value = in.readUTF();
			if (!key.matches("[a-f0-9]{64}") || !(episodes ? Set.of("TRAIN", "TUNING", "AUDIT").contains(value) : value.matches("[a-f0-9]{64}")) || result.putIfAbsent(key, value) != null)
			{ throw new IllegalArgumentException("V4 ledger identity"); }
		}
		return result;
	}
}
