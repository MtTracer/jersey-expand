package jaxrs.expand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.BadRequestException;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class IndexParser {

	private static final char SUB_EXPANSION_SEPARATOR = '.';

	private static final Pattern indexPattern = Pattern
			.compile("(?<field>.*?)\\[(?<start>-?\\d*)?(?:\\:(?<end>-?\\d*)?)?\\]");

	Map<String, ExpansionContext> parseExpansions(final Object entity, final List<String> expansionParams) {
		final Map<String, ExpansionContext> expansions = new HashMap<>();
		for (final String expansionParam : expansionParams) {
			if (expansionParam.isEmpty()) {
				continue;
			}

			final ExpansionContext ctx = new ExpansionContext();
			String mainExpansion = handleSubExpansions(expansionParam, ctx);
			mainExpansion = handleIndices(mainExpansion, ctx, entity);

			expansions.put(mainExpansion, ctx);
		}
		return expansions;
	}

	private String handleSubExpansions(final String expansionParam, final ExpansionContext ctx) {

		final int dotIndex = expansionParam.indexOf(SUB_EXPANSION_SEPARATOR);
		if (dotIndex < 0) {
			return expansionParam;
		} else if (dotIndex == 0 || dotIndex == expansionParam.length() - 1) {
			throw new BadRequestException("Invalid expansion parameter: " + expansionParam);
		} else {
			ctx.subExpansion = expansionParam.substring(dotIndex + 1);
			return expansionParam.substring(0, dotIndex);
		}
	}

	private String handleIndices(final String mainExpansion, final ExpansionContext currentCtx, final Object entity) {

		final int entitySize = getEntitySize(entity);

		final Matcher matcher = indexPattern.matcher(mainExpansion);
		if (!matcher.matches()) {
			currentCtx.startIndex = entitySize < 0 ? -1 : 0;
			currentCtx.endIndex = entitySize < 0 ? -1 : entitySize - 1;
			return mainExpansion;
		}

		if (entitySize < 0) {
			throw new BadRequestException("Invalid use of indices. Entity is not an iterable or map: " + mainExpansion);
		}

		final int startIndex = parseIndex(matcher.group("start"), entitySize);
		currentCtx.startIndex = startIndex < 0 ? 0 : startIndex;
		final int endIndex = parseIndex(matcher.group("end"), entitySize);
		currentCtx.endIndex = endIndex < 0 ? entitySize - 1 : endIndex;
		return matcher.group("field");

	}

	private int getEntitySize(final Object entity) {
		if (entity instanceof Iterable) {
			final Iterable<?> iterable = (Iterable<?>) entity;
			return Iterables.size(iterable) - 1;
		} else if (entity instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) entity;
			return map.size() - 1;
		}

		return -1;
	}

	private int parseIndex(final String indexStr, final int entitySize) {
		if (Strings.isNullOrEmpty(indexStr)) {
			return -1;
		}

		try {
			final int index = Integer.parseInt(indexStr);
			if (index >= 0) {
				return index;
			}

			// size - abs(index)
			return entitySize + index;

		} catch (final NumberFormatException e) {
			throw new BadRequestException("Invalid index: " + indexStr);
		}
	}

	static final class ExpansionContext {
		private int startIndex = 0;

		private int endIndex = -1;

		private String subExpansion;

		public int getStartIndex() {
			return startIndex;
		}

		public int getEndIndex() {
			return endIndex;
		}

		public String getSubExpansion() {
			return subExpansion;
		}

	}

}
