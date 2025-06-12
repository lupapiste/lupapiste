(ns lupapalvelu.mml.property.client
  "API of the MML \"Kiinteistötietojen kyselypalvelu (OGC API Features)\".

  Required environmental variables:
  - mml.property-api.username
  - mml.property-api.password
  - mml.property-api.url

  The OGC API has several variations (each with their own URL) depending on whether the variation requires
  a contractor or which services (\"Aineistot\") it provides. For our purposes the relevant URLs are:
  - Testing:    https://koekaytto-paikkatieto.maanmittauslaitos.fi/kiinteisto-luvanvarainen/features/v3
  - Production: https://sopimus-paikkatieto.maanmittauslaitos.fi/kiinteisto-luvanvarainen/features/v3"
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.mml.property.protocol :as api]
            [lupapalvelu.mml.property.util :as property-util]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def ^:private service->path
  "Different services (\"Aineistot\") provide different properties and may or may not include geometries.
  On the MML website, look at for example:
   - Kiinteistötietojen rajapintapalvelut
   -- Kiinteistötietojen kyselypalvelu (OGC API Features)
   --- Tekninen kuvaus Luvanvaraiset tuotteet (sopimus) OR Tekninen kuvaus Avoimet tuotteet (sopimus)
   ---- Aineistot ja tuotteet"
  {;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/PalstanSijaintitiedot_20201005_1.pdf
   ::lots-location      "/collections/PalstanSijaintitiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/ProjisoidunPalstanSijaintitiedot_20201005_1.pdf
   ::lots-proj-location "/collections/ProjisoidunPalstanSijaintitiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/KiinteistotunnuksenSijaintitiedot_20201005_1.pdf
   ::properties         "/collections/KiinteistotunnuksenSijaintitiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/RekisteriyksikonLaajatSijaintitiedot_20201005_1.pdf
   ::units-location     "/collections/RekisteriyksikonLaajatSijaintitiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/RekisteriyksikonPerustiedot_20201005_1.pdf
   ::units-basic        "/collections/RekisteriyksikonPerustiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/RekisteriyksikonLaajatSijaintitiedot_20201005_1.pdf
   ::units-extensive    "/collections/RekisteriyksikonLaajatSijaintitiedot/items"
   ;; https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/RekisteriyksikonAvoimetOminaisuustiedot_20201005_1.pdf
   ::units-properties   "/collections/RekisteriyksikonAvoimetOminaisuustiedot/items"})

(def ^:private default-params
  {:crs         "http://www.opengis.net/def/crs/EPSG/0/3067"
   :filter-crs  "http://www.opengis.net/def/crs/EPSG/0/3067"
   :filter-lang "cql2-text"})

(defn- run-query!
  [{:keys [service query]} client]
  (let [args {:api    :property-api
              :path   (service->path service)
              :accept "application/geo+json"
              :query  (merge query default-params)}]
    (client args)))

(defn- query-by-property-id
  [property-id]
  {:kiinteistotunnus property-id})

(defn- query-by-geometry
  [{:keys [geometry function]
    :or   {function "S_INTERSECTS"}}]
  ;; TODO: The old WFS implementations manipulates the geometry and
  ;; e.g. removes holes from polygons. At the moment we don't match
  ;; that behaviour.
  {:filter (format "%s(geometry,%s)" function geometry)})

(defn- with-buffer
  [query buffer]
  (update query :geometry (fn [geometry] (format "BUFFER(%s, %s)" geometry buffer))))

(defn- filter-within
  [query]
  (assoc query :function "S_WITHIN"))

(defn- query-by-point
  [x y]
  (query-by-geometry {:geometry (format "POINT (%s %s)"
                                        (property-util/coord->str x)
                                        (property-util/coord->str y))}))

(defn- unit-properties-response->properties
  [{:keys [features]}]
  (->> features
       (map :properties)
       (map (fn [{:keys                     [kiinteistotunnus]
                  [{:keys [kuntatunnus
                           nimiSuomeksi
                           nimiRuotsiksi]}] :kunta}]
              {:propertyId   kiinteistotunnus
               :municipality kuntatunnus
               :name         {:fi nimiSuomeksi
                              :sv nimiRuotsiksi}}))))

