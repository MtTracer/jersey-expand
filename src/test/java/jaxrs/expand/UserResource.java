package jaxrs.expand;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.linking.InjectLink;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import jaxrs.expand.GroupResource.Group;
import jaxrs.expand.PermissionResource.Permission;
import jersey.repackaged.com.google.common.collect.Maps;

@Path("users")
@Produces(MediaType.APPLICATION_JSON)
public final class UserResource

{

	@GET
	public List<User> findAll() {

		final User user1 = getUser(1);
		user1.getGroup()
				.setId(10);
		final User user2 = getUser(2);
		user2.getGroup()
				.setId(10);
		final User user3 = getUser(3);
		user3.getGroup()
				.setId(10);

		return ImmutableList.of(getUser(0), user1, user2, user3, getUser(4));
	}

	@Path("{id}")
	@GET
	public User getUser(@PathParam("id") final long id) {
		final User user = new User();
		user.setId(id);
		user.setName("user" + id);

		final long groupId = new Random().nextLong();
		final Group group = new Group();
		group.setId(groupId);
		user.setGroup(group);
		
		Permission permission1 = new Permission();
		permission1.setGroupId(6);
		permission1.setName("expandMe");
		Permission permission2 = new Permission();
		permission2.setGroupId(8);
		permission2.setName("dontExpandMe");
		Map<String, Permission> userPermissions = Maps.newHashMapWithExpectedSize(2);
		userPermissions.put("expandMe", permission1);
		userPermissions.put("dontExpandMe", permission2);
		user.setUserPermissions(userPermissions);
		

		return user;
	}

	public static final class User {
		private long id;

		private String name;

		private Group group;
		
		private Map<String, Permission> userPermissions;

		@InjectLink(resource = UserResource.class, method = "getUser")
		private URI self;

		public long getId() {
			return id;
		}

		public void setId(final long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		public Group getGroup() {
			return group;
		}

		public void setGroup(final Group group) {
			this.group = group;
		}

		public URI getSelf() {
			return self;
		}

		public Map<String, Permission> getUserPermissions() {
			return userPermissions;
		}

		public void setUserPermissions(Map<String, Permission> userPermissions) {
			this.userPermissions = userPermissions;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.omitNullValues()
					.add("id", id)
					.add("name", name)
					.add("group", group)
					.toString();
		}
	}

}