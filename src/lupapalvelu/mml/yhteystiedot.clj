(ns lupapalvelu.mml.yhteystiedot
  "Integrates with \"Kiinteistötietojen kyselypalvelu (REST)\",
   see `https://www.maanmittauslaitos.fi/kiinteistotietojen-kyselypalvelu-rest`"
  (:require [clojure.string :as str]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.core :refer [fail fail!]]
            [sade.env :as env]
            [sade.util :as util]
            [sade.validators :as v]
            [sade.xml :refer :all]))

(defn- path [& p] (interpose :> (concat [enlive/root] p)))

(defn- ->henkilolaji [abbrev]
  (let [translation {:KP :kuolinpesa
                     :LU :luonnollinen
                     :JU :juridinen
                     :TU :tuntematon
                     :VA :valtio}]
    ((keyword abbrev) translation)))

(defn- ->henkilo [henkilo-xml]
  (when-not (empty? henkilo-xml)
    (util/assoc-when-pred
      {}
      util/not-empty-or-nil?
      ;; OL=omaan lukuun (oletus), PA=perustettavan asunto-osakeyhtion lukuun, PY=perustettavan yhtion lukuun
      ;; :lukuuntoiminnanlaji (get-in henkilo-xml [:attrs :lukuuntoiminnanlaji])
      :henkilolaji (->henkilolaji (get-text henkilo-xml (path :henkilonTiedot :henkilolaji)))
      ;; Kuolinpesa (KP)
      :kuolinpvm (cr/get-date henkilo-xml (path :henkilonTiedot :kuolinpvm))
      :yhteyshenkilo (->henkilo (select1 henkilo-xml :Kuolinpesa :Yhteyshenkilo))
      ;; Luonnollinen henkilo (LU)
      :etunimet (get-text henkilo-xml (path :henkilonTiedot :etunimet))
      :sukunimi (get-text henkilo-xml (path :henkilonTiedot :sukunimi))
      ;; :ulkomaalainen (get-boolean henkilo-xml (path :henkilonTiedot :ulkomaalainen))
      ;; :syntymapvm (get-date henkilo-xml (path :henkilonTiedot :syntymapvm))
      ;; Juridinen henkilo (JU) or Tuntematon henkilo (TU) or Valtio (VA)
      :nimi (get-text henkilo-xml (path :henkilonTiedot :nimi))
      :ytunnus (get-text henkilo-xml (path :henkilonTiedot :ytunnus))
      ;; Osoite
      :jakeluosoite (get-text henkilo-xml (path :Osoite :jakeluosoite))
      :postinumero (get-text henkilo-xml (path :Osoite :postinumero))
      :paikkakunta (get-text henkilo-xml (path :Osoite :paikkakunta)))))

(defn- get-yhteystiedot-url-template
  [system]
  {:pre [(map? system)]}
  (-> system :config :mml :yhteystiedot :uri-template))

(defn yhteystiedot-configured
  [{:keys [system]}]
  {:pre [(map? system)]}
  (when-not (string? (get-yhteystiedot-url-template system))
    (fail :error.no-uri-template)))

(defn- get-yhteystiedot
  ([system property-id] (get-yhteystiedot system property-id false))
  ([system property-id raw?]
   (let [template (or (get-yhteystiedot-url-template system)
                      (fail! :error.no-uri-template))
         uri      (str/replace template "${kohdetunnus}" property-id)
         options  {:http-error :error.integration.ktj-down, :connection-error :error.integration.ktj-down}
         username (env/value :mml :yhteystiedot :username)
         password (env/value :mml :yhteystiedot :password)]
     (cr/get-xml uri options [username password] raw?))))

(defn get-owners [system property-id]
  {:pre [(v/kiinteistotunnus? property-id)
         (map? system)]}
  (let [xml      (cr/strip-xml-namespaces (get-yhteystiedot system property-id))
        henkilot (children (select1 xml :kohteenHenkilot))]
    (map ->henkilo henkilot)))
