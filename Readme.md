# Application Cns

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
