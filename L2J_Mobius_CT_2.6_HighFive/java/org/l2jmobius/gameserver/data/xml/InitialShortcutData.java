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
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.data.xml;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.IXmlReader;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.MacroType;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.enums.player.ShortcutType;
import org.l2jmobius.gameserver.model.actor.holders.player.Macro;
import org.l2jmobius.gameserver.model.actor.holders.player.MacroCmd;
import org.l2jmobius.gameserver.model.actor.holders.player.Shortcut;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.ShortcutRegister;

/**
 * Manages initial shortcut and macro configurations for new player characters.<br>
 * Loads configuration data from XML files and provides shortcuts based on player class.
 * <ul>
 * <li>Class-specific shortcut configurations for different player classes.</li>
 * <li>Global shortcut settings available to all players.</li>
 * <li>Macro preset definitions with commands and parameters.</li>
 * <li>Automatic shortcut registration for skills, items, and macros.</li>
 * </ul>
 * @author Zoey76, Mobius
 */
public class InitialShortcutData implements IXmlReader
{
	private static final Logger LOGGER = Logger.getLogger(InitialShortcutData.class.getName());
	
	// Shortcut Data Storage.
	private final Map<PlayerClass, List<Shortcut>> _initialShortcutData = new EnumMap<>(PlayerClass.class);
	private final List<Shortcut> _initialGlobalShortcutList = new ArrayList<>();
	
	// Macro Configuration.
	private final Map<Integer, Macro> _macroPresets = new HashMap<>();

	public enum DeliveryMode
	{
		CLIENT,
		POPULATION
	}
	
	protected InitialShortcutData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_initialShortcutData.clear();
		_initialGlobalShortcutList.clear();
		
