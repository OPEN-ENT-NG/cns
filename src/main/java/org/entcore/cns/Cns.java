/*
 * Copyright © Région Nord Pas de Calais-Picardie, Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

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
