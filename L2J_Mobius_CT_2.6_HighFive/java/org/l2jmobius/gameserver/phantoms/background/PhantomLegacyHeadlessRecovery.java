/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.LegacyHeadlessWitness;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.LegacyMaterializedWitness;

/** A single bounded startup pass over privately attested, exact legacy rows. */
public final class PhantomLegacyHeadlessRecovery
{
	public static final String RELATIVE_PATH = "data/phantoms/recovery/m1-005-legacy-headless-autosave.tsv";
	public static final String MATERIALIZED_RELATIVE_PATH = "data/phantoms/recovery/m1-005-legacy-materialized37.tsv";
	private static final String HEADER = "M1_LEGACY_HEADLESS_AUTOSAVE_V1";
	private static final String MATERIALIZED_HEADER = "M1_LEGACY_MATERIALIZED_37_V1";
	private static final int MAX_WITNESSES = 256;
	private static final int ATTESTED_WITNESSES = 216;
	private static final String ATTESTED_MANIFEST_SHA256 = "05c92c224b3a07e7fb5f4ccb5c65b92eca3b190712c8f9e010f88a4398e1aad1";
	private static final String ATTESTED_MATERIALIZED_SHA256 = "d150d55a0143d51a1a249b0245a29306db68496252cf500241ac325c7a8df797";
	private static final Logger LOGGER = Logger.getLogger(PhantomLegacyHeadlessRecovery.class.getName());

	private PhantomLegacyHeadlessRecovery()
	{
	}

	public static List<LegacyHeadlessWitness> load(Path path) throws IOException
	{
		if (!Files.exists(path))
		{
			return List.of();
		}
		return parse(readBounded(path));
	}

	private static byte[] readBounded(Path path) throws IOException
	{
		if (!Files.isRegularFile(path) || (Files.size(path) > 65536))
		{
			throw new IllegalArgumentException("Legacy recovery witness file is not a bounded regular file.");
		}
		final byte[] data = Files.readAllBytes(path);
		if (data.length > 65536)
		{
			throw new IllegalArgumentException("Legacy recovery witness file exceeded its size bound.");
		}
		return data;
	}

