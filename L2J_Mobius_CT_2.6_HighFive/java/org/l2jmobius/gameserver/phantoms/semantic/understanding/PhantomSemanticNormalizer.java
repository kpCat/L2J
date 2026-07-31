/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic.understanding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.EvidenceQuality;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.NormalizedText;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.Token;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.TokenKind;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.LexicalAlias;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.NormalizationPolicy;

/**
 * Linear, locale-independent normalization with original code-point spans.
 */
public final class PhantomSemanticNormalizer
{
	public static final String EMPTY_HASH = sha256("");

	private PhantomSemanticNormalizer()
	{
	}

	public static NormalizedText normalize(String input, PhantomSemanticPack pack)
	{
		if (pack == null)
		{
			throw new IllegalArgumentException("Semantic pack must not be null.");
		}
		final BaseText base = base(input, pack.normalization(), pack.limits().maxInputCodePoints(), pack.limits().maxInputUtf8Bytes(), pack.limits().maxTokens(), pack.limits().maxTokenCodePoints());
		final ArrayList<Token> tokens = new ArrayList<>(base.tokens().size());
		for (BaseToken token : base.tokens())
		{
			final LexicalAlias alias = token.kind() == TokenKind.WORD ? pack.lexicalAlias(token.value()) : null;
			tokens.add(new Token(token.value(), alias == null ? token.value() : alias.target(), token.start(), token.end(), token.kind(), alias == null ? EvidenceQuality.EXACT : alias.quality()));
		}
		return new NormalizedText(base.value(), sha256(base.value()), tokens);
	}

	static List<String> packPhrase(String input, NormalizationPolicy policy, int maximumTokens, int maximumTokenCodePoints)
	{
		final BaseText base = base(input, policy, 256, 1024, maximumTokens, maximumTokenCodePoints);
		final ArrayList<String> result = new ArrayList<>(base.tokens().size());
		for (BaseToken token : base.tokens())
		{
			if (token.kind() == TokenKind.PUNCTUATION)
			{
				throw new IllegalArgumentException("Semantic pack phrases must not contain punctuation.");
			}
			result.add(token.value());
		}
		return List.copyOf(result);
	}

	private static BaseText base(String input, NormalizationPolicy policy, int maximumCodePoints, int maximumUtf8Bytes, int maximumTokens, int maximumTokenCodePoints)
	{
		if (input == null)
		{
			throw new Rejection("reject.unsupported");
		}
		validateSurrogates(input);
		final int codePoints = input.codePointCount(0, input.length());
		if ((codePoints > maximumCodePoints) || (input.getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes))
		{
			throw new Rejection("reject.too_long");
		}
		final List<Unit> units = normalizeUnits(input, policy);
		final ArrayList<BaseToken> tokens = new ArrayList<>();
		int index = 0;
		while (index < units.size())
		{
			final Unit unit = units.get(index);
			if (Character.isWhitespace(unit.codePoint()))
			{
				index++;
				continue;
			}
			if (isWord(unit.codePoint()))
			{
				final int startIndex = index;
				final StringBuilder value = new StringBuilder();
				int previous = -1;
				int repeated = 0;
				while ((index < units.size()) && isWord(units.get(index).codePoint()))
				{
					final int codePoint = units.get(index).codePoint();
					if (codePoint == previous)
					{
						repeated++;
					}
					else
					{
						previous = codePoint;
						repeated = 1;
					}
					if (repeated <= policy.repeatLimit())
					{
						value.appendCodePoint(codePoint);
					}
					index++;
				}
				validateScript(value.toString());
				if (value.codePointCount(0, value.length()) > maximumTokenCodePoints)
				{
					throw new Rejection("reject.too_long");
				}
				final Unit first = units.get(startIndex);
				final Unit last = units.get(index - 1);
				final TokenKind kind = value.codePoints().allMatch(Character::isDigit) ? TokenKind.NUMBER : TokenKind.WORD;
				tokens.add(new BaseToken(value.toString(), first.originalStart(), last.originalEnd(), kind));
			}
			else if (policy.punctuationCodePoints().contains(unit.codePoint()))
			{
				tokens.add(new BaseToken(new String(Character.toChars(unit.codePoint())), unit.originalStart(), unit.originalEnd(), TokenKind.PUNCTUATION));
				index++;
			}
			else
			{
				throw new Rejection("reject.unsupported");
			}
			if (tokens.size() > maximumTokens)
			{
				throw new Rejection("reject.too_long");
			}
		}
		if (tokens.isEmpty())
		{
			throw new Rejection("reject.unsupported");
		}
		final String value = String.join(" ", tokens.stream().map(BaseToken::value).toList());
		return new BaseText(value, List.copyOf(tokens));
	}

