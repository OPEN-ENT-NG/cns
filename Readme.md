# Application Cns

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Région Hauts-de-France (ex Picardie), Département Essonne, Région Nouvelle Aquitaine (ex Poitou-Charente)

* Développeur(s) : ATOS, Edifice

* Financeur(s) : Région Hauts-de-France (ex Picardie), Département Essonne, Région Nouvelle Aquitaine (ex Poitou-Charente)

* Description : cette appplication permet de visualiser dans l'ENT ses ressources CNS. Les ressources sont récupérées via un service web, il est nécessaire de se rapprocher de l'éditeur CNS afin d'obtenir les informations nécessaires à sa configuration.

## Configuration

*Fichier json de configuration vert.x*

```javascript
"wsConfig": [{
        "domain": "",   //Domaine du site ent-core
        "endpoint": "", //Adresse du webservice
        "key": "",      //Clef unique fournie par CNS
        "platform": ""  //ID Plateforme fourni par CNS
    }] // Tableau de configuation par domaine
```

*Fichier de configuration springboard*

`wsConfig` ci-dessus correspond à la variable `cnsConfig` dans le fichier conf.properties ou test.properties.
