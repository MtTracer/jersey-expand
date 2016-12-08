package jaxrs.expand;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import jaxrs.expand.IndexParser.ExpansionContext;

@Priority(Priorities.ENTITY_CODER - 100)
public class ExpansionInterceptor implements WriterInterceptor {

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

		System.out.println("interception | original entity: " + entity);

		for (final Field field : entityFields) {

			final ExpansionContext expansionCtx = expansionNames.get(field.getName());
			if (null != expansionCtx) {
				expandEntityField(entity, field, expansionCtx, expansionInvoker);
			}
		}

		System.out.println("interceptor | changed entity to: " + entity);
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
			final String expandableFieldUri = getExpansionUri(uriFieldName, unexpandedFieldValue);
			final URI fetchUri = uriInfo.getBaseUriBuilder()
					.path(expandableFieldUri)
					.build();

			final Object expandedFieldValue = fetchExpandedObject(expansionField, fetchUri, ctx, expansionInvoker);

			if (null != expandedFieldValue) {
				expansionField.set(entity, expandedFieldValue);
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new InternalServerErrorException("Could not expand entity field " + expansionField, e);
		} finally {
			expansionField.setAccessible(fieldWasAccessible);
		}

	}

	private Object fetchExpandedObject(final Field expansionField, final URI fetchUri,
			final ExpansionContext ctx, final ExpansionInvoker expansionInvoker) {
		final Object expandedFieldValue;
		final Object cached = expandedUris.get()
				.get(fetchUri);
		if (null != cached) {
			expandedFieldValue = cached;
		} else {
			expandedFieldValue = expansionInvoker.fetchExpanded(fetchUri, expansionField.getType(),
					ctx.getSubExpansion());
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
