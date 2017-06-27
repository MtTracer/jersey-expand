package jaxrs.expand;

import java.net.URI;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.linking.InjectLink;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;

import jaxrs.expand.PermissionResource.Permission;

@Path("groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupResource {

	@GET
	@Path("{id}")
	public Group getGroup(@PathParam("id") final long id) {

		final Group group = new Group();
		group.setId(id);
		group.setName("group" + id);

		final Permission permission1 = new Permission();
		permission1.setGroupId(id);
		permission1.setName("PRINT");
		final Permission permission2 = new Permission();
		permission2.setGroupId(id);
		permission2.setName("DELETE");
		final Permission permission3 = new Permission();
		permission3.setGroupId(id);
		permission3.setName("KILL");
		group.setPermissions(Sets.newHashSet(permission1, permission2, permission3));

		return group;
	}

	@Expandable("self")
	public static final class Group {
		private long id;

		private String name;

		private Set<Permission> permissions;

		@InjectLink(resource = GroupResource.class, method = "getGroup")
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

		public void setPermissions(final Set<Permission> permissions) {
			this.permissions = permissions;
		}

		public Set<Permission> getPermissions() {
			return permissions;
		}

		public URI getSelf() {
			return self;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.omitNullValues()
					.add("id", id)
					.add("name", name)
					.add("permissions", permissions)
					.toString();
		}
	}
}
