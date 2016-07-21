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

package org.entcore.cns.controllers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.soap.SOAPException;

import org.entcore.common.soap.SoapHelper;
import org.entcore.common.soap.SoapHelper.SoapDescriptor.Element;
import org.entcore.common.soap.SoapHelper.SoapDescriptor;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.eventbus.Message;

import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;

public class CnsController extends BaseController {

	private final HttpClient soapClient;
	private final JsonObject conf;
	private final URL soapEndpoint;

	private final Pattern mefStatPattern = Pattern.compile(".*\\$([0-9]{6}).*\\$.*");
	private final Pattern classGroupPattern = Pattern.compile(".*\\$(.*)");
	private final Pattern matPattern = Pattern.compile(".*\\$.*\\$([0-9]{6}).*");

	public CnsController(HttpClient soapClient, JsonObject conf){
		this.soapClient = soapClient;
		this.conf = conf;
		URL endpoint = null;
		try {
			endpoint = new URL(conf.getString("endpoint"));
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
		} catch (MalformedURLException e) {
			log.error(e);
		}
		soapEndpoint = endpoint;
	}

	private SoapDescriptor initDescriptor(String function){
		SoapDescriptor descriptor = new SoapDescriptor(function);
		descriptor
			.addNamespace("web", "http://cns.connecteur-universel.com/webservices")
			.setBodyNamespace("", "web");

		return descriptor;
	}

	private String produceHash(String input){
		String datePadding = new SimpleDateFormat("ddMMyyyy").format(Calendar.getInstance().getTime());
		String hash = input + datePadding;
		try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(hash.getBytes());
            StringBuffer sb = new StringBuffer();
    		for (byte b : messageDigest) {
    			sb.append(String.format("%02x", b & 0xff));
    		}
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e) {
        	log.error(e);
        }

