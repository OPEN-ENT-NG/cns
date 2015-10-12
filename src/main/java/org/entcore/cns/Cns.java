package org.entcore.cns;

import org.entcore.cns.controllers.CnsController;
import org.entcore.common.http.BaseServer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.json.JsonObject;

public class Cns extends BaseServer {

	private HttpClient soapClient;

	@Override
	public void start() {
		super.start();

		final JsonObject config = container.config().getObject("wsConfig", new JsonObject());
		if(!config.containsField("endpoint")){
			this.stop();
			log.error("[CNS] No endpoint provided.");
			return;
		}

		soapClient = vertx.createHttpClient();
		addController(new CnsController(soapClient, config));
	}

	@Override
	public void stop(){
		super.stop();
		if(soapClient != null)
			soapClient.close();
	}

}
