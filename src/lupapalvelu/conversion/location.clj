(ns lupapalvelu.conversion.location
  "Functionality for handling conversion locations (coordinates, addresses and property ids)."
  (:require [clojure.set :as set]
            [lupapalvelu.application :as app]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.property-location :refer [property-id-muni-by-point]]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.property :as prop]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [schema.core :as sc]))

(defn- check [m]
  (when-not (conv-sc/check-location m)
    m))

(defn tidy-location [m]
  (-> m
      (update :x conv-util/->double)
      (update :y conv-util/->double)
      ss/trimwalk
      util/strip-blanks))

(sc/defn valid-xy-or-nil :- (sc/maybe ssc/Location)
  ([x y source-projection]
   (let [[x y] (cond->> (mapv conv-util/->double [x y])
                 source-projection
                 (coord/convert source-projection "EPSG:3067" 2))]
     (check {:x x :y y})))
  ([x y]
   (valid-xy-or-nil x y nil))
  ([{:keys [x y source-projection]}]
   (valid-xy-or-nil x y source-projection)))

(defn- property-id-or-nil [s]
  (let [pid (util/pcond-> (or (ss/trim s) "")
              #(re-matches prop/property-id-pattern %)
              prop/->db-property-id)]
    (when (v/kiinteistotunnus? pid)
      pid)))

(sc/defn process-location :- (sc/maybe conv-sc/ConversionLocation)
  [{:keys [propertyId address] :as m}]
  (try
    (when-let [xy (valid-xy-or-nil m)]
      (-> xy
          (util/assoc-when
            :propertyId (or (property-id-or-nil propertyId)
                            (some-> (property-id-muni-by-point xy)
                                    :propertyId
                                    property-id-or-nil))
            :address (some-> address ss/trim ss/blank-as-nil))
          tidy-location))
    (catch Exception e
      (log/error (ex-message e) m))))

(def ^:private override-keys [:backend-id :x :y :propertyId :address])

(def ColumnKeys {(apply sc/enum override-keys) sc/Keyword})

(sc/defn ^:always-validate make-location-override-map :- conv-sc/LocationOverrides
  "Convenience function for making `conv-sc/LocationOverrides` map. `xs` is list of
  maps (typically originally from a csv or an excel file) and `column-keys` is a mapping
  from item keys to proper location keys. If `source-projection` (EPSG:xxxx) is given the
  coordinates are converted to the Lupapiste native EPSG:3067."
  ([xs
    column-keys :- (sc/maybe ColumnKeys)
    source-projection :- (sc/maybe ssc/NonBlankStr)]
   (let [column-keys (when-not (empty? column-keys)
                       (zipmap (vals column-keys)
                               (keys column-keys)))]
     (->> xs
          (keep (fn [m]
                  (let [m   (cond-> m
                              column-keys       (set/rename-keys column-keys)
                              source-projection (assoc :source-projection source-projection))
                        lox (process-location m)]
                    (when lox
                      [(:backend-id m) lox]))))
          (into {})
          not-empty)))
  ([xs column-keys]
   (make-location-override-map xs column-keys nil))
  ([xs]
   (make-location-override-map xs nil nil)))

;; -------------------------------
;; Fix locations batchrun
;; -------------------------------

(defn- =location
  "The ordering is important, since only the keys in the `new` location are considered."
  [old new]
  (if-let [new (not-empty (tidy-location new))]
    (= (select-keys (tidy-location old) (keys new)) new)
    (throw (ex-info "No new location given" {:new new}))))

(def ^:private not=location (complement =location))

(sc/defn ^:private ^:always-validate application-location
  :- (sc/maybe conv-sc/ConversionLocation)
  [document]
  (when-let [a (conv-doc/read-application document [:address :location
                                                    :propertyId])]
    (merge (zipmap [:x :y] (:location a))
           (select-keys a [:address :propertyId]))))

(defn- update-application-location
  "We always update every location field + `location-wgs84`."
  [application-id {:keys [x y] :as m}]
  (log/with-logging-context {:applicationId application-id}
    (log/debug "Relocate to" m)
    (->> (mapv conv-util/->double [x y])
         app/location-map ; `:location` and `location-wgs84`.
         (merge (select-keys m [:address :propertyId]))
         (hash-map $set)
         (mongo/update-by-id :applications application-id))))

(defn- resolve-new-location
  [{fallback :location-fallback
    relax    :location-relax}
   doc
   new-location]
  (let [app-lox  (application-location doc)
        app-xy   (valid-xy-or-nil app-lox)
        reset-xy (valid-xy-or-nil (:reset-location doc))
        doc-lox  (tidy-location (:location doc))
        fall-xy  (valid-xy-or-nil fallback)
        new-lox  (tidy-location new-location)
        new-xy   (valid-xy-or-nil new-lox)]
    (cond
      ;; No new coordinates
      (nil? new-xy)                                  nil
      ;; Application location changed by the user
      (and app-xy reset-xy (not= app-xy reset-xy))   nil
      ;; No reset reference (explicit or implicit) and no relaxing.
      (and app-xy (not (or reset-xy fall-xy relax))) nil
      ;; No need to change application location
      (and app-xy (=location app-lox new-lox))       nil
      ;; Application location changed
      (and app-xy (or (= app-xy (or reset-xy fall-xy))
                      (and relax (not (or reset-xy fall-xy)))))
      {:application (merge app-lox new-lox)}
      ;; Application has location but is not changed
      app-xy                                         nil
      ;; Document location changed
      (not=location doc-lox new-lox)
      {:document (merge doc-lox new-lox)})))

(sc/defn fix-location
  "Updates document and application (if converted) location to `location`. If not
  converted then the existing document `location` is just merged into the `new-location`
  if there is a difference. However, if there exists a converted application the location
  is updated only if the current location is different from `new-location` AND the
  location has not been changed by a user. Since the location changer information is not
  stored anywhere, the latter situation needs to be deduced from from the conversion
  document (`reset-location`) or the configuration (`:location-fallback`). In other words,
  we assume that the location has not been changed if it matches either one.

  Since the older conversion documents might not have `reset-location` field, their
  location is changed only if the current location matches the default/fallback
  location. This functionality can be overridden with `location-relax` configuration
  option that ignores missing `reset-location` properties."
  [cfg :- conv-sc/ConversionConfiguration
   {app-id :LP-id :as document} :- conv-sc/ConversionDocument
   new-location :- conv-sc/ConversionLocation]
  (when-let [{app-lox :application
              doc-lox :document} (resolve-new-location cfg document new-location)]
    (some->> app-lox (update-application-location app-id))
    (conv-doc/upsert-document :fix-locations
                              :document document
                              :location (or app-lox doc-lox))))
