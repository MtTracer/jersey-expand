package jaxrs.expand;

import java.lang.annotation.Annotation;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import jaxrs.expand.AccessEntriesBy.ValueAccessor;
import jaxrs.expand.FieldAccessorFactory.FieldAccessor;

public class MapFieldAccessor implements FieldAccessor {

	private final Map<Object, Object> map;

	private final String fieldName;

	private ValueAccessor valueAccessor;

	public MapFieldAccessor(final Map<Object, Object> map, final String fieldName, ValueAccessor valueAccessor) {
		this.map = map;
		this.fieldName = fieldName;
		this.valueAccessor = valueAccessor;
	}

	@Nullable
	public Object getFieldValue() {
		return valueAccessor.getValue(map, fieldName);
	}

	public void setFieldValue(final Object value) {
		valueAccessor.updateValue(map, fieldName, value);
	}

	public boolean hasField() {
		return null != getFieldValue();
	}

	public Class<?> getFieldType() {
		Object mapValue = getFieldValue();
		Preconditions.checkState(null != mapValue, "Unable to determine type of null map entry.");
		return mapValue.getClass();
	}

	public String getFieldName() {
		return fieldName;
	}

	public <T extends Annotation> T findAnnotation(Class<T> annotationClass) {
		Class<?> fieldType = getFieldType();
		return fieldType.getAnnotation(annotationClass);
	}

}
