(ns lupapalvelu.building-attributes
  (:require [clojure.string :as s]
            [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.remote-excel-reader :as excel-reader]
            [monger.operators :refer [$set $push $in $and $ne]]
            [monger.result :as mresult]
            [noir.response :as resp]
            [sade.core :refer [fail] :as sade]
            [sade.env :as env]
            [sade.http :as http]
            [sade.property :as prop]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [schema.utils :as scu]
            [taoensso.timbre :refer [info warn warnf]]))

(sc/defschema BuildingAttributeField
  (sc/enum "ratu"
           "vtjprt"
           "kiinteistotunnus"
           "visibility"
           "publicity"
           "myyntipalvelussa"))

(sc/defschema Visibility
  (sc/enum "asiakas-ja-viranomainen"
           "viranomainen"
           "julkinen"))

(sc/defschema Publicity
  (sc/enum
   "julkinen"
   "salainen"
   "osittain-salassapidettava"))

(sc/defschema HistoryEntryType
  (sc/enum "sent-to-archive"))

(def HttpStatus sc/Keyword)
(def FileId sc/Str)

(sc/defschema HistoryEntryDocs
  {HttpStatus [FileId]})

(sc/defschema HistoryEntry
  {:type HistoryEntryType
   :time sc/Num
   (sc/optional-key :docs-by-status) HistoryEntryDocs})

(sc/defschema BuildingMeta
  {(sc/optional-key :removed) sc/Bool
   (sc/optional-key :modified) sc/Num
   (sc/optional-key :history) [HistoryEntry]})

(def BuildingId sc/Str)

(sc/defschema RemoveBuildingRequest
  {:organizationId sc/Str
   :building-id BuildingId})

(sc/defschema UpdateInArchiveRequest
  {:organizationId sc/Str
   :building-ids [BuildingId]})

(sc/defschema BuildingAttributeUpdate
  {(sc/optional-key :id) (sc/maybe sc/Str)
   (sc/optional-key :ratu) (sc/maybe sc/Str)
   (sc/optional-key :vtjprt) ssc/Rakennustunnus
   (sc/optional-key :kiinteistotunnus) (sc/maybe ssc/Kiinteistotunnus)
   (sc/optional-key :address) (sc/maybe sc/Str)
   (sc/optional-key :comment) (sc/maybe sc/Str)
   (sc/optional-key :visibility) (sc/maybe Visibility)
   (sc/optional-key :publicity) (sc/maybe Publicity)
   (sc/optional-key :myyntipalvelussa) sc/Bool})

(sc/defschema BuildingAttributes
  {:vtjprt ssc/Rakennustunnus
   (sc/optional-key :ratu) sc/Str
   (sc/optional-key :kiinteistotunnus) ssc/Kiinteistotunnus
   (sc/optional-key :address) sc/Str
   (sc/optional-key :comment) sc/Str
   (sc/optional-key :visibility) Visibility
   (sc/optional-key :publicity) Publicity
   (sc/optional-key :myyntipalvelussa) sc/Bool})

(sc/defschema Building
  {:id sc/Str
   :organization sc/Str
   :attributes BuildingAttributes
   (sc/optional-key :meta) BuildingMeta})

(sc/defschema OnkaloMetadata
  {:myyntipalvelu sc/Bool
   (sc/optional-key :julkisuusluokka) Publicity
   (sc/optional-key :nakyvyys) Visibility})

(sc/defschema BuildingOnkaloUpdate
  {:id sc/Str
   :search {:national-building-id ssc/Rakennustunnus}
   :metadata OnkaloMetadata})

(def publicity-secret-defaults
  {:salassapitoperuste "-"
   :salassapitoaika 1
   :suojaustaso "ei-luokiteltu"
   :turvallisuusluokka "ei-turvallisuusluokkaluokiteltu"
   :kayttajaryhma "viranomaisryhma"
   :kayttajaryhmakuvaus "muokkausoikeus"})

