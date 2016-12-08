package jaxrs.expand;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.jersey.jackson.JacksonFeature;

public class ConfiguredJacksonFeature implements Feature {

	@Override
	public boolean configure(FeatureContext context) {
		Configuration config = context.getConfiguration();
		if (!config.isRegistered(ObjectMapperProvider.class)) {
			context.register(ObjectMapperProvider.class);
		}

		if (!config.isRegistered(JacksonFeature.class)) {
			context.register(JacksonFeature.class);
		}

		return true;
	}

}
