package jaxrs.expand;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.linking.InjectLink;

import com.google.common.base.MoreObjects;

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

		final Permission permission = new Permission();
		permission.setGroupId(id);
		permission.setName("PRINT");
		group.setPermission(permission);

		return group;
	}

	@Expandable("self")
	public static final class Group {
		private long id;

		private String name;

		private Permission permission;

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

		public void setPermission(final Permission permission) {
			this.permission = permission;
		}

		public Permission getPermission() {
			return permission;
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
					.add("permission", permission)
					.toString();
		}
	}
}
