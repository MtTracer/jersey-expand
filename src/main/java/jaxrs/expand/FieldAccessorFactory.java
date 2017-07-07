package jaxrs.expand;

import java.lang.annotation.Annotation;
import java.util.Map;

import jaxrs.expand.AccessEntriesBy.ValueAccessor;

public class FieldAccessorFactory {

	public static FieldAccessor getFieldAccessor(FieldAccessor parent, String fieldName) {
		Object entity = parent.getFieldValue();
		if(entity instanceof Map) {
			ValueAccessor keyMapper = getKeyMapper(parent);
			return new MapFieldAccessor((Map)entity, fieldName, keyMapper);
		} else {
			return new ObjectFieldAccessor(entity, fieldName);
		}
	}
	
	public static FieldAccessor getFieldAccessor(Object entity, String fieldName) {
		if(entity instanceof Map) {
			return new MapFieldAccessor((Map)entity, fieldName, new ToStringValueAccessor());
		} else {
			return new ObjectFieldAccessor(entity, fieldName);
		}
		
	}
	
	private static ValueAccessor getKeyMapper(final FieldAccessor expansionFieldAccessor)  {
		AccessEntriesBy accessEntriesByAnnotation = expansionFieldAccessor.findAnnotation(AccessEntriesBy.class);
		if(null==accessEntriesByAnnotation) {
			return new ToStringValueAccessor();
		}
		Class<? extends ValueAccessor> keyMapperClass = accessEntriesByAnnotation.value();
		try {
			return keyMapperClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		}
	}
	
	public interface FieldAccessor {
		Object getFieldValue();
		void setFieldValue(Object  newValue);
		boolean hasField();
		Class<?> getFieldType();
		String getFieldName();
		<T extends Annotation> T findAnnotation(Class<T> annotationClazz);
	}
}
