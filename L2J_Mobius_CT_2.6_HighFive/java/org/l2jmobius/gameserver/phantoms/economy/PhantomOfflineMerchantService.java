/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.TownZone;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;
import org.l2jmobius.gameserver.network.serverpackets.RecipeShopMsg;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Ledger;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Dedicated offline owners. Native inventory/listing/restore remain authoritative; no battle registrations. */
public final class PhantomOfflineMerchantService implements PhantomSchedulerControlPort
{
	private static final Logger LOGGER = Logger.getLogger(PhantomOfflineMerchantService.class.getName());
	private static final ConcurrentHashMap<Integer, NativeOwner> NATIVE_OWNERS = new ConcurrentHashMap<>();
	private final PhantomProfileRepository _profiles;
	private final PhantomMerchantPolicy _policy;
	private final Supplier<PhantomPrivateMarketPricingAuthority> _pricing;
	private final List<PhantomNavigationPoint> _arrivalAnchors;
	private final TreeMap<Integer, SavedRole> _roles = new TreeMap<>();
	private final Map<Integer, Player> _owners = new HashMap<>();
	private final Map<Integer, Player> _newActors = new HashMap<>();
	private final Map<Integer, Lease> _leases = new HashMap<>();
	private final Map<Integer, AutoCloseable> _observers = new HashMap<>();
	private final TreeSet<Due> _due = new TreeSet<>(Comparator.comparingLong(Due::time).thenComparingInt(Due::ordinal));
	private final Map<Integer, Due> _dueByOwner = new HashMap<>();
	private final java.util.Set<Integer> _dirty = ConcurrentHashMap.newKeySet();
	private PhantomMerchantCatalog _catalog;
	private PhantomMerchantCraftProvision _craftProvision;
	private PhantomMerchantMarketProvision _marketProvision;
	private final Map<Integer, Long> _assortmentAt = new HashMap<>();
	private final Map<Integer, Long> _listingEpochs = new HashMap<>();
	private final Map<Integer, Idle> _idle = new HashMap<>();
	private List<PhantomNavigationPoint> _slots;
	private PhantomProfileComponent _server;
	private Ledger _ledger;
	private long _nextPulse;
	private long _opened;
	private long _pulses;
	private final java.util.concurrent.atomic.LongAdder _transactions = new java.util.concurrent.atomic.LongAdder();
	private long _blocked;
	private volatile boolean _stopping;
	private volatile java.util.Set<Integer> _wantedItems = java.util.Set.of();
	private boolean _reportedTarget;

	public PhantomOfflineMerchantService(PhantomProfileRepository profiles, PhantomMerchantPolicy policy, Supplier<PhantomPrivateMarketPricingAuthority> pricing, List<PhantomNavigationPoint> arrivalAnchors)
	{
		_profiles = java.util.Objects.requireNonNull(profiles); _policy = java.util.Objects.requireNonNull(policy); _pricing = java.util.Objects.requireNonNull(pricing); _arrivalAnchors = List.copyOf(arrivalAnchors);
	}

