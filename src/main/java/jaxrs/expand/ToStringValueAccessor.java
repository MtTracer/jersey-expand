package jaxrs.expand;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import jaxrs.expand.AccessEntriesBy.ValueAccessor;

public class ToStringValueAccessor implements ValueAccessor {

	@Override
	@Nullable
	public Object getValue(Map map, String fieldName) {

		try {
			return map.get(fieldName);
		} catch (ClassCastException e) {
			// nothing
		}

		return findKey(map, fieldName).map(map::get).orElse(null);
	}

	@Override
	public void updateValue(Map map, String fieldName, Object value) {
		try {
			if (map.containsKey(fieldName)) {
				map.put(fieldName, value);
			}
		} catch (ClassCastException e) {
			// nothing
		}

		findKey(map, fieldName).ifPresent(key -> map.put(key, value));
	}

	private Optional<Object> findKey(Map map, String fieldName) {
		return map.keySet().stream().filter(k -> String.valueOf(k).equals(fieldName)).findFirst();
	}

}
