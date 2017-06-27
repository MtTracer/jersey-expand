package jaxrs.expand;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import com.google.common.collect.Maps;

import jaxrs.expand.ExpansionParser.ExpansionContext;
import jersey.repackaged.com.google.common.collect.Lists;

@Priority(Priorities.ENTITY_CODER - 100)
public class ExpansionInterceptor implements WriterInterceptor {

	private final Logger logger = Logger.getLogger(getClass().getName());

	private static final String DEFAULT_EXPANSION_URI_FIELD_NAME = "self";

	@Context
	private UriInfo uriInfo;

	private final ThreadLocal<Map<URI, Object>> expandedUris = new InheritableThreadLocal<Map<URI, Object>>() {
		@Override
		protected Map<URI, Object> initialValue() {
			return new HashMap<>();
		};

	};

	@Override
	public void aroundWriteTo(final WriterInterceptorContext context) throws IOException, WebApplicationException {

		final List<String> expansions = (List<String>) context.getProperty("expand");
		if (null == expansions || expansions.isEmpty()) {
			context.proceed();
			return;
		}

		final Object entity = context.getEntity();
		// TODO make ignoreInvalid configurable
		final Map<String, ExpansionContext> expansionContexts = new ExpansionParser(true).parseExpansions(entity,
				expansions);

		final MediaType mediaType = context.getMediaType();
		final MultivaluedMap<String, Object> headers = context.getHeaders();
		try (ExpansionInvoker expansionInvoker = new ExpansionInvoker(mediaType, headers)) {

			if (entity instanceof Iterable) {
				final Iterable<?> iterable = (Iterable<?>) entity;
				for (final Object subEntity : iterable) {
					expandEntity(subEntity, expansionContexts, expansionInvoker);
				}
			} else if (entity instanceof Map) {
				final Map<?, ?> map = (Map<?, ?>) entity;
				for (final Object subEntity : map.values()) {
					expandEntity(subEntity, expansionContexts, expansionInvoker);
				}
			} else {
				expandEntity(entity, expansionContexts, expansionInvoker);
			}

			context.proceed();
		}

	}

	private void expandEntity(final Object entity, final Map<String, ExpansionContext> expansionContexts,
			final ExpansionInvoker expansionInvoker) {

		for (final Entry<String, ExpansionContext> expansionEntry : expansionContexts.entrySet()) {
			final String expansionFieldName = expansionEntry.getKey();
			final FieldAccessor fieldAccessor = new FieldAccessor(entity, expansionFieldName);
			final Field optField = fieldAccessor.findField();
			if (null == optField) {
				continue;
			}

			expandEntityField(entity, optField, expansionEntry.getValue(), expansionInvoker);

		}

	}

	@SuppressWarnings("unchecked")
	private void expandEntityField(final Object entity, final Field expansionField, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker) {

		final String uriFieldName = findExpansionUriFieldName(expansionField);
		if (null == uriFieldName) {
			return;
		}

		final boolean fieldWasAccessible = expansionField.isAccessible();
		expansionField.setAccessible(true);
		try {
			final Object unexpandedFieldValue = expansionField.get(entity);
			if (unexpandedFieldValue instanceof Collection) {
				expandCollection(ctx, expansionInvoker, uriFieldName, (Collection<Object>) unexpandedFieldValue);
			} else if (unexpandedFieldValue instanceof Map) {
				expandMap(ctx, expansionInvoker, uriFieldName, (Map<Object, Object>) unexpandedFieldValue);
			} else {
				expandSingleField(entity, expansionField, ctx, expansionInvoker, uriFieldName);
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new InternalServerErrorException("Could not expand entity field " + expansionField, e);
		} finally {
			expansionField.setAccessible(fieldWasAccessible);
		}

	}

	private void expandCollection(final ExpansionContext ctx, final ExpansionInvoker expansionInvoker,
			final String uriFieldName, final Collection<Object> collection)
			throws NoSuchFieldException, IllegalAccessException {
		final ArrayList<Object> copy = Lists.newArrayList(collection);
		collection.clear();
		for (int i = 0; i < copy.size(); i++) {
			final Object unexpandedElement = copy.get(i);
			if (i < ctx.getStartIndex() || i > ctx.getEndIndex()) {
				collection.add(unexpandedElement);
				continue;
			}

			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			collection.add(expandedElement);
		}
	}

	private void expandMap(final ExpansionContext ctx, final ExpansionInvoker expansionInvoker,
			final String uriFieldName, final Map<Object, Object> map)
			throws NoSuchFieldException, IllegalAccessException {
		final LinkedHashMap<Object, Object> copy = Maps.newLinkedHashMap(map);
		map.clear();
		final ArrayList<Object> indexKeys = Lists.newArrayList(copy.keySet());
		for (int i = 0; i < copy.size(); i++) {
			final Object key = indexKeys.get(i);
			final Object unexpandedElement = copy.get(key);
			if (i < ctx.getStartIndex() || i > ctx.getEndIndex()) {
				map.put(key, unexpandedElement);
				continue;
			}

			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			map.put(key, expandedElement);
		}
	}

	private void expandSingleField(final Object entity, final Field expansionField, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker, final String uriFieldName)
			throws IllegalAccessException, NoSuchFieldException {
		final Object unexpandedFieldValue = expansionField.get(entity);
		final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedFieldValue);

		final Object expandedFieldValue = fetchExpandedObject(expansionField.getType(), fetchUri, expansionInvoker);

		if (null != expandedFieldValue) {
			expansionField.set(entity, expandedFieldValue);
		}
	}

	private URI createFetchUri(final ExpansionContext ctx, final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final String expandableFieldUri = getExpansionUri(uriFieldName, unexpandedFieldValue);
		final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder()
				.path(expandableFieldUri);
		final String subExpansion = ctx.getSubExpansion();
		if (subExpansion != null) {
			uriBuilder.queryParam("expand", subExpansion);
		}
		return uriBuilder.build();
	}

	private <T> T fetchExpandedObject(final Class<T> entityType, final URI fetchUri,
			final ExpansionInvoker expansionInvoker) {
		final T expandedFieldValue;
		final Object cached = expandedUris.get()
				.get(fetchUri);
		if (null != cached) {
			expandedFieldValue = entityType.cast(cached);
		} else {
			expandedFieldValue = expansionInvoker.fetchExpanded(fetchUri, entityType);
			expandedUris.get()
					.put(fetchUri, expandedFieldValue);
		}
		return expandedFieldValue;
	}

	private String getExpansionUri(final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final Field uriField = unexpandedFieldValue.getClass()
				.getDeclaredField(uriFieldName);

		final boolean wasAccessible = uriField.isAccessible();
		uriField.setAccessible(true);

		final String expandableFieldUri = uriField.get(unexpandedFieldValue)
				.toString();

		uriField.setAccessible(wasAccessible);

		return expandableFieldUri;
	}

	private String findExpansionUriFieldName(final Field expansionField) {
		final Expandable fieldAnnotation = expansionField.getAnnotation(Expandable.class);
		if (null != fieldAnnotation) {
			return fieldAnnotation.value();
		}

		final Expandable typeAnnotation = expansionField.getType()
				.getAnnotation(Expandable.class);
		if (null != typeAnnotation) {
			return typeAnnotation.value();
		}

		return DEFAULT_EXPANSION_URI_FIELD_NAME;
	}

}
