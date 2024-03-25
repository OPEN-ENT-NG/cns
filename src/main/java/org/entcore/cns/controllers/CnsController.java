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

import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.entcore.cns.services.CnsService;
import org.entcore.common.soap.SoapHelper.SoapDescriptor.Element;
import org.entcore.common.soap.SoapHelper.SoapDescriptor;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import io.vertx.core.eventbus.Message;

import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.BaseController;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class CnsController extends BaseController {

	private final Pattern mefStatPattern = Pattern.compile(".*\\$([0-9]{6}).*\\$.*");
	private final Pattern classGroupPattern = Pattern.compile(".*\\$(.*)");
	private final Pattern matPattern = Pattern.compile(".*\\$.*\\$([0-9]{6}).*");

	private final HashMap<String, CnsService> services = new HashMap<>();

	private final JsonArray configurations;

	public CnsController(JsonArray configurations){
		this.configurations = configurations;
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions){
		super.init(vertx, config, rm, securedActions);

		for(Object confObj : configurations){
			JsonObject conf = (JsonObject) confObj;
			String domain = conf.getString("domain");
			if(domain != null){
				try{
					services.put(domain, new CnsService(vertx, conf));
				} catch(MalformedURLException e){
					log.error("[CNS] Malformed endpoint URL for domain : "+domain);
				}
			}
		}
	}

	private JsonObject getConfByHost(final HttpServerRequest request){
		CnsService service = getServiceByHost(request);
		return service != null ? service.getConf() : new JsonObject();
	}

	private CnsService getServiceByHost(final HttpServerRequest request){
		CnsService service = this.services.get(getHost(request));
		if(service == null){
			for(Entry<String, CnsService> item: this.services.entrySet()){
				service = item.getValue();
				break;
			}
		}
		return service;
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
		CnsService service = getServiceByHost(request);

		if(UAI == null || UAI.trim().length() == 0 || service == null){
			badRequest(request);
			return;
		}

		SoapDescriptor descriptor = initDescriptor("InitUserRessourcesCatalog");

		Element input = descriptor.createElement("input", "");
		input.createElement("Cle", produceHash(service.getConf().getString("key")));
		input.createElement("Pf", service.getConf().getString("platform", ""));
		input.createElement("ENTPersonStructRattachUAI", UAI);

		service.processMessage(descriptor, new Handler<Either<String,Buffer>>() {
			public void handle(Either<String, Buffer> event) {
				if(event.isLeft()){
					log.error(event.left().getValue());
					renderError(request);
				} else {
					request.response().end(event.right().getValue());
				}
			}
		});
	}

	@Get("/UserRessourcesCatalog")
	@SecuredAction(type = ActionType.AUTHENTICATED, value = "")
	public void InitUserRessourcesCatalogResponse(final HttpServerRequest request){
		final String UAI = request.params().get("uai");
		final String TypeSSO = request.params().get("typesso");
		final CnsService service = getServiceByHost(request);

		if(UAI == null || UAI.trim().isEmpty() || TypeSSO == null || TypeSSO.trim().isEmpty() || service == null){
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
				jo.put("action", "getUser").put("userId", infos.getUserId());
				eb.request("directory", jo, handlerToAsyncHandler(event -> {
          JsonObject data = event.body().getJsonObject("result");
          if ("ok".equals(event.body().getString("status")) && data != null) {

            //Enfants
            String childStr = "";
            for(Object childObj : data.getJsonArray("children", new JsonArray())){
              JsonObject child = (JsonObject) childObj;
              childStr += childStr.isEmpty() ? "" : "|";
              childStr += child.getString("externalId", "");
            }
            //Code enseignement
            String codeEnseignement = "";
            for(Object codeEnsObj : data.getJsonArray("fieldOfStudy", new JsonArray())){
              String codeEns = (String) codeEnsObj;
              codeEnseignement += codeEnseignement.isEmpty() ? "" : "|";
              codeEnseignement += codeEns;
            }
            //MEF élève
            String eleveMef = data.getString("module", "");
            //MEF enseignant
            String ensMef = "";
            for(Object moduleObj: data.getJsonArray("modules", new JsonArray())){
              String module = (String) moduleObj;
              Matcher m = mefStatPattern.matcher(module);
              if(m.matches() && m.groupCount() >= 1){
                ensMef += ensMef.isEmpty() ? "" : "|";
                ensMef += m.group(1);
              }
            }
            //Classes
            String classes = "";
            for(Object classObj: data.getJsonArray("classes", new JsonArray())){
              String classStr = (String) classObj;
              Matcher m = classGroupPattern.matcher(classStr);
              if(m.matches() && m.groupCount() >= 1){
                classes += classes.isEmpty() ? "" : "|";
                classes += m.group(1);
              }
            }
            //Matieres enseignant
            String ensMat = "";
            for(Object matObj: data.getJsonArray("classesFieldOfStudy", new JsonArray())){
              String mat = (String) matObj;
              Matcher m = matPattern.matcher(mat);
              if(m.matches() && m.groupCount() >= 1){
                ensMat += ensMat.isEmpty() ? "" : "|";
                ensMat += m.group(1);
              }
            }
            //Groupes
            String groups = "";
            for(Object groupObj: data.getJsonArray("groups", new JsonArray())){
              String groupStr = (String) groupObj;
              Matcher m = classGroupPattern.matcher(groupStr);
              if(m.matches() && m.groupCount() >= 1){
                groups += groups.isEmpty() ? "" : "|";
                groups += m.group(1);
              }
            }
            //Type structure
            String structType = "";
            for(Object structObj: data.getJsonArray("structureNodes", new JsonArray())){
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

            input.createElement("Cle", produceHash(service.getConf().getString("key")));
            input.createElement("Pf", service.getConf().getString("platform", ""));
            input.createElement("ENTPersonStructRattachUAI", UAI);

            JsonArray profiles = data.getJsonArray("type", new JsonArray());
            if(profiles.size() > 0){
              String profile = profiles.getString(0);
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

                service.processMessage(descriptor, new Handler<Either<String,Buffer>>() {
              public void handle(Either<String, Buffer> event) {
                if(event.isLeft()){
                  log.error(event.left().getValue());
                  renderError(request);
                } else {
                  request.response().end(event.right().getValue());
                }
              }
            });

          } else {
            renderError(request);
          }
        }));
			}
		});
	}

}
