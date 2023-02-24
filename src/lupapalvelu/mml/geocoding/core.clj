(ns lupapalvelu.mml.geocoding.core
  (:require [lupapalvelu.mml.geocoding.client :as client]
            [lupapalvelu.mml.geocoding.util :as geo-util]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def default-crs client/crs-etrs-tm32fin)

(def default-query {:crs         default-crs
                    :request-crs default-crs})

(defn- properties->primary-address
  [properties lang]
  (let [target-lang (if (contains? #{"sv" "se"} lang)
                      "swe" "fin")]
    (if (= target-lang (:osoite.Osoite#2.kieli properties))
      {:street (:osoite.Osoite#2.katunimi properties)
       :number (or (:osoite.Osoite#2.katunumero properties)
                   (:osoite.Osoite#2.jarjestysnumero properties))}
      {:street (:osoite.Osoite.katunimi properties)
       :number (or (:osoite.Osoite.katunumero properties)
                   (:osoite.Osoite.jarjestysnumero properties))})))

(defn- feature->address
  [{properties           :properties
    {[x y] :coordinates} :geometry}]
  {:municipality (:kuntatunnus properties)
   :location     {:x x
                  :y y}
   :name         {:fi (:kuntanimiFin properties)
                  :sv (:kuntanimiSwe properties)}})

(defn- reverse-search-feature->address
  [lang {properties :properties
         :as        feature}]
  (-> (merge (feature->address feature)
             (properties->primary-address properties lang))
      util/strip-blanks
      util/strip-empty-maps))

(sc/defschema AddressDetails
  {:municipality ssc/DecimalString
   :street       ssc/NonBlankStr
   :number       ssc/NonBlankStr
   :name         {:fi ssc/NonBlankStr
                  :sv ssc/NonBlankStr}
   :location     ssc/Location})

(sc/defn address-by-point! :- (sc/maybe AddressDetails)
  "Find matching building address by point"
  ([lang {x :x y :y}]
   (address-by-point! lang x y))
  ([lang :- client/Language
    x :- ssc/LocationX
    y :- ssc/LocationY]
   (some->> (merge default-query
                   {:point.lon x
                    :point.lat y
                    ;; Query building addresses as that provides more
                    ;; relevant results than searching for the neareast road/street
                    :sources   [client/source-building-addresses]
                    :size      1
                    :lang      lang})
            client/reverse-search!
            :features
            first
            (reverse-search-feature->address lang))))

(defn- forward-search-feature->address
  [{properties :properties
    :as        feature}]
  (-> (feature->address feature)
      (merge {:street (:katunimi properties)
              :number (or (:katunumero properties)
                          (:jarjestysnumero properties))})
      util/strip-blanks
      util/strip-empty-maps))

(sc/defschema FindFeatureQuery
  (assoc (st/optional-keys
           {:lang              client/Language
            :sources           [client/Source]
            :options           [sc/Keyword]
            :size              ssc/PosInt
            :throw-on-failure? sc/Bool})
         :text ssc/NonBlankStr))

(sc/defn find-feature!
  [{:keys [options] :as query} :- FindFeatureQuery]
  (some->> (merge default-query
                  {:lang              "fi"
                   :sources           [client/source-road-addresses]
                   :throw-on-failure? false}
                  query
                  (when (seq options)
                    {:options (ss/join-non-blanks "," (map name options))}))
           util/strip-blanks
           client/forward-search!
           :features))

(sc/defschema AddressByNameQuery
  (st/merge
    {:street ssc/NonBlankStr}
    (st/optional-keys
      {:number       ssc/NonBlankStr
       :city         ssc/NonBlankStr
       :postal-code  ssc/Zipcode})))

(sc/defn address-by-text! :- [AddressDetails]
  ([query]
   (address-by-text! "fi" false query))
  ([lang query]
   (address-by-text! lang false query))
  ([lang :- client/Language
    throw-on-failure? :- sc/Bool
    {:keys [postal-code] :as query} :- AddressByNameQuery]
   (some->> (find-feature! {:text              (geo-util/query->search-text query)
                            :sources           [(if postal-code
                                                  client/source-building-addresses
                                                  client/source-road-addresses)]
                            :options           (when postal-code
                                                 [:use_postal_code :use_any_codelist_lang_match])
                            :size              100
                            :lang              lang
                            :throw-on-failure? throw-on-failure?})
            (map forward-search-feature->address)
            geo-util/sort-addresses
            (take 10))))

(sc/defn address-by-property-id! :- (sc/maybe AddressDetails)
  "Location via `property-id` and the address details via the resolved location."
  ([property-id]
   (address-by-property-id! "fi" false property-id))
  ([lang  property-id]
   (address-by-property-id! lang false property-id))
  ([lang throw-on-failure? property-id]
   (some->> (find-feature! {:lang              lang
                            :throw-on-failure? throw-on-failure?
                            :text              property-id
                            :sources           [client/source-cadastral-units]
                            :options           [:nowildcard :use_any_codelist_lang_match]
                            :size              1})
            first :geometry :coordinates
            (apply address-by-point! lang))))


(comment
  (address-by-point! "fi" 309895.789 6649487.765)
  (address-by-text! "fi" {:street "lukinkat" :number "7"})
  (address-by-property-id! "52950200010158")
  (address-by-property-id! "529-502-1-158"))
