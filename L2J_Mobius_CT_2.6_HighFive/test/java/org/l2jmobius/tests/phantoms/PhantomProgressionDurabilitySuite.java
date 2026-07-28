/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Trainer;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerSkillLearn;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomClassSkillLearningTransaction;
import org.l2jmobius.gameserver.phantoms.progression.PhantomClassSkillLearningTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class PhantomProgressionDurabilitySuite implements PhantomTestSuite
{
	private static final int SUBCLASS_ID = 94;
	private static final int SUBCLASS_INDEX = 1;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Trainer> _trainers = new ArrayList<>();
	private final AtomicInteger _skillLearnEvents = new AtomicInteger();
	private final AtomicBoolean _eventsAfterPostconditions = new AtomicBoolean(true);
	private PhantomGameKnowledgeService _knowledge;
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private PhantomMaterializationService _materialization;
	private PhantomProgressionService _progression;
	private Player _player;
	private boolean _mainSuccess;
	private boolean _mainReload;
	private boolean _idempotent;
	private boolean _concurrencyExact;
	private boolean _autosaveExact;
	private boolean _failStop;
	private boolean _failStopReload;
	private boolean _subclassSuccess;
	private boolean _subclassReload;
	private boolean _faultMatrix;
	private boolean _typedConflicts;
	private boolean _exactItemSuccess;
	private boolean _eventCountExact;
	private boolean _noLeaks;
	private boolean _cleaned;
	private long _elapsedMillis;

	@Override
	public String id()
	{
		return "progression-durability";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(13001302L, context.seed(), "Goal 013B requires deterministic seed 13001302.");
		_environment.initialize(context);
		try
		{
			_knowledge = PhantomGameKnowledgeService.inertForTesting("0".repeat(64));
			PhantomAssertions.assertTrue(_knowledge.start(), "Durability knowledge fixture did not start.");
			_repository = PhantomProfileRepository.open();
			_profile = _repository.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(_materialization.start(), "Durability materialization service did not start.");
			materialize();
			startProgression(new PhantomClassSkillLearningTransaction());
			final long started = System.nanoTime();
			exerciseMainSuccessAndReload();
			exerciseConcurrentAutosave();
			exercisePostCommitFailStopAndReload();
			exerciseSubclassSuccessAndReload();
			exerciseFaultMatrixAndExactItem();
			_elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
			_eventCountExact = (_skillLearnEvents.get() == 3) && _eventsAfterPostconditions.get();
			_noLeaks = (_progression.snapshot().currentOperations() == 0) && (_progression.snapshot().currentActorLeases() == 0);
			context.record("progressionDurability.seed", context.seed());
			context.record("progressionDurability.faultPoints", 7);
			context.record("progressionDurability.subclassIndex", SUBCLASS_INDEX);
			context.record("progressionDurability.elapsedMillis", _elapsedMillis);
		}
		catch (Throwable failure)
		{
			cleanup();
			throw failure;
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-main-class-durable-success", _ -> PhantomAssertions.assertTrue(_mainSuccess, "Main-class DB and runtime postconditions were not exact."));
		registry.add("02-main-class-restart-proof", _ -> PhantomAssertions.assertTrue(_mainReload, "Main-class skill or SP did not survive materialization restart."));
		registry.add("03-idempotent-retry-has-no-second-cost", _ -> PhantomAssertions.assertTrue(_idempotent, "Repeated durable request was not an exact no-cost idempotent result."));
		registry.add("04-same-profile-concurrency-one-commit", _ -> PhantomAssertions.assertTrue(_concurrencyExact, "Concurrent same-profile requests did not produce exactly one commit."));
		registry.add("05-concurrent-autosave-preserves-commit", _ -> PhantomAssertions.assertTrue(_autosaveExact, "Concurrent autosave overwrote committed SP."));
		registry.add("06-postcommit-failure-enters-fail-stop", _ -> PhantomAssertions.assertTrue(_failStop, "Postcommit durable-read failure did not fail-stop progression."));
		registry.add("07-fail-stop-committed-state-reloads", _ -> PhantomAssertions.assertTrue(_failStopReload, "Fail-stop committed state did not reload from DB."));
		registry.add("08-subclass-row-isolation", _ -> PhantomAssertions.assertTrue(_subclassSuccess, "Subclass SP/skill rows were not isolated from main class."));
		registry.add("09-main-subclass-main-restart-proof", _ -> PhantomAssertions.assertTrue(_subclassReload, "Main/subclass/main reload state was not exact."));
		registry.add("10-all-precommit-faults-roll-back", _ -> PhantomAssertions.assertTrue(_faultMatrix, "At least one injected precommit stage changed runtime or DB."));
		registry.add("11-durable-conflicts-are-typed", _ -> PhantomAssertions.assertTrue(_typedConflicts, "SP, skill, item or active-class conflict was not typed."));
		registry.add("12-exact-item-object-commits-once", _ -> PhantomAssertions.assertTrue(_exactItemSuccess, "Exact required item object did not reconcile with committed DB."));
		registry.add("13-success-only-event-count", _ -> PhantomAssertions.assertTrue(_eventCountExact, "Skill-learn event count or postcondition ordering changed."));
		registry.add("14-operation-and-lease-counts-drain", _ -> PhantomAssertions.assertTrue(_noLeaks, "Durability suite leaked an operation or actor lease."));
		registry.add("15-bounded-durability-runtime", _ -> PhantomAssertions.assertTrue(_elapsedMillis <= 120_000, "Durability operation matrix exceeded 120 seconds."));
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup();
	}

	private void exerciseMainSuccessAndReload() throws Exception
	{
		_player.getStat().setLevel((byte) 20);
		final Trainer trainer = createTrainer();
		final SkillLearn learn = nextLearn(true);
		final int cost = learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
		_player.setSp(cost + 1301L);
		_player.storeMe();
		final long expectedSp = _player.getSp() - cost;
		final LearnSkillRequest request = request(trainer, learn);
		final OperationResult result = _progression.learnClassSkill(request);
		final OperationResult repeated = _progression.learnClassSkill(request);
		awaitEventCount(1);
		_mainSuccess = (result.status() == OperationStatus.SUCCESS) && (readSp(0) == expectedSp) && (readSkillLevel(learn.getSkillId(), 0) == learn.getSkillLevel()) && (_player.getSp() == expectedSp) && (_player.getSkillLevel(learn.getSkillId()) == learn.getSkillLevel());
		_idempotent = (repeated.status() == OperationStatus.IDEMPOTENT) && (repeated.spBefore() == repeated.spAfter()) && (readSp(0) == expectedSp);
		reload();
		_mainReload = (_player.getSp() == expectedSp) && (_player.getSkillLevel(learn.getSkillId()) == learn.getSkillLevel());
	}

	private void exerciseConcurrentAutosave() throws Exception
	{
		stopProgression();
		final CountDownLatch beforeCommit = new CountDownLatch(1);
		final CountDownLatch continueCommit = new CountDownLatch(1);
		final AtomicBoolean blocked = new AtomicBoolean();
		final PhantomClassSkillLearningTransaction transaction = new PhantomClassSkillLearningTransaction(point ->
		{
			if ((point == FaultPoint.BEFORE_COMMIT) && blocked.compareAndSet(false, true))
			{
				beforeCommit.countDown();
				awaitLatch(continueCommit, "Concurrent durability transaction was not released.");
			}
		});
		startProgression(transaction);
		final Trainer trainer = createTrainer();
		final SkillLearn learn = nextLearn(true);
		final int cost = learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
		_player.setSp(cost + 2302L);
		_player.storeMe();
		final long expectedSp = _player.getSp() - cost;
		final LearnSkillRequest request = request(trainer, learn);
		final CompletableFuture<OperationResult> first = CompletableFuture.supplyAsync(() -> _progression.learnClassSkill(request));
		PhantomAssertions.assertTrue(beforeCommit.await(10, TimeUnit.SECONDS), "Concurrent transaction did not reach BEFORE_COMMIT.");
		final CompletableFuture<Void> autosave = CompletableFuture.runAsync(_player::storeMe);
		final OperationResult competing = _progression.learnClassSkill(request);
		continueCommit.countDown();
		final OperationResult committed = first.get(15, TimeUnit.SECONDS);
		autosave.get(15, TimeUnit.SECONDS);
		final OperationResult repeated = _progression.learnClassSkill(request);
		awaitEventCount(2);
		_concurrencyExact = (committed.status() == OperationStatus.SUCCESS) && (competing.status() == OperationStatus.OPERATION_IN_PROGRESS) && (repeated.status() == OperationStatus.IDEMPOTENT) && (readSkillLevel(learn.getSkillId(), 0) == learn.getSkillLevel()) && (readSp(0) == expectedSp);
		_autosaveExact = (_player.getSp() == expectedSp) && (readSp(0) == expectedSp);
	}

	private void exercisePostCommitFailStopAndReload() throws Exception
	{
		stopProgression();
		startProgression(new PhantomClassSkillLearningTransaction(point ->
		{
			if (point == FaultPoint.BEFORE_POSTCONDITION_READ)
			{
				throw new InjectedFailure(point);
			}
		}));
		final Trainer trainer = createTrainer();
		final SkillLearn learn = nextLearn(true);
		final int cost = learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
		_player.setSp(cost + 3303L);
		_player.storeMe();
		final long expectedSp = _player.getSp() - cost;
		final LearnSkillRequest request = request(trainer, learn);
		final OperationResult result = _progression.learnClassSkill(request);
		final int eventsBeforeRejectedRetry = _skillLearnEvents.get();
		final OperationResult rejected = _progression.learnClassSkill(request);
		_failStop = (result.status() == OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED) && (_progression.snapshot().state() == PhantomProgressionService.State.FAILED) && (rejected.status() == OperationStatus.SERVICE_NOT_RUNNING) && (_skillLearnEvents.get() == eventsBeforeRejectedRetry) && (readSp(0) == expectedSp) && (readSkillLevel(learn.getSkillId(), 0) == learn.getSkillLevel());
		stopProgression();
		reload();
		_failStopReload = (_player.getSp() == expectedSp) && (_player.getSkillLevel(learn.getSkillId()) == learn.getSkillLevel());
		startProgression(new PhantomClassSkillLearningTransaction());
	}

	private void exerciseSubclassSuccessAndReload() throws Exception
	{
		final long mainSpBefore = readSp(0);
		PhantomAssertions.assertTrue(_player.addSubClass(SUBCLASS_ID, SUBCLASS_INDEX), "Could not add real test subclass.");
		_player.setActiveClass(SUBCLASS_INDEX);
		_player.getStat().setLevel((byte) 74);
		final Trainer trainer = createTrainer();
		final SkillLearn learn = nextLearn(true);
		final int cost = learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
		_player.setSp(cost + 4304L);
		_player.storeMe();
		final long expectedSubclassSp = _player.getSp() - cost;
		final OperationResult result = _progression.learnClassSkill(request(trainer, learn));
		awaitEventCount(3);
		_subclassSuccess = (result.status() == OperationStatus.SUCCESS) && (readSp(SUBCLASS_INDEX) == expectedSubclassSp) && (readSp(0) == mainSpBefore) && (readSkillLevel(learn.getSkillId(), SUBCLASS_INDEX) == learn.getSkillLevel());
		reload();
		final boolean subclassReload = (_player.getClassIndex() == SUBCLASS_INDEX) && (_player.getSp() == expectedSubclassSp) && (_player.getSkillLevel(learn.getSkillId()) == learn.getSkillLevel());
		_player.setActiveClass(0);
		final boolean mainReload = (_player.getClassIndex() == 0) && (_player.getSp() == mainSpBefore);
		_player.setActiveClass(SUBCLASS_INDEX);
		final boolean subclassAgain = (_player.getClassIndex() == SUBCLASS_INDEX) && (_player.getSp() == expectedSubclassSp) && (_player.getSkillLevel(learn.getSkillId()) == learn.getSkillLevel());
		_player.setActiveClass(0);
		_subclassReload = subclassReload && mainReload && subclassAgain;
	}

	private void exerciseFaultMatrixAndExactItem() throws Exception
	{
		final ClassSkillWithItem fixture = findClassSkillWithOneItem();
		_player.setPlayerClass(fixture.playerClass().getId());
		final Skill skill = SkillData.getInstance().getSkill(fixture.learn().getSkillId(), fixture.learn().getSkillLevel());
		if (fixture.learn().getSkillLevel() > 1)
		{
			final Skill previous = SkillData.getInstance().getSkill(fixture.learn().getSkillId(), fixture.learn().getSkillLevel() - 1);
			PhantomAssertions.assertTrue(previous != null, "Exact item fixture previous skill level is unavailable.");
			_player.addSkill(previous, true);
		}
		final ItemHolder itemCost = fixture.learn().getRequiredItems().iterator().next();
		final Item exactItem = _player.getInventory().addItem(ItemProcessType.REWARD, itemCost.getId(), itemCost.getCount(), _player, this);
		PhantomAssertions.assertTrue(exactItem != null, "Could not create exact durable item fixture.");
		final RequiredItem requiredItem = new RequiredItem(itemCost.getId(), itemCost.getCount());
		final int previousLevel = _player.getSkillLevel(skill.getId());
		PhantomAssertions.assertEquals(fixture.learn().getSkillLevel() - 1, previousLevel, "Exact item fixture previous level is not exact.");
		final int spCost = 17;
		_player.setSp(5305L);
		_player.storeMe();
		final long spBefore = _player.getSp();
		final long itemBefore = exactItem.getCount();
		final Skill previous = SkillData.getInstance().getSkill(skill.getId(), previousLevel);
		exerciseConflictMatrix(skill, previous, previousLevel, spCost, exactItem, requiredItem, spBefore, itemBefore);
		final FaultPoint[] points =
		{
			FaultPoint.BEFORE_ITEM_SQL,
			FaultPoint.AFTER_ITEM_SQL,
			FaultPoint.BEFORE_SP_SQL,
			FaultPoint.AFTER_SP_SQL,
			FaultPoint.BEFORE_SKILL_SQL,
			FaultPoint.AFTER_SKILL_SQL,
			FaultPoint.BEFORE_COMMIT
		};
		boolean allRolledBack = true;
		for (FaultPoint point : points)
		{
			final PhantomClassSkillLearningTransaction transaction = new PhantomClassSkillLearningTransaction(current ->
			{
				if (current == point)
				{
					throw new InjectedFailure(current);
				}
			});
			final OperationResult result = transaction.execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem);
			allRolledBack &= (result.status() == OperationStatus.BACKEND_FAILURE) && (_player.getSp() == spBefore) && (_player.getSkillLevel(skill.getId()) == previousLevel) && (exactItem.getCount() == itemBefore) && (readSp(0) == spBefore) && (readSkillLevel(skill.getId(), 0) == previousLevel) && (readItemCount(exactItem.getObjectId()) == itemBefore);
		}
		_faultMatrix = allRolledBack;
		final OperationResult success = new PhantomClassSkillLearningTransaction().execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem);
		final long expectedItemCount = itemBefore - requiredItem.count();
		_exactItemSuccess = (success.status() == OperationStatus.SUCCESS) && (_player.getSp() == (spBefore - spCost)) && (readSp(0) == (spBefore - spCost)) && (_player.getSkillLevel(skill.getId()) == skill.getLevel()) && (readSkillLevel(skill.getId(), 0) == skill.getLevel()) && (readItemCount(exactItem.getObjectId()) == expectedItemCount);
	}

	private void exerciseConflictMatrix(Skill skill, Skill previous, int previousLevel, int spCost, Item exactItem, RequiredItem requiredItem, long spBefore, long itemBefore) throws Exception
	{
		final PhantomClassSkillLearningTransaction transaction = new PhantomClassSkillLearningTransaction();
		executeUpdate("UPDATE characters SET sp = ? WHERE charId = ?", spBefore + 1, _player.getObjectId());
		final boolean spConflict = transaction.execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem).status() == OperationStatus.DURABLE_SP_STATE_CONFLICT;
		executeUpdate("UPDATE characters SET sp = ? WHERE charId = ?", spBefore, _player.getObjectId());

		executeUpdate("UPDATE character_skills SET skill_level = ? WHERE charId = ? AND skill_id = ? AND class_index = 0", skill.getLevel(), _player.getObjectId(), skill.getId());
		final boolean skillConflict = transaction.execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem).status() == OperationStatus.DURABLE_SKILL_STATE_CONFLICT;
		executeUpdate("UPDATE character_skills SET skill_level = ? WHERE charId = ? AND skill_id = ? AND class_index = 0", previousLevel, _player.getObjectId(), skill.getId());

		executeUpdate("UPDATE items SET count = ? WHERE object_id = ?", itemBefore + 1, exactItem.getObjectId());
		final boolean itemConflict = transaction.execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem).status() == OperationStatus.DURABLE_ITEM_STATE_CONFLICT;
		executeUpdate("UPDATE items SET count = ? WHERE object_id = ?", itemBefore, exactItem.getObjectId());

		final int activeClass = _player.getActiveClass();
		final int contradictoryClass = activeClass == 0 ? 1 : 0;
		executeUpdate("UPDATE characters SET classid = ? WHERE charId = ?", contradictoryClass, _player.getObjectId());
		final boolean classConflict = transaction.execute(_player, skill, null, previousLevel, spCost, exactItem, requiredItem).status() == OperationStatus.DURABLE_SP_STATE_CONFLICT;
		executeUpdate("UPDATE characters SET classid = ? WHERE charId = ?", activeClass, _player.getObjectId());

		_player.addSkill(skill, false);
		final boolean runtimeDbConflict = transaction.execute(_player, skill, null, skill.getLevel(), 0, null, null).status() == OperationStatus.DURABLE_SKILL_STATE_CONFLICT;
		_player.addSkill(previous, false);
		_typedConflicts = spConflict && skillConflict && itemConflict && classConflict && runtimeDbConflict && (readSp(0) == spBefore) && (readSkillLevel(skill.getId(), 0) == previousLevel) && (readItemCount(exactItem.getObjectId()) == itemBefore);
	}

	private SkillLearn nextLearn(boolean noRequiredItem)
	{
		return SkillTreeData.getInstance().getAvailableSkills(_player, _player.getLearningClass(), false, false).stream().filter(SkillLearn::isLearnedByNpc).filter(learn -> !noRequiredItem || learn.getRequiredItems().isEmpty()).filter(learn -> learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass()) > 0).min(Comparator.comparingInt(SkillLearn::getSkillId).thenComparingInt(SkillLearn::getSkillLevel)).orElseThrow(() -> new AssertionError("No real zero-item CLASS SkillLearn is available for class " + _player.getActiveClass() + "."));
	}

	private ClassSkillWithItem findClassSkillWithOneItem()
	{
		return java.util.Arrays.stream(PlayerClass.values()).sorted(Comparator.comparingInt(PlayerClass::getId)).flatMap(playerClass -> SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream().map(learn -> new ClassSkillWithItem(playerClass, learn))).filter(value -> value.learn().isLearnedByNpc()).filter(value -> value.learn().getRequiredItems().size() == 1).filter(value -> _player.getSkillLevel(value.learn().getSkillId()) < value.learn().getSkillLevel()).filter(value -> SkillData.getInstance().getSkill(value.learn().getSkillId(), value.learn().getSkillLevel()) != null).filter(value -> (value.learn().getSkillLevel() == 1) || (SkillData.getInstance().getSkill(value.learn().getSkillId(), value.learn().getSkillLevel() - 1) != null)).findFirst().orElseThrow(() -> new AssertionError("No real CLASS SkillLearn with one required item exists."));
	}

	private LearnSkillRequest request(Trainer trainer, SkillLearn learn)
	{
		_player.setLastFolkNPC(trainer);
		return new LearnSkillRequest(_profile.profileId(), trainer.getObjectId(), AcquireKind.CLASS, learn.getSkillId(), learn.getSkillLevel(), () -> false);
	}

	private Trainer createTrainer()
	{
		for (NpcTemplate template : NpcData.getInstance().getTemplates(value -> "Trainer".equals(value.getType())))
		{
			final Trainer trainer = new Trainer(template);
			if (template.canTeach(_player.getLearningClass()))
			{
				trainer.setInstanceId(0);
				trainer.spawnMe(_player.getX() + 10, _player.getY(), _player.getZ());
				trainer.addListener(new ConsumerEventListener(trainer, EventType.ON_PLAYER_SKILL_LEARN, (OnPlayerSkillLearn event) ->
				{
					_skillLearnEvents.incrementAndGet();
					try
					{
						final int classIndex = event.getPlayer().getClassIndex();
						_eventsAfterPostconditions.compareAndSet(true, (event.getPlayer().getSkillLevel(event.getSkill().getId()) == event.getSkill().getLevel()) && (readSkillLevel(event.getPlayer().getObjectId(), event.getSkill().getId(), classIndex) == event.getSkill().getLevel()));
					}
					catch (Exception failure)
					{
						_eventsAfterPostconditions.set(false);
					}
				}, this));
				_trainers.add(trainer);
				return trainer;
			}
			trainer.deleteMe();
		}
		throw new AssertionError("No real trainer can teach the active class.");
	}

	private void startProgression(PhantomClassSkillLearningTransaction transaction)
	{
		_progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _knowledge.query(), transaction), PhantomProgressionPolicy.productionDefaults());
		_progression.start();
	}

	private void stopProgression()
	{
		if (_progression == null)
		{
			return;
		}
		_progression.beginStop();
		PhantomAssertions.assertTrue(_progression.finishStop(), "Durability progression service did not stop.");
		_progression = null;
	}

	private void reload()
	{
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Durability actor did not dematerialize.");
		materialize();
	}

	private void materialize()
	{
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Durability actor did not materialize.");
		_player = World.getInstance().getPlayer(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Durability materialized Player is absent.");
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(-71338, 258271, -3104);
		_player.spawnMe();
		_player.revalidateZone(true);
	}

	private long readSp(int classIndex) throws Exception
	{
		final String sql = classIndex == 0 ? "SELECT sp FROM characters WHERE charId = ?" : "SELECT sp FROM character_subclasses WHERE charId = ? AND class_index = ?";
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setInt(1, _player.getObjectId());
			if (classIndex > 0)
			{
				statement.setInt(2, classIndex);
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Durable SP row is missing.");
				final long value = result.getLong(1);
				PhantomAssertions.assertFalse(result.next(), "Durable SP identity is not unique.");
				return value;
			}
		}
	}

	private int readSkillLevel(int skillId, int classIndex) throws Exception
	{
		return readSkillLevel(_player.getObjectId(), skillId, classIndex);
	}

	private static int readSkillLevel(int characterObjectId, int skillId, int classIndex) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT skill_level FROM character_skills WHERE charId = ? AND skill_id = ? AND class_index = ?"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, skillId);
			statement.setInt(3, classIndex);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return -1;
				}
				final int value = result.getInt(1);
				PhantomAssertions.assertFalse(result.next(), "Durable skill identity is not unique.");
				return value;
			}
		}
	}

	private long readItemCount(int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT count FROM items WHERE object_id = ?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return 0;
				}
				final long value = result.getLong(1);
				PhantomAssertions.assertFalse(result.next(), "Durable item object identity is not unique.");
				return value;
			}
		}
	}

	private static void executeUpdate(String sql, Object... values) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < values.length; index++)
			{
				statement.setObject(index + 1, values[index]);
			}
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Controlled durability conflict fixture did not update exactly one row.");
		}
	}

	private static void awaitLatch(CountDownLatch latch, String message)
	{
		try
		{
			if (!latch.await(15, TimeUnit.SECONDS))
			{
				throw new IllegalStateException(message);
			}
		}
		catch (InterruptedException failure)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException(message, failure);
		}
	}

	private void awaitEventCount(int expected) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while ((_skillLearnEvents.get() < expected) && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertEquals(expected, _skillLearnEvents.get(), "Unexpected OnPlayerSkillLearn event count.");
	}

	private void cleanup() throws Exception
	{
		if (_cleaned)
		{
			return;
		}
		_cleaned = true;
		Throwable failure = null;
		try
		{
			stopProgression();
			for (Trainer trainer : List.copyOf(_trainers))
			{
				if (trainer.isSpawned())
				{
					trainer.deleteMe();
				}
			}
			_trainers.clear();
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, _materialization.shutdown().state(), "Durability materialization service did not stop.");
			}
			if ((_repository != null) && (_profile != null))
			{
				_repository.find(_profile.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Durability knowledge service did not stop.");
			}
			if (_player != null)
			{
				_environment.assertClean(_environment.primary(), _player);
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	private record ClassSkillWithItem(PlayerClass playerClass, SkillLearn learn)
	{
	}

	private static final class InjectedFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		InjectedFailure(FaultPoint point)
		{
			super(point.name());
		}
	}
}
