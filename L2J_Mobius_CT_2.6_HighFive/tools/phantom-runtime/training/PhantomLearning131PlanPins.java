/* Copyright (c) 2013 L2jMobius */

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;

/** Offline plan signatures via the production codec. The tool never initializes a lineage or a server. */
final class PhantomLearning131PlanPins
{
	public static void main(String[] args) throws Exception
	{
		require(args.length == 4 || args.length == 5 && args[4].equals("TUNING"), "ARGUMENTS");
		final boolean tuning = args.length == 5;
		final Path ledger = physical(Path.of(args[0]), false);
		final String head = args[1], learner = args[2], opponent = args[3];
		require(ledger.endsWith(Path.of(".phantom-local/logs/TASK131/fit-ledger")), "OWN_PATHS");
		require(List.of(head, learner, opponent).stream().allMatch(value -> value.matches("[a-f0-9]{64}")), "PINS");
		final var state = ActionContinuationV4.load(ledger);
		require(state.head().equals(head) && state.model().sha256().equals(learner), "COLD_LINEAGE");
		final var learnerModel = model(ledger, learner);
		require(Arrays.equals(state.model().bytes(), learnerModel.bytes()), "LEARNER_BYTES");
		if (!tuning) { model(ledger, opponent); }
		final byte[] request = System.in.readNBytes(4097);
		require(request.length <= 4096, "REQUEST_BYTES");
		final var rows = new String(request, StandardCharsets.US_ASCII).lines().toList();
		require(rows.size() >= 2 && rows.size() <= (tuning ? 4 : 5) && rows.getFirst().equals("episodeId\tsourceSha\tseed\tcap\tepsilon\tskills"), "PLAN_REQUEST");
		final var seen = new HashSet<String>();
		final var pins = new StringBuilder();
		for (String row : rows.subList(1, rows.size()))
		{
			final var fields = row.split("\t", -1);
			require(fields.length == 6 && seen.add(fields[0]) && !state.episodes().containsKey(fields[0]), "FRESH_EPISODE");
			final var plan = new ActionExplorationV4.Plan(2, tuning ? "MODEL_V2" : "EPSILON_V2", Long.parseLong(fields[2]), Integer.parseInt(fields[3]),
				Double.parseDouble(fields[4]), tuning ? "TUNING" : "TRAIN", fields[0], fields[1], learner, List.of(fields[5].split(",", -1)));
			if (tuning) { ActionExplorationV4.frozen(plan, ledger, head); }
			pins.append(plan.episode()).append('\t').append(plan.sha256()).append('\n');
		}
		require(ActionContinuationV4.load(ledger).head().equals(head), "HEAD_CHANGED");
		System.out.print(pins);
	}

	private static ActionValueV4 model(Path ledger, String sha) throws Exception
	{
		final var file = physical(ledger.resolve("candidate-" + sha + ".bin"), true);
		require(Files.size(file) <= 1_048_576, "MODEL_BYTES");
		return ActionValueV4.restore(Files.readAllBytes(file), sha);
	}

	private static Path physical(Path path, boolean file) throws Exception
	{
		require(path.isAbsolute() && path.normalize().equals(path), "CANONICAL_PATH");
		for (Path part = path; part != null; part = part.getParent())
		{
			require(!Files.isSymbolicLink(part) && part.toRealPath().equals(part), "PHYSICAL_PATH");
		}
		require(file ? Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) : Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), "PATH_KIND");
		return path;
	}

	private static void require(boolean allowed, String reason)
	{
		if (!allowed) { throw new IllegalArgumentException("TASK131_SOLO_PLAN_" + reason); }
	}
}