(defn- property-infos-by-property-id!
  [client property-id]
  (-> {:service ::units-properties
       :query   (query-by-property-id property-id)}
      (run-query! client)
      unit-properties-response->properties))

(defn- property-infos-by-point!
  [client x y]
  (-> {:service ::units-properties
       :query   (query-by-point x y)}
      (run-query! client)
      unit-properties-response->properties))

(defn- lot-response->property-ids
  [{:keys [features]}]
  (->> features
       (map (comp :kiinteistotunnus :properties))))

(defn- lot-property-ids-by-point!
  [client x y]
  (-> {:service ::lots-location
       :query   (query-by-point x y)}
      (run-query! client)
      lot-response->property-ids))

(defn- lot-response->lots
  [{:keys [features]}]
  (->> features
       (map (juxt :id :geometry :properties))
       (map (fn [[id geom {{[x y] :coordinates} :kiinteistotunnuksenSijainti
                           :keys                [kiinteistotunnus]}]]
              {:kiinttunnus kiinteistotunnus
               :lot-id      (str id)
               :x           (property-util/coord->str x)
               :y           (property-util/coord->str y)
               :wkt         (property-util/geojson->wkt geom)}))))

(defn- default-lots-by-property-id!
  [client property-id]
  (-> {:service ::lots-location
       :query   (query-by-property-id property-id)}
      (run-query! client)
      lot-response->lots))

(defn- proj-lots-by-property-id!
  [client property-id]
  (-> {:service ::lots-proj-location
       :query   (query-by-property-id property-id)}
      (run-query! client)
      lot-response->lots))

(defn- lots-by-property-id!
  [client property-id]
  ;; Search for normal lots first and fall back
  ;; to 3D->2D projected lots
  (or (not-empty (default-lots-by-property-id! client property-id))
      (proj-lots-by-property-id! client property-id)))

(defn- unit-basic-response->properties
  [{:keys [features]}]
  (->> features
       (map (juxt :id :properties))
       (map (fn [[id {:keys                     [kiinteistotunnus
                                                 rekisteriyksikkolaji]
                      [{:keys [kuntatunnus
                               nimiSuomeksi
                               nimiRuotsiksi]}] :kunta}]]
              (let [unit-type (str rekisteriyksikkolaji)]
                {:id                   id
                 :kiinttunnus          kiinteistotunnus
                 :kunta                kuntatunnus
                 :nimi                 {:fi nimiSuomeksi
                                        :sv nimiRuotsiksi}
                 :rekisteriyksikkolaji {:id     unit-type
                                        :selite (get property-util/rekisteriyksikkolaji unit-type)}})))))

(defn- find-lot-by-id
  [lots property-id]
  ;; Matching using the property id is not ideal. If the response contains
  ;; multiple lots, it is not uncommon for all the lots to share the same
  ;; property id.
  ;; e.g. query for "POINT(409530.28750612 6687129.0994414)"
  ;; Note that the caller may separately query for the individual
  ;; lot geometries with `lots-by-property-id` (as is done in the neighbour search ui).
  (->> lots
       (filter (fn [lot]
                 (= property-id (:kiinteistotunnus lot))))
       first))

(defn- unit-extensive-response->properties
  [{:keys [features]}]
  (->> features
       (map (juxt :id :properties))
       (map (fn [[id {:keys     [kiinteistotunnus]
                      lots      :rekisteriyksikonLaajatSijaintitiedotPalsta
                      ;; On very rare occasions we only get the 3D->2D projected lot
                      proj-lots :rekisteriyksikonLaajatSijaintitiedotProjisoituPalsta}]]
              (let [{:keys [geometry] :as lot} (or (find-lot-by-id lots kiinteistotunnus)
                                                   (find-lot-by-id proj-lots kiinteistotunnus))
                    [x y]                      (-> lot :kiinteistotunnuksenSijainti :coordinates)]
                {:id  id
                 :wkt (property-util/geojson->wkt geometry)
                 :x   (property-util/coord->str x)
                 :y   (property-util/coord->str y)})))))