	public synchronized void start()
	{
		if (_server != null || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !OfflineTradeConfig.RESTORE_OFFLINERS || _policy.capitalPerIdentity() > PlayerConfig.MAX_ADENA) { throw new IllegalStateException("Native offline trade/restore and bounded merchant capital required."); }
		final var servers = _profiles.listManagedAfter(PhantomMerchantBootstrap.COMPONENT, 0, 2);
		if (servers.size() > 1) { throw new IllegalStateException("Ambiguous merchant server quota; no new subsidy."); }
		_server = servers.isEmpty() ? _profiles.createWithComponent(PhantomMerchantBootstrap.COMPONENT, 1, PhantomMerchantBootstrap.encode(Ledger.empty(_policy.lifetimeIdentities(), _policy.globalCapital(), _policy.globalStockValue(), _policy.perItemQuota()))).component() : servers.getFirst().component();
		if (_server.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown merchant server component."); }
		_ledger = PhantomMerchantBootstrap.decodeLedger(_server.payload());
		for (var component : _profiles.listComponents(_server.profileId()))
		{
			if (!component.componentType().startsWith(PhantomMerchantBootstrap.ROLE_PREFIX)) { continue; }
			if (component.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown merchant role component."); }
			final var role = PhantomMerchantBootstrap.decodeRole(component.payload());
			if (!component.componentType().equals(role.component()) || role.ordinal() >= _ledger.nextOrdinal() || _roles.put(role.ordinal(), new SavedRole(role, component.rowVersion())) != null) { throw new IllegalStateException("Merchant role/issued quota differs."); }
		}
		_catalog = new PhantomMerchantCatalog(_pricing.get());
		_marketProvision = new PhantomMerchantMarketProvision(_profiles, _server.profileId(), PhantomMerchantMarketPolicy.load(new java.io.File(org.l2jmobius.gameserver.config.ServerConfig.DATAPACK_ROOT, PhantomMerchantPolicy.FILE).toPath()), _roles.values().stream().collect(java.util.stream.Collectors.toMap(saved -> saved.role().ordinal(), SavedRole::role)));
		_craftProvision = PhantomMerchantCraftProvision.load(new java.io.File(org.l2jmobius.gameserver.config.ServerConfig.DATAPACK_ROOT, PhantomMerchantCraftProvision.FILE).toPath(), _profiles, _pricing.get());
		_slots = legalSlots();
		for (int id : _policy.preparedOfflineIds())
		{
			if (_roles.values().stream().anyMatch(saved -> saved.role().characterId() == id)) { continue; }
			try { reservePrepared(id); }
			catch (RuntimeException failure) { _blocked++; LOGGER.log(Level.WARNING, "Prepared offline owner not admitted id=" + id, failure); }
		}
		// Stock OfflineTraderTable has already restored open stores. Adoption always checks World before load.
		for (var saved : List.copyOf(_roles.values()))
		{
			try { resume(saved.role()); }
			catch (RuntimeException failure) { _blocked++; schedule(saved.role().ordinal(), System.currentTimeMillis() + _policy.rotationSeconds() * 1000L); LOGGER.log(Level.WARNING, "Merchant restore held ordinal=" + saved.role().ordinal(), failure); }
		}
		refreshWanted();
		LOGGER.info("Offline merchant role ready: durable=" + _roles.size() + ", rawSlots=" + _policy.sites().stream().mapToInt(site -> site.columns() * site.rows()).sum() + ", legalSlots=" + _slots.size() + ", bootstrapSource=BOOTSTRAP_CAPITAL_STOCK; issuedCapital=" + _ledger.issuedCapital() + ", remainingCapital=" + (_ledger.capitalLimit() - _ledger.issuedCapital()));
	}

	@Override public void onPulse() { pulse(System.currentTimeMillis()); }
	public synchronized void pulse(long now)
	{
		if (_stopping || _server == null || now < _nextPulse) { return; }
		_nextPulse = now + 1000; _pulses++;
		for (int ordinal : List.copyOf(_dirty)) { _dirty.remove(ordinal); schedule(ordinal, now); }
		for (int processed = 0; processed < _policy.batchSize() && !_due.isEmpty() && _due.first().time() <= now; processed++)
		{
			final var due = _due.pollFirst(); _dueByOwner.remove(due.ordinal());
			final var player = _owners.get(due.ordinal());
			if (player == null && _roles.get(due.ordinal()).role().stage() != Stage.INCONSISTENT)
			{
				try { resume(_roles.get(due.ordinal()).role()); }
				catch (RuntimeException failure) { _blocked++; LOGGER.log(Level.WARNING, "Merchant pending admission held ordinal=" + due.ordinal(), failure); }
			}
			try
			{
				if (player != null && World.getInstance().getPlayer(player.getObjectId()) == player && !player.isAlikeDead()) { refreshListing(_roles.get(due.ordinal()).role(), player, now); }
			}
			catch (RuntimeException failure) { _blocked++; LOGGER.log(Level.WARNING, "Merchant listing held ordinal=" + due.ordinal(), failure); }
			schedule(due.ordinal(), now + _policy.rotationSeconds() * 1000L);
		}
		for (int added = 0; added < _policy.batchSize() && liveStoreCount() < _policy.targetStores() && _roles.size() < _policy.hardCap(); added++)
		{
			Role birth = null;
			try { birth = reserveBirth(); if (birth == null) { break; } resume(birth); }
			catch (RuntimeException failure) { _blocked++; if (birth != null) { schedule(birth.ordinal(), now + _policy.rotationSeconds() * 1000L); } LOGGER.log(Level.WARNING, "Merchant admission held; no subsidy replay.", failure); break; }
		}
		if (!_reportedTarget && liveStoreCount() >= _policy.targetStores()) { _reportedTarget = true; LOGGER.info("Offline merchant target reached: " + snapshot()); }
	}

	private Role reserveBirth()
	{
		if (_ledger.nextOrdinal() >= _ledger.identityLimit() || _policy.capitalPerIdentity() > _ledger.capitalLimit() - _ledger.issuedCapital()) { return null; }
		final var used = _roles.values().stream().map(saved -> new PhantomNavigationPoint(saved.role().x(), saved.role().y(), saved.role().z(), 0)).toList();
		final var point = _slots.stream().filter(slot -> used.stream().noneMatch(old -> old.distanceTo(slot) < 60) && !observed(slot)).findFirst().orElse(null);
		if (point == null) { return null; }
		final int ordinal = _ledger.nextOrdinal();
		final int bucket = Math.floorMod(ordinal * 37, 100);
		final int kind = bucket < _policy.buyPercent() ? 1 : bucket < _policy.buyPercent() + _policy.manufacturePercent() ? 2 : 0;
		final var pool = _catalog.bootstrapEntries(); if (pool.isEmpty()) { return null; }
		final var stock = new TreeMap<Integer, Long>(); long value = 0;
		// A single expensive seed must not consume the lifetime stock envelope of all future owners.
		final long identityStockLimit = _ledger.stockValueLimit() / _ledger.identityLimit();
		if (kind != 1)
		{
			// Durable birth receipts allow eight stock kinds even when native listing policy allows twenty.
			for (int offset = 0; offset < pool.size() && stock.size() < Math.min(8, _policy.linesPerStore()); offset++)
			{
				final var entry = pool.get(Math.floorMod(ordinal * _policy.linesPerStore() + offset, pool.size()));
				final long count = entry.item().isStackable() ? _policy.stackCount() : 1;
				if (_ledger.issuedItems().getOrDefault(entry.item().getId(), 0L) + count > _ledger.perItemLimit()) { continue; }
				final long cost = Math.multiplyExact(entry.quote().fair(), count);
				if (cost > identityStockLimit - value) { continue; }
				if (cost > _ledger.stockValueLimit() - _ledger.issuedStockValue() - value) { continue; }
				stock.put(entry.item().getId(), count); value = Math.addExact(value, cost);
			}
			if (stock.isEmpty()) { return null; }
		}
		final var next = _ledger.reserve(_policy.capitalPerIdentity(), value, stock);
		final byte[] token = new byte[32]; new SecureRandom().nextBytes(token);
		final String suffix = Long.toUnsignedString(_server.profileId(), 36) + 'x' + Integer.toString(ordinal, 36);
		final String name = "Trader" + suffix;
		final int birthClass = _policy.classIds().get(ordinal % _policy.classIds().size());
		final int classId = _craftProvision == null ? birthClass : org.l2jmobius.gameserver.phantoms.progression.PhantomCraftRecipePolicy.startingClass(kind, _policy.classIds(), birthClass);
		final var role = new Role(ordinal, 0, "phm_" + suffix, Base64.getUrlEncoder().withoutPadding().encodeToString(token), name, classId, kind, point.x(), point.y(), point.z(), _policy.capitalPerIdentity(), stock, Stage.RESERVED);
		commitNewRole(role, next);
		return role;
	}

	private void reservePrepared(int characterId)
	{
		final var player = World.getInstance().getPlayer(characterId);
		if (_roles.size() >= _policy.hardCap() || player == null || player.getAccessLevel().getLevel() != 0 || player.isAlikeDead() || !nonempty(player) || player.getPrivateStoreType() != PrivateStoreType.MANUFACTURE || player.getClient() != null || player.hasHeadlessOutboundSession() || player.getNativeWorkOwner() != null || player.isInParty() || player.isProcessingTransaction() || RecipeManager.getInstance().isMakingItem(player) || player.hasPendingOwnedStore() || !player.isInsideZone(ZoneId.PEACE) || player.getInstanceId() != 0 || PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(characterId) != null || _profiles.findByCharacterObjectId(characterId).isPresent()) { throw new IllegalArgumentException("Prepared native manufacturer has another owner or is not a settled offline store."); }
		validateDurableEligibility(player);
		final var next = _ledger.reserve(0, 0, Map.of());
		final byte[] token = new byte[32]; new SecureRandom().nextBytes(token);
		final var role = new Role(_ledger.nextOrdinal(), characterId, player.getAccountName(), Base64.getUrlEncoder().withoutPadding().encodeToString(token), player.getName(), player.getActiveClass(), 2, player.getX(), player.getY(), player.getZ(), 0, Map.of(), Stage.READY);
		commitNewRole(role, next);
		LOGGER.info("Prepared offline manufacturer reserved: character=" + characterId + "; source=EXISTING_NATIVE; bootstrapCapital=0; bootstrapStock=0");
	}

	private void validateDurableEligibility(Player player)
	{
		if (player.getAccessLevel().getLevel() != 0 || _profiles.findByCharacterObjectId(player.getObjectId()).isPresent()) { throw new IllegalArgumentException("Merchant access/profile authority changed."); }
		if (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE)
		{
			for (var line : player.getManufactureItems().values())
			{
				final var recipe = RecipeData.getInstance().getRecipeList(line.getRecipeId());
				if (recipe == null || !player.hasRecipeList(recipe.getId()) || player.getSkillLevel(recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId()) < recipe.getLevel() || line.getCost() <= 0) { throw new IllegalArgumentException("Native manufacturer lacks its recipe/skill/fee."); }
			}
		}
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT accessLevel FROM accounts WHERE login=?"))
		{
			query.setString(1, player.getAccountName());
			try (var rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) != -1 || rows.next()) { throw new IllegalArgumentException("Prepared manufacturer account is not explicitly disabled."); } }
		}
		catch (java.sql.SQLException failure) { throw new IllegalStateException("Prepared manufacturer account verification failed.", failure); }
	}

