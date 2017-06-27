package jaxrs.expand;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.linking.InjectLink;

import com.google.common.base.MoreObjects;

@Path("permissions")
@Produces(MediaType.APPLICATION_JSON)
public class PermissionResource {

	@GET
	@Path("{groupId}/{name}")
	public Permission getOne(@PathParam("groupId") final long groupId, @PathParam("name") final String name) {
		final Permission permission = new Permission();
		permission.setGroupId(groupId);
		permission.setName(name);
		permission.setRead(true);
		permission.setDescription("Some permission.");
		return permission;
	}

	public static final class Permission {
		private long groupId;

		private String name;

		private String description;

		private Boolean create;

		private Boolean read;

		private Boolean update;

		private Boolean delete;

		@InjectLink(resource = PermissionResource.class, method = "getOne")
		private URI self;

		public URI getSelf() {
			return self;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}

		public void setGroupId(final long groupId) {
			this.groupId = groupId;
		}

		public long getGroupId() {
			return groupId;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		public Boolean isCreate() {
			return create;
		}

		public void setCreate(final boolean create) {
			this.create = create;
		}

		public Boolean isRead() {
			return read;
		}

		public void setRead(final boolean read) {
			this.read = read;
		}

		public Boolean isUpdate() {
			return update;
		}

		public void setUpdate(final boolean update) {
			this.update = update;
		}

		public Boolean isDelete() {
			return delete;
		}

		public void setDelete(final boolean delete) {
			this.delete = delete;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this) //
					.omitNullValues() //
					.add("groupId", groupId)
					.add("name", name) //
					.add("description", description)
					.add("create", create) //
					.add("read", read) //
					.add("update", update)
					.add("delete", delete)
					.toString();
		}
	}
}
