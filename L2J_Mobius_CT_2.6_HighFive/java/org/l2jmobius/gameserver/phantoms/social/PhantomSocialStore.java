/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.StoredState;

/**
 * Optimistic social.state adapter over the existing bounded profile component.
 */
public final class PhantomSocialStore implements PersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomSocialCatalog _catalog;
	private final PhantomSocialStateCodec _codec = new PhantomSocialStateCodec();

	public PhantomSocialStore(PhantomProfileRepository profiles, PhantomSocialCatalog catalog)
	{
		_profiles = Objects.requireNonNull(profiles);
		_catalog = Objects.requireNonNull(catalog);
	}

	@Override
	public boolean profileExists(long profileId)
	{
		return _profiles.find(profileId).isPresent();
	}

	@Override
	public Optional<StoredState> load(long profileId)
	{
		final Optional<PhantomProfileComponent> state = _profiles.findComponent(profileId, PhantomSocialModel.COMPONENT_TYPE);
		final Optional<PhantomProfileComponent> receipts = _profiles.findComponent(profileId, PhantomSocialReceiptLedger.COMPONENT_TYPE);
		if (state.isEmpty() && receipts.isEmpty())
		{
			return Optional.empty();
		}
		if (state.isEmpty() || receipts.isEmpty())
		{
			throw new IllegalArgumentException("Social state and receipt components must exist together.");
		}
		return Optional.of(decode(state.get(), receipts.get()));
	}

	@Override
	public StoredState save(long profileId, long expectedStateRowVersion, long expectedReceiptRowVersion, SocialState state, PhantomSocialReceiptLedger receipts)
	{
		_catalog.validateState(state);
		validateReceipts(receipts);
		final List<PhantomProfileComponent> components = _profiles.mutateComponentsAtomically(profileId, List.of( //
			new ComponentMutation(PhantomSocialReceiptLedger.COMPONENT_TYPE, expectedReceiptRowVersion, PhantomSocialReceiptLedger.SCHEMA_VERSION, receipts.encode()), //
			new ComponentMutation(PhantomSocialModel.COMPONENT_TYPE, expectedStateRowVersion, PhantomSocialModel.SCHEMA_VERSION, _codec.encode(state))));
		return decode(components.get(1), components.get(0));
	}

	private StoredState decode(PhantomProfileComponent stateComponent, PhantomProfileComponent receiptComponent)
	{
		if ((stateComponent.componentSchemaVersion() != PhantomSocialModel.SCHEMA_VERSION) || (receiptComponent.componentSchemaVersion() != PhantomSocialReceiptLedger.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown social component schema version.");
		}
		if ((stateComponent.profileId() != receiptComponent.profileId()) || !stateComponent.componentType().equals(PhantomSocialModel.COMPONENT_TYPE) || !receiptComponent.componentType().equals(PhantomSocialReceiptLedger.COMPONENT_TYPE))
		{
			throw new IllegalArgumentException("Social component identity is inconsistent.");
		}
		final SocialState state = _codec.decode(stateComponent.payload());
		final PhantomSocialReceiptLedger receipts = PhantomSocialReceiptLedger.decode(receiptComponent.payload());
		_catalog.validateState(state);
		validateReceipts(receipts);
		return new StoredState(stateComponent.profileId(), stateComponent.rowVersion(), receiptComponent.rowVersion(), state, receipts);
	}

	private void validateReceipts(PhantomSocialReceiptLedger receipts)
	{
		Objects.requireNonNull(receipts);
		receipts.receipts().forEach(receipt -> _catalog.requireEvent(receipt.eventCode()));
	}
}