		return input;
	}


	@Get("")
	@SecuredAction("cns.access")
	public void view(HttpServerRequest request) {
		renderView(request);
	}

	@Get("/InitUserRessourcesCatalog")
	@SecuredAction(type = ActionType.AUTHENTICATED, value = "")
	public void InitUserRessourcesCatalog(final HttpServerRequest request){
		String UAI = request.params().get("uai");

		if(UAI == null || UAI.trim().length() == 0){
			badRequest(request);
			return;
		}

		SoapDescriptor descriptor = initDescriptor("InitUserRessourcesCatalog");

		Element input = descriptor.createElement("input", "");
		input.createElement("Cle", produceHash(conf.getString("key")));
		input.createElement("Pf", conf.getString("platform", ""));
		input.createElement("ENTPersonStructRattachUAI", UAI);

		processMessage(request, descriptor);
	}

	@Get("/UserRessourcesCatalog")
	@SecuredAction(type = ActionType.AUTHENTICATED, value = "")
	public void InitUserRessourcesCatalogResponse(final HttpServerRequest request){
		final String UAI = request.params().get("uai");
		final String TypeSSO = request.params().get("typesso");

		if(UAI == null || UAI.trim().isEmpty() || TypeSSO == null || TypeSSO.trim().isEmpty()){
			badRequest(request);
			return;
		}

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			public void handle(final UserInfos infos) {
				if(infos == null){
					renderError(request);
					return;
				}

				JsonObject jo = new JsonObject();
				jo.putString("action", "getUser").putString("userId", infos.getUserId());
				eb.send("directory", jo, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						JsonObject data = event.body().getObject("result");
						if ("ok".equals(event.body().getString("status")) && data != null) {

							//Enfants
							String childStr = "";
							for(Object childObj : data.getArray("children", new JsonArray())){
								JsonObject child = (JsonObject) childObj;
								childStr += childStr.length() == 0 ? "" : "|";
								childStr += child.getString("externalId", "");
							}
							//Code enseignement
							String codeEnseignement = "";
							for(Object codeEnsObj : data.getArray("fieldOfStudy", new JsonArray())){
								String codeEns = (String) codeEnsObj;
								codeEnseignement += codeEnseignement.length() == 0 ? "" : "|";
								codeEnseignement += codeEns;
							}
							//MEF élève
							String eleveMef = data.getString("module", "");
							//MEF enseignant
							String ensMef = "";
							for(Object moduleObj: data.getArray("modules", new JsonArray())){
								String module = (String) moduleObj;
								Matcher m = mefStatPattern.matcher(module);
								if(m.matches() && m.groupCount() >= 1){
									ensMef += ensMef.length() == 0 ? "" : "|";
									ensMef += m.group(1);
								}
							}
							//Classes
							String classes = "";
							for(Object classObj: data.getArray("classes", new JsonArray())){
								String classStr = (String) classObj;
								Matcher m = classGroupPattern.matcher(classStr);
								if(m.matches() && m.groupCount() >= 1){
									classes += classes.length() == 0 ? "" : "|";
									classes += m.group(1);
								}
							}
							//Matieres enseignant
							String ensMat = "";
							for(Object matObj: data.getArray("classesFieldOfStudy", new JsonArray())){
								String mat = (String) matObj;
								Matcher m = matPattern.matcher(mat);
								if(m.matches() && m.groupCount() >= 1){
									ensMat += ensMat.length() == 0 ? "" : "|";
									ensMat += m.group(1);
								}
							}
							//Groupes
							String groups = "";
							for(Object groupObj: data.getArray("groups", new JsonArray())){
								String groupStr = (String) groupObj;
								Matcher m = classGroupPattern.matcher(groupStr);
								if(m.matches() && m.groupCount() >= 1){
									groups += groups.length() == 0 ? "" : "|";
									groups += m.group(1);
								}
							}
							//Type structure
							String structType = "";
							for(Object structObj: data.getArray("structureNodes", new JsonArray())){
								JsonObject struct = (JsonObject) structObj;
								if(UAI.equals(struct.getString("UAI"))){
									switch(struct.getString("type", "")){
										case "ECOLE DE NIVEAU ELEMENTAIRE":
											structType = "1ORD";
											break;
										case "COLLEGE":
										case "COLLEGE CLIMATIQUE":
											structType = "CLG";
											break;
										case "LYCEE D ENSEIGNEMENT GENERAL":
										case "LYCEE POLYVALENT":
											structType = "LYC";
											break;
										case "LYCEE PROFESSIONNEL":
											structType = "LP";
											break;
										default:
											structType = struct.getString("type", "");
									}
									break;
								}
							}


							SoapDescriptor descriptor = initDescriptor("UserRessourcesCatalog");
							Element input = descriptor.createElement("input", "");

							input.createElement("Cle", produceHash(conf.getString("key")));
							input.createElement("Pf", conf.getString("platform", ""));
							input.createElement("ENTPersonStructRattachUAI", UAI);

							JsonArray profiles = data.getArray("type", new JsonArray());
							if(profiles.size() > 0){
								String profile = profiles.get(0);
								switch(profile) {
									case "Student":
										input.createElement("ENTPersonProfils", "National_ELV");
										input.createElement("ENTEleveMEF", eleveMef);
										input.createElement("ENTEleveCodeEnseignements", codeEnseignement);
										input.createElement("ENTEleveClasses", classes);
										input.createElement("ENTEleveGroupes", groups);
										input.createElement("ENTAuxEnsClassesMatieres", "");
										input.createElement("ENTAuxEnsGroupes", "");
								        input.createElement("ENTAuxEnsClasses", "");
								        input.createElement("ENTAuxEnsMEF", "");
								        input.createElement("EnfantId", "");
										break;
									case "Teacher":
										input.createElement("ENTPersonProfils", "National_ENS");
										input.createElement("ENTEleveMEF", "");
										input.createElement("ENTEleveCodeEnseignements", "");
										input.createElement("ENTEleveClasses", "");
										input.createElement("ENTEleveGroupes", "");
										input.createElement("ENTAuxEnsClassesMatieres", ensMat);
										input.createElement("ENTAuxEnsGroupes", groups);
								        input.createElement("ENTAuxEnsClasses", classes);
								        input.createElement("ENTAuxEnsMEF", ensMef);
								        input.createElement("EnfantId", "");
										break;
									case "Personnel":
										input.createElement("ENTPersonProfils", "National_DOC");
										input.createElement("ENTEleveMEF", "");
										input.createElement("ENTEleveCodeEnseignements", "");
										input.createElement("ENTEleveClasses", "");
										input.createElement("ENTEleveGroupes", "");
										input.createElement("ENTAuxEnsClassesMatieres", "");
										input.createElement("ENTAuxEnsGroupes", "");
								        input.createElement("ENTAuxEnsClasses", "");
								        input.createElement("ENTAuxEnsMEF", "");
										input.createElement("EnfantId", "");
										break;
									case "Relative":
										input.createElement("ENTPersonProfils", "National_TUT");
										input.createElement("ENTEleveMEF", "");
										input.createElement("ENTEleveCodeEnseignements", "");
										input.createElement("ENTEleveClasses", "");
										input.createElement("ENTEleveGroupes", "");
										input.createElement("ENTAuxEnsClassesMatieres", "");
										input.createElement("ENTAuxEnsGroupes", "");
								        input.createElement("ENTAuxEnsClasses", "");
								        input.createElement("ENTAuxEnsMEF", "");
										input.createElement("EnfantId", childStr);
										break;
									default:
										input.createElement("ENTPersonProfils", profile);
										input.createElement("ENTEleveMEF", eleveMef);
										input.createElement("ENTEleveCodeEnseignements", codeEnseignement);
										input.createElement("ENTEleveClasses", classes);
										input.createElement("ENTEleveGroupes", groups);
										input.createElement("ENTAuxEnsClassesMatieres", ensMat);
										input.createElement("ENTAuxEnsGroupes", groups);
								        input.createElement("ENTAuxEnsClasses", classes);
								        input.createElement("ENTAuxEnsMEF", ensMef);
								        input.createElement("EnfantId", childStr);
								}
							} else {
								input.createElement("ENTPersonProfils", "");
								input.createElement("ENTEleveMEF", eleveMef);
								input.createElement("ENTEleveCodeEnseignements", codeEnseignement);
								input.createElement("ENTEleveClasses", classes);
								input.createElement("ENTEleveGroupes", groups);
								input.createElement("ENTAuxEnsClassesMatieres", ensMat);
								input.createElement("ENTAuxEnsGroupes", groups);
						        input.createElement("ENTAuxEnsClasses", classes);
						        input.createElement("ENTAuxEnsMEF", ensMef);
						        input.createElement("EnfantId", childStr);
							}
					        input.createElement("user", infos.getExternalId());
					        input.createElement("ENTStructureTypeStruct", structType);

							processMessage(request, descriptor);

						} else {
							renderError(request);
						}
					}
				});
			}
		});
	}

	private void processMessage(final HttpServerRequest request, SoapDescriptor messageDescriptor){
		String xml = "";
		try {
			xml = SoapHelper.createSoapMessage(messageDescriptor);
		} catch (SOAPException | IOException e) {
			log.error("["+CnsController.class.getSimpleName()+"]("+messageDescriptor.getBodyTagName()+") Error while building the soap request.");
			log.error(e);
		}

		HttpClientRequest req = soapClient.post(soapEndpoint.getPath(), new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse response) {
				response.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer body) {
						request.response().end(body);
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
