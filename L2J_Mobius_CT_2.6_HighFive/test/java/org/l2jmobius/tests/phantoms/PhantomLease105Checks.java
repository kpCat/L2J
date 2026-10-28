/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerState;

/** DB-free boundary policy used by the native harness; fixture acceptance is not native proof. */
public final class PhantomLease105Checks
{
    record Witness(OwnerSnapshot owner, boolean ordinary, boolean observer, Object worldPlayer, Object servicePlayer,
        long serviceToken, boolean nativeOwner, boolean quiescent, boolean persisted, String account, String roleAccount)
    {
    }
    record Sample(int global, List<Witness> owners)
    {
        Sample { owners = List.copyOf(owners); }
    }
    @FunctionalInterface interface Step { void run() throws Exception; }

    static int verify(Sample first, Sample second)
    {
        validate(first);
        validate(second);
        require(first.global() == second.global(), "OWN105 non-quiescent global census");
        final var after = new java.util.HashMap<Integer, Witness>();
        for (var witness : second.owners()) { after.put(witness.owner().objectId(), witness); }
        for (var before : first.owners())
        {
            final var current = after.get(before.owner().objectId());
            require(current != null && before.owner().equals(current.owner()) && before.serviceToken() == current.serviceToken()
                && before.worldPlayer() == current.worldPlayer() && before.servicePlayer() == current.servicePlayer()
                && before.account().equals(current.account()) && before.roleAccount().equals(current.roleAccount()), "OWN105 ownership changed between samples");
        }
        return first.global();
    }

    private static void validate(Sample sample)
    {
        require(sample.global() == sample.owners().size(), "UNATTRIBUTED_LEASE_BLOCKED");
        final var objects = new HashSet<Integer>();
        for (var witness : sample.owners())
        {
            final var owner = witness.owner();
            require(owner != null && owner.objectId() > 0 && objects.add(owner.objectId()) && owner.ownerKind() == OwnerKind.PHANTOM
                && owner.state() == OwnerState.RESERVED && owner.token() > 0 && owner.token() == witness.serviceToken()
                && !witness.ordinary() && !witness.observer() && witness.worldPlayer() != null && witness.worldPlayer() == witness.servicePlayer()
                && witness.nativeOwner() && witness.quiescent() && witness.persisted() && witness.account() != null
                && witness.account().equals(witness.roleAccount()), "OWN105 invalid residual owner");
        }
    }

    static void finish(boolean drained, Step proof, Step checkpoint, Step pools, Step database, Step blocked) throws Exception
    {
        boolean safe = false;
        try
        {
            require(drained, "OWN105 subsystem not drained");
            proof.run();
            safe = true;
            checkpoint.run();
        }
        catch (Exception | AssertionError failure)
        {
            try { blocked.run(); } catch (Exception receiptFailure) { failure.addSuppressed(receiptFailure); }
            throw failure;
        }
        finally
        {
            if (safe) { try { pools.run(); } finally { database.run(); } }
        }
    }