	private void commitNewRole(Role role, Ledger next)
	{
		final var mutations = List.of(new ComponentMutation(role.component(), -1, 1, PhantomMerchantBootstrap.encode(role)), new ComponentMutation(PhantomMerchantBootstrap.COMPONENT, _server.rowVersion(), 1, PhantomMerchantBootstrap.encode(next))).stream().sorted(Comparator.comparing(ComponentMutation::componentType)).toList();
		final var committed = _profiles.mutateComponentsAtomically(_server.profileId(), mutations);
		_server = committed.stream().filter(component -> component.componentType().equals(PhantomMerchantBootstrap.COMPONENT)).findFirst().orElseThrow(); _ledger = next;
		_roles.put(role.ordinal(), new SavedRole(role, committed.stream().filter(component -> component.componentType().equals(role.component())).findFirst().orElseThrow().rowVersion()));
	}

	private void resume(Role initial)
	{
		Role role = initial;
		if (role.stage() == Stage.INCONSISTENT) { return; }
		if (role.stage() == Stage.PENDING) { save(role.afterUncertainRestart()); _blocked++; return; }
		if (role.stage() == Stage.RESERVED)
		{
			try (var connection = DatabaseFactory.getConnection())
			{
				try (var query = connection.prepareStatement("SELECT password,accessLevel FROM accounts WHERE login=?"))
				{
					query.setString(1, role.account());
					try (var rows = query.executeQuery())
					{
						if (rows.next()) { if (!role.token().equals(rows.getString(1)) || rows.getInt(2) != -1) { throw new IllegalStateException("Merchant reserved account owner differs."); } }
						else { try (var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,?,-1,1)")) { insert.setString(1, role.account()); insert.setString(2, role.token()); insert.executeUpdate(); } }
					}
				}
				int characterId = 0;
				try (var query = connection.prepareStatement("SELECT charId,char_name,base_class,accesslevel FROM characters WHERE account_name=?"))
				{
					query.setString(1, role.account());
					try (var rows = query.executeQuery()) { if (rows.next()) { characterId = rows.getInt(1); if (!role.name().equals(rows.getString(2)) || rows.getInt(3) != role.classId() || rows.getInt(4) != 0 || rows.next()) { throw new IllegalStateException("Merchant character identity differs."); } } }
				}
				if (characterId == 0)
				{
					final var template = PlayerTemplateData.getInstance().getTemplate(role.classId());
					if (template == null) { throw new IllegalArgumentException("Native merchant starting class absent."); }
					final var created = Player.create(template, role.account(), role.name(), new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
					if (created == null) { throw new IllegalStateException("Native merchant creation failed."); }
					created.stopAllTasks(); characterId = created.getObjectId(); _newActors.put(characterId, created);
				}
				role = role.withCharacter(characterId); save(role);
			}
			catch (java.sql.SQLException failure) { throw new IllegalStateException("Merchant native identity preparation failed.", failure); }
		}
		if (_owners.containsKey(role.ordinal())) { return; }
		final Lease lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(role.characterId(), OwnerKind.PHANTOM);
		if (lease == null) { throw new IllegalStateException("Merchant identity already has a lifecycle owner."); }
		Player player = null;
		boolean existingOffline = false;
		boolean nativeAdmission = false;
		try
		{
			player = World.getInstance().getPlayer(role.characterId());
			final boolean restored = player != null;
			existingOffline = restored;
			if (player == null) { player = _newActors.remove(role.characterId()); }
			if (player == null) { player = Player.load(role.characterId()); }
			if (player == null || !role.account().equals(player.getAccountName()) || player.getObjectId() != role.characterId() || player.isGM() || player.getClient() != null || player.hasHeadlessOutboundSession() || player.getNativeWorkOwner() != null || player.isInParty() || RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { throw new IllegalStateException("Merchant native owner/admission differs or native mutation is pending."); }
			validateDurableEligibility(player);
			nativeAdmission = true;
			if (role.stage() == Stage.CHARACTER)
			{
				if (restored || !player.getInventory().getItems().isEmpty() || player.getAdena() != 0) { throw new IllegalStateException("Merchant bootstrap preimage is not empty."); }
				if (observed(new PhantomNavigationPoint(role.x(), role.y(), role.z(), 0))) { player.stopAllTasks(); player.storeMe(); lease.close(); schedule(role.ordinal(), System.currentTimeMillis() + _policy.rotationSeconds() * 1000L); return; }
				save(role.pending());
				// Pristine native createDb writes HP0. A restart before the first bootstrap loads
				// that uninitialized birth as dead; initialize it only inside this exact empty stage.
				if (player.isDead()) { player.doRevive(); }
				player.getStatus().setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				if (role.capital() > 0) { player.addAdena(ItemProcessType.REWARD, role.capital(), player, false); }
				for (var item : role.stock().entrySet()) { player.getInventory().addItem(ItemProcessType.REWARD, item.getKey(), item.getValue(), player, "BOOTSTRAP_STOCK"); }
				player.getInventory().updateDatabase(); player.storeMe();
				final int characterId = role.characterId();
				if (player.getAdena() != role.capital() || role.stock().entrySet().stream().anyMatch(item -> playerCount(characterId, item.getKey()) != item.getValue())) { throw new IllegalStateException("Merchant bootstrap durable delta differs; no retry."); }
				role = role.withStage(Stage.READY); save(role);
			}
			if (role.stage() != Stage.READY) { throw new IllegalStateException("Merchant bootstrap not settled."); }
			if (_craftProvision != null && !observed(new PhantomNavigationPoint(role.x(), role.y(), role.z(), 0))) { _craftProvision.qualify(_server.profileId(), role, player, lease); }
			player.abortAttack(); player.abortCast(); player.stopMove(null); player.setAutoPlaying(false); player.getAI().setIntention(Intention.IDLE); player.getAI().stopAITask();
			player.setOnlineStatus(true, false);
			if (!restored)
			{
				if (observed(new PhantomNavigationPoint(role.x(), role.y(), role.z(), 0))) { throw new IllegalStateException("Merchant restore position is observed."); }
				player.spawnMe(role.x(), role.y(), role.z());
			}
			if (!player.isInsideZone(ZoneId.PEACE) || player.getInstanceId() != 0) { throw new IllegalStateException("Merchant native town slot is not peaceful."); }
			player.setOfflineStartTime(player.getOfflineStartTime() > 0 ? player.getOfflineStartTime() : System.currentTimeMillis());
			player.setOnlineStatus(true, true);
			_owners.put(role.ordinal(), player); _leases.put(role.ordinal(), lease);
			final var previousOwner = NATIVE_OWNERS.putIfAbsent(player.getObjectId(), new NativeOwner(player, this));
			if (previousOwner != null && (previousOwner.player() != player || previousOwner.service() != this)) { throw new IllegalStateException("Duplicate native merchant actor."); }
			if (!restored) { World.OFFLINE_TRADE_COUNT++; }
			final int ordinal = role.ordinal();
			// Admission owns this identity even if a transient pricing/listing check fails below.
			schedule(ordinal, System.currentTimeMillis() + _policy.rotationSeconds() * 1000L);
			_observers.put(ordinal, PrivateStoreService.getInstance().observeOwner(player.getObjectId(), new PrivateStoreService.OwnerObserver()
			{
				@Override public boolean beforeTransaction(Player owner, org.l2jmobius.gameserver.network.holders.TradeList list)
				{
					final boolean allowed = !_stopping && ownsNative(owner) && safeTradePrices(owner, list);
					if (!allowed) { _dirty.add(ordinal); } return allowed;
				}
				@Override public void afterTransaction(Player owner, org.l2jmobius.gameserver.network.holders.TradeList list, boolean successful) { if (successful) { owner.storeMe(); _transactions.increment(); _dirty.add(ordinal); } }
			}));
			refreshListing(role, player);
		}
		catch (RuntimeException failure)
		{
			if (!_owners.containsKey(role.ordinal()))
			{
				if (existingOffline && !nativeAdmission) { lease.close(); }
				else if (player != null && World.getInstance().getPlayer(player.getObjectId()) == player) { _owners.put(role.ordinal(), player); _leases.put(role.ordinal(), lease); save(_roles.get(role.ordinal()).role().withStage(Stage.INCONSISTENT)); }
				else { if (player != null) { player.stopAllTasks(); } lease.close(); }
			}
			throw failure;
		}
	}

	private static long playerCount(int characterId, int itemId)
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY'"))
		{
			query.setInt(1, characterId); query.setInt(2, itemId); try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
		}
		catch (java.sql.SQLException failure) { throw new IllegalStateException("Merchant inventory verification failed.", failure); }
	}
	private void save(Role role)
	{
		final var prior = _roles.get(role.ordinal());
		final var component = _profiles.updateComponent(_server.profileId(), role.component(), prior.version(), 1, PhantomMerchantBootstrap.encode(role));
		_roles.put(role.ordinal(), new SavedRole(role, component.rowVersion()));
	}
	private void schedule(int ordinal, long when) { final var old = _dueByOwner.remove(ordinal); if (old != null) { _due.remove(old); } final var due = new Due(ordinal, when); _due.add(due); _dueByOwner.put(ordinal, due); }

	/** Current native tax/stock and the immutable price generation are checked under the native list lock. */
	private boolean safeTradePrices(Player player, org.l2jmobius.gameserver.network.holders.TradeList list)
	{
		if (player.getAccessLevel().getLevel() != 0 || player.getClient() != null || player.getNativeWorkOwner() != null || _marketProvision != null && !_marketProvision.canTrade(player.getObjectId())) { return false; }
		try
		{
			final var type = list == player.getBuyList() ? PhantomStorePlan.Type.BUY : PhantomStorePlan.Type.SELL;
			if (list.getItems().isEmpty()) { return false; }
			final var lines = list.getItems().stream().map(item -> new PhantomStorePlan.Line(type == PhantomStorePlan.Type.BUY ? item.getItem().getId() : item.getObjectId(), item.getItem().getId(), item.getCount(), item.getPrice())).toList();
			if (lines.stream().anyMatch(line -> line.count() <= 0 || line.price() <= 0 || !_catalog.contains(line.itemId()) || type == PhantomStorePlan.Type.SELL && !saleable(player.getInventory().getItemByObjectId(line.objectOrRecipeId())))) { return false; }
			final var priced = _pricing.get().pricePlan(player, new PhantomStorePlan(type, PhantomStorePlan.State.REQUESTED, "Supplies", lines, Long.MAX_VALUE));
			long committedBudget = 0;
			for (int index = 0; index < lines.size(); index++)
			{
				if (type == PhantomStorePlan.Type.BUY)
				{
					if (lines.get(index).price() > priced.lines().get(index).price()) { return false; }
					committedBudget = Math.addExact(committedBudget, Math.multiplyExact(lines.get(index).count(), lines.get(index).price()));
				}
				else if (lines.get(index).price() < priced.lines().get(index).price()) { return false; }
			}
			return type != PhantomStorePlan.Type.BUY || committedBudget <= player.getAdena();
		}
		catch (RuntimeException unknownOrStale) { return false; }
	}

	private void refreshListing(Role role, Player player)
	{
		refreshListing(role, player, System.currentTimeMillis());
	}
	private void refreshListing(Role role, Player player, long now)
	{
		if (_stopping || !ownsNative(player) || World.getInstance().getPlayer(player.getObjectId()) != player || RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { return; }
		if (_craftProvision != null && !observed(new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()))) { _craftProvision.qualify(_server.profileId(), role, player, _leases.get(role.ordinal())); }
		synchronized (player.getSellList())
		{
			synchronized (player.getBuyList())
			{
				synchronized (player.getManufactureItems())
				{
					if (_stopping || !ownsNative(player) || player.isCrafting() || RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { return; }
					// No visible shop disappears or changes underneath an observer/customer, including unsafe prices.
					if (observed(new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()))) { if (!nonempty(player)) { idle(role, player, now, "OBSERVED"); } return; }
					if (_marketProvision != null && !_marketProvision.canTrade(player.getObjectId()))
					{
						player.getSellList().clear(); player.getBuyList().clear(); player.getManufactureItems().clear(); player.setPrivateStoreType(PrivateStoreType.NONE);
						OfflineTraderTable.getInstance().onTransaction(player, true, false); idle(role, player, now, "MARKET_GRANT_UNCERTAIN"); refreshWanted(); return;
					}
					if (nonempty(player))
					{
						boolean safe;
						if (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE)
						{
							try { validateDurableEligibility(player); safe = player.getManufactureItems().values().stream().allMatch(line -> { final var recipe = RecipeData.getInstance().getRecipeList(line.getRecipeId()); return line.getCost() == _pricing.get().manufactureFee(recipe.getId(), recipe.getItemId()); }); }
							catch (RuntimeException invalid) { safe = false; }
						}
						else { safe = safeTradePrices(player, player.getPrivateStoreType() == PrivateStoreType.BUY ? player.getBuyList() : player.getSellList()); }
						// A BUY birth has no item stock. Its current inventory is the durable purchase
						// receipt, including after restart between native close and reopen. Never reseed it.
						boolean recycle = false;
						if (safe && role.storeKind() == 1 && player.getPrivateStoreType() == PrivateStoreType.BUY && !observed(new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId())))
						{
							final var purchased = sellPlan(player);
							if (purchased != null) { _pricing.get().pricePlan(player, purchased); recycle = true; }
						}
						final long listedAt = _assortmentAt.computeIfAbsent(role.ordinal(), _ -> now);
						final boolean rotate = player.getPrivateStoreType() != PrivateStoreType.MANUFACTURE && assortmentDue(listedAt, now, _marketProvision.policy().assortmentSeconds(), false, false);
						if (safe && !recycle && !rotate) { return; }
						// Price/profile changes never mutate stock or subsidize the owner; only this owner's offers close.
						player.getSellList().clear(); player.getBuyList().clear(); player.getManufactureItems().clear(); player.setPrivateStoreType(PrivateStoreType.NONE);
						OfflineTraderTable.getInstance().onTransaction(player, true, false);
						refreshWanted();
					}
					open(role, player, now);
				}
			}
		}
	}

	private void open(Role role, Player player)
	{
		open(role, player, System.currentTimeMillis());
	}
	static boolean assortmentDue(long listedAt, long now, int seconds, boolean observed, boolean pending)
	{
		return !observed && !pending && now >= listedAt && now - listedAt >= seconds * 1000L;
	}
	private boolean marketOwner(Role role, Player player, Lease lease)
	{
		final var current = _roles.get(role.ordinal());
		final var owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId());
		return !_stopping && current != null && current.role().stage() == Stage.READY && current.role().characterId() == player.getObjectId()
			&& role.stage() == Stage.READY && role.characterId() == player.getObjectId() && role.account().equals(player.getAccountName()) && role.name().equals(player.getName())
			&& lease != null && _leases.get(role.ordinal()) == lease && lease.objectId() == player.getObjectId() && lease.ownerKind() == OwnerKind.PHANTOM && owner != null && owner.token() == lease.token()
			&& ownsNative(player) && World.getInstance().getPlayer(player.getObjectId()) == player && player.getAccessLevel().getLevel() == 0 && player.getClient() == null && !player.hasHeadlessOutboundSession()
			&& player.getNativeWorkOwner() == null && !player.isInParty() && player.getClanId() == 0 && player.getClassIndex() == 0 && !player.isChangingClass() && !player.isAlikeDead()
			&& !player.isCrafting() && !RecipeManager.getInstance().isMakingItem(player) && !player.isProcessingTransaction() && !player.hasPendingOwnedStore() && player.isInsideZone(ZoneId.PEACE) && player.getInstanceId() == 0
			&& Thread.holdsLock(player.getSellList()) && Thread.holdsLock(player.getBuyList()) && Thread.holdsLock(player.getManufactureItems())
			&& !observed(new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()));
	}
	private void open(Role role, Player player, long now)
	{
		if (_stopping || role.stage() != Stage.READY || !ownsNative(player) || World.getInstance().getPlayer(player.getObjectId()) != player || player.isAlikeDead() || player.isCrafting() || player.isProcessingTransaction() || player.hasPendingOwnedStore() || !player.isInsideZone(ZoneId.PEACE) || player.getInstanceId() != 0 || observed(new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()))) { return; }
		synchronized (player.getSellList())
		{
			synchronized (player.getBuyList())
			{
				if (nonempty(player)) { return; }
				if (_marketProvision != null && !_marketProvision.canTrade(player.getObjectId())) { return; }
				_listingEpochs.put(role.ordinal(), now / (_marketProvision.policy().assortmentSeconds() * 1000L));
				if (role.storeKind() != 2)
				{
					final var lease = _leases.get(role.ordinal());
					if (!marketOwner(role, player, lease)) { return; }
					// BUY births keep their funded demand. Purchased goods take priority on the next opening.
					if (role.storeKind() != 1) { _marketProvision.stock(role, player, now, _catalog, _pricing.get(), sellPlan(player) != null, () -> marketOwner(role, player, lease)); }
					if (sellPlan(player) == null) { _marketProvision.fund(role, player, now, () -> marketOwner(role, player, lease)); }
					if (!_marketProvision.canTrade(player.getObjectId())) { return; }
				}
				final PhantomStorePlan plan = selectPlan(role.storeKind(), () -> manufacturePlan(player), () -> sellPlan(player), () -> buyPlan(role, player));
				if (plan == null) { idle(role, player, now, "NO_ADMISSIBLE_STOCK_OR_AFFORDABLE_PLAN"); return; }
				player.getSellList().clear(); player.getBuyList().clear(); player.getManufactureItems().clear(); player.setPrivateStoreType(PrivateStoreType.NONE);
				final var priced = _pricing.get().pricePlan(player, plan);
				if (!PhantomStoreService.installNative(player, priced)) { throw new IllegalStateException("Native merchant listing rejected."); }
				player.getSellList().setTitle(priced.title()); player.getBuyList().setTitle(priced.title()); player.setStoreName(priced.title());
				// The same native per-character writer handles both ordinary and merchant stores. No second bulk save.
				OfflineTraderTable.getInstance().onTransaction(player, true, false);
				player.storeMe(); OfflineTraderTable.getInstance().onTransaction(player, false, true); _opened++;
				_assortmentAt.put(role.ordinal(), now);
				_idle.remove(role.ordinal());
				refreshWanted();
			}
		}
	}
	private record Idle(long sinceMillis, String reason, long wallet) { }
	private void idle(Role role, Player player, long now, String reason)
	{
		final var prior = _idle.get(role.ordinal());
		_idle.put(role.ordinal(), new Idle(prior != null && prior.reason().equals(reason) ? prior.sinceMillis() : now, reason, player.getAdena()));
	}
	private boolean saleable(org.l2jmobius.gameserver.model.item.instance.Item item)
	{
		if (item == null || item.getId() == 57 || !_catalog.contains(item.getId()) || !item.isTradeable() || item.isEquipped() || item.getCount() <= 0 || item.getEnchantLevel() < 0 || item.getEnchantLevel() > 25 || item.isAugmented() || item.isPvp() || item.isShadowItem() || item.isTimeLimitedItem() || item.getElementals() != null || item.getTemplate().getElementals() != null) { return false; }
		return _marketProvision == null || !_catalog.classification(item.getId()).category().equals("FINISHED_GEAR") || _marketProvision.policy().gearGrades().contains(item.getTemplate().getCrystalType());
	}
	private PhantomStorePlan sellPlan(Player player)
	{
		final var lines = new ArrayList<PhantomStorePlan.Line>();
		final var role = _roles.values().stream().map(SavedRole::role).filter(value -> value.characterId() == player.getObjectId()).findFirst().orElse(null);
		final int limit = Math.min(_policy.linesPerStore(), player.getPrivateSellStoreLimit());
		if (limit <= 0 || _marketProvision != null && !_marketProvision.canTrade(player.getObjectId())) { return null; }
		final var stock = player.getInventory().getItems().stream().sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getId).thenComparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).toList();
		final long epoch = role == null ? 0 : _listingEpochs.getOrDefault(role.ordinal(), 0L);
		final int start = stock.isEmpty() ? 0 : (int) Math.floorMod((role == null ? 0 : role.ordinal() * 20L) + epoch * 20L, stock.size());
		int gearLines = 0; final int gearLimit = _marketProvision == null ? limit : _marketProvision.policy().gearShare() == 0 ? 0 : Math.max(1, limit * _marketProvision.policy().gearShare() / 100);
		final var gearStock = stock.stream().filter(this::saleable).filter(item -> _catalog.classification(item.getId()).category().equals("FINISHED_GEAR")).toList();
		final int reservedGear = Math.min(gearLimit, gearStock.size());
		final boolean positive = gearStock.stream().anyMatch(item -> item.getEnchantLevel() > 0), zero = gearStock.stream().anyMatch(item -> item.getEnchantLevel() == 0);
		int nonGearLines = 0, positiveLines = 0, zeroLines = 0;
		final var pricing = _pricing.get();
		for (int offset = 0; offset < stock.size() && lines.size() < limit; offset++)
		{
			final var item = stock.get((start + offset) % stock.size());
			if (!saleable(item)) { continue; }
			final boolean gear = _catalog.classification(item.getId()).category().equals("FINISHED_GEAR");
			if (gear && (gearLines >= gearLimit || _marketProvision != null && !_marketProvision.policy().gearGrades().contains(item.getTemplate().getCrystalType()))) { continue; }
			if (!gear && nonGearLines >= limit - reservedGear) { continue; }
			if (gear && gearLines == reservedGear - 1 && positive && positiveLines == 0 && item.getEnchantLevel() == 0) { continue; }
			if (gear && reservedGear >= 2 && gearLines == reservedGear - 1 && zero && zeroLines == 0 && item.getEnchantLevel() > 0) { continue; }
			try
			{
				final var quote = pricing.quoteStock(item.getId(), item.getEnchantLevel());
				lines.add(new PhantomStorePlan.Line(item.getObjectId(), item.getId(), item.getCount(), quote.ask()));
				if (gear) { gearLines++; if (item.getEnchantLevel() > 0) { positiveLines++; } else { zeroLines++; } } else { nonGearLines++; }
			}
			catch (IllegalArgumentException unpriced) { continue; }
		}
		return lines.isEmpty() ? null : new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Supplies / materials", lines, Long.MAX_VALUE);
	}
	static PhantomStorePlan selectPlan(int kind, Supplier<PhantomStorePlan> manufacture, Supplier<PhantomStorePlan> sell, Supplier<PhantomStorePlan> buy)
	{
		PhantomStorePlan plan = kind == 2 ? manufacture.get() : null;
		if (plan == null) { plan = sell.get(); }
		if (plan == null && kind != 2) { plan = buy.get(); }
		return plan;
	}
	private PhantomStorePlan buyPlan(Role role, Player player)
	{
		final long epoch = _listingEpochs.getOrDefault(role.ordinal(), 0L);
		final var pool = _catalog.buySample(role.ordinal() * 20L + epoch * 20L, 128);
		final var pricing = _pricing.get();
		final var quotes = pool.stream().map(entry -> pricing.quoteItem(entry.item().getId())).toList();
		final var lines = allocateBuyLines(quotes, player.getAdena(), _policy.linesPerStore(), player.getPrivateBuyStoreLimit(), _policy.stackCount(), 0);
		return lines.isEmpty() ? null : new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Buying craft supplies", lines, Long.MAX_VALUE);
	}
	static List<PhantomStorePlan.Line> allocateBuyLines(List<PhantomPrivateMarketPricingAuthority.ItemQuote> pool, long budget, int configuredSlots, int nativeSlots, int stackCount, long cursor)
	{
		if (pool.isEmpty() || budget <= 0 || configuredSlots <= 0 || nativeSlots <= 0 || stackCount <= 0) { return List.of(); }
		final int limit = Math.min(configuredSlots, nativeSlots);
		final var selected = new ArrayList<PhantomPrivateMarketPricingAuthority.ItemQuote>();
		final var seen = new java.util.HashSet<Integer>();
		final int start = (int) Math.floorMod(cursor, pool.size());
		// Reserve the first real unit before allocating extra stacks. Empty native slots cost nothing.
		for (int offset = 0; offset < pool.size() && selected.size() < limit; offset++)
		{
			final var quote = pool.get((start + offset) % pool.size());
			if (quote == null || !quote.priced() || !quote.bidAllowed() || quote.itemId() <= 0 || quote.bid() <= 0 || quote.bid() > budget || !seen.add(quote.itemId())) { continue; }
			selected.add(quote); budget -= quote.bid();
		}
		final var lines = new ArrayList<PhantomStorePlan.Line>();
		for (int index = 0; index < selected.size(); index++)
		{
			final var quote = selected.get(index);
			final long extra = Math.min(stackCount - 1L, budget / (selected.size() - index) / quote.bid());
			lines.add(new PhantomStorePlan.Line(quote.itemId(), quote.itemId(), 1 + extra, quote.bid())); budget -= Math.multiplyExact(extra, quote.bid());
		}
		return List.copyOf(lines);
	}
	private PhantomStorePlan manufacturePlan(Player player)
	{
		if (!OfflineTradeConfig.OFFLINE_CRAFT_ENABLE) { return null; }
		final var lines = new ArrayList<PhantomStorePlan.Line>();
		for (var recipe : java.util.stream.Stream.concat(player.getDwarvenRecipeBook().stream(), player.getCommonRecipeBook().stream()).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.recipe.RecipeList::getId)).toList())
		{
			if (player.getSkillLevel(recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId()) < recipe.getLevel()) { continue; }
			try { final long fee = _pricing.get().manufactureFee(recipe.getId(), recipe.getItemId()); if (fee <= 0) { continue; } lines.add(new PhantomStorePlan.Line(recipe.getId(), recipe.getItemId(), 1, fee)); }
			catch (IllegalArgumentException unknown) { continue; }
			if (lines.size() == _policy.linesPerStore()) { break; }
		}
		return lines.isEmpty() ? null : new PhantomStorePlan(PhantomStorePlan.Type.MANUFACTURE, PhantomStorePlan.State.REQUESTED, "Craft service", lines, Long.MAX_VALUE);
	}
	public static boolean nonempty(Player player)
	{
		if (player == null || !player.isOnline() || player.getOfflineStartTime() <= 0) { return false; }
		return switch (player.getPrivateStoreType())
		{
			case SELL, PACKAGE_SELL -> player.getSellList().getItemCount() > 0;
			case BUY -> player.getBuyList().getItemCount() > 0;
			case MANUFACTURE -> !player.getManufactureItems().isEmpty();
			default -> false;
		};
	}
	public static boolean ownsNative(Player player)
	{
		final var owner = player == null ? null : NATIVE_OWNERS.get(player.getObjectId());
		return owner != null && owner.player() == player && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.PHANTOM;
	}
	/** Called under the native manufacture-list lock; ordinary owners retain native admission. */
	public static boolean permitsNativeManufacture(Player player, int recipeListId)
	{
		final var owner = NATIVE_OWNERS.get(player.getObjectId());
		if (owner == null) { return true; }
		if (owner.player() != player || owner.service()._stopping || !ownsNative(player)) { return false; }
		try
		{
			final var recipe = RecipeData.getInstance().getRecipeList(recipeListId);
			final var listing = player.getManufactureItems().get(recipeListId);
			return recipe != null && listing != null && listing.getCost() > 0 && listing.getCost() == owner.service()._pricing.get().manufactureFee(recipeListId, recipe.getItemId());
		}
		catch (RuntimeException unavailable) { return false; }
	}
	private int liveStoreCount() { return (int) _owners.values().stream().filter(PhantomOfflineMerchantService::nonempty).count(); }
	private void refreshWanted() { _wantedItems = _owners.values().stream().filter(owner -> owner.getPrivateStoreType() == PrivateStoreType.BUY).flatMap(owner -> owner.getBuyList().getItems().stream()).map(line -> line.getItem().getId()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }

	private List<PhantomNavigationPoint> legalSlots()
	{
		final var geo = GeoEngine.getInstance();
		final var npcs = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Npc).map(object -> (Npc) object).filter(npc -> npc.getInstanceId() == 0).toList();
		final var doors = List.copyOf(DoorData.getInstance().getDoors());
		final var slots = new ArrayList<PhantomNavigationPoint>();
		for (var site : _policy.sites())
		{
			for (int row = 0; row < site.rows(); row++)
			{
				for (int column = 0; column < site.columns(); column++)
				{
					final int x = site.x() + column * site.spacing(), y = site.y() + row * site.spacing();
					if (!geo.hasGeo(x, y)) { continue; }
					final int z = geo.getHeight(x, y, site.z());
					if (Math.abs((long) z - site.z()) > 200 || ZoneManager.getInstance().getZones(x, y, z).stream().noneMatch(zone -> zone instanceof TownZone || zone instanceof PeaceZone)) { continue; }
					final var point = new PhantomNavigationPoint(x, y, z, 0);
					if (_arrivalAnchors.stream().anyMatch(anchor -> anchor.distanceTo(point) < 300) || slots.stream().anyMatch(old -> old.distanceTo(point) < 60)) { continue; }
					if (npcs.stream().anyMatch(npc -> Math.abs((long) npc.getZ() - z) < 200 && Math.hypot((long) npc.getX() - x, (long) npc.getY() - y) < Math.max(160, npc.getTemplate().getCollisionRadius() + 60))) { continue; }
					if (doors.stream().anyMatch(door -> Math.abs((long) door.getZ() - z) < 200 && Math.hypot((long) door.getX() - x, (long) door.getY() - y) < 160)) { continue; }
					if (!geo.canMoveToTarget(x, y, z, x + 16, y, geo.getHeight(x + 16, y, z), 0) || !geo.canMoveToTarget(x, y, z, x, y + 16, geo.getHeight(x, y + 16, z), 0)) { continue; }
					slots.add(point);
				}
			}
		}
		return List.copyOf(slots);
	}
	private static boolean observed(PhantomNavigationPoint point)
	{
		return World.getInstance().getPlayers().stream().filter(player -> player.isOnline() && player.getInstanceId() == point.instanceId())
			.filter(player -> player.getClient() != null && !player.getClient().isDetached() || PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.LOCALPLAY_TEST_HUMAN)
			.anyMatch(player -> player.calculateDistance3D(point.x(), point.y(), point.z()) < 2000);
	}

	/** Freeze the single rotation before native bulk persistence. Pending native craft must finish first. */
	public synchronized boolean beginStop()
	{
		_stopping = true;
		if (_marketProvision != null && _marketProvision.snapshot().quarantinedOwners() > 0) { return false; }
		if (_owners.values().stream().anyMatch(player -> RecipeManager.getInstance().isMakingItem(player))) { return false; }
		for (var player : _owners.values())
		{
			synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
			{
				if (RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { return false; }
				player.storeMe();
			} } }
		}
		for (var player : _newActors.values()) { player.stopAllTasks(); }
		return true;
	}

	/** Explicit owner removal only after native deletion; never deletes ordinary stores or refunds bootstrap. */
	public synchronized void releaseDeletedOwners()
	{
		for (var entry : List.copyOf(_owners.entrySet()))
		{
			if (World.getInstance().getPlayer(entry.getValue().getObjectId()) == entry.getValue()) { continue; }
			final int ordinal = entry.getKey();
			NATIVE_OWNERS.remove(entry.getValue().getObjectId(), new NativeOwner(entry.getValue(), this));
			try { final var observer = _observers.remove(ordinal); if (observer != null) { observer.close(); } }
			catch (Exception failure) { throw new IllegalStateException("Merchant observer release failed.", failure); }
			final var lease = _leases.remove(ordinal); if (lease != null) { lease.close(); } _owners.remove(ordinal);
		}
	}
	public synchronized List<Integer> characterIds() { return _owners.values().stream().map(Player::getObjectId).sorted().toList(); }
	public boolean hasBuyDemand(int itemId) { return _wantedItems.contains(itemId); }
	public synchronized Snapshot snapshot()
	{
		final long activeCombat = _owners.values().stream().filter(player -> player.isAutoPlaying() || player.isAttackingNow() || player.getNativeWorkOwner() != null).count();
		return new Snapshot(_roles.size(), _owners.size(), liveStoreCount(), _leases.size(), _slots == null ? 0 : _slots.size(), _opened, _transactions.sum(), _pulses, _blocked, _ledger == null ? 0 : _ledger.issuedCapital(), _ledger == null ? 0 : _ledger.issuedStockValue(), activeCombat, _due.size());
	}
	public record Snapshot(int durableRoles, int nativeOwners, int nonemptyStores, int identityLeases, int legalSlots, long openings, long transactionCallbacks, long sharedPulses, long blocked, long issuedCapital, long issuedStockValue, long combatOrMaterializationOwners, int dueOwners) { }
	/** Immutable admission evidence; a later mutable World census is a separate observation. */
	public synchronized List<PhantomNavigationPoint> admissionSlots() { return _slots == null ? List.of() : _slots; }

	private record SavedRole(Role role, long version) { }
	private record Due(int ordinal, long time) { }
	private record NativeOwner(Player player, PhantomOfflineMerchantService service) { }
}
