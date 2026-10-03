package org.l2jmobius.gameserver.localplay;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** LocalPlay diagnostic only: bounded synchronous journal, with no gameplay authority. */
public final class LocalPlayPhantomStoreJournal
{
	private static final Logger LOGGER = Logger.getLogger(LocalPlayPhantomStoreJournal.class.getName());
	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final long MAX_BYTES = 1024 * 1024;
	private static volatile Path _root;
	private static volatile java.util.Map<Long, Long> _selected = java.util.Map.of();
	private static final AtomicLong SNAPSHOTS = new AtomicLong();
	private static final AtomicLong ENCODES = new AtomicLong();
	private static final AtomicLong HASHES = new AtomicLong();
	private static final AtomicLong OPENS = new AtomicLong();
	private static final AtomicLong FORCES = new AtomicLong();
	private static boolean _reportedFailure;

	private LocalPlayPhantomStoreJournal() {}

	static synchronized void configure(Path runtime) throws Exception
	{
		_root = null;
		_selected = java.util.Map.of();
		final Path root = runtime.resolve("playtest-lifecycle");
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { return; }
		if (!LocalPlayPilotService.safeDirectory(root) || !LocalPlayPilotService.privateAcl(root)) { throw new IllegalArgumentException("OWNED_STORE_JOURNAL_PRIVATE_GUARD"); }
		for (String name : java.util.List.of("stores.log", "stores.1.log", "stores.2.log"))
		{
			final Path file = root.resolve(name);
			if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !LocalPlayPilotService.privateAcl(file) || (Files.size(file) > MAX_BYTES + 8192))) { throw new IllegalArgumentException("OWNED_STORE_JOURNAL_FILE_GUARD"); }
		}
		_root = root;
	}

	/** Explicit diagnostic scope; never enables another materialization epoch. */
	public static synchronized void select(java.util.Map<Long, Long> selected)
	{
		if ((selected.size() > 8) || selected.entrySet().stream().anyMatch(entry -> (entry.getKey() <= 0) || (entry.getValue() <= 0))) { throw new IllegalArgumentException("OWNED_STORE_JOURNAL_SCOPE"); }
		_selected = java.util.Map.copyOf(selected);
	}

	public static boolean enabledFor(long profileId, long epoch)
	{
		return (_root != null) && java.util.Objects.equals(_selected.get(profileId), epoch);
	}

	public record OperationCounts(long snapshots, long encodes, long hashes, long opens, long forces) { }
	public static OperationCounts operationCounts() { return new OperationCounts(SNAPSHOTS.get(), ENCODES.get(), HASHES.get(), OPENS.get(), FORCES.get()); }

	public static synchronized long begin(long profileId, Player player, String kind, PhantomOwnedStoreIntent intent)
	{
		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos())) { return 0; }
		final long sequence = SEQUENCE.incrementAndGet();
		final String current = snapshot(player);
		write(profileId, player, kind, intent, sequence, "PREPARE", "DURABLE_PREPARE", current, current, null);
		return sequence;
	}

	public static synchronized void end(long profileId, Player player, String kind, PhantomOwnedStoreIntent intent, long sequence, String before, String after, String status, PhantomBackgroundState completed)
	{
		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos()) || (sequence == 0)) { return; }
		write(profileId, player, kind, intent, sequence, "COMPLETE", status, before, after, completed);
	}

	public static synchronized void rejected(long profileId, Player player, PhantomOwnedStoreIntent intent, String reason)
	{
		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos())) { return; }
		final String current = snapshot(player);
		write(profileId, player, "OTHER_OWNED_STORE", intent, SEQUENCE.incrementAndGet(), "REJECTED", reason.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '), current, current, null);
	}

	private static String projection(PhantomBackgroundState state)
	{
		if (state == null) { return "UNKNOWN"; }
		ENCODES.incrementAndGet(); HASHES.incrementAndGet();
		return state.state() + "/" + state.progress() + "/" + state.vitals() + "/" + state.position() + "/inventory=" + state.inventory().canonicalHash() + "/payload=" + PhantomBackgroundTransaction.payloadDigest(new PhantomBackgroundStateCodec().encode(state));
	}

	public static String snapshot(Player player)
	{
		SNAPSHOTS.incrementAndGet();
		return player.getCurrentHp() + "/" + player.getMaxHp() + "," + player.getCurrentMp() + "/" + player.getMaxMp() + "," + player.getCurrentCp() + "/" + player.getMaxCp() + "," + player.getX() + "," + player.getY() + "," + player.getZ() + "," + player.getHeading();
	}

	private static void write(long profileId, Player player, String kind, PhantomOwnedStoreIntent intent, long sequence, String event, String status, String before, String after, PhantomBackgroundState completed)
	{
		try
		{
			final Path file = _root.resolve("stores.log");
			if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(file) || !LocalPlayPilotService.privateAcl(file))) { throw new IllegalStateException("OWNED_STORE_JOURNAL_PATH_CHANGED"); }
			if (Files.exists(file) && (Files.size(file) >= MAX_BYTES))
			{
				final Path older = _root.resolve("stores.1.log"); final Path oldest = _root.resolve("stores.2.log");
				for (Path previous : java.util.List.of(older, oldest)) { if (Files.exists(previous, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(previous) || !LocalPlayPilotService.privateAcl(previous))) { throw new IllegalStateException("OWNED_STORE_JOURNAL_ROTATION_CHANGED"); } }
				if (Files.exists(older)) { Files.move(older, oldest, StandardCopyOption.REPLACE_EXISTING); }
				Files.move(file, older, StandardCopyOption.REPLACE_EXISTING);
			}
			final String receipt = intent == null ? "UNKNOWN" : "epoch=" + intent.materializedAtNanos() + ",background=" + intent.previousState() + "/" + (intent.preparedRowVersion() - 1) + "/" + intent.previousPayloadHash() + ",before=" + projection(intent.before()) + ",after=" + projection(intent.after()) + ",skills=" + intent.skillsHash();
			final String line = Instant.now() + "\tpid=" + ProcessHandle.current().pid() + "\tseq=" + sequence + "\tprofile=" + profileId + "\tobject=" + player.getObjectId() + "\tkind=" + kind + "\tevent=" + event + "\tbefore=" + before + "\tafter=" + after + "\treceipt=" + receipt + "\tcompleted=" + projection(completed) + "\tstatus=" + status + "\tworld=" + (World.getInstance().getPlayer(player.getObjectId()) == player) + "\tidentity=" + PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId()) + "\tautosave=" + PlayerAutoSaveTaskManager.getInstance().contains(player) + "\n";
			final byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
			if (bytes.length > 8192) { throw new IllegalStateException("OWNED_STORE_JOURNAL_LINE_TOO_LARGE"); }
			OPENS.incrementAndGet();
			try (var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS))
			{
				final var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) { channel.write(buffer); } FORCES.incrementAndGet(); channel.force(true);
			}
		}
		catch (Exception failure)
		{
			_root = null;
			if (!_reportedFailure) { _reportedFailure = true; LOGGER.warning("OWNED_STORE_JOURNAL_DISABLED:" + failure.getClass().getSimpleName()); }
		}
	}
}
