# ALLU-integraation evoluutio

ALLU on Helsingin YA:n uusi taustajärjestelmä, johon Lupapiste integroituu kutsumalla JSON REST APIa.

## Just Make it Work

* Suoraviivainen lähtökohta: funktioita, jotka käyttävät suoraan HTTP clientiä.

```clojure
(ns lupapalvelu.backing-system.allu.core)

(defn submit-application! [{:keys [application] :as command}]
  (let [response (http/post (str (env/value :allu :url) "/placementcontracts")
                            {:throw-exceptions false
                             :headers {"authorization" (str "Bearer " (env/value :allu :jwt))}
                             :content-type :json
                             :body (->> application
                                        (application->allu-placement-contract true)
                                        json/encode)})]
    (match response
      {:status (:or 200 201), :body body} (application/set-integration-key (:id application) :ALLU {:id body})
      response (fail! :error.allu-http (select-keys response [:status :body])))))
```

* Eri ympäristöt (Prod, QA, devauskoneet) konfiguroitu osoittamaan prod/staging/testi-ALLUun normaalisti `sade.env`
  teknologialla.

## Multiple Personalities Appear

* Haaste: Integraatio pitää voida korvata tyngillä interaktiivista kehitystä sekä yksikkö- ja integraatiotestejä varten.
* Ratkaisu: `binding`, `alter-var-root` jne.
* Liikkuvien osien vähentämiseksi integraatiofunktiot kerättiin protokollaan, joka on ainoa Clojuren "ymmärtämä"
  rajapinnan käsite.

```clojure
(defprotocol ALLUIntegration
  (submit-application! [self command])
  (approve-application! [self command])
  ...)

(deftype RemoteALLU
  ALLUIntegration
  ...)

(def allu-instance (->RemoteALLU)) ; "Sauma" testejä jne. varten
```

## Concerns Cut Across

* Haaste: Kaikkien integraatiofunktioiden täytyy
    - Tehdä pyynnöstä JSONia ja lukea vastaus-JSON
    - Lisätä pyyntöön Authorization ym. HTTP headerit
    - Käsitellä HTTP virheet
    - Logittaa
    - Tallentaa pyynnöt ja vastauksen Mongon integration-messages -kokoelmaan
* Don't Repeat Yourself
* Ratkaisu: Kuorruttaja-malli (Decorator Pattern)

```clojure
(deftype LoggingALLU [inner-allu]
  ALLUIntegration
  (submit-application! [_ command]
    (let [response (submit-application! inner-allu command)]
      (when (contains? #{200 201} (:status response))
        (info "ALLU operation submit-application! succeeded."))
      response))
  ...)

(def allu-instance (->LoggingALLU (... (->RemoteALLU) ...)))
```

## Oh nOh, OO! So Dysfunctional

* Haaste: Integraatioviestit halutaan ajaa JMS-jonon kautta
    - Uudelleenlähetys
    - Dead Letter Queue ja manuaalinen uudelleenlähetys
    - Hakemuspäivitysten debounce (toteuttamatta, LPK-3875)
* JMS-sarjallistuksen takia viestien pitää olla dataa alusta alkaen. Samalla hieman nolo Kuorruttaja-malli muuttuu
  middleware-funktioiksi.

```clojure
(defn remote-allu-handler [request] ...)

(defn logging-middleware [handler]
  (fn [request] ...))

(def allu-request-handler (logging-middleware (... remote-allu-handler ...)))

(defn allu-jms-msg-handler ...)

(defstate allu-jms-session ...)
(defstate allu-jms-consumer ...)
```

## Concerns Poke Around

* Haaste: Tiettyjen integraatiotoimintojen pitää esim.
    - Käyttää HTTP multipart-parametreja
    - Tallentaa ALLUsta saatuja pdf:iä
* Halutaan omaan middlewareen (Single Responsibility Principle), mutta käyttää vain tiettyjen ALLU APIn reittien ollessa
  kyseessä. SRP:n mukaan myös middlewaret, jotka tekevät/eivät tee asioita pyynnön reitin perusteella,
  ovat epätoivottavia.
* Ratkaisu: `metosin/reitit` mahdollistaa reitti-spesifiset middlewaret ja hierarkkiset reitit. Se on myös tarpeeksi
  geneerinen käytettäväksi "takaperin" HTTP-clientin tekemiseen serverin sijaan. Lisäksi saadaan URL- ym. parametrit
  interpoloitua pyyntöihin aiempaa nätimmin alun perin fronttireitityksen tarpeisiin tehdyillä ominaisuuksilla
  ('reverse routing').

Katso `lupapalvelu.backing-system.allu.core/routes`.

## Iffy Call-sites All Around

* Haaste: Integraatiofunktioita kutsutaan actioneista, mutta tämä ei saisi aiheuttaa `allu-application?` iffittelyn
  tulvaa.
* Ratkaisu: Tehdään taustajärjestelmäintegraatiosta abstraktio;
  `lupapalvelu.backing-system.core/BackingSystem`-protokolla.
    - Toteutus asianhallinta-integraatiolle ja erinäisiä operaatioita puuttuu vielä.
    - Tämä olisi pitänyt muutenkin tehdä jo kauan sitten.
    - Operaatiot syövät paljon dataa, koska ensi hätään rajapinnaksi "pienin yhteinen monikerta"
