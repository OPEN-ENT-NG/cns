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

		HttpClientRequest req = soapClient.post(soapEndpoint.getPath(), new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse response) {
				response.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer body) {
						handler.handle(new Either.Right<String, Buffer>(body));
					}
				});
			}
		});
		req
			.putHeader("SOAPAction", "http://cns.connecteur-universel.com/webservices/#" + messageDescriptor.getBodyTagName())
			.putHeader(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8");
		req.end(xml);
	}

}
