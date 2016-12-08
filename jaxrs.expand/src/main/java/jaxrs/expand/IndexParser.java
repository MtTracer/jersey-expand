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

	private static final Pattern indexPattern = Pattern.compile("(.*?)\\[(-?\\d*)?(?:\\:(-?\\d*)?)?\\]");

	Map<String, ExpansionContext> parseExpansions(final Object entity, final List<String> expansions) {
		final Map<String, ExpansionContext> splitted = new HashMap<>();
		for (final String expansion : expansions) {
			if (expansion.isEmpty()) {
				continue;
			}

			String mainExpansion;
			final ExpansionContext ctx = new ExpansionContext();
			final int dotIndex = expansion.indexOf(SUB_EXPANSION_SEPARATOR);
			if (dotIndex < 0) {
				mainExpansion = expansion;
			} else if (dotIndex == 0 || dotIndex == expansion.length() - 1) {
				throw new BadRequestException("Invalid expansion parameter: " + expansion);
			} else {
				mainExpansion = expansion.substring(0, dotIndex);
				ctx.subExpansion = expansion.substring(dotIndex + 1);
			}

			if (entity instanceof Iterable) {
				ctx.endIndex = Iterables.size((Iterable<?>) entity) - 1;
			} else if (entity instanceof Map) {
				ctx.endIndex = ((Map<?, ?>) entity).values()
						.size() - 1;
			}

			final Matcher matcher = indexPattern.matcher(mainExpansion);
			if (matcher.matches()) {

				if (ctx.endIndex < 0) {
					throw new BadRequestException(
							"Invalid use of indices for entity of type " + entity.getClass() + ": " + mainExpansion);
				}

				final int size = ctx.endIndex + 1;

				mainExpansion = matcher.group(1);
				final int startIndex = parseIndex(matcher.group(2), size);
				ctx.startIndex = startIndex < 0 ? 0 : startIndex;
				final int endIndex = parseIndex(matcher.group(3), size);
				ctx.endIndex = endIndex < 0 ? size - 1 : endIndex;
			}

			splitted.put(mainExpansion, ctx);
		}
		return splitted;
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
