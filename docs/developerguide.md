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
   Ks. JSON APIs
   Joni
   
## Session
   
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