	private static void validateSurrogates(String input)
	{
		for (int index = 0; index < input.length(); index++)
		{
			final char value = input.charAt(index);
			if (Character.isHighSurrogate(value))
			{
				if (((index + 1) >= input.length()) || !Character.isLowSurrogate(input.charAt(index + 1)))
				{
					throw new Rejection("reject.unsupported");
				}
				index++;
			}
			else if (Character.isLowSurrogate(value))
			{
				throw new Rejection("reject.unsupported");
			}
		}
	}

	private static List<Unit> normalizeUnits(String input, NormalizationPolicy policy)
	{
		final ArrayList<Unit> result = new ArrayList<>();
		int charIndex = 0;
		int codePointIndex = 0;
		while (charIndex < input.length())
		{
			final int startChar = charIndex;
			final int startCodePoint = codePointIndex;
			int codePoint = input.codePointAt(charIndex);
			charIndex += Character.charCount(codePoint);
			codePointIndex++;
			while (charIndex < input.length())
			{
				final int following = input.codePointAt(charIndex);
				final int type = Character.getType(following);
				if ((type != Character.NON_SPACING_MARK) && (type != Character.COMBINING_SPACING_MARK) && (type != Character.ENCLOSING_MARK))
				{
					break;
				}
				charIndex += Character.charCount(following);
				codePointIndex++;
			}
			String cluster = input.substring(startChar, charIndex);
			cluster = Normalizer.normalize(cluster, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё', 'е');
			for (int offset = 0; offset < cluster.length();)
			{
				codePoint = cluster.codePointAt(offset);
				offset += Character.charCount(codePoint);
				codePoint = normalizedPunctuation(codePoint);
				validateCodePoint(codePoint);
				result.add(new Unit(codePoint, startCodePoint, codePointIndex));
			}
		}
		return List.copyOf(result);
	}

	private static int normalizedPunctuation(int codePoint)
	{
		return switch (codePoint)
		{
			case 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015, 0x2212, 0xfe58, 0xfe63, 0xff0d -> '-';
			case 0x2018, 0x2019, 0x201a, 0x201b, 0x2032, 0x2035 -> '\'';
			case 0x201c, 0x201d, 0x201e, 0x201f, 0x2033, 0x2036 -> '"';
			default -> codePoint;
		};
	}

	private static void validateCodePoint(int codePoint)
	{
		if (Character.isWhitespace(codePoint))
		{
			return;
		}
		final int type = Character.getType(codePoint);
		if ((type == Character.CONTROL) || (type == Character.FORMAT) || (type == Character.PRIVATE_USE) || (type == Character.SURROGATE) || (type == Character.UNASSIGNED))
		{
			throw new Rejection("reject.unsupported");
		}
	}

	private static boolean isWord(int codePoint)
	{
		final int type = Character.getType(codePoint);
		return Character.isLetterOrDigit(codePoint) || (type == Character.NON_SPACING_MARK) || (type == Character.COMBINING_SPACING_MARK);
	}

	private static void validateScript(String value)
	{
		boolean latin = false;
		boolean cyrillic = false;
		for (int codePoint : value.codePoints().toArray())
		{
			if (!Character.isLetter(codePoint))
			{
				continue;
			}
			final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
			if (script == Character.UnicodeScript.LATIN)
			{
				latin = true;
			}
			else if (script == Character.UnicodeScript.CYRILLIC)
			{
				cyrillic = true;
			}
			else
			{
				throw new Rejection("reject.unsupported");
			}
		}
		if (latin && cyrillic)
		{
			throw new Rejection("reject.mixed_script");
		}
	}

	static String sha256(String value)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException(exception);
		}
	}

	public static final class Rejection extends IllegalArgumentException
	{
		private static final long serialVersionUID = 1L;
		private final String _reasonKey;

		private Rejection(String reasonKey)
		{
			super(reasonKey);
			_reasonKey = reasonKey;
		}

		public String reasonKey()
		{
			return _reasonKey;
		}
	}

	private record Unit(int codePoint, int originalStart, int originalEnd)
	{
	}

	private record BaseToken(String value, int start, int end, TokenKind kind)
	{
	}

	private record BaseText(String value, List<BaseToken> tokens)
	{
	}
}
