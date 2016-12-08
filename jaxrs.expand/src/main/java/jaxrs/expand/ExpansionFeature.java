package jaxrs.expand;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.jersey.linking.DeclarativeLinkingFeature;

public class ExpansionFeature implements Feature {

	@Override
	public boolean configure(FeatureContext context) {
		Configuration config = context.getConfiguration();
		if (!config.isRegistered(ExpansionInterceptor.class)) {
			context.register(ExpansionInterceptor.class);
		}
		if (!config.isRegistered(ExpansionRequestFilter.class)) {
			context.register(ExpansionRequestFilter.class);
		}
		if (!config.isRegistered(DeclarativeLinkingFeature.class)) {
			context.register(DeclarativeLinkingFeature.class);
		}
		return true;
	}

}