(defn ->map-by-id
  [responses]
  (->> responses
       (map (juxt :id identity))
       (into {})))

(defn- property-infos-by-wkt!
  [client wkt radius]
  ;; Unfortunately the `RekisteriyksikonPerustiedot` service does not contain the geometry
  ;; (or I don't know how to ask for it) but we can also query the `RekisteriyksikonLaajatSijaintitiedot`
  ;; which does and merge the results.
  (let [;; For backwards compatibility: only use a buffer and S_WITHIN if we're doing a point search.
        use-buffer?   (and radius (ss/starts-with? wkt "POINT"))
        query         (cond-> {:geometry wkt}
                        use-buffer? (with-buffer radius)
                        use-buffer? (filter-within)
                        true        (query-by-geometry))
        basic-data    (-> {:service ::units-basic
                           :query   query}
                          (run-query! client)
                          unit-basic-response->properties
                          ->map-by-id)
        location-data (-> {:service ::units-extensive
                           :query   query}
                          (run-query! client)
                          unit-extensive-response->properties
                          ->map-by-id)]
    (when-not (= (set (keys basic-data))
                 (set (keys location-data)))
      (log/warn "Basic data points do not match the location data!"))
    (->> basic-data
         (keep (fn [[id basic]]
                 (when-let [location (get location-data id)]
                   (-> (merge basic location)
                       (dissoc :id))))))))

(def ^:private ogc-date-formatter (date/make-parser "yyyy-MM-dd"))
(def ^:private output-date-formatter (date/pattern-formatter property-util/registration-date-format))

(defn- format-registration-date
  [date]
  (try
    (some-> date
            (date/zoned-parse ogc-date-formatter)
            (date/zoned-format output-date-formatter))
    (catch Exception _
      nil)))

(defn square-meters->hectares-str
  [area-in-sq-meters]
  (when area-in-sq-meters
    (ss/format-fixed "%.4f" (/ area-in-sq-meters 10000.0))))

(defn- unit-basic-response->details
  [{:keys [features]}]
  (->> features
       (map :properties)
       (map (fn [{:keys [nimi
                         kiinteistotunnus
                         kokonaispintaala
                         maapintaala
                         vesipintaala
                         olotila
                         rekisterointipvm
                         rekisteriyksikkolaji]}]
              (util/assoc-when
                {:kiinteistotunnus     kiinteistotunnus
                 :olotila              (str olotila)
                 :rekisterointipvm     (format-registration-date rekisterointipvm)
                 :rekisteriyksikkolaji (str rekisteriyksikkolaji)}
                :nimi                 (when-not (ss/blank? nimi)
                                        nimi)
                :maapintaala (square-meters->hectares-str (or maapintaala kokonaispintaala))
                :vesipintaala (square-meters->hectares-str vesipintaala))))))

(defn- property-details-by-id!
  [client id]
  (-> {:service ::units-basic
       :query   (query-by-property-id id)}
      (run-query! client)
      unit-basic-response->details))

(defrecord ApiClient [http-client]
  api/PropertyApi

  (-property-info-by-id
    [_ property-id]
    (first (property-infos-by-property-id! http-client property-id)))

  (-property-infos-by-wkt
    [_ wkt radius]
    (into [] (property-infos-by-wkt! http-client wkt radius)))

  (-property-infos-by-point
    [_ x y]
    (into [] (property-infos-by-point! http-client x y)))

  (-property-details-by-id
    [_ property-id]
    (first (property-details-by-id! http-client property-id)))

  (-lot-property-id-by-point
    [_ x y]
    (first (lot-property-ids-by-point! http-client x y)))

  (-lots-by-property-id
    [_ property-id]
    (into [] (lots-by-property-id! http-client property-id))))
