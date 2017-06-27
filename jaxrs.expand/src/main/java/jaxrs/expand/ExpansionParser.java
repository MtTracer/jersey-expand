package jaxrs.expand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class ExpansionParser {

	private static final char SUB_EXPANSION_SEPARATOR = '.';

	private static final Pattern indexPattern = Pattern
			.compile("(?<field>.*?)\\[(?<start>-?\\d*)?(?:\\:(?<end>-?\\d*)?)?\\]");

	private final Logger logger = Logger.getLogger(getClass().getName());

	private final boolean ignoreInvalid;

	ExpansionParser(final boolean ignoreInvalid) {
		this.ignoreInvalid = ignoreInvalid;
	}

	Map<String, ExpansionContext> parseExpansions(final Object entity, final List<String> expansionParams) {
		final Map<String, ExpansionContext> expansions = new HashMap<>();
		for (final String expansionParam : expansionParams) {
			if (expansionParam.isEmpty()) {
				continue;
			}

			final ExpansionContext ctx = new ExpansionContext();
			try {
				String mainExpansion = handleSubExpansions(expansionParam, ctx);
				mainExpansion = handleIndices(mainExpansion, ctx, entity);
				expansions.put(mainExpansion, ctx);
			} catch (final BadRequestException e) {
				if (ignoreInvalid) {
					logger.log(Level.WARNING, "Could not parse expansion parameter: " + expansionParam, e);
					continue;
				}
				throw e;
			} catch (final Exception e) {
				throw new InternalServerErrorException("Could not parse expansion parameter: " + expansionParam, e);
			}

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

	private String handleIndices(final String mainExpansion, final ExpansionContext currentCtx, final Object entity)
			throws IllegalArgumentException, IllegalAccessException {

		final Matcher matcher = indexPattern.matcher(mainExpansion);
		if (!matcher.matches()) {
			final int entitySize = getFieldSize(entity, mainExpansion);
			currentCtx.startIndex = entitySize < 0 ? -1 : 0;
			currentCtx.endIndex = entitySize < 0 ? -1 : entitySize - 1;
			return mainExpansion;
		}

		final String expansionField = matcher.group("field");
		final int entitySize = getFieldSize(entity, expansionField);
		if (entitySize < 0) {
			throw new BadRequestException("Invalid use of indices. Entity is not an iterable or map: " + mainExpansion);
		}

		final int startIndex = parseIndex(matcher.group("start"), entitySize);
		currentCtx.startIndex = startIndex < 0 ? 0 : startIndex;
		final String endIndexStr = matcher.group("end");
		if (null == endIndexStr) {
			currentCtx.endIndex = currentCtx.startIndex;
		} else {
			final int endIndex = parseIndex(endIndexStr, entitySize);
			currentCtx.endIndex = endIndex < 0 ? entitySize - 1 : endIndex;
		}
		return expansionField;

	}

	private int getFieldSize(final Object entity, final String fieldName)
			throws IllegalArgumentException, IllegalAccessException {
		final FieldAccessor fieldAccessor = new FieldAccessor(entity, fieldName);

		final Object fieldEntity = fieldAccessor.getFieldValue();
		if (fieldEntity instanceof Iterable) {
			final Iterable<?> iterable = (Iterable<?>) fieldEntity;
			return Iterables.size(iterable) - 1;
		} else if (fieldEntity instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) fieldEntity;
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