	private static List<LegacyHeadlessWitness> parse(byte[] data)
	{
		final List<String> lines = new String(data, StandardCharsets.UTF_8).lines().toList();
		if (lines.isEmpty() || !HEADER.equals(lines.get(0)) || (lines.size() > (MAX_WITNESSES + 1)))
		{
			throw new IllegalArgumentException("Legacy recovery witness header or count is invalid.");
		}
		final List<LegacyHeadlessWitness> witnesses = new ArrayList<>(lines.size() - 1);
		long previousProfileId = 0;
		for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++)
		{
			final String[] fields = lines.get(lineIndex).split("\t", -1);
			if (fields.length != 11)
			{
				throw new IllegalArgumentException("Legacy recovery witness field count is invalid at line " + (lineIndex + 1));
			}
			final LegacyHeadlessWitness witness = new LegacyHeadlessWitness(Long.parseLong(fields[0]), Integer.parseInt(fields[1]), Long.parseLong(fields[2]), fields[3], Double.parseDouble(fields[4]), Double.parseDouble(fields[5]), Double.parseDouble(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]), Integer.parseInt(fields[9]), Integer.parseInt(fields[10]));
			if (witness.profileId() <= previousProfileId)
			{
				throw new IllegalArgumentException("Legacy recovery witnesses must have unique ascending profile IDs.");
			}
			previousProfileId = witness.profileId();
			witnesses.add(witness);
		}
		return List.copyOf(witnesses);
	}

	public static void apply(Path path, PhantomBackgroundService background) throws IOException
	{
		if (!Files.exists(path))
		{
			return;
		}
		final byte[] data = readBounded(path);
		final List<LegacyHeadlessWitness> witnesses = parse(data);
		if ((witnesses.size() != ATTESTED_WITNESSES) || !sha256(data).equals(ATTESTED_MANIFEST_SHA256))
		{
			throw new IllegalArgumentException("M1 legacy witness file differs from the attested read-only PLAY audit.");
		}
		int recovered = 0;
		for (LegacyHeadlessWitness witness : witnesses)
		{
			final PhantomBackgroundService.OperationResult result = background.recoverAttestedLegacyHeadlessDrift(witness);
			if (result.successful())
			{
				recovered++;
			}
			else
			{
				LOGGER.warning("M1 legacy recovery kept profile " + witness.profileId() + " fail-closed: " + result.reason());
			}
		}
		LOGGER.info("M1 legacy recovery witnesses=" + witnesses.size() + " recovered=" + recovered + " failClosed=" + (witnesses.size() - recovered));
	}

	public static List<LegacyMaterializedWitness> loadMaterialized(Path path) throws IOException
	{
		if (!Files.exists(path))
		{
			return List.of();
		}
		return parseMaterialized(readBounded(path));
	}

	private static List<LegacyMaterializedWitness> parseMaterialized(byte[] data)
	{
		final List<String> lines = new String(data, StandardCharsets.UTF_8).lines().toList();
		if (lines.isEmpty() || !MATERIALIZED_HEADER.equals(lines.get(0)) || (lines.size() > 38))
		{
			throw new IllegalArgumentException("Materialized legacy witness header or count is invalid.");
		}
		final List<LegacyMaterializedWitness> witnesses = new ArrayList<>(lines.size() - 1);
		long previousProfileId = 0;
		for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++)
		{
			final String[] fields = lines.get(lineIndex).split("\t", -1);
			if (fields.length != 12)
			{
				throw new IllegalArgumentException("Materialized legacy witness field count is invalid at line " + (lineIndex + 1));
			}
			final LegacyMaterializedWitness witness = new LegacyMaterializedWitness(Long.parseLong(fields[0]), Integer.parseInt(fields[1]), Long.parseLong(fields[2]), fields[3], fields[4], Double.parseDouble(fields[5]), Double.parseDouble(fields[6]), Double.parseDouble(fields[7]), Integer.parseInt(fields[8]), Integer.parseInt(fields[9]), Integer.parseInt(fields[10]), Integer.parseInt(fields[11]));
			if (witness.profileId() <= previousProfileId)
			{
				throw new IllegalArgumentException("Materialized legacy witnesses must have unique ascending profile IDs.");
			}
			previousProfileId = witness.profileId();
			witnesses.add(witness);
		}
		return List.copyOf(witnesses);
	}

	/** Run once at startup with the exact 37-row private audit manifest. */
	public static void applyMaterialized(Path path, PhantomBackgroundService background) throws IOException
	{
		if (!Files.exists(path))
		{
			return;
		}
		final byte[] data = readBounded(path);
		final List<LegacyMaterializedWitness> witnesses = parseMaterialized(data);
		if ((witnesses.size() != 37) || !sha256(data).equals(ATTESTED_MATERIALIZED_SHA256))
		{
			throw new IllegalArgumentException("M1 materialized legacy witness file differs from the exact read-only PLAY audit.");
		}
		int recovered = 0;
		for (LegacyMaterializedWitness witness : witnesses)
		{
			final PhantomBackgroundService.OperationResult result = background.recoverAttestedLegacyMaterializedDrift(witness);
			if (result.successful())
			{
				recovered++;
			}
			else
			{
				LOGGER.warning("M1 materialized legacy recovery kept profile " + witness.profileId() + " fail-closed: " + result.reason());
			}
		}
		LOGGER.info("M1 materialized legacy recovery witnesses=" + witnesses.size() + " recovered=" + recovered + " failClosed=" + (witnesses.size() - recovered));
	}

	private static String sha256(byte[] data)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		}
		catch (NoSuchAlgorithmException failure)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", failure);
		}
	}
}
