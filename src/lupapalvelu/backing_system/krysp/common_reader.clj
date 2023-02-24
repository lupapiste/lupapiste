(ns lupapalvelu.backing-system.krysp.common-reader
  (:require [ring.util.codec :as codec]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.wfs :as wfs]
            [sade.common-reader :as scr]
            [sade.coordinate :as coord]
            [sade.strings :as ss]
            [sade.xml :refer [get-text select1] :as sxml]
            [sade.util :as util]))


;; Object types (URL encoded)
(def building-type    "typeName=rakval%3AValmisRakennus")
(def rakval-case-type "typeName=rakval%3ARakennusvalvontaAsia")
(def poik-case-type   "typeName=ppst%3APoikkeamisasia,ppst%3ASuunnittelutarveasia")
;; (def ya-type          "typeName=yak%3AYleisetAlueet")
(def yl-case-type     "typeName=ymy%3AYmparistolupa")
(def mal-case-type    "typeName=ymm%3AMaaAineslupaAsia")
(def vvvl-case-type   "typeName=ymv%3AVapautus")

;;(def kt-case-type-prefix  "typeName=kiito%3A")

(def rakennuksen-kiinteistotunnus "rakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun")

;; Object types as enlive selector
(def case-elem-selector #{[:RakennusvalvontaAsia]
                          [:Poikkeamisasia]
                          [:Suunnittelutarveasia]
                          [:Sijoituslupa]
                          [:Kayttolupa]
                          [:Liikennejarjestelylupa]
                          [:Tyolupa]
                          [:Ilmoitukset]
                          [:Ymparistolupa]
                          [:MaaAineslupaAsia]
                          [:Vapautus]})

(def outlier-elem-selector #{[:Lohkominen]
                             [:Rasitetoimitus]
                             [:YleisenAlueenLohkominen]
                             [:KiinteistolajinMuutos]
                             [:YhtAlueenOsuuksienSiirto]
                             [:KiinteistojenYhdistaminen]
                             [:Halkominen]
                             [:KiinteistonMaaritys]
                             [:Tilusvaihto]})

;; Only those types supported by Facta are included.
(def kt-types (let [elems (map (comp (partial str "kiito:") name)
                               [:KiinteistolajinMuutos
                                :KiinteistojenYhdistaminen
                                :Lohkominen
                                :YleisenAlueenLohkominen
                                :Rasitetoimitus])]
                (str "typeName=" (ss/join "," elems))))

