(ns lupapalvelu.mml.yhteystiedot
  (:require [sade.common-reader :as cr]
            [sade.xml :refer :all]
            [sade.common-reader :refer :all]
            [sade.util :refer [assoc-when]]
            [sade.env :as env]
            [net.cgrand.enlive-html :as enlive]
            [clojure.string :as str])
  (:import [java.net URLEncoder]))

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
    (assoc-when
      {}
      ;; OL=omaan lukuun (oletus), PA=perustettavan asunto-osakeyhtion lukuun, PY=perustettavan yhtion lukuun
      ;;:lukuuntoiminnanlaji (get-in henkilo-xml [:attrs :lukuuntoiminnanlaji])
      :henkilolaji (->henkilolaji (get-text henkilo-xml (path :henkilonTiedot :henkilolaji)))
      ;; Kuolinpesa (KP)
      :kuolinpvm (get-date henkilo-xml (path :henkilonTiedot :kuolinpvm))
      :yhteyshenkilo (->henkilo (select1 henkilo-xml :Kuolinpesa :Yhteyshenkilo))
      ;; Luonnollinen henkilo (LU)
      :etunimet (get-text henkilo-xml (path :henkilonTiedot :etunimet))
      :sukunimi (get-text henkilo-xml (path :henkilonTiedot :sukunimi))
      ;:ulkomaalainen (get-boolean henkilo-xml (path :henkilonTiedot :ulkomaalainen))
      ;:syntymapvm (get-date henkilo-xml (path :henkilonTiedot :syntymapvm))
      ;; Juridinen henkilo (JU) or Tuntematon henkilo (TU) or Valtio (VA)
      :nimi (get-text henkilo-xml (path :henkilonTiedot :nimi))
      :ytunnus (get-text henkilo-xml (path :henkilonTiedot :ytunnus))
      ;; Osoite
      :jakeluosoite (get-text henkilo-xml (path :Osoite :jakeluosoite))
      :postinumero (get-text henkilo-xml (path :Osoite :postinumero))
      :paikkakunta (get-text henkilo-xml (path :Osoite :paikkakunta)))))

(defn- get-yhteystiedot-url-template [] (env/value :mml :yhteystiedot :uri-template))

(defn- get-yhteystiedot
  ([id] (get-yhteystiedot id false))
  ([id raw?]
  (let [uri (str/replace (get-yhteystiedot-url-template) "${kohdetunnus}" (URLEncoder/encode id "UTF-8"))
        username (env/value :mml :yhteystiedot :username)
        password (env/value :mml :yhteystiedot :password)]
    (cr/get-xml uri [username password] raw?))))

(defn get-owners [id]
  (let [xml (cr/strip-xml-namespaces (get-yhteystiedot id))
        henkilot (children (select1 xml :kohteenHenkilot))]
    (map ->henkilo henkilot)))

(defn get-owners-raw [id]
  (get-yhteystiedot id true))
