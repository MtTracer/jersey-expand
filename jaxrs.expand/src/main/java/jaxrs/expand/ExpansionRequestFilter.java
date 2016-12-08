package jaxrs.expand;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

public class ExpansionRequestFilter implements ContainerRequestFilter {

	@Override
	public void filter(final ContainerRequestContext requestContext) throws IOException {
		final MultivaluedMap<String, String> queryParams = requestContext.getUriInfo()
				.getQueryParameters();
		final List<String> expansionParams = queryParams.get("expand");
		requestContext.setProperty("expand", expansionParams);
	}

}