(defmulti get-tunnus-xml-path
  "Get path for reading xml without ns."
  {:arglists '([permit-type search-type])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod get-tunnus-xml-path :default
  [_ search-type]
  (case search-type
    :application-id  [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto :MuuTunnus :tunnus]
    :kuntalupatunnus [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus]))

(defmethod get-tunnus-xml-path :P
  [_ search-type]
  (case search-type
    :application-id  [:luvanTunnistetiedot :LupaTunnus :muuTunnustieto :MuuTunnus :tunnus]
    :kuntalupatunnus [:luvanTunnistetiedot :LupaTunnus :kuntalupatunnus]))

(defmethod get-tunnus-xml-path :KT
  [_ _]
  [:hakemustunnustieto :Hakemustunnus :tunnus])

(defmulti get-tunnus-path
  "Get url path for fetching xmls from krysp."
  {:arglists '([permit-type search-type])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod get-tunnus-path :default
  [permit-type search-type]
  (let [prefix (permit/get-metadata permit-type :wfs-krysp-url-asia-prefix)]
    (->> (get-tunnus-xml-path permit-type search-type)
         (rest)
         (map (partial str "yht"))
         (ss/join "/")
         (str prefix))))

(defmethod get-tunnus-path :KT
  [_ _]
  "kiito:toimitushakemustieto/kiito:Toimitushakemus/kiito:hakemustunnustieto/kiito:Hakemustunnus/yht:tunnus")

(defn property-equals
  "Returns URL-encoded search parameter suitable for 'filter'"
  [property value]
  (->> (wfs/property-is-equal property value)
       (scr/strip-xml-namespaces)
       (sxml/element-to-string)
       (codec/url-encode)))

(defn property-in
  "Returns WFS 1.1.0 compatible URL-encoded search parameter suitable for 'filter'"
  [property values]
  (->> (wfs/property-in property values)
       (scr/strip-xml-namespaces)
       (sxml/element-to-string)
       (codec/url-encode)))

(defn wfs-krysp-url [server object-type filter]
  (let [server (if (ss/contains? server "?")
                 (if (ss/ends-with server "&")
                   server
                   (str server "&"))
                 (str server "?"))]
    (str server "request=GetFeature&"
         "maxFeatures=1000" ; LPK-3648
         "&" object-type "&filter=" filter)))

(defn wfs-krysp-url-with-service [server object-type filter]
  (str (wfs-krysp-url server object-type filter) "&service=WFS"))

; --- Conversion helpers

(defn get-updated-if [current to-add]
  (if to-add
    (str current to-add)
    current))

(defn str-or-nil [& v]
  (when-not (some nil? v) (reduce str v)))

(defn get-osoite [osoite]
  (-> (get-text osoite :osoitenimi :teksti)
      (get-updated-if (str-or-nil " " (get-text osoite :osoitenumero)))
      (get-updated-if (str-or-nil "\u2013" (get-text osoite :osoitenumero2)));SFS4175 stardardin mukainen valiviiva
      (get-updated-if (get-text osoite :jakokirjain))
      (get-updated-if (str-or-nil "\u2013" (get-text osoite :jakokirjain2)))
      (get-updated-if (str-or-nil " " (get-text osoite :porras)))
      (get-updated-if (str-or-nil " " (get-text osoite :huoneisto)))))

(defn ->henkilo [xml-without-ns]
  "Hetu-type is set to true iff ulkomainenHenkilotunnus contains value but hetu (finnish hetu) does not."
  (let [henkilo         (select1 xml-without-ns [:henkilo])
        hetu            (get-text henkilo :henkilotunnus)
        ulkomainen-hetu (get-text henkilo :ulkomainenHenkilotunnus)]
    {:_selected "henkilo"
     :henkilo   {:henkilotiedot {:etunimi  (get-text henkilo :nimi :etunimi)
                                 :sukunimi (get-text henkilo :nimi :sukunimi)
                                 :hetu     hetu
                                 :ulkomainenHenkilotunnus ulkomainen-hetu
                                 :turvakieltoKytkin (scr/to-boolean (get-text xml-without-ns :turvakieltoKytkin))
                                 :not-finnish-hetu (when (and (ss/blank? hetu) (ss/not-blank? ulkomainen-hetu)))}
                 :yhteystiedot  {:email     (get-text henkilo :sahkopostiosoite)
                                 :puhelin   (get-text henkilo :puhelin)}
                 :osoite        {:katu         (get-osoite (select1 henkilo :osoite))
                                 :postinumero  (get-text henkilo :osoite :postinumero)
                                 :postitoimipaikannimi  (get-text henkilo :osoite :postitoimipaikannimi)}}}))

(defn ->yritys [xml-without-ns]
  (let [yritys (select1 xml-without-ns [:yritys])]
    {:_selected "yritys"
     :yritys {:yritysnimi                             (get-text yritys :nimi)
              :liikeJaYhteisoTunnus                   (get-text yritys :liikeJaYhteisotunnus)
              :osoite {:katu                          (get-osoite (select1 yritys :postiosoite))
                       :postinumero                   (get-text yritys :postiosoite :postinumero)
                       :postitoimipaikannimi          (get-text yritys :postiosoite :postitoimipaikannimi)}
              :yhteyshenkilo (-> (->henkilo xml-without-ns) :henkilo (dissoc :osoite))}}))

(def to-projection "EPSG:3067")
(def ^String allowed-projection-prefix "EPSG:")

(defn ->source-projection [xml path]
  (let [^String source-projection-attr (sxml/select1-attribute-value xml path :srsName)                          ;; e.g. "urn:x-ogc:def:crs:EPSG:3879"
        source-projection-point-dimension (-> (sxml/select1-attribute-value xml path :srsDimension) (util/->int false))]
    (when (and source-projection-attr (= 2 source-projection-point-dimension))
      (let [projection-name-index    (.lastIndexOf source-projection-attr allowed-projection-prefix) ;; find index of "EPSG:"
            source-projection        (when (> projection-name-index -1)
                                       (subs source-projection-attr projection-name-index))          ;; rip "EPSG:3879"
            source-projection-number (subs source-projection (count allowed-projection-prefix))]
        (if (util/->int source-projection-number false)              ;; make sure the stuff after "EPSG:" parses as an Integer
          source-projection
          (throw (Exception. (str "No coordinate source projection could be parsed from string '" source-projection-attr "'"))))))))

(defn ->polygon-source-projection [alue-element]
  (let [source-projection-attr (ss/upper-case (sxml/select1-attribute-value alue-element [:Polygon] :srsName))]
    (when source-projection-attr
      (let [projection-name-index (.lastIndexOf source-projection-attr "EPSG")
            source-projection (when (> projection-name-index -1)
                                (-> (subs source-projection-attr projection-name-index)
                                    (ss/replace #".XML#" ":")))
            source-projection-number (subs source-projection (count allowed-projection-prefix))]
    (if (util/->int source-projection-number false)
      source-projection
      (throw (Exception. (str "No coordinate source projection could be parsed from string '" source-projection-attr "'"))))))))

(defn piste->coordinates
  "Returns tuple of string coordinates read from [`piste-tag` :Point :pos] selector.
   Selector is part of yht:SijaintiType"
  ([point-xml piste-tag]
   (-> point-xml
       (get-text [piste-tag :Point :pos])
       (ss/split #"\s+")))
  ([point-xml]
   (piste->coordinates point-xml :piste)))

(defn point->location-map
  "Expects xml wihtout namespace as argument, which is yht:Sijainti element of type point (`piste-tag` element exists).
  Returns a map with converted coordinates: :location key has EPSG:3067, :location-wgs84 has WGS84."
  ([point-xml piste-tag]
   (try
     (let [source-projection (->source-projection point-xml [:Point])
           coords            (piste->coordinates point-xml piste-tag)]
       (when (and source-projection (= 2 (count coords)))
         {:location       (coord/convert source-projection to-projection 3 coords)
          :location-wgs84 (coord/convert source-projection :WGS84 5 coords)}))
     (catch Exception e
       (error e "Coordinate conversion failed for point string:" (piste->coordinates point-xml piste-tag)))))
  ([point-xml]
   (point->location-map point-xml :piste)))

(defn find-valid-point
  "Returns first valid `Point` coordinates ([x y] vector) found in (namespace-stripped)
  `xml`. The optional `selector` vector can be used to prefix the search space. Note that
  `:Point` _must not_ be included in the selector. Nil if no valid point is found."
  ([xml selector]
   (some->> (concat selector [:Point])
            (sxml/select xml)
            (some (fn [elem]
                    (try
                      (let [proj   (->source-projection elem [:Point])
                            coords (some-> (get-text elem [:pos])
                                           ss/trim
                                           (ss/split #"\s+"))]
                        (when (and proj (= 2 (count coords)))
                          (let [xy (coord/convert proj to-projection 3 coords)]
                            (when-not (coord/validate-coordinates xy)
                              xy))))
                      (catch Exception _))))))
  ([xml]
   (find-valid-point xml nil)))