    public static void main(String[] args) throws Exception
    {
        final var registry = PhantomIdentityLeaseRegistry.getInstance();
        require(registry.getActiveLeaseCount() == 0, "Focused JVM has foreign leases");
        final Object player = new Object();
        final List<PhantomIdentityLeaseRegistry.Lease> ordinary = new ArrayList<>();
        for (int index = 0; index < 16; index++) { ordinary.add(registry.tryAcquire(1050000 + index, OwnerKind.PHANTOM)); }
        try (var human = registry.tryAcquire(1050100, OwnerKind.LOCALPLAY_TEST_HUMAN);
            var merchant = registry.tryAcquire(1050200, OwnerKind.PHANTOM))
        {
            ordinary.forEach(PhantomIdentityLeaseRegistry.Lease::close);
            human.close();
            final var valid = new Witness(registry.getOwnerSnapshot(merchant.objectId()), false, false, player, player,
                merchant.token(), true, true, true, "phm_fixture", "phm_fixture");
            final var sample = new Sample(registry.getActiveLeaseCount(), List.of(valid));
            require(verify(sample, sample) == 1, "Lawful merchant incorrectly requires global zero");
            System.out.println("PASS lease105.lawfulMerchantAfterOrdinary16AndHuman0");
            final var invalid = List.of(
                new Witness(valid.owner(), true, false, player, player, merchant.token(), true, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, true, player, player, merchant.token(), true, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, player, player, merchant.token() - 1, true, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, new Object(), player, merchant.token(), true, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, null, player, merchant.token(), true, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, player, player, merchant.token(), false, true, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, player, player, merchant.token(), true, false, true, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, player, player, merchant.token(), true, true, false, valid.account(), valid.account()),
                new Witness(valid.owner(), false, false, player, player, merchant.token(), true, true, true, valid.account(), "phm_other"),
                new Witness(new OwnerSnapshot(merchant.objectId(), OwnerKind.BACKGROUND, OwnerState.RESERVED, merchant.token()), false, false, player, player, merchant.token(), true, true, true, valid.account(), valid.account()),
                new Witness(new OwnerSnapshot(merchant.objectId(), OwnerKind.REAL_LOGIN, OwnerState.RETAINED, merchant.token()), false, false, player, player, merchant.token(), true, true, true, valid.account(), valid.account()));
            for (var value : invalid) { rejects(() -> verify(new Sample(1, List.of(value)), new Sample(1, List.of(value)))); }
            rejects(() -> verify(new Sample(2, List.of(valid)), sample));
            rejects(() -> verify(new Sample(2, List.of(valid, valid)), new Sample(2, List.of(valid, valid))));
            rejects(() -> verify(sample, new Sample(0, List.of())));
            System.out.println("PASS lease105.unknownStaleTokenWorldPendingPersistenceAndCensusNegatives");
        }
        require(registry.getActiveLeaseCount() == 0, "Focused leases leaked");
        final List<String> calls = new ArrayList<>();
        rejects(() -> finish(true, () -> { throw new AssertionError("unverified persistence"); },
            () -> calls.add("checkpoint"), () -> calls.add("pools"), () -> calls.add("database"), () -> calls.add("blocked")));
        require(calls.equals(List.of("blocked")), "Unsafe persistence failure closed resources or forged checkpoint");
        calls.clear();
        try
        {
            finish(true, () -> calls.add("proof"), () -> { throw new java.io.IOException("checkpoint failure"); },
                () -> calls.add("pools"), () -> calls.add("database"), () -> calls.add("blocked"));
            throw new AssertionError("Checkpoint failure hidden");
        }
        catch (java.io.IOException expected) { require(calls.equals(List.of("proof", "blocked", "pools", "database")), "Safe checkpoint error bypassed pool/DB closure"); }
        calls.clear();
        finish(true, () -> calls.add("proof"), () -> calls.add("checkpoint"), () -> calls.add("pools"), () -> calls.add("database"), () -> calls.add("blocked"));
        require(calls.equals(List.of("proof", "checkpoint", "pools", "database")), "Native stop ordering changed");
        calls.clear();
        final var existingReceipt = java.nio.file.Files.createTempFile("lease105-existing-receipt-", ".txt");
        try
        {
            PhantomPopulation102Native.finishBoundary105(true, () -> calls.add("proof"),
                () -> java.nio.file.Files.writeString(existingReceipt, "receipt", java.nio.file.StandardOpenOption.CREATE_NEW),
                () -> calls.add("checkpoint"), () -> calls.add("pools"), () -> calls.add("database"), () -> calls.add("blocked"));
            throw new AssertionError("Receipt collision hidden");
        }
        catch (java.nio.file.FileAlreadyExistsException expected)
        { require(calls.equals(List.of("proof", "blocked", "pools", "database")), "Receipt failure after native safety proof retained pool/DB"); }
        finally { java.nio.file.Files.delete(existingReceipt); }
        require(!DatabaseFactory.isInitialized(), "Focused checks initialized JDBC");
        System.out.println("PASS lease105.guardedResourceClosure;tests=3;JDBC=0;World=NOT_INITIALIZED;nativeProof=NOT_RUN");
    }

    private static void rejects(Step step) throws Exception
    {
        boolean rejected = false;
        try { step.run(); } catch (AssertionError expected) { rejected = true; }
        require(rejected, "Invalid boundary accepted");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
