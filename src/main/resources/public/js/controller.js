var CnsController;

CnsController = function($scope, $http, xmlHelper) {
  var InitUserRessourcesCatalog, UserRessourcesCatalog, i, j, len, ref, uai;
  $scope.me = model.me;
  $scope.data = {};
  $scope.targetResource = null;
  $scope.popup = function(resource) {
    return $scope.targetResource = resource;
  };
  InitUserRessourcesCatalog = function(uai, dataObj, hook) {
    return $http.get("/cns/InitUserRessourcesCatalog", {
      params: {
        uai: uai
      }
    }).success(function(data) {
      var codeRetour, error, xmlDocument;
      try {
        xmlDocument = $.parseXML(data);
        codeRetour = $(xmlDocument).find('messageRetour')[0].innerHTML;
        if (codeRetour !== "OK") {
          console.log(codeRetour);
          return dataObj.loading = false;
        } else {
          dataObj.typeSSO = $(xmlDocument).find('TypeSSO')[0].innerHTML;
          if (hook) {
            return typeof hook === "function" ? hook(uai, dataObj) : void 0;
          } else {
            return dataObj.loading = false;
          }
        }
      } catch (_error) {
        error = _error;
        dataObj.loading = false;
        return console.log(error);
      }
    }).error(function(data, status) {
      return dataObj.loading = false;
    });
  };
  UserRessourcesCatalog = function(uai, dataObj) {
    return $http.get("/cns/UserRessourcesCatalog", {
      params: {
        uai: uai,
        typesso: dataObj.typeSSO
      }
    }).success(function(data) {
      var codeRetour, error, xmlDocument;
      try {
        xmlDocument = $.parseXML(data);
        codeRetour = $(xmlDocument).find('messageRetour')[0].innerHTML;
        if (codeRetour !== "OK") {
          console.log(codeRetour);
        } else {
          dataObj.resources = [];
          _.each($(xmlDocument).find('ressources').children(), function(ressource) {
            return dataObj.resources.push(xmlHelper.xmlToJson(ressource).Ressource);
          });
        }
        return dataObj.loading = false;
      } catch (_error) {
        error = _error;
        dataObj.loading = false;
        return console.log(error);
      }
    }).error(function(data, status) {
      return dataObj.loading = false;
    });
  };
  $scope.selectStructure = function(uai) {
    $scope.selectedUai = uai;
    if (!$scope.data[uai].resources) {
      $scope.data[uai].loading = true;
      return InitUserRessourcesCatalog(uai, $scope.data[uai], UserRessourcesCatalog);
    }
  };
  ref = model.me.uai;
  for (i = j = 0, len = ref.length; j < len; i = ++j) {
    uai = ref[i];
    $scope.data[uai] = {
      name: model.me.structureNames[i]
    };
    if (i === 0) {
      $scope.selectStructure(uai);
    }
  }
  return this;
};
