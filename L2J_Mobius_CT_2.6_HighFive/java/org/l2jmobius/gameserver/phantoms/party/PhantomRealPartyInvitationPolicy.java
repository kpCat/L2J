/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationContext;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationDecision;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationPolicy;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/** Bounded consent for a real player's delivered invitation; canonical legality remains native. */
public final class PhantomRealPartyInvitationPolicy implements ManagedInvitationPolicy
{
	public static final String POLICY_KEY = "party.real.invitation";

	public record Settings(boolean enabled, int minimumPreference)
	{
		public Settings
		{
			if ((minimumPreference < -3000) || (minimumPreference > 3000))
			{
				throw new IllegalArgumentException("Real party invitation preference is outside social catalog bounds.");
			}
		}
	}

	private final Settings _settings;
	private final PhantomSocialService _social;
	private final LongSupplier _epochMinute;

	public PhantomRealPartyInvitationPolicy(Settings settings, PhantomSocialService social, LongSupplier epochMinute)
	{
		_settings = Objects.requireNonNull(settings);
		_social = Objects.requireNonNull(social);
		_epochMinute = Objects.requireNonNull(epochMinute);
	}

	public static Settings loadSettings(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length < 1) || (bytes.length > 4096) || Files.isSymbolicLink(path))
			{
				throw new IllegalArgumentException("Real party invitation policy file is invalid.");
			}
			StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			final Set<String> attributes = Set.of("id", "version", "enabled", "minimumPreference");
			if (!root.getTagName().equals("realPartyInvitationPolicy") || !root.getAttribute("id").equals("high-five-real-party-invitation-v1") || !root.getAttribute("version").equals("1") || (root.getAttributes().getLength() != attributes.size()) || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(index -> !attributes.contains(root.getAttributes().item(index).getNodeName())) || java.util.stream.IntStream.range(0, root.getChildNodes().getLength()).anyMatch(index -> root.getChildNodes().item(index) instanceof Element) || !root.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Real party invitation policy identity or shape is invalid.");
			}
			final String enabled = root.getAttribute("enabled");
			final String minimum = root.getAttribute("minimumPreference");
			if ((!enabled.equals("true") && !enabled.equals("false")) || !minimum.matches("-?[0-9]+"))
			{
				throw new IllegalArgumentException("Real party invitation policy values are invalid.");
			}
			return new Settings(Boolean.parseBoolean(enabled), Integer.parseInt(minimum));
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load real party invitation policy.", exception);
		}
	}

	@Override
	public ManagedInvitationDecision evaluate(ManagedInvitationContext context)
	{
		if (!_settings.enabled() || (context.requesterProfileId() != 0) || (context.inviteeSnapshot() == null) || context.inviteeSnapshot().dead() || (context.inviteeSnapshot().ref().kind() != MemberKind.PHANTOM) || (context.inviteeSnapshot().ref().profileId() != context.inviteeProfileId()) || (context.inviteeSnapshot().ref().characterObjectId() != context.invitation().inviteeObjectId()) || ((context.inviteeClaim() != null) && (context.inviteeClaim().status() != StateStatus.SOLO)) || !ordinaryGoal(context.inviteeGoal()))
		{
			return ManagedInvitationDecision.REFUSE;
		}
		final var preference = _social.modifier(context.inviteeProfileId(), SubjectRef.character(context.invitation().requesterObjectId()), "party.invite.preference", _epochMinute.getAsLong());
		if (!preference.available() || (preference.value() == null))
		{
			return ManagedInvitationDecision.DEFER;
		}
		return preference.value().deltaBasisPoints() >= _settings.minimumPreference() ? ManagedInvitationDecision.ACCEPT : ManagedInvitationDecision.REFUSE;
	}

	private static boolean ordinaryGoal(PhantomGoal goal)
	{
		if (goal == null)
		{
			return true;
		}
		if ((goal.status() != PhantomGoalStatus.ACTIVE) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			return false;
		}
		try
		{
			PhantomBackgroundGoalSpec.parse(goal);
			return true;
		}
		catch (IllegalArgumentException exception)
		{
			return false;
		}
	}
}
