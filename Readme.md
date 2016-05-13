# Application Cns

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Conseil Régional Nord Pas de Calais - Picardie, Conseil départemental de l'Essonne, Conseil régional d'Aquitaine-Limousin-Poitou-Charentes

* Développeur(s) : ATOS

* Financeur(s) : Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes


## Configuration

*Fichier json de configuration vert.x*

```javascript
"wsConfig": {
        "endpoint": "", //Adresse du webservice
        "key": "",      //Clef unique fournie par CNS
        "platform": ""  //ID Plateforme fourni par CNS
    }
```

*Fichier de configuration springboard*

`wsConfig` ci-dessus correspond à la variable `cnsConfig` dans le fichier conf.properties ou test.properties.
