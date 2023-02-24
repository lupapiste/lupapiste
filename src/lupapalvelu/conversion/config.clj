(ns lupapalvelu.conversion.config
  "Conversion configuration processing."
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.backing-system.krysp.common-reader :refer [get-tunnus-xml-path]]
            [lupapalvelu.conversion.schemas :refer [ConversionConfiguration
                                                    ConversionEdn ConversionLocation
                                                    ResolvedTarget]]
            [lupapalvelu.mongo :as mongo]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.property :as prop]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]
            [taoensso.timbre :refer [error]]))

(defn- find-xml-ids [permit-type xml-file-or-filename]
  (let [xml (-> (slurp xml-file-or-filename)
                (xml/parse-string "utf8")
                strip-xml-namespaces)]
    (util/strip-nils
      {:backend-id     (some->> (get-tunnus-xml-path permit-type :kuntalupatunnus)
                                (xml/get-text xml))
       :application-id (some->> (get-tunnus-xml-path permit-type :application-id)
                                (xml/get-text xml))})))

(sc/defn ^:always-validate resolve-filepath :- ssc/NonBlankStr
  "Returns absolute version of `filename`. If `filename` is relative it is resolved in
  relation to `reference` filename."
  [ reference :- sc/Str
   filename   :- ssc/NonBlankStr]
  (let [reference (ss/trim reference)
        filename  (ss/trim filename)
        resolved  (if (or (fs/absolute? filename) (ss/blank? reference))
                    filename
                    (str (cond-> reference
                           (not (fs/directory? reference)) fs/parent)
                         "/"
                         filename))]
    (str (fs/absolute resolved))))

(sc/defn ^:always-validate resolve-target
  :- (sc/maybe ResolvedTarget)
  [{:keys [permit-type organization-id]}
   target-type :- (sc/enum :id :filename)
   target      :- sc/Str]
  (try
    (let [m (if (= target-type :id)
              {:id target}
              (let [{:keys [backend-id
                            application-id]} (find-xml-ids permit-type target)]
                (util/assoc-when {:id       backend-id
                                  :filename target}
                                 :xml-application-id application-id)))]
      (->> (mongo/select-one :conversion {:backend-id   (:id m)
                                          :organization organization-id})
           (util/assoc-when m :conversion-doc)
           ;; Validate explicitly in order to be caught.
           (sc/validate ResolvedTarget)))
    (catch Exception e
      (error "Bad target" target-type target ":" (ex-message e)))))

(sc/defn ^:always-validate read-configuration :- ConversionConfiguration
  "Parses the `filename` EDN file and 'canonizes' the configuration."
  [filename]
  (let [{:keys [backend-ids files organization-id permit-type]
         :as   cfg} (some->> (util/read-edn-file filename)
                             ss/trimwalk
                             (sc/validate ConversionEdn))
        files       (map (partial resolve-filepath filename) files)
        targets     (concat (map #(hash-map :id %) backend-ids)
                            (map #(hash-map :filename %) files))]
    (-> cfg
        (dissoc :backend-ids :files)
        (assoc :targets targets
               :municipality (->> (mongo/by-id :organizations organization-id [:scope])
                                  :scope
                                  (util/find-by-key :permitType permit-type)
                                  :municipality)))))

(defn write-configuration
  "Writes conversion configuration EDN to `filename`. The target files are every file that
  matches `glob`. Each filename in `:files` is either an absolute path (default),
  path/basename, if `path` is given or just basename if given `path` is blank (but not
  nil)."
  [filename organization-id & {:keys [permit-type overwrite? force-terminal-state?
                                      glob path ids skip-id-validation?
                                      location-overrides location-fallback]}]
  {:pre [filename organization-id]}
  (let [permit-type (or permit-type "R")
        globbed     (some-> (fs/glob glob) seq sort)
        cfg         (util/assoc-when-pred {:organization-id       organization-id
                                           :permit-type           permit-type
                                           :overwrite?            (boolean overwrite?)
                                           :force-terminal-state? (boolean force-terminal-state?)}
                                          not-empty
                                          :backend-ids ids
                                          :files (seq (mapv (fn [f]
                                                              (cond
                                                                (nil? path)      (str f)
                                                                (ss/blank? path) (str (fs/base-name f))
                                                                :else
                                                                (ss/replace (str path "/" (fs/base-name f))
                                                                            #"//" "/")))
                                                            globbed))
                                          :location-overrides location-overrides
                                          :location-fallback location-fallback)]
    (if-let [failed-files (when-not skip-id-validation?
                            (some->> globbed
                                     (remove #(:backend-id (find-xml-ids permit-type %)))
                                     (map fs/base-name)
                                     seq))]
      (error "Backend id not found for files" failed-files)
      (util/write-edn-file filename (sc/validate ConversionEdn cfg)))))

(defn- process-row [row]
  (->> (xls/cell-seq row)
       (map xls/read-cell)
       ss/trimwalk))

(defn excel->maps
  "Converts `excel-filename` into a list of maps. For example,

  +------------+----------+-----------+
  | First name | lastName |  num_id   |
  +------------+----------+-----------+
  | Frank      | First    |     1     |
  +------------+----------+-----------+
  | Sonja      | Second   |   2.34    |
  +------------+----------+-----------+

  Results in

  [{:first-name 'Frank' :last-name 'First' :num-id 1}
   {:first-name 'Sonja' :last-name 'Second' :num-id 2.34}]"
  [excel-filename]
  (let [[header & rows] (->> (xls/load-workbook-from-file excel-filename)
                             (xls/select-sheet (constantly true))
                             (xls/row-seq)
                             (map process-row))
        header          (map (comp keyword ->kebab-case) header)]
    (map (partial zipmap header) rows)))

(defn make-location-override-map
  "Process given excel-maps (from `excel->maps`) into location override map. `backend-id`,
  `x` and `y` refer to the parsed column names (e.g., `:c-kuntalupatunnus`). Likewise, the
  optional `property-id` and `address` values. If `application-id` is given then the rows
  that have any value for the column are omitted from the map, since the corresponding
  backend-id is already in Lupapiste."
  [excel-maps backend-id x y & {:keys [property-id address application-id]}]
  (->> excel-maps
       (keep (fn [m]
               (when-not (get m application-id)
                 (let [backend-id (backend-id m)
                       location   (util/strip-blanks
                                    {:propertyId (when property-id
                                                   (util/pcond-> (property-id m)
                                                     #(re-matches prop/property-id-pattern %)
                                                     prop/to-property-id))
                                     :address    (when address (address m))
                                     :x          (x m)
                                     :y          (y m)})]
                   (when-not (or (sc/check ssc/NonBlankStr backend-id)
                                 (sc/check ConversionLocation location))
                     [backend-id location])))))
       (into {})))

(def rymattyla {:address    "Rymättylä oletusosoite"
                :propertyId "52954700010024"
                :x          221226.69
                :y          6704089.69})

(def velkua {:address    "Velkua oletusosoite"
             :propertyId "52956400040102"
             :x          208762.63
             :y          6714585.88})

(def livonsaari {:address    "Livonsaari oletusosoite"
                 ;; The same as Velkua, not a typo.
                 :propertyId "52956400040102"
                 :x          209243.50
                 :y          6716422.50})

(def merimasku {:address    "Merimasku oletusosoite"
                :propertyId "52947200030003"
                :x          218047.75
                :y          6714838.75})

(def mantsala {:address    "Mäntsälä oletusosoite"
               :propertyId "50540300100038"
               :x          403098.01
               :y          6722445.53})
