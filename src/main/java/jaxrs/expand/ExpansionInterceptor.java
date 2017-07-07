package jaxrs.expand;

import java.io.IOException;
import java.lang.reflect.Array;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import jaxrs.expand.AccessEntriesBy.ValueAccessor;
import jaxrs.expand.ExpansionParser.ExpansionContext;
import jaxrs.expand.FieldAccessorFactory.FieldAccessor;
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
				//TODO expand like single entity
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
			final FieldAccessor fieldAccessor = FieldAccessorFactory.getFieldAccessor(entity, expansionFieldName);
			if (!fieldAccessor.hasField()) {
				continue;
			}

			expandEntityField(fieldAccessor, expansionEntry.getValue(), expansionInvoker);

		}

	}

	@SuppressWarnings("unchecked")
	private void expandEntityField(final FieldAccessor expansionFieldAccessor, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker) {

		final String uriFieldName = findExpansionUriFieldName(expansionFieldAccessor);
		if (null == uriFieldName) {
			return;
		}

		try {
			final Object unexpandedFieldValue = expansionFieldAccessor.getFieldValue();
			if (unexpandedFieldValue instanceof List) {
				expandList(ctx, expansionInvoker, uriFieldName, (List<Object>) unexpandedFieldValue);
			} else if (unexpandedFieldValue instanceof Object[]) {
				expandArray(ctx, expansionInvoker, uriFieldName, (Object[]) unexpandedFieldValue);
			} else if (unexpandedFieldValue instanceof Map) {
				expandMap(expansionFieldAccessor, ctx, expansionInvoker);
			} else {
				expandSingleField(expansionFieldAccessor, ctx, expansionInvoker, uriFieldName);
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new InternalServerErrorException(
					"Could not expand entity field " + expansionFieldAccessor.getFieldName(), e);
		}

	}

	private void expandArray(final ExpansionContext ctx, final ExpansionInvoker expansionInvoker,
			final String uriFieldName, final Object[] objects) throws NoSuchFieldException, IllegalAccessException {
		for (int i = 0; i < objects.length; i++) {
			final Object unexpandedElement = objects[i];
			if (i < ctx.getStartIndex() || i > ctx.getEndIndex()) {
				continue;
			}

			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			objects[i] = expandedElement;
		}

	}

	private void expandList(final ExpansionContext ctx, final ExpansionInvoker expansionInvoker,
			final String uriFieldName, final List<Object> objects) throws NoSuchFieldException, IllegalAccessException {
		for (int i = 0; i < objects.size(); i++) {
			final Object unexpandedElement = objects.get(i);
			if (i < ctx.getStartIndex() || i > ctx.getEndIndex()) {
				continue;
			}

			final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedElement);
			final Object expandedElement = fetchExpandedObject(unexpandedElement.getClass(), fetchUri,
					expansionInvoker);
			objects.set(i, expandedElement);
		}

	}

	private void expandMap(FieldAccessor parentAccessor, final ExpansionContext ctx, final ExpansionInvoker expansionInvoker) {

		ExpansionParser expansionParser = new ExpansionParser(true);
		String expansionKey = expansionParser.parseMainExpansion(ctx.getSubExpansion());
		
		FieldAccessor mapAccessor = FieldAccessorFactory.getFieldAccessor(parentAccessor, expansionKey);
		if (mapAccessor.hasField()) {
			Object subEntity = mapAccessor.getFieldValue();
			Map<String, ExpansionContext> subExpansions = expansionParser.parseExpansions(subEntity, ImmutableList.of(ctx.getSubExpansion()));
			ExpansionContext subContext = subExpansions.get(expansionKey);
			
			expandEntityField(mapAccessor, subContext, expansionInvoker);
		}
	}

	private void expandSingleField(final FieldAccessor expansionFieldAccessor, final ExpansionContext ctx,
			final ExpansionInvoker expansionInvoker, final String uriFieldName)
			throws IllegalAccessException, NoSuchFieldException {
		final Object unexpandedFieldValue = expansionFieldAccessor.getFieldValue();
		final URI fetchUri = createFetchUri(ctx, uriFieldName, unexpandedFieldValue);

		final Object expandedFieldValue = fetchExpandedObject(expansionFieldAccessor.getFieldType(), fetchUri,
				expansionInvoker);

		if (null != expandedFieldValue) {
			expansionFieldAccessor.setFieldValue(expandedFieldValue);
		}
	}

	private URI createFetchUri(final ExpansionContext ctx, final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final String expandableFieldUri = getExpansionUri(uriFieldName, unexpandedFieldValue);
		final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder().path(expandableFieldUri);
		final String subExpansion = ctx.getSubExpansion();
		if (subExpansion != null) {
			uriBuilder.queryParam("expand", subExpansion);
		}
		return uriBuilder.build();
	}

	private <T> T fetchExpandedObject(final Class<T> entityType, final URI fetchUri,
			final ExpansionInvoker expansionInvoker) {
		final T expandedFieldValue;
		final Object cached = expandedUris.get().get(fetchUri);
		if (null != cached) {
			expandedFieldValue = entityType.cast(cached);
		} else {
			expandedFieldValue = expansionInvoker.fetchExpanded(fetchUri, entityType);
			expandedUris.get().put(fetchUri, expandedFieldValue);
		}
		return expandedFieldValue;
	}

	private String getExpansionUri(final String uriFieldName, final Object unexpandedFieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		final Field uriField = unexpandedFieldValue.getClass().getDeclaredField(uriFieldName);

		final boolean wasAccessible = uriField.isAccessible();
		uriField.setAccessible(true);

		final String expandableFieldUri = uriField.get(unexpandedFieldValue).toString();

		uriField.setAccessible(wasAccessible);

		return expandableFieldUri;
	}

	private String findExpansionUriFieldName(final FieldAccessor expansionFieldAccessor) {
		final Expandable fieldAnnotation = expansionFieldAccessor.findAnnotation(Expandable.class);
		if (null != fieldAnnotation) {
			return fieldAnnotation.value();
		}

		return DEFAULT_EXPANSION_URI_FIELD_NAME;
	}

}
