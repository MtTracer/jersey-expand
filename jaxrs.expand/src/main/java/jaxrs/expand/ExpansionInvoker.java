package jaxrs.expand;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class ExpansionInvoker implements AutoCloseable {

	private final Logger logger = Logger.getLogger(getClass().getName());

	private final Client client;

	private final MediaType mediaType;

	private final MultivaluedMap<String, Object> headers;

	ExpansionInvoker(final MediaType mediaType, final MultivaluedMap<String, Object> headers) {
		this.client = ClientBuilder.newClient();
		this.mediaType = mediaType;
		this.headers = headers;
	}

	<T> T fetchExpanded(final URI fetchUri, final Class<T> entityType) {
		final WebTarget target = client.target(fetchUri);
		final Response response = target //
				.request(mediaType) //
				.headers(headers) //
				.get();

		if (response.getStatus() == Status.OK.getStatusCode()) {
			return response.readEntity(entityType);
		}

		// TODO enable throwing WebApplicationException when configured
		logger.warning("Could not fetch expanded entity from " + fetchUri + ": " + response);
		return null;
	}

	@Override
	public void close() throws IOException {
		client.close();
	}
}
