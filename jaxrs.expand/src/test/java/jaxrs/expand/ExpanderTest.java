package jaxrs.expand;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

public class ExpanderTest extends JerseyTest {

	// TODO test cached expanded objects

	@Override
	protected Application configure() {
		forceSet(TestProperties.CONTAINER_PORT, "0");
		set(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
		return new ResourceConfig().register(ConfiguredJacksonFeature.class) //
				.register(ExpansionFeature.class) //
				.register(UserResource.class) //
				.register(GroupResource.class)
				.register(PermissionResource.class);
	}

	@Test
	public void testExpansion() {
		try {
			final String result = target("users") //
					// .path("1234") //
					.queryParam("expand", "group.permission") //
					.queryParam("pretty")
					.request(MediaType.APPLICATION_JSON) //
					.get(String.class);
			System.out.println(result);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