		parseDatapackFile("data/stats/players/initialShortcuts.xml");
		
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _initialGlobalShortcutList.size() + " initial global shortcut data.");
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _initialShortcutData.size() + " initial shortcut data.");
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _macroPresets.size() + " macro presets.");
	}
	
	@Override
	public void parseDocument(Document document, File file)
	{
		for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if ("list".equals(node.getNodeName()))
			{
				for (Node dataNode = node.getFirstChild(); dataNode != null; dataNode = dataNode.getNextSibling())
				{
					switch (dataNode.getNodeName())
					{
						case "shortcuts":
						{
							NamedNodeMap attributes = dataNode.getAttributes();
							final Node classIdNode = attributes.getNamedItem("classId");
							final List<Shortcut> shortcutList = new ArrayList<>();
							
							for (Node childNode = dataNode.getFirstChild(); childNode != null; childNode = childNode.getNextSibling())
							{
								if ("page".equals(childNode.getNodeName()))
								{
									attributes = childNode.getAttributes();
									final int pageId = parseInteger(attributes, "pageId");
									for (Node slotNode = childNode.getFirstChild(); slotNode != null; slotNode = slotNode.getNextSibling())
									{
										if ("slot".equals(slotNode.getNodeName()))
										{
											final NamedNodeMap slotAttributes = slotNode.getAttributes();
											final int slotId = parseInteger(slotAttributes, "slotId");
											final ShortcutType shortcutType = parseEnum(slotAttributes, ShortcutType.class, "shortcutType");
											final int shortcutId = parseInteger(slotAttributes, "shortcutId");
											final int shortcutLevel = parseInteger(slotAttributes, "shortcutLevel", 0);
											shortcutList.add(new Shortcut(slotId, pageId, shortcutType, shortcutId, shortcutLevel, 0));
										}
									}
								}
							}
							
							if (classIdNode != null)
							{
								_initialShortcutData.put(PlayerClass.getPlayerClass(Integer.parseInt(classIdNode.getNodeValue())), shortcutList);
							}
							else
							{
								_initialGlobalShortcutList.addAll(shortcutList);
							}
							break;
						}
						case "macros":
						{
							for (Node childNode = dataNode.getFirstChild(); childNode != null; childNode = childNode.getNextSibling())
							{
								if ("macro".equals(childNode.getNodeName()))
								{
									NamedNodeMap attributes = childNode.getAttributes();
									if (!parseBoolean(attributes, "enabled", true))
									{
										continue;
									}
									
									final int macroId = parseInteger(attributes, "macroId");
									final int icon = parseInteger(attributes, "icon");
									final String name = parseString(attributes, "name");
									final String description = parseString(attributes, "description");
									final String acronym = parseString(attributes, "acronym");
									final List<MacroCmd> commands = new ArrayList<>(1);
									int entryIndex = 0;
									
									for (Node commandNode = childNode.getFirstChild(); commandNode != null; commandNode = commandNode.getNextSibling())
									{
										if ("command".equals(commandNode.getNodeName()))
										{
											attributes = commandNode.getAttributes();
											final MacroType type = parseEnum(attributes, MacroType.class, "type");
											int parameterOne = 0;
											int parameterTwo = 0;
											final String commandText = commandNode.getTextContent();
											
											switch (type)
											{
												case SKILL:
												{
													parameterOne = parseInteger(attributes, "skillId"); // Skill ID.
													parameterTwo = parseInteger(attributes, "skillLevel", 0); // Skill level.
													break;
												}
												case ACTION:
												{
													parameterOne = parseInteger(attributes, "actionId"); // Not handled by client.
													break;
												}
												case TEXT:
												{
													// Text commands have no numeric parameters.
													break;
												}
												case SHORTCUT:
												{
													parameterOne = parseInteger(attributes, "page"); // Page.
													parameterTwo = parseInteger(attributes, "slot", 0); // Slot.
													break;
												}
												case ITEM:
												{
													parameterOne = parseInteger(attributes, "itemId"); // Not handled by client.
													break;
												}
												case DELAY:
												{
													parameterOne = parseInteger(attributes, "delay"); // Delay in seconds.
													break;
												}
											}
											
											commands.add(new MacroCmd(entryIndex++, type, parameterOne, parameterTwo, commandText));
										}
									}
									
									_macroPresets.put(macroId, new Macro(macroId, icon, name, description, acronym, commands));
								}
							}
							break;
						}
					}
				}
			}
		}
	}
	
	/**
	 * Registers all available shortcuts for the specified player including global and class-specific shortcuts.<br>
	 * Validates item availability, skill knowledge, and macro definitions before registration.
	 * @param player the {@link Player} for whom to register the shortcuts.
	 */
	public void registerAllShortcuts(Player player)
	{
		registerAllShortcuts(player, DeliveryMode.CLIENT);
	}

	/**
	 * Resolves and durably registers the same initial plan for both creation
	 * authorities. Only CLIENT mode publishes the legacy client packets.
	 * @param player the initialized player
	 * @param deliveryMode initial-plan delivery authority
	 */
	public void registerAllShortcuts(Player player, DeliveryMode deliveryMode)
	{
		if (player == null)
		{
			return;
		}
		Objects.requireNonNull(deliveryMode, "Initial shortcut delivery mode must not be null.");
		final InitialPlan plan = resolvePlan(
			player.getPlayerClass(),
			itemId -> player.getInventory().getItemByItemId(itemId) != null,
			skillId -> player.getSkills().containsKey(skillId));
		registerResolvedPlan(player, plan, deliveryMode);
	}

	/**
	 * Pure logical resolution. Item shortcuts retain their item template ID;
	 * the actual owned object ID is bound only while applying the plan.
	 * @param playerClass player class
	 * @param availableItems available owned item template IDs
	 * @param knownSkills known skill IDs
	 * @return immutable exact initial plan
	 */
	public InitialPlan resolvePlan(PlayerClass playerClass, IntPredicate availableItems, IntPredicate knownSkills)
	{
		Objects.requireNonNull(playerClass, "Player class must not be null.");
		Objects.requireNonNull(availableItems, "Available-item predicate must not be null.");
		Objects.requireNonNull(knownSkills, "Known-skill predicate must not be null.");
		final List<ShortcutPlan> shortcuts = new ArrayList<>();
		final Map<Integer, MacroPlan> macros = new LinkedHashMap<>();
		resolveInto(_initialGlobalShortcutList, availableItems, knownSkills, shortcuts, macros);
		resolveInto(_initialShortcutData.getOrDefault(playerClass, List.of()), availableItems, knownSkills, shortcuts, macros);
		return new InitialPlan(shortcuts, new ArrayList<>(macros.values()));
	}

	/**
	 * Applies a previously resolved plan. POPULATION mode performs only durable
	 * registration and never reaches Player.sendPacket.
	 * @param player player receiving the plan
	 * @param plan immutable resolved plan
	 * @param deliveryMode delivery authority
	 */
	public void registerResolvedPlan(Player player, InitialPlan plan, DeliveryMode deliveryMode)
	{
		registerResolvedPlan(player, plan, deliveryMode, RegistrationObserver.noop());
	}

	public void registerResolvedPlan(Player player, InitialPlan plan, DeliveryMode deliveryMode, RegistrationObserver observer)
	{
		registerResolvedPlan(player, plan, deliveryMode, Set.of(), observer);
	}

	public void registerResolvedPlan(Player player, InitialPlan plan, DeliveryMode deliveryMode, Set<Integer> existingMacroIds, RegistrationObserver observer)
	{
		Objects.requireNonNull(player, "Player must not be null.");
		Objects.requireNonNull(plan, "Initial shortcut plan must not be null.");
		Objects.requireNonNull(deliveryMode, "Initial shortcut delivery mode must not be null.");
		Objects.requireNonNull(existingMacroIds, "Existing macro IDs must not be null.");
		Objects.requireNonNull(observer, "Initial registration observer must not be null.");
		final Map<Integer, MacroPlan> macros = new LinkedHashMap<>();
		for (MacroPlan macro : plan.macros())
		{
			macros.put(macro.id(), macro);
		}
		final Set<Integer> registeredMacros = new LinkedHashSet<>(existingMacroIds);
		if (deliveryMode == DeliveryMode.POPULATION)
		{
			for (MacroPlan macro : plan.macros())
			{
				if (registeredMacros.add(macro.id()))
				{
					registerPopulationMacro(player, macro);
					observer.after(RegistrationKind.MACRO, macro.id());
				}
			}
		}
		for (ShortcutPlan shortcut : plan.shortcuts())
		{
			int shortcutId = shortcut.logicalId();
			switch (shortcut.type())
			{
				case ITEM:
				{
					final Item item = player.getInventory().getItemByItemId(shortcutId);
					if (item == null)
					{
						throw new IllegalStateException("Resolved initial item shortcut has no owned item.");
					}
					shortcutId = item.getObjectId();
					break;
				}
				case SKILL:
				{
					if (!player.getSkills().containsKey(shortcutId))
					{
						throw new IllegalStateException("Resolved initial skill shortcut has no known skill.");
					}
					break;
				}
				case MACRO:
				{
					final MacroPlan macro = macros.get(shortcutId);
					if (macro == null)
					{
						throw new IllegalStateException("Resolved initial macro shortcut has no macro plan.");
					}
					if ((deliveryMode == DeliveryMode.CLIENT) && registeredMacros.add(macro.id()))
					{
						player.registerMacro(macro.toMacro());
					}
					break;
				}
			}

			final Shortcut newShortcut = new Shortcut(shortcut.slot(), shortcut.page(), shortcut.type(), shortcutId, shortcut.level(), shortcut.characterType());
			if (deliveryMode == DeliveryMode.CLIENT)
			{
				player.sendPacket(new ShortcutRegister(newShortcut));
			}
			player.registerShortcut(newShortcut);
			if (deliveryMode == DeliveryMode.POPULATION)
			{
				observer.after(RegistrationKind.SHORTCUT, (shortcut.page() * 100) + shortcut.slot());
			}
		}
	}

	private void resolveInto(List<Shortcut> source, IntPredicate availableItems, IntPredicate knownSkills, List<ShortcutPlan> shortcuts, Map<Integer, MacroPlan> macros)
	{
		for (Shortcut shortcut : source)
		{
			switch (shortcut.getType())
			{
				case ITEM:
				{
					if (!availableItems.test(shortcut.getId()))
					{
						continue;
					}
					break;
				}
				case SKILL:
				{
					if (!knownSkills.test(shortcut.getId()))
					{
						continue;
					}
					break;
				}
				case MACRO:
				{
					final Macro macro = _macroPresets.get(shortcut.getId());
					if (macro == null)
					{
						continue;
					}
					macros.putIfAbsent(macro.getId(), MacroPlan.from(macro));
					break;
				}
			}
			shortcuts.add(ShortcutPlan.from(shortcut));
		}
	}

	private static void registerPopulationMacro(Player player, MacroPlan plan)
	{
		final Macro macro = plan.toMacro();
		if (player.getMacros().getAllMacroses().putIfAbsent(macro.getId(), macro) != null)
		{
			throw new IllegalStateException("Population initial macro already exists.");
		}
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_macroses (charId,id,icon,name,descr,acronym,commands) VALUES (?,?,?,?,?,?,?)"))
		{
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, macro.getId());
			statement.setInt(3, macro.getIcon());
			statement.setString(4, macro.getName());
			statement.setString(5, macro.getDescr());
			statement.setString(6, macro.getAcronym());
			statement.setString(7, plan.serializedCommands());
			if (statement.executeUpdate() != 1)
			{
				throw new IllegalStateException("Population initial macro insert did not affect one row.");
			}
		}
		catch (Exception e)
		{
			player.getMacros().getAllMacroses().remove(macro.getId(), macro);
			LOGGER.log(Level.WARNING, "Could not durably register population initial macro.", e);
			throw new IllegalStateException("Could not durably register population initial macro.", e);
		}
	}

	public record InitialPlan(List<ShortcutPlan> shortcuts, List<MacroPlan> macros)
	{
		public InitialPlan
		{
			shortcuts = List.copyOf(shortcuts);
			macros = List.copyOf(macros);
		}

		public InitialPlan subset(Set<ShortcutKey> shortcutKeys, Set<Integer> macroIds)
		{
			Objects.requireNonNull(shortcutKeys, "Shortcut keys must not be null.");
			Objects.requireNonNull(macroIds, "Macro IDs must not be null.");
			return new InitialPlan(
				shortcuts.stream().filter(shortcut -> shortcutKeys.contains(shortcut.key())).toList(),
				macros.stream().filter(macro -> macroIds.contains(macro.id())).toList());
		}
	}

	public record ShortcutPlan(int slot, int page, ShortcutType type, int logicalId, int level, int characterType)
	{
		public ShortcutPlan
		{
			Objects.requireNonNull(type, "Shortcut type must not be null.");
		}

		private static ShortcutPlan from(Shortcut shortcut)
		{
			return new ShortcutPlan(shortcut.getSlot(), shortcut.getPage(), shortcut.getType(), shortcut.getId(), shortcut.getLevel(), shortcut.getCharacterType());
		}

		public ShortcutKey key()
		{
			return new ShortcutKey(page, slot);
		}
	}

	public record ShortcutKey(int page, int slot)
	{
	}

	public record MacroPlan(int id, int icon, String name, String description, String acronym, List<MacroCommandPlan> commands)
	{
		public MacroPlan
		{
			name = Objects.requireNonNull(name);
			description = Objects.requireNonNull(description);
			acronym = Objects.requireNonNull(acronym);
			commands = List.copyOf(commands);
		}

		private static MacroPlan from(Macro macro)
		{
			return new MacroPlan(macro.getId(), macro.getIcon(), macro.getName(), macro.getDescr(), macro.getAcronym(), macro.getCommands().stream().map(MacroCommandPlan::from).toList());
		}

		public Macro toMacro()
		{
			return new Macro(id, icon, name, description, acronym, commands.stream().map(MacroCommandPlan::toMacroCommand).toList());
		}

		public String serializedCommands()
		{
			final StringBuilder serialized = new StringBuilder(300);
			for (MacroCommandPlan command : commands)
			{
				serialized.append(command.type().ordinal()).append(',').append(command.parameterOne()).append(',').append(command.parameterTwo());
				if (!command.command().isEmpty())
				{
					serialized.append(',').append(command.command());
				}
				serialized.append(';');
			}
			if (serialized.length() > 255)
			{
				serialized.setLength(255);
			}
			return serialized.toString();
		}
	}

	public record MacroCommandPlan(int entry, MacroType type, int parameterOne, int parameterTwo, String command)
	{
		public MacroCommandPlan
		{
			Objects.requireNonNull(type, "Macro command type must not be null.");
			command = command == null ? "" : command;
		}

		private static MacroCommandPlan from(MacroCmd command)
		{
			return new MacroCommandPlan(command.getEntry(), command.getType(), command.getD1(), command.getD2(), command.getCmd());
		}

		private MacroCmd toMacroCommand()
		{
			return new MacroCmd(entry, type, parameterOne, parameterTwo, command);
		}
	}

	public enum RegistrationKind
	{
		SHORTCUT,
		MACRO
	}

	@FunctionalInterface
	public interface RegistrationObserver
	{
		void after(RegistrationKind kind, int ordinal);

		static RegistrationObserver noop()
		{
			return (kind, ordinal) ->
			{
			};
		}
	}
	
	public static InitialShortcutData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final InitialShortcutData INSTANCE = new InitialShortcutData();
	}
}
