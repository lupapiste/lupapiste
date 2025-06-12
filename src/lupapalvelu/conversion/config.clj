(ns lupapalvelu.conversion.config
  "Conversion configuration processing."
  (:require [babashka.fs :as fs]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.schemas :refer [ConversionConfiguration ConversionEdn ImportConfig other-ts-fields
                                                    OtherConfig ResolvedTarget]]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :refer [error errorf]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [org.dhatim.fastexcel.reader Cell
                                        CellType
                                        ReadableWorkbook
                                        Row
                                        Sheet]))

(set! *warn-on-reflection* true)

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
                           (not (fs/directory? reference)) fs/parent
                           true (fs/path filename))))]
    (str (fs/absolutize resolved))))

(sc/defn ^:always-validate resolve-target
  :- (sc/maybe ResolvedTarget)
  [{:keys [organization-id]}
   target-type :- (sc/enum :id :filename)
   target      :- sc/Str]
  (try
    (let [m (if (= target-type :id)
              {:id target}
              (let [{:keys [backend-id
                            application-id]} (conv-util/find-xml-ids target)]
                (util/assoc-when {:id       backend-id
                                  :filename target}
                                 :xml-application-id application-id)))]
      (->> (conv-doc/document organization-id (:id m))
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
                                  (util/find-first #(if permit-type
                                                      (= permit-type (:permitType %))
                                                      identity) )
                                  :municipality)))))

(defn write-configuration
  "Writes conversion configuration EDN to `filename`. The target files are every file that
  matches `glob` (`glob` is defined relatively to `filename` folder). Each filename in
  `:files` is a relative path in terms of `filename`."
  [filename organization-id & {:keys [glob ids] :as options}]
  {:pre [filename organization-id]}
  (let [dir         (or (fs/parent filename) ".")
        globbed     (some->> glob (fs/glob dir) (map str) sort)
        cfg         (-> (select-keys options
                                     [:permit-type :overwrite? :force-terminal-state?
                                      :tags :location-overrides
                                      :location-fallback :state-overrides
                                      :state-mapping])
                        (assoc
                          :organization-id organization-id
                          :backend-ids (->> (map ss/trim ids)
                                            distinct
                                            (keep conv-util/valid-permit-id-or-nil))
                          :files (some->> globbed seq
                                          (keep (fn [path]
                                                  (let [{:keys [backend-id]} (conv-util/find-xml-ids path)]
                                                    (cond
                                                      (nil? backend-id)
                                                      (error "Backend-id not found in file" (fs/file-name path))

                                                      (not (conv-util/valid-permit-id-or-nil backend-id))
                                                      (errorf "Bad backend-id %s in file %s"
                                                              backend-id (fs/file-name path))

                                                      :else (str (fs/relativize dir path))))))))
                        (util/strip-matches util/emptyish?))
        bad-id-n (- (count ids) (count (:backend-ids cfg)))]
    (when-not (zero? bad-id-n)
      (error bad-id-n "bad or duplicate backend ids omitted."))
    (util/write-edn-file filename (sc/validate ConversionEdn cfg))))

(defn- process-row
  [field-keys date-keys ^Row row]
  (->> (iterator-seq (.iterator row))
       (zipmap field-keys)
       (map (fn [[k ^Cell cell]]
              (let [cell-type (some-> cell .getType)]
                [k (cond
                     (nil? cell) nil

                     (date-keys k)
                     (some-> (.asDate cell) (.atZone date/default-zone-id))

                     (= cell-type CellType/NUMBER)
                     (some-> (.asNumber cell) .doubleValue)

                     (= cell-type CellType/BOOLEAN) (.asBoolean cell)
                     (= cell-type CellType/STRING)  (.asString cell)

                     :else (.getText cell))])))
       (into {})
       ;; Remove carriage return garbage and trim
       (ss/stringwalk (util/fn-> (ss/replace "_x000D_" "") ss/trim))))

(defn- header-row [^Row row]
  (map (fn [^Cell cell]
         (some-> cell (.getText) conv-util/column-key))
       (iterator-seq (.iterator row))))

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

  [{:first-name 'Frank' :last-name 'First' :num-id 1.0}
   {:first-name 'Sonja' :last-name 'Second' :num-id 2.34}]

  By default, the first sheet is processed, but the sheet index can be given with
  `sheet`.

  Cell values are interpreted according to their type. Number cells are parsed as doubles.

  Note: For the reader, the date cells are just numbers and interpreted as such. The
  column values can be forced to be parsed as `ZonedDateTime` instances with `date-keys`
  argument. The set values are final key values (e.g., `first-name` above)."
  ([excel-filename date-keys sheet]
   (with-open [wb (ReadableWorkbook. (io/file excel-filename))]
     (let [^Sheet sheet    (.get (.getSheet wb (or sheet 0)))
           [header & rows] (.read sheet)
           header          (header-row header)]
       (map #(process-row header (set (or date-keys #{})) %) rows))))
  ([excel-filename date-keys]
   (excel->maps excel-filename date-keys nil))
  ([excel-filename]
   (excel->maps excel-filename nil nil)))

(defn csv->maps
  "Like `excel->maps` but for CSV files. Optional `csv-options` are passed as such to
  `csv/read-csv`."
  [csv-filename & {:as csv-options}]
  (let [[header & rows] (csv/read-csv (slurp csv-filename)
                                      (merge {:separator \;} csv-options))
        header          (map conv-util/column-key header)]
    (map (partial zipmap header) rows)))

;; -------------------
;; Default locations
;; -------------------


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

(def jarvenpaa {:address    "Järvenpää oletusosoite"
                :propertyId "18600707610002"
                :x          394999
                :y          6706736})

(def nokia {:address    "Nokia oletusosoite"
            :propertyId "53601999030000"
            :x          311409
            :y          6816511})

(def kempele {:address    "Kempele oletusosoite"
              :propertyId "24489500000814"
              :x          429445
              :y          7199475})

(def savonlinna {:address    "Savonlinna oletusosoite"
                 :propertyId "74050800030023"
                 :x          598665
                 :y          6861614})

(def kangasala {:address    "Kangasala oletusosoite"
                :propertyId "21143508760001"
                :x          347266
                :y          6836009})

(def ilmajoki {:address    "Ilmajoki oletusosoite"
               :propertyId "14541900030064"
               :x          274558.47
               :y          6963623.01})

(def kuusamo {:address    "Kuusamo oletusosoite"
              :propertyId "30542100360001"
              :x          579000
              :y          7325000})

(def kirkkonummi {:address    "Kirkkonummi oletusosoite"
                  :propertyId "25789400010001"
                  :x          363198.6
                  :y          6644785.96})

(defn write-other-configuration
  [filename organization-id & {:keys [permit-type ids] :as opts}]
  {:pre [(ss/not-blank? filename)]}
  (->> {:organization-id organization-id
        :permit-type     (or permit-type "R")
        :application-ids (some->> ids seq ss/trimwalk distinct)}
       (merge (select-keys opts other-ts-fields))
       util/strip-nils
       (sc/validate OtherConfig)
       (util/write-edn-file filename)))

(defn write-import-configuration
  ([filename organization-id op items]
   {:pre [(ss/not-blank? filename)]}
   (->> {:organization-id organization-id
         :op              (keyword op)
         :items           items}
        util/strip-blanks
        ss/trimwalk
        (sc/validate ImportConfig)
        (util/write-edn-file filename)))
  ([filename op items]
   (write-import-configuration filename nil op items)))