(defn limited-publicity? [julkisuusluokka]
  (contains? #{"salainen" "osittain-salassapidettava"} julkisuusluokka))

(defn limited-visibility? [nakyvyys]
  (contains? #{"viranomainen" "asiakas-ja-viranomainen"} nakyvyys))

(defn limited-building?
  "Visibility or publicity of the building has been limited"
  [{:keys [publicity visibility]}]
  (or (limited-publicity? publicity)
      (limited-visibility? visibility)))

(defn with-secrecy-defaults [{:keys [julkisuusluokka] :as metadata}]
  (cond->>  metadata
    (limited-publicity? julkisuusluokka) (merge publicity-secret-defaults)))

(defn valid-onkalo-update? [onkalo-update]
  (if-let [err (sc/check BuildingOnkaloUpdate onkalo-update)]
    (do
      (warn "Invalid onkalo-update" onkalo-update err)
      false)
    true))

(defn ->myyntipalvelu-in-onkalo-update [{:keys [myyntipalvelussa publicity visibility]}]
  (cond
    (and publicity  (not= "julkinen"  publicity))  false
    (and visibility (not= "julkinen"  visibility)) false
    :else (boolean myyntipalvelussa)))

(defn ->onkalo-update [{:keys [id vtjprt myyntipalvelussa publicity visibility] :as building}]
  (cond-> {:id id
           :search {:national-building-id vtjprt}
           :metadata {:myyntipalvelu (->myyntipalvelu-in-onkalo-update building)}}
    publicity  (assoc-in [:metadata :julkisuusluokka] publicity)
    visibility (assoc-in [:metadata :nakyvyys] visibility)))

(defn validation-error? [err]
  (= schema.utils.ValidationError (scu/type-of err)))

(defn ->error-msg
  "Extracts error-msg from (not (<error-msg> <type>)) or similar into <error-msg>
   E.g. turns (not (:error.external-building-id-required a-clojure.lang.PersistentArrayMap)) or similar
   into :error.external-building-id-required"
  [validation-error]
  (let [error-data (scu/validation-error-explain validation-error)]
    (let [[_ [error-msg _]]  error-data]
      error-msg)))

(defn ->validation-error-msg [error]
  (if (validation-error? error)
    (->error-msg error)
    error))

(defn has?
  "Returns a predicate that tests whether a map contains another map"
  [submap]
  (fn [m]
    (= (select-keys m (or (keys submap) [])) submap)))

(defn latest-entry
  ([history]
   (latest-entry history [(constantly true)]))
  ([history predicates]
   (->> history
        (filter (apply every-pred predicates))
        (sort-by :time)
        last)))

(defn ->building-result [{:keys [id attributes meta] :as building}]
  (when building
    (merge {:id id
            :modified (:modified meta)
            :sent-to-archive (latest-entry (:history meta)
                                           [(has? {:type "sent-to-archive"})])}
           attributes)))

(defn fetch-buildings [organization-id & [{:keys [building-ids vtjprts]}]]
  (let [conditions (cond-> [{:organization organization-id}]
                     building-ids (conj {:_id {$in building-ids}})
                     vtjprts      (conj {"attributes.vtjprt" {$in vtjprts}}))
        query {$and conditions}]
    (->> (mongo/select :building-info query)
         (remove (fn [building]
                   (get-in building [:meta :removed])))
         (map ->building-result))))

(defn org-has-existing-buildings? [organization-id]
  (mongo/any? :building-info {:organization organization-id
                              "meta.removed" {$ne true}}))

(defn fetch-secret-buildings [org vtjprts]
  (->> (fetch-buildings org {:vtjprts vtjprts})
       (filter limited-building?)))

(defn check-building [building]
  (when-let [err (sc/check Building building)]
    (let [attributes-error (get err :attributes)]
      (if-let [validation-error-msg (->validation-error-msg attributes-error)]
        validation-error-msg
        :error.building.invalid-attribute))))

(defn validate-building-attribute-update [{{{:keys [field value] :as update} :building-update} :data :as command}]
  (when-let [err (sc/check BuildingAttributeUpdate {(keyword field) value})]
    (warn "Invalid building update request: " update " error: " err)
    (fail :error.building.invalid-attribute)))

(defn validate-building-remove-request [{request :data :as command}]
  (when-let [err (sc/check RemoveBuildingRequest request)]
    (warn "Invalid building-remove request: " request " error: " err)
    (fail :error.building.invalid-remove-request)))

(defn validate-update-buildings-in-archive-request [{request :data :as command}]
  (when-let [err (sc/check UpdateInArchiveRequest request)]
    (warn "Invalid update-buildings-in-archive request: " request " error: " err)
    (fail :error.building.invalid-update-in-archive-request)))

(defn unique-identifier? [field-name]
  (#{:ratu :vtjprt} field-name))

(defn get-duplicates [{:keys [id field value] :as attr} buildings]
  (if (and value (unique-identifier? field))
    (filter (fn [{building-id :id :as building}]
              (let [field-value (get building field)]
                (and
                 (not= id building-id) ;;Do not treat the building itself as duplicate
                 (= value field-value))))
            buildings)
    []))

(defn missing-external-id-error [building]
  (when-not (get-in building [:attributes :vtjprt])
    :error.building.external-building-id-required))

(defn validate [{:keys [field] :as attribute} updated-building other-buildings]
  (let [duplicates (get-duplicates attribute other-buildings)

        schema-error (check-building updated-building)
        error (or (missing-external-id-error updated-building)
                  schema-error
                  (when (seq duplicates) :error.building.duplicate-identifier))
        error-data (when (seq duplicates) {:duplicates (map (fn [building]
                                                              (select-keys building [:id field]))
                                                            duplicates)})]
    (when error
      {:error-msg error
       :error-data error-data})))

(defn fetch-building [id]
  (when id
    (mongo/by-id :building-info id)))

(defn set-building-attribute! [org-id {:keys [id field value] :as building-attribute}]
  (let [path (keyword (str "attributes." (name field)))
        current-buildings (fetch-buildings org-id)
        current-building (fetch-building id)
        updated-building (assoc-in current-building [:attributes field] value)
        {:keys [error-msg error-data] :as validation-error} (validate building-attribute updated-building current-buildings)]
    (when-not validation-error
      (mongo/update-by-id :building-info id {$set {path value
                                                   "meta.modified" (sade/now)}}))
    {:building-id id
     :updated-building (->building-result (fetch-building id))
     :error error-msg
     :error-data error-data}))

(defn remove-attribute! [{:keys [id field]}]
  (let [{:keys [attributes] :as building} (fetch-building id)
        updated-attributes (dissoc attributes field)
        updated-building (assoc building :attributes updated-attributes)
        error (check-building building)]
    (when-not error
      (mongo/update-by-id :building-info id {$set {"attributes" updated-attributes
                                                   "meta.modified" (sade/now)}}))
    {:building-id id
     :updated-building (->building-result (fetch-building id))
     :error error}))

(defn add-building! [org-id {:keys [field value] :as attribute}]
  (let [id (mongo/create-id)
        new-building {:id id
                      :organization org-id
                      :attributes {field value}
                      :meta {:modified (sade/now)}}
        current-buildings (fetch-buildings org-id)
        {:keys [error-msg error-data] :as validation-error} (validate attribute new-building current-buildings)]
    (when-not error-msg
      (mongo/insert :building-info new-building))
    {:updated-building (->building-result (fetch-building id))
     :building-id id
     :error error-msg
     :error-data error-data}))

(defn batch-add-buildings! [db-buildings]
  {:ok (-> (mongo/insert-batch :building-info db-buildings)
           (mresult/acknowledged?))})

(defn upsert-building-attribute! [org-id {:keys [id field value] :as building-update}]
  (let [building-update (update building-update :field keyword)
        current-building (fetch-building id)]
    (cond (not org-id) {:error :no-organization-id-provided}
          (and current-building (nil? value)) (remove-attribute! building-update)
          current-building (set-building-attribute! org-id building-update)
          id {:building-id id
              :error :unknown-building-id}
          :else (add-building! org-id building-update))))

(defn mark-building-removed! [building-id]
  (if-let [exists? (fetch-building building-id)]
    (mongo/update-by-id :building-info building-id
                        {$set {:meta.removed true}})
    {:error :unknown-building-id}))

(defn add-to-history!
  [building-id history-entry]
  (if-let [exists? (fetch-building building-id)]
    (mongo/update-by-id :building-info building-id
                        {$push {:meta.history history-entry}})
    {:error :unknown-building-id}))

(defn json-string->edn [body]
  (if (string? body)
    (try
      (json/decode body keyword)
      (catch Exception e (warnf "Response body was not JSON. We got: %s" body))
      (finally {}))
    body))

(defn send-to-archive! [organization updates]
  (let [host (env/value :arkisto :host)
        app-id (env/value :arkisto :app-id)
        app-key (env/value :arkisto :app-key)
        endpoint "/api/v2/buildings/update-buildings"
        url (str host endpoint)
        {:keys [status body]} (http/post url {:basic-auth [app-id app-key]
                                              :throw-exceptions false
                                              :quiet false
                                              :form-params {:organization organization
                                                            :updates updates}
                                              :content-type :json
                                              :as :json})]
    {:status status :body (json-string->edn body)}))

(defn update-in-archive! [organization building-ids]
  (let [buildings (fetch-buildings organization {:building-ids building-ids})
        updates (->> buildings
                     (map ->onkalo-update)
                     (filter valid-onkalo-update?))
        {:keys [status body]} (send-to-archive! organization updates)
        timestamp (sade/now)
        history-entry-by-building (->> body
                                       keywordize-keys
                                       :results
                                       (map (fn [{:keys [id results]}]
                                              [id {:type "sent-to-archive"
                                                   :time timestamp
                                                   :docs-by-status results}]))
                                       (into {}))]
    (doseq [[id history-entry] history-entry-by-building]
      (add-to-history! id history-entry))
    {:result-by-building history-entry-by-building
     :error (when (not= status 200) :error.building.archive-update-failed)}))

(defn- lookup
  ([lookup-map value]
   (lookup lookup-map value identity))
  ([lookup-map value post-fn]
   (-> (get lookup-map value value)
       post-fn)))

(def col-header->attribute-key {"VTJPRT"           :vtjprt
                                "MYYNTIPALVELUSSA" :myyntipalvelussa
                                "NÄKYVYYS"         :visibility
                                "JULKISUUS"        :publicity
                                "RATU"             :ratu
                                "KIINTEISTÖTUNNUS" :kiinteistotunnus
                                "OSOITE"           :address
                                "LISÄTIETO"        :comment})

(defn header->attribute-keys [header]
  (map (comp col-header->attribute-key s/upper-case) header))

(def default-visibility "viranomainen")
(def default-myyntipalvelussa false)

(defn ->visiblity-value [excel-value]
  (let [excel-val->visibility {"1" "viranomainen"
                               "2" "asiakas-ja-viranomainen"
                               "3" "julkinen"}]
    (lookup excel-val->visibility excel-value ss/blank-as-nil)))

(defn ->publicity-value [excel-value]
  (let [excel-val->publicity {"1" "salainen"
                              "2" "osittain-salassapidettava"
                              "3" "julkinen"}]
    (-> (lookup excel-val->publicity excel-value ss/blank-as-nil))))

(defn ->myyntipalvelussa-value [excel-value]
  (let [blank-as-nil (fn [x] (if (string? x) (ss/blank-as-nil x) x))
        lookup {"1" true
                "0" false}]
    (-> (get lookup excel-value excel-value)
        blank-as-nil)))

(defn human-readable-property-id? [value]
  (some->> value (re-matches prop/property-id-pattern)))

(defn ->db-kiinteistotunnus [excel-value]
  (if (human-readable-property-id? excel-value)
    (prop/to-property-id excel-value)
    (ss/blank-as-nil excel-value)))

(defn ->building-attributes [{:keys [vtjprt myyntipalvelussa visibility publicity ratu kiinteistotunnus address comment]
                              :as excel-entry}]
  (-> {:vtjprt           vtjprt
       :myyntipalvelussa (->myyntipalvelussa-value myyntipalvelussa)
       :visibility       (->visiblity-value visibility)
       :publicity        (->publicity-value publicity)
       :ratu             (ss/blank-as-nil ratu)
       :kiinteistotunnus (->db-kiinteistotunnus kiinteistotunnus)
       :address          (ss/blank-as-nil address)
       :comment          (ss/blank-as-nil comment)}
      util/strip-nils))

(defn- remove-duplicates [rows]
  (->> rows
       (group-by :vtjprt)
       vals
       (map last)))

(defn header+rows->building-attributes [header rows]
  (cond
    (empty? header) []
    (empty? rows)   []
    :else (->> rows
               (map #(map ss/trim %))
               (map #(zipmap (header->attribute-keys header) %))
               (map ->building-attributes)
               remove-duplicates)))

(defn ->building-update [org-id created-ts id-fn attributes]
  {:id (id-fn)
   :organization org-id
   :meta {:modified created-ts}
   :attributes attributes})

(defn ->db-buildings [attributes-list org-id created-ts id-fn]
  (let [db-buildings (map (partial ->building-update org-id created-ts id-fn) attributes-list)
        building-validity #(if (check-building %) :invalid-buildings :valid-buildings)]
    (->>  db-buildings
          (group-by building-validity)
          (merge {:valid-buildings []}))))

(defn- remove-instruction-rows [rows]
  (drop-while (fn [[first-item]] (not (= "VTJPRT" first-item))) rows))

(defn- ->header-and-rows [[header & rows]]
  {:header header
   :rows   rows})

(defn rows->header-and-rows [rows]
  (-> (remove-instruction-rows rows)
      ->header-and-rows))

(defn upload-building-data [org-id files {user :user created :created}]
  (if-let [{:keys [header rows]} (some-> files
                                         first
                                         (excel-reader/xls-to-rows (:id user))
                                         rows->header-and-rows)]
    (let [building-attributes (header+rows->building-attributes header rows)
          {:keys [valid-buildings
                  invalid-buildings]} (->db-buildings building-attributes org-id created mongo/create-id)
          {import-ok? :ok} (when (empty? invalid-buildings)
                             (batch-add-buildings! valid-buildings))
          response-data       {:ok     import-ok?
                               :result {:valid-buildings (count valid-buildings)
                                        :invalid-entries (map :attributes invalid-buildings)}
                               :text   (if import-ok?
                                         "building-extra-attributes.import-success"
                                         "building-extra-attributes.error.import-failed")}]
      (->> response-data
           (resp/json)
           (resp/status 200)))
    ; ok could be that status is actually 400/500, but no time to check how frontend handles this
    (resp/status 200 (resp/json (fail "building-extra-attributes.error.import-failed")))))
