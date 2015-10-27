CnsController = ($scope, $http, xmlHelper) ->

    $scope.me = model.me
    $scope.data = {}
    $scope.targetResource = null

    $scope.popup = (resource) ->
        $scope.targetResource = resource

    InitUserRessourcesCatalog = (uai, dataObj, hook) ->
        $http.get "/cns/InitUserRessourcesCatalog", {params: {uai: uai}}
            .success (data) ->
                try
                    xmlDocument = $.parseXML(data)
                    codeRetour = $(xmlDocument).find('messageRetour')[0].textContent

                    if codeRetour isnt "OK"
                        console.error codeRetour
                        dataObj.loading = false
                    else
                        dataObj.typeSSO = $(xmlDocument).find('TypeSSO')[0].textContent
                        if hook then hook?(uai, dataObj) else dataObj.loading = false
                catch error
                    dataObj.loading = false
                    console.error error

            .error (data, status) ->
                dataObj.loading = false

    UserRessourcesCatalog = (uai, dataObj) ->
        $http.get "/cns/UserRessourcesCatalog", {params: {uai: uai, typesso: dataObj.typeSSO}}
            .success (data) ->
                try
                    xmlDocument = $.parseXML(data)
                    codeRetour = $(xmlDocument).find('messageRetour')[0].textContent
                    if codeRetour isnt "OK"
                        console.error codeRetour
                    else
                        dataObj.resources = []
                        _.each $(xmlDocument).find('ressources').children(), (ressource) ->
                            dataObj.resources.push xmlHelper.xmlToJson(ressource).Ressource

                    dataObj.loading = false
                catch error
                    dataObj.loading = false
                    console.error error

            .error (data, status) ->
                dataObj.loading = false

    $scope.selectStructure = (uai) ->
        $scope.selectedUai = uai

        if not $scope.data[uai].resources
            $scope.data[uai].loading = true
            InitUserRessourcesCatalog uai, $scope.data[uai], UserRessourcesCatalog

    for uai, i in model.me.uai
        $scope.data[uai] =
            name: model.me.structureNames[i]
        #On display, select first UAI
        $scope.selectStructure uai if i is 0
        #InitUserRessourcesCatalog uai, $scope.data[uai], UserRessourcesCatalog
    @
