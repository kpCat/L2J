package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteScenes.Scene;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;

/** In-memory evidence for owner demonstrations, scoped to one server incarnation. */
final class HumanRouteFarmRefusals
{
	static final long MAX_AGE_MILLIS = 10 * 60_000;
	private static final String ALGORITHM = "HmacSHA256";
	private static final int MAX_REFUSALS = 128;

	record Evidence(UUID incarnation, String fingerprint, long geoGeneration, long zoneGeneration, long profileId,
		int objectId, long epoch, long requestId, Point origin, Point target, long observedAt, LimitKind reason) { }
	record Signed(Evidence evidence, String signature) { }
	private record Edge(long profileId, int objectId, Point origin, Point target) { }

	private final UUID _incarnation;
	private final SecretKeySpec _key;
	private final Map<Edge, Signed> _recent = new LinkedHashMap<>();

	HumanRouteFarmRefusals(UUID incarnation)
	{
		_incarnation = incarnation;
		final byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		_key = new SecretKeySpec(key, ALGORITHM);
	}

	Signed sign(Evidence evidence)
	{
		return new Signed(evidence, HexFormat.of().formatHex(mac(evidence)));
	}

	synchronized void observe(Evidence evidence)
	{
		final var edge = new Edge(evidence.profileId(), evidence.objectId(), evidence.origin(), evidence.target());
		final var previous = _recent.get(edge);
		if (previous != null && previous.evidence().observedAt() > evidence.observedAt()) { return; }
		_recent.remove(edge);
		_recent.put(edge, sign(evidence));
		if (_recent.size() > MAX_REFUSALS) { _recent.remove(_recent.keySet().iterator().next()); }
	}

	synchronized Signed find(Scene scene, long now)
	{
		final var source = scene.farmSource();
		if (source == null) { return null; }
		final var edge = new Edge(source.profileId(), source.objectId(), scene.start(), scene.target());
		final var signed = _recent.get(edge);
		if (signed != null && now - signed.evidence().observedAt() >= MAX_AGE_MILLIS)
		{
			_recent.remove(edge);
			return null;
		}
		return signed;
	}

	boolean matches(Signed signed, Scene scene, String fingerprint, long geoGeneration, long zoneGeneration, long epoch, long now)
	{
		if (signed == null || scene.farmSource() == null || epoch <= 0 || zoneGeneration < 0) { return false; }
		final var evidence = signed.evidence();
		final var source = scene.farmSource();
		return _incarnation.equals(evidence.incarnation()) && fingerprint.equals(evidence.fingerprint())
			&& evidence.geoGeneration() == geoGeneration && evidence.zoneGeneration() == zoneGeneration
			&& evidence.profileId() == source.profileId() && evidence.objectId() == source.objectId()
			&& evidence.epoch() == epoch && evidence.requestId() > 0 && evidence.observedAt() > 0
			&& now >= evidence.observedAt() && now - evidence.observedAt() < MAX_AGE_MILLIS
			&& evidence.origin().instance() == 0 && evidence.target().instance() == 0
			&& scene.start().equals(evidence.origin()) && scene.target().equals(evidence.target())
			&& (evidence.reason() == LimitKind.SEARCH_LIMIT || evidence.reason() == LimitKind.NO_PATH_WITHIN_SEARCH)
			&& signed.signature().matches("[0-9a-f]{64}") && MessageDigest.isEqual(mac(evidence), HexFormat.of().parseHex(signed.signature()));
	}

	private byte[] mac(Evidence evidence)
	{
		try
		{
			final Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(_key);
			return mac.doFinal(evidence.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException failure)
		{
			throw new IllegalStateException("Cannot authenticate farm refusal.", failure);
		}
	}
}
