package org.entcore.cns.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.soap.SOAPException;

import org.entcore.common.soap.SoapHelper;
import org.entcore.common.soap.SoapHelper.SoapDescriptor;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import fr.wseduc.webutils.Either;

public class CnsService {

	private final HttpClient soapClient;
	private final JsonObject conf;
	private final URL soapEndpoint;

	private final Logger log = LoggerFactory.getLogger(CnsService.class);

	public CnsService(HttpClient soapClient, JsonObject conf) throws MalformedURLException{
		this.soapClient = soapClient;
		this.conf = conf;

		URL endpoint = new URL(conf.getString("endpoint"));
		if("https".equals(endpoint.getProtocol())){
			soapClient
				.setHost(endpoint.getHost())
				.setSSL(true)
				.setTrustAll(true)
				.setPort(443);
		} else {
			soapClient
				.setHost(endpoint.getHost())
				.setPort(endpoint.getPort() == -1 ? 80 : endpoint.getPort());
		}
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
