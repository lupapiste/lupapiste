# Korkean tason domain-kuvaus

Suomeksi

Tietomalli
Roolit

# Arkkitehtuuri yleiskuvaus
(copy paste)
front+back
fyysinen pino: front, app, mongodb, geoserver, sftp jne

# Frontend arkkitehtuuri
## Yleiskuvaus
  SPA intial startup
  Kommunikointi backendiin
    Query and Command

## Globaalit objektit
        Ajax
        Hub
        User feedback on success and error event
        Localizations
    Lupapiste Map
      Vespe
    lupapisteApp
      models
      services
    LUPAPISTE
      config
    Docgen, viittaus skeemojen määrittelyyn
## KnockoutJS käyttö
  component, model, template
  services

  Tommi

## Compass + SASS
  tyyliarkkitehtuuri / konventiot

  Vespe

## Oskari Map
  The hub between Lupapiste and Oskari Map
  (copy-paste)

## Robot Framework
  (copy-paste)
  Muista 2 välilyöntiä.

# Backend arkkitehtuuri
## Yleiskuvaus
Kerrosarkkitehtuurin kuvaus (copy-paste)
Kuva olis kiva

## Action pipeline
Routes /api/command/:name, /api/query/:name, /api/datatables/:name, /data-api/json/:name and /api/raw/:name are defined in **web.clj**.

Commands, queries (incl. datatables requests), exports (/data-api) and raw actions are then executed by the framework in **action.clj**.

Use _defcommand_, _defquery_, _defraw_ and _defexport_ macros to define new actions. Action definitions are in the following form:

    (def<action-type>
      { ; metadata map
      }

      [command] ; action argument map, supports destruction
      ; action body
    )

Actions must return a map. Commands, queries and exports return a map that contain :ok key with a boolean success value. Use sade.core/ok, fail and fail! to generate these responses.

**Note:** coding convention: define actions only in _api.clj namespaces. Remember to require the api-namespace in server.clj or web.clj, so the action is registered and the route is available on server startup.

Supported action metadata-map keys (see ActionMetadata definition in action.clj):

Key | Description
--- | -----------
user-roles | Required. Set of user role keywords. Action pipeline checks that the action is allowed for the current user before executing the action.
parameters | Vector of parameters. Parameters can be keywords or symbols. Symbols will be available in the action body. If a parameter is missing from request, an error will be raised.
optional-parameters | Vector of parameters. Same as 'parameters', but error is not raised if missing from request.
user-authz-roles | Set of application context role keywords. Action pipeline checks that the current application is accessible by the current user before executing the action.
org-authz-roles | Set of application organization context keywords. User is authorized to use action only if user has valid role in application's organization.
description | Documentation string.
notified | Boolean. Documents that the action will be sending (email) notifications.
pre-checks | Vector of functions. Functions are called with 2 parameters: action argument map (command) and current application (if any). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error.
input-validators | Vector of functions. Functions are called with 1 parameter, the action argument map (command). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error.
states | Vector of application state keywords. Action pipeline checks that the current application is in correct state before executing the action.
on-complete | Function or vector of functions. Functions are called with 2 parameters: action argument map (command) and the action execution return value map.
on-success | Function or vector of functions. Functions are called only if the action return map contains :ok true. See :on-complete.
on-fail | Function or vector of functions. Functions are called only if the action return map contains :ok false. See :on-complete.
feature | Keyword: feature flag name. Action is run only if the feature flag is true. If you have feature.some-feature properties file, use :feature :some-feature in action meta data.

Example query:
    (defquery comments
      {:parameters [id]
       :user-roles #{:applicant :authority :oirAuthority}
       :user-authz-roles action/all-authz-writer-roles
       :org-authz-roles action/commenter-org-authz-roles
       :states states/all-states}
      [{application :application}]
      (ok (select-keys application [:id :comments])))
Returns JSON to frontend: `{:ok true :data [{:id "123" :comments []}]}`


## Session
Backend delegates session handling to Ring. Semantics:

- If action return value map (see above) contains :session key, value is copied to response
- If response map contains :session key, session will be replaced to contain this new value. If value is nil, session will be deleted.
- If response map does not contain the :session key, session is left as is.

Components that wish to change the session can use sade.session namespace to merge changes to session.

Actions get the session as a part of the parameter map, under **:session** key.

Normally session contains the following keys:

- id (string): unique session id
- user (map): Current user contains keys
-- id (string): mongo-id reference to users collection
-- username (string)
-- firstName (string)
-- lastName (string)
-- role (string): "applicant", "authority", "oirAuthority", "authorityAdmin", "admin" or "trusted-etl"
-- email (string)
-- orgAuthz (map), keys are mongo-id referenced to organizations collection as Keywords, values are a set of Keywords: :authority, :tos-editor, :tos-publisher, :authorityAdmin
-- company (map):
--- id (string): mongo-id reference to companies collection
--- role (string): "user" or "admin"
-- architect (boolean)
- redirect-after-login (string): URL where browser should be redirected after login
- expires (long): timestamp, when the session expires (4h inactivity timeout)

Session cookien encryption key is read from sessionkey file (in working directory). If the file is missing, a random key will be used.

## Notifications
Kuinka lähetän sähköpostia

## Integrations
    WFS (KTJ, maastotietokanta, naapurit)
  WMTS/WMS
    KRYSP (miten keskustellaan taustajärjestelmien kanssa)
  Asianhallinta

## Tilat ja tilakone

## VETUMA

## Database
    Tietomalli (collectionit), ks. informaatioarkkitehtuuri knowledgessa

## Schemat
Antti

{
(with-doc ""
  :avain) (schema/Str)
}

## Koodauskäytännöt
- Käytetään omia apu-namespaceja (ss, mongo jne)

## Testit
unit, itest, stest

Readme-tasolle:
- unix-rivinvaihdot
- 2 spacea sisennys
- jshint
- testien ajaminen

# Laajennuspisteet

Jari

## uuden permit typen lisääminen

## uudet operaation lisääminen
### metatiedot, puu, näiden lokalisaatiot

## uuden skeeman lisääminen

## uuden liitetyypin lisääminen

## UI komponentit (ui_components.clj, auto-skannatut tiedostot ui-components hakemistossa)
