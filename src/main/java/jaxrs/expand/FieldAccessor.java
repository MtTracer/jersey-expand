package jaxrs.expand;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

public class FieldAccessor {

	private final Object obj;

	private final String fieldName;

	public FieldAccessor(final Object obj, final String fieldName) {
		this.obj = obj;
		this.fieldName = fieldName;
	}

	@Nullable
	Object getFieldValue() {
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

	void setFieldValue(final Object value) {
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
}
