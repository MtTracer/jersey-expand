package jaxrs.expand;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import javax.annotation.Nullable;

import jaxrs.expand.FieldAccessorFactory.FieldAccessor;

public class ObjectFieldAccessor implements FieldAccessor {

	private final Object obj;

	private final String fieldName;

	public ObjectFieldAccessor(final Object obj, final String fieldName) {
		this.obj = obj;
		this.fieldName = fieldName;
	}

	@Nullable
	public Object getFieldValue() {
		final Field field = findField();
		if (null == field) {
			return null;
		}

		final boolean wasAccessible = field.isAccessible();
		field.setAccessible(true);
		try {
			return field.get(obj);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO
			return null;
		} finally {
			field.setAccessible(wasAccessible);
		}
	}

	public void setFieldValue(final Object value) {
		final Field field = findField();
		if (null == field) {
			return;
		}

		final boolean wasAccessible = field.isAccessible();
		field.setAccessible(true);
		try {
			field.set(obj, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO
			return;
		} finally {
			field.setAccessible(wasAccessible);
		}
	}

	@Nullable
	public Field findField() {
		Class<? extends Object> clazz = obj.getClass();
		do {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (final NoSuchFieldException e) {
				// nothing
			}
		} while (null != (clazz = clazz.getSuperclass()));

		return null;
	}

	public boolean hasField() {
		return null != findField();
	}

	public Class<?> getFieldType() {
		Field field = findField();
		return field.getType();
	}

	public String getFieldName() {
		return fieldName;
	}

	public <T extends Annotation> T findAnnotation(Class<T> annotationClass) {
		T annotation = findFieldAnnotation(annotationClass);
		if (null == annotation) {
			return annotation;
		}

		return findTypeAnnotation(annotationClass);
	}

	private <T extends Annotation> T findTypeAnnotation(Class<T> annotationClass) {
		Class<?> fieldType = getFieldType();
		return fieldType.getAnnotation(annotationClass);
	}

	private <T extends Annotation> T findFieldAnnotation(Class<T> annotationClass) {
		Field field = findField();
		if (null == field) {
			return null;
		}
		return field.getAnnotation(annotationClass);
	}
}
