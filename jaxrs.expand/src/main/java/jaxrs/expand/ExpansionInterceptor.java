package jaxrs.expand;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import jaxrs.expand.IndexParser.ExpansionContext;
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
		final Map<String, ExpansionContext> expansionNames = new IndexParser().parseExpansions(entity, expansions);

		final MediaType mediaType = context.getMediaType();
		final MultivaluedMap<String, Object> headers = context.getHeaders();
		try (ExpansionInvoker expansionInvoker = new ExpansionInvoker(mediaType, headers)) {

			if (entity instanceof Iterable) {
				final Iterable<?> iterable = (Iterable<?>) entity;
				for (final Object subEntity : iterable) {
					expandEntity(subEntity, expansionNames, expansionInvoker);
				}
			} else if (entity instanceof Map) {
				final Map<?, ?> map = (Map<?, ?>) entity;
				for (final Object subEntity : map.values()) {
					expandEntity(subEntity, expansionNames, expansionInvoker);
				}
			} else {
				expandEntity(entity, expansionNames, expansionInvoker);
			}

			context.proceed();
		}

	}

	private void expandEntity(final Object entity, final Map<String, ExpansionContext> expansionNames,
			final ExpansionInvoker expansionInvoker) {
		final Field[] entityFields = entity.getClass()
				.getDeclaredFields();

		for (final Field field : entityFields) {

			final ExpansionContext expansionCtx = expansionNames.get(field.getName());
			if (null != expansionCtx) {
				expandEntityField(entity, field, expansionCtx, expansionInvoker);
			}
		}

	}

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
			if (unexpandedFieldValue instanceof Iterable) {
				expandIterable(entity, expansionField, ctx, expansionInvoker, uriFieldName, unexpandedFieldValue);
			} else if (unexpandedFieldValue instanceof Map) {
				expandMap(entity, expansionField, ctx, expansionInvoker, uriFieldName, unexpandedFieldValue);
			} else {
				expandSingleField(entity, expansionField, ctx, expansionInvoker, uriFieldName);
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new InternalServerErrorException("Could not expand entity field " + expansionField, e);
		} finally {
			expansionField.setAccessible(fieldWasAccessible);
		}

	}

	private void expandMap(final Object entity, final Field expansionField, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker, final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final Map<?, ?> map = (Map<?, ?>) unexpandedFieldValue;
		final LinkedHashMap<Object, Object> copy = Maps.newLinkedHashMap(map);
		final ArrayList<Object> indexKeys = Lists.newArrayList(copy.keySet());
		for (int i = 0; i < copy.size(); i++) {
			final Object key = indexKeys.get(i);
			final Object unexpandedElement = copy.get(key);
			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			copy.put(key, expandedElement);
		}
		expansionField.set(entity, copy);
	}

	private void expandIterable(final Object entity, final Field expansionField, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker, final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final Iterable<?> iterable = (Iterable<?>) unexpandedFieldValue;
		final ArrayList<Object> copy = Lists.newArrayList(iterable);
		for (int i = 0; i < copy.size(); i++) {
			final Object unexpandedElement = copy.get(i);
			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			copy.set(i, expandedElement);
		}
		// FIXME classcast - field can be any iterable, not just an
		// arraylist
		expansionField.set(entity, copy);
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

	private Object fetchExpandedObject(final Class<?> entityType, final URI fetchUri,
			final ExpansionInvoker expansionInvoker) {
		final Object expandedFieldValue;
		final Object cached = expandedUris.get()
				.get(fetchUri);
		if (null != cached) {
			expandedFieldValue = cached;
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
