package jaxrs.expand;

import java.util.Map;

import javax.annotation.Nullable;

public @interface AccessEntriesBy {

	Class<? extends ValueAccessor> value();
	
	public interface ValueAccessor {
		@Nullable
		Object getValue(Map map, String fieldName);
		
		void updateValue(Map map, String fieldName, Object value);
	}
}
