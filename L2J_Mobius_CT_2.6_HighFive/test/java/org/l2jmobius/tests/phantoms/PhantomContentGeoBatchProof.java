package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Replays only production-buffer hermetic LIVE-002 checks; never reruns oversized A*. */
public final class PhantomContentGeoBatchProof
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0)
		{
			throw new IllegalArgumentException("No arguments expected.");
		}
		final Path evidence = Path.of("data/phantoms/evidence");
		final List<Path> inputs;
		try (Stream<Path> stream = Files.list(evidence))
		{
			inputs = stream.filter(path -> {
				final String name = path.getFileName().toString();
				return name.endsWith("-input.tsv") && (name.startsWith("live002-content-") || name.equals("live002-imperial-oversized-hops-input.tsv"));
			}).sorted().toList();
		}
		if (inputs.size() != 31)
		{
			throw new IllegalStateException("LIVE-002 directed proof input count drift: " + inputs.size());
		}
		for (Path input : inputs)
		{
			final Path output = input.resolveSibling(input.getFileName().toString().replace("-input.tsv", "-proof.tsv"));
			PhantomTravelGeoProbe.main(new String[] {input.toString(), output.toString()});
		}
		System.out.println("CONTENT GEO BATCH: PASS proofs=" + inputs.size());
	}
}
