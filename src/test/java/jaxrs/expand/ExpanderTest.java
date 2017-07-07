package jaxrs.expand;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import jaxrs.expand.GroupResource.Group;
import jaxrs.expand.PermissionResource.Permission;
import jaxrs.expand.UserResource.User;

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
		final Response response = target("users") //
				// .path("1234") //
				.queryParam("expand", "group.permissions[0]") //
				.queryParam("pretty")
				.request(MediaType.APPLICATION_JSON) //
				.get();

		response.bufferEntity();
		System.out.println(response.readEntity(String.class));

		final List<User> users = response.readEntity(new GenericType<List<User>>() {
		});
		assertThat(users).hasSize(5);
		final User user0 = users.get(0);
		final Group group = user0.getGroup();
		assertThat(group.getName()).isNotNull();
		assertThat(group.getName()).isNotEmpty();
		final Permission[] permissions = group.getPermissions();
		final Permission permission = permissions[0];
		assertThat(permission.getDescription()).isNotNull();
		assertThat(permission.getDescription()).isNotEmpty();
		final Permission permission2 = permissions[1];
		assertThat(permission2.getDescription()).isNull();
		final Permission permission3 = permissions[2];
		assertThat(permission3.getDescription()).isNull();

	}
	
	@Test
	public void testMapExpansion() {
		final Response response = target("users") //
				.path("0") //
				.queryParam("expand", "userPermissions.expandMe") //
				.queryParam("pretty")
				.request(MediaType.APPLICATION_JSON) //
				.get();
		
		response.bufferEntity();
		System.out.println(response.readEntity(String.class));
		
		final User user = response.readEntity(new GenericType<User>() {
		});
		
		Map<String, Permission> userPermissions = user.getUserPermissions();
		Permission expandedPermission = userPermissions.get("expandMe");
		assertThat(expandedPermission.getDescription()).isNotNull();
		Permission unexpandedPermission = userPermissions.get("dontExpandMe");
		assertThat(unexpandedPermission.getDescription()).isNull();
		
		
	}

}
