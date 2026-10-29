/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Ledger;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Receipt;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Usage;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

/** Called only inside service -> sell -> buy -> manufacture. Native ownership remains with the merchant. */
final class PhantomMerchantMarketProvision
{
	private final PhantomProfileRepository _profiles;
	private final long _profileId;
	private final Map<Long, Receipt> _receipts = new java.util.TreeMap<>();
	private final Map<Integer, OwnerUsage> _owners = new HashMap<>();
	private final Map<Integer, Long> _items = new HashMap<>();
	private final Map<CrystalType, Long> _grades = new java.util.EnumMap<>(CrystalType.class);
	private final Map<CrystalType, Long> _gear = new java.util.EnumMap<>(CrystalType.class);
	private final Map<OwnerEpoch, Integer> _stockEpochs = new HashMap<>();
	private final Set<Integer> _quarantined = ConcurrentHashMap.newKeySet();
	private PhantomProfileComponent _component;
	private Ledger _ledger;
	private boolean _frozen;
	private long _refused;

	PhantomMerchantMarketProvision(PhantomProfileRepository profiles, long profileId, PhantomMerchantMarketPolicy policy, Map<Integer, Role> roles)
	{
		_profiles = profiles; _profileId = profileId;
		_component = profiles.findComponent(profileId, PhantomMerchantMarketLedger.COMPONENT).orElse(null);
		if (_component == null)
		{
			_component = profiles.insertComponent(profileId, PhantomMerchantMarketLedger.COMPONENT, 1, PhantomMerchantMarketLedger.encode(Ledger.empty(policy)));
		}
		if (_component.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown market ledger schema."); }
		_ledger = PhantomMerchantMarketLedger.decodeLedger(_component.payload());
		long funds = 0, stock = 0;
		for (var component : profiles.listComponents(profileId))
		{
			if (!component.componentType().startsWith(PhantomMerchantMarketLedger.RECEIPT_PREFIX)) { continue; }
			if (component.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown market intent schema."); }
			final var receipt = PhantomMerchantMarketLedger.decodeReceipt(component.payload());
			final var role = roles.get(receipt.ordinal());
			if (role == null || role.characterId() != receipt.characterId() || !component.componentType().equals(receipt.component()) || _receipts.put(receipt.sequence(), receipt) != null) { throw new IllegalStateException("Market receipt owner/identity differs."); }
			if (receipt.itemId() == 57) { funds = Math.addExact(funds, receipt.value()); } else { stock = Math.addExact(stock, receipt.value()); }
			if (receipt.quarantined()) { _quarantined.add(receipt.characterId()); }
			index(receipt, policy);
		}
		if (_receipts.size() != _ledger.sequence() || funds != _ledger.fundingIssued() || stock != _ledger.stockIssued()
			|| _receipts.keySet().stream().anyMatch(sequence -> sequence < 1 || sequence > _ledger.sequence())
			|| profiles.findComponent(profileId, PhantomMerchantMarketLedger.COMPONENT).orElseThrow().rowVersion() != _component.rowVersion()) { throw new IllegalStateException("Market reserved receipts/counters differ; no quota reset."); }
		final var next = _ledger.withPolicy(policy, List.copyOf(_receipts.values()), System.currentTimeMillis());
		if (!next.equals(_ledger))
		{
			// A new startup policy gets a new durable version; all issued amounts and pending quotas survive.
			_component = profiles.updateComponent(profileId, PhantomMerchantMarketLedger.COMPONENT, _component.rowVersion(), 1, PhantomMerchantMarketLedger.encode(next)); _ledger = next;
		}
	}
	boolean canTrade(int characterId) { return !_quarantined.contains(characterId); }
	static boolean stockRequired(boolean ownedSale, boolean gearWanted, boolean desiredCategoryCovered) { return !ownedSale || gearWanted && !desiredCategoryCovered; }
	PhantomMerchantMarketPolicy policy() { return _ledger.policy(); }
	private Usage usage(Role role, int itemId, CrystalType grade)
	{
		final var owner = _owners.getOrDefault(role.ordinal(), new OwnerUsage(0, 0, 0, 0));
		return new Usage(owner.funding(), owner.stockValue(), _items.getOrDefault(itemId, 0L), _grades.getOrDefault(grade, 0L), _gear.getOrDefault(grade, 0L), owner.grants(), owner.lastFundingAt());
	}
	private void index(Receipt receipt, PhantomMerchantMarketPolicy policy)
	{
		final var owner = _owners.getOrDefault(receipt.ordinal(), new OwnerUsage(0, 0, 0, 0));
		final boolean funding = receipt.itemId() == 57;
		_owners.put(receipt.ordinal(), new OwnerUsage(Math.addExact(owner.funding(), funding ? receipt.value() : 0), Math.addExact(owner.stockValue(), funding ? 0 : receipt.value()), owner.grants() + 1, funding ? Math.max(owner.lastFundingAt(), receipt.createdAt()) : owner.lastFundingAt()));
		if (!funding)
		{
			_items.merge(receipt.itemId(), receipt.quantity(), Math::addExact); _grades.merge(receipt.grade(), receipt.quantity(), Math::addExact);
			if (receipt.category().equals("FINISHED_GEAR")) { _gear.merge(receipt.grade(), receipt.quantity(), Math::addExact); }
			_stockEpochs.merge(new OwnerEpoch(receipt.ordinal(), receipt.createdAt() / (policy.assortmentSeconds() * 1000L)), 1, Math::addExact);
		}
	}
	void fund(Role role, Player player, long now, BooleanSupplier exactOwner)
	{
		final var p = policy();
		if (_frozen || !canTrade(player.getObjectId()) || !p.fundingEnabled() || player.getAdena() >= p.fundingGrant() / 2) { return; }
		final var usage = usage(role, 57, CrystalType.NONE);
		final long quantity = Math.min(p.fundingGrant(), Math.min(p.fundingPerOwner() - usage.funding(), Math.min(p.fundingGlobal() - _ledger.fundingIssued(), PlayerConfig.MAX_ADENA - player.getAdena())));
		if (quantity <= 0 || usage.lastFundingAt() > 0 && now - usage.lastFundingAt() < p.fundingCooldown() * 1000L) { return; }
		final var before = nativeItem(player, 57);
		final var intent = new Receipt(_ledger.sequence() + 1, role.ordinal(), role.characterId(), State.PREPARED, 57, quantity, 0, quantity, "FUNDING", CrystalType.NONE, now, player.getAdena(), before.count(), before.objectId(), 0, "OWNER_OPERATING_CAPITAL;role=" + role.component());
		deliver(role, player, intent, usage, exactOwner, null, null);
	}
	void stock(Role role, Player player, long now, PhantomMerchantCatalog catalog, PhantomPrivateMarketPricingAuthority pricing, boolean ownedSale, BooleanSupplier exactOwner)
	{
		if (_frozen || !canTrade(player.getObjectId()) || !policy().stockEnabled()) { return; }
		final var ownerUsage = _owners.getOrDefault(role.ordinal(), new OwnerUsage(0, 0, 0, 0));
		if (_ledger.sequence() >= policy().grantsGlobal() || ownerUsage.grants() >= policy().grantsPerOwner() || _ledger.stockIssued() >= policy().stockGlobal() || ownerUsage.stockValue() >= policy().stockPerOwner()) { return; }
		final long epoch = now / (policy().assortmentSeconds() * 1000L);
		final long issuedThisEpoch = _stockEpochs.getOrDefault(new OwnerEpoch(role.ordinal(), epoch), 0);
		if (issuedThisEpoch >= 2) { return; }
		final boolean gearWanted = Math.floorMod(role.ordinal() + epoch, 100) < policy().gearShare();
		final int desiredFamily = (int) Math.floorMod(role.ordinal() + epoch, 3);
		final boolean desiredEnchanted = role.ordinal() % 2 == 0;
		final boolean covered = player.getInventory().getItems().stream().anyMatch(item -> item.getCount() > 0 && !item.isEquipped() && !item.isAugmented() && !item.isPvp() && !item.isShadowItem() && !item.isTimeLimitedItem() && item.getElementals() == null && item.getTemplate().getElementals() == null
			&& catalog.contains(item.getId()) && catalog.classification(item.getId()).category().equals("FINISHED_GEAR") && policy().gearGrades().contains(item.getTemplate().getCrystalType()) && gearFamily(item.getTemplate()) == desiredFamily && (item.getEnchantLevel() > 0) == desiredEnchanted);
		// Unsold inventory is reused. Only an explicitly missing requested gear family may seed beside it.
		if (!stockRequired(ownedSale, gearWanted, covered)) { return; }
		int delivered = (int) issuedThisEpoch;
		for (var entry : catalog.sample(role.ordinal() * 20L + epoch * 20L, 128))
		{
			if (delivered >= 2 || _frozen || !canTrade(player.getObjectId())) { break; }
			final var classification = catalog.classification(entry.item().getId());
			final boolean gear = classification.category().equals("FINISHED_GEAR");
			if (ownedSale && !gear || gear != (gearWanted && delivered == 0) || gear && gearFamily(entry.item()) != desiredFamily || player.getInventory().getItemByItemId(entry.item().getId()) != null || gear && !policy().gearGrades().contains(entry.item().getCrystalType()) || entry.item().isPvpItem() || entry.item().getElementals() != null) { continue; }
			final int enchant = gear && desiredEnchanted ? Math.min(policy().enchantMax(), 4 + Math.floorMod(role.ordinal(), 3)) : 0;
			final PhantomPrivateMarketPricingAuthority.StockQuote quote;
			try { quote = pricing.quoteStock(entry.item().getId(), enchant); }
			catch (IllegalArgumentException unsafe) { _refused++; continue; }
			final long quantity = gear ? 1 : Math.min(policy().stockStack(), Math.max(1, policy().stockPerOwner() / 64 / quote.fair()));
			if (!player.getInventory().validateCapacityByItemId(entry.item().getId(), quantity) || !player.getInventory().validateWeightByItemId(entry.item().getId(), quantity)) { _refused++; continue; }
			final long value;
			try { value = Math.multiplyExact(quote.fair(), quantity); } catch (ArithmeticException unsafe) { _refused++; continue; }
			final CrystalType grade = gear ? entry.item().getCrystalType() : classification.category().equals("MATERIAL_RESOURCE") || classification.category().equals("OTHER_USEFUL") ? CrystalType.NONE : highestGrade(classification.targetGrade());
			final var before = nativeItem(player, entry.item().getId());
			final var intent = new Receipt(_ledger.sequence() + 1, role.ordinal(), role.characterId(), State.PREPARED, entry.item().getId(), quantity, enchant, value, classification.category(), grade, now, player.getAdena(), before.count(), before.objectId(), 0, "OWNER_PROVISIONED;role=" + role.component() + ";source=" + entry.quote().sourceReason());
			if (deliver(role, player, intent, usage(role, entry.item().getId(), grade), exactOwner, quote, pricing)) { delivered++; if (ownedSale) { return; } }
		}
	}
	private boolean deliver(Role role, Player player, Receipt intent, Usage usage, BooleanSupplier exactOwner, PhantomPrivateMarketPricingAuthority.StockQuote quote, PhantomPrivateMarketPricingAuthority pricing)
	{
		final Ledger next;
		try { next = _ledger.reserve(intent, usage); }
		catch (IllegalArgumentException exhausted) { _refused++; return false; }
		if (!exactOwner.getAsBoolean() || !canTrade(player.getObjectId())) { return false; }
		authority(role); verifyPreimage(player, intent);
		PhantomProfileComponent prepared = null;
		_quarantined.add(player.getObjectId());
		try
		{
			final var mutations = List.of(new ComponentMutation(PhantomMerchantMarketLedger.COMPONENT, _component.rowVersion(), 1, PhantomMerchantMarketLedger.encode(next)), new ComponentMutation(intent.component(), -1, 1, PhantomMerchantMarketLedger.encode(intent))).stream().sorted(Comparator.comparing(ComponentMutation::componentType)).toList();
			final var committed = _profiles.mutateComponentsAtomically(_profileId, mutations);
			_component = committed.stream().filter(component -> component.componentType().equals(PhantomMerchantMarketLedger.COMPONENT)).findFirst().orElseThrow(); _ledger = next;
			prepared = committed.stream().filter(component -> component.componentType().equals(intent.component())).findFirst().orElseThrow(); _receipts.put(intent.sequence(), intent);
			index(intent, policy());
			if (!exactOwner.getAsBoolean()) { throw new IllegalStateException("Market grant owner epoch changed after reservation."); }
			authority(role); verifyPreimage(player, intent);
			if (intent.itemId() != 57 && (!player.getInventory().validateCapacityByItemId(intent.itemId(), intent.quantity()) || !player.getInventory().validateWeightByItemId(intent.itemId(), intent.quantity()))) { throw new IllegalStateException("Native market inventory capacity/weight changed."); }
			if (quote != null && !quote.equals(pricing.quoteStock(intent.itemId(), intent.enchant()))) { throw new IllegalStateException("Market stock source quote changed."); }
			final Item item;
			if (intent.itemId() == 57) { player.addAdena(ItemProcessType.REWARD, intent.quantity(), player, false); item = player.getInventory().getItemByItemId(57); }
			else
			{
				item = player.getInventory().addItem(ItemProcessType.REWARD, intent.itemId(), intent.quantity(), player, "OWNER_PROVISIONED_MARKET");
				if (item == null) { throw new IllegalStateException("Native market stock writer returned no item."); }
				item.setEnchantLevel(intent.enchant());
			}
			if (item == null || item.isEquipped() || item.isAugmented() || item.isPvp() || item.isShadowItem() || item.isTimeLimitedItem() || item.getElementals() != null || item.getTemplate().getElementals() != null || item.getEnchantLevel() != intent.enchant()) { throw new IllegalStateException("Native delivered stock differs."); }
			item.updateDatabase(true); player.storeMe();
			final var after = nativeItem(player, intent.itemId());
			if (!exactOwner.getAsBoolean() || after.objectId() != item.getObjectId() || after.count() != Math.addExact(intent.beforeCount(), intent.quantity()) || after.enchant() != intent.enchant()
				|| player.getAdena() != Math.addExact(intent.beforeAdena(), intent.itemId() == 57 ? intent.quantity() : 0) || sqlItem(player.getObjectId(), intent.itemId()).equals(after) == false
				|| quote != null && !quote.equals(pricing.quoteStock(intent.itemId(), intent.enchant()))) { throw new IllegalStateException("Market delivery native/SQL/quote after-image not proven."); }
			final var ready = intent.withState(State.COMMITTED, item.getObjectId());
			_profiles.updateComponent(_profileId, intent.component(), prepared.rowVersion(), 1, PhantomMerchantMarketLedger.encode(ready));
			_receipts.put(ready.sequence(), ready); _quarantined.remove(player.getObjectId()); return true;
		}
		catch (RuntimeException uncertain)
		{
			if (prepared == null) { _frozen = true; }
			else
			{
				final var inconsistent = intent.withState(State.INCONSISTENT, 0); _receipts.put(intent.sequence(), inconsistent);
				try { _profiles.updateComponent(_profileId, intent.component(), prepared.rowVersion(), 1, PhantomMerchantMarketLedger.encode(inconsistent)); }
				catch (RuntimeException failedAttestation) { uncertain.addSuppressed(failedAttestation); }
			}
			throw new IllegalStateException("Market grant uncertain; owner quarantined, reservation spent, no retry.", uncertain);
		}
	}
	private void verifyPreimage(Player player, Receipt intent)
	{
		final var before = nativeItem(player, intent.itemId());
		if (player.getAdena() != intent.beforeAdena() || before.count() != intent.beforeCount() || before.objectId() != intent.beforeObjectId() || !before.equals(sqlItem(player.getObjectId(), intent.itemId()))
			|| !nativeItem(player, 57).equals(sqlItem(player.getObjectId(), 57))) { throw new IllegalStateException("Market grant native/SQL preimage differs."); }
	}
	private static void authority(Role role)
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT a.password,a.accessLevel,c.charId FROM accounts a JOIN characters c ON c.account_name=a.login WHERE a.login=?"))
		{
			query.setString(1, role.account());
			try (var rows = query.executeQuery()) { if (!rows.next() || !role.token().equals(rows.getString(1)) || rows.getInt(2) != -1 || rows.getInt(3) != role.characterId() || rows.next()) { throw new IllegalStateException("Market exact disabled account/character differs."); } }
		}
		catch (java.sql.SQLException failed) { throw new IllegalStateException("Market authority unavailable.", failed); }
	}
	private record PhysicalItem(int objectId, long count, int enchant) { }
	private static PhysicalItem nativeItem(Player player, int id)
	{
		final var items = player.getInventory().getItems().stream().filter(item -> item.getId() == id).toList();
		if (items.isEmpty()) { return new PhysicalItem(0, 0, 0); }
		if (items.size() != 1) { throw new IllegalStateException("Market provision item identity ambiguous."); }
		final var item = items.getFirst(); return new PhysicalItem(item.getObjectId(), item.getCount(), item.getEnchantLevel());
	}
	private static PhysicalItem sqlItem(int owner, int id)
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT object_id,count,enchant_level FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY' ORDER BY object_id"))
		{
			query.setInt(1, owner); query.setInt(2, id);
			try (var rows = query.executeQuery())
			{
				if (!rows.next()) { return new PhysicalItem(0, 0, 0); }
				final var item = new PhysicalItem(rows.getInt(1), rows.getLong(2), rows.getInt(3)); if (rows.next()) { throw new IllegalStateException("Market SQL item identity ambiguous."); } return item;
			}
		}
		catch (java.sql.SQLException failed) { throw new IllegalStateException("Market inventory readback unavailable.", failed); }
	}
	Snapshot snapshot() { return new Snapshot(_ledger.policyVersion(), _ledger.sequence(), _ledger.fundingIssued(), _ledger.stockIssued(), _quarantined.size(), _frozen, _refused); }
	record Snapshot(int policyVersion, long reservedGrants, long fundingIssued, long stockIssued, int quarantinedOwners, boolean frozen, long refused) { }
	private record OwnerUsage(long funding, long stockValue, int grants, long lastFundingAt) { }
	private record OwnerEpoch(int ordinal, long epoch) { }
	private static int gearFamily(org.l2jmobius.gameserver.model.item.ItemTemplate item)
	{
		if (item.isWeapon()) { return 0; }
		return Set.of(org.l2jmobius.gameserver.model.item.enums.BodyPart.NECK, org.l2jmobius.gameserver.model.item.enums.BodyPart.L_EAR, org.l2jmobius.gameserver.model.item.enums.BodyPart.R_EAR, org.l2jmobius.gameserver.model.item.enums.BodyPart.LR_EAR, org.l2jmobius.gameserver.model.item.enums.BodyPart.L_FINGER, org.l2jmobius.gameserver.model.item.enums.BodyPart.R_FINGER, org.l2jmobius.gameserver.model.item.enums.BodyPart.LR_FINGER).contains(item.getBodyPart()) ? 2 : 1;
	}
	private static CrystalType highestGrade(String text) { return java.util.Arrays.stream(text.split(",")).map(CrystalType::valueOf).max(Comparator.comparingInt(CrystalType::getLevel)).orElse(CrystalType.NONE); }
}
