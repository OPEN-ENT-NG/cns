package org.entcore.cns.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.soap.SOAPException;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import org.entcore.common.soap.SoapHelper;
import org.entcore.common.soap.SoapHelper.SoapDescriptor;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import fr.wseduc.webutils.Either;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

public class CnsService {

	private final HttpClient soapClient;
	private final JsonObject conf;
	private final URL soapEndpoint;

	private final Logger log = LoggerFactory.getLogger(CnsService.class);

	public CnsService(Vertx vertx, JsonObject conf) throws MalformedURLException{
		HttpClientOptions soapClientOptions = new HttpClientOptions();
		this.conf = conf;

		URL endpoint = new URL(conf.getString("endpoint"));
		if("https".equals(endpoint.getProtocol())){
			soapClientOptions
				.setDefaultHost(endpoint.getHost())
				.setSsl(true)
				.setTrustAll(true)
				.setDefaultPort(443);
		} else {
			soapClientOptions
				.setDefaultHost(endpoint.getHost())
				.setDefaultPort(endpoint.getPort() == -1 ? 80 : endpoint.getPort());
		}
		soapClient = vertx.createHttpClient(soapClientOptions);
		soapEndpoint = endpoint;
	}

	public JsonObject getConf(){
		return this.conf;
	}

	public void processMessage(final SoapDescriptor messageDescriptor, final Handler<Either <String, Buffer>> handler){
		String xml = "";
		try {
			xml = SoapHelper.createSoapMessage(messageDescriptor);
		} catch (SOAPException | IOException e) {
			log.error("["+CnsService.class.getSimpleName()+"]("+messageDescriptor.getBodyTagName()+") Error while building the soap request.");
			handler.handle(new Either.Left<String, Buffer>(e.getMessage()));
			return;
		}
		final String payload = xml;
		final RequestOptions options = new RequestOptions()
			.setURI(soapEndpoint.getPath())
			.addHeader("SOAPAction", "http://cns.connecteur-universel.com/webservices/#" + messageDescriptor.getBodyTagName())
			.addHeader(CONTENT_TYPE, "text/xml;charset=UTF-8");
		soapClient.request(options)
			.flatMap(r -> r.send(payload))
			.onSuccess(response -> response.bodyHandler(body -> handler.handle(new Either.Right<>(body))))
			.onFailure(th -> handler.handle(new Either.Left<>(th.getMessage())));
	}

}
