(ns lupapalvelu.conversion.building
  (:require [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all]
            [schema-tools.core :as st]))

(def ^:private MapArray
  "Non-empty collection where every item is a map. Bit of an overkill, but maybe useful to
  catch query regressions later on."
  (sc/pred (every-pred not-empty (partial every? map?))))

(sc/defschema ^:private AppProjection
  {:id        ssc/NonBlankStr
   :buildings MapArray
   :documents MapArray})

(sc/defschema ^:private TargetDoc
  {:doc-id                              ssc/NonBlankStr
   ;; Op-id should be present, but we can manage without.
   (sc/optional-key :doc-op-id)         ssc/NonBlankStr
   (sc/optional-key :doc-usage)         ssc/NonBlankStr
   (sc/optional-key :doc-building-type) ssc/NonBlankStr
   (sc/optional-key :doc-building-id)   ssc/NonBlankStr
   (sc/optional-key :doc-national-id)   ssc/NonBlankStr})

(sc/defschema ^:private SourceBuilding
  (st/merge {:nationalId ssc/NonBlankStr
             ;; How many times the building is listed in the buildings array.
             ::slots     sc/Int
             ::i         sc/Int}
            (st/optional-keys
              (zipmap [:operationId :usage :buildingType :buildingId]
                      (repeat ssc/NonBlankStr)))))

(def ^:private source-building-fields (-> SourceBuilding
                                          st/required-keys
                                          (st/dissoc ::slots ::i)
                                          keys))

(defn vtj-prt-schema-names
  "Names (as in `schema-info.name`) of each schema that has `valtakunnallinenNumero` field."
  []
  (-<>> (schemas/get-schemas 1)
        (keep (fn [[k v]]
                (when (util/find-by-key :name "valtakunnallinenNumero" (:body v))
                  k)))
        set
        (disj <> "aiemman-luvan-toimenpide" "archiving-project")))

(sc/defn ^:private ^:always-validate prepare-documents
  :- (sc/maybe [TargetDoc])
  "Document items for target documents."
  [{:keys [documents]}]
  (let [schema-names (vtj-prt-schema-names)]
    (->> documents
         (keep (fn [{:keys [id data schema-info]}]
                 (letfn [(vfn [& path]
                           (get-in data (util/conjv path :value)))]
                   (when (contains? schema-names (:name schema-info))
                     (util/strip-blanks
                       {:doc-id            id
                        :doc-op-id         (some-> schema-info :op :id)
                        :doc-usage         (vfn :kaytto :kayttotarkoitus)
                        :doc-building-type (vfn :kaytto :rakennusluokka)
                        :doc-building-id   (vfn :buildingId)
                        :doc-national-id   (vfn :valtakunnallinenNumero)})))))
         seq)))

(sc/defn ^:private ^:always-validate prepare-buildings
  :- (sc/maybe [SourceBuilding])
  "Every building in the application buildings array that has a proper `nationalId`
  value. We are only interested in the building fields that are relevant to the
  matching. Note that `:index` is ignored in order to find duplicates."
  [{:keys [buildings]}]
  (some->> buildings
           (filter (util/fn-> :nationalId ss/not-blank?))
           seq
           (map (util/fn-> (select-keys source-building-fields)
                           util/strip-blanks))
           (group-by identity)
           (map-indexed (fn [i [k v]]
                          (assoc k ::slots (count v) ::i i)))))

(def ^:private score-mapping
  "Field key: document field, building field, score"
  {:national-id   [:doc-national-id :nationalId 1000]
   :building-id   [:doc-building-id :buildingId 500]
   :operation-id  [:doc-op-id :operationId 10]
   :building-type [:doc-building-type :buildingType 5]
   :usage         [:doc-usage :usage 1]})

(defn- field-score
  "Matches and scores `field-key` field between `document` and `building`. Returns positive
  score if the fields match and nil they do not or either value is blank. However, returns
  overwhelming negative score on national-id mismatch in order to make sure that the
  tallied score is also negative."
  [field-key document building]
  (let [[doc-k build-k score] (get score-mapping field-key)
        a                     (get document doc-k)
        b                     (get building build-k)]
    (cond
      (or (ss/blank? a) (ss/blank? b)) nil
      (ss/=trim-i a b)                 score
      (= field-key :national-id)       (- 0 score))))

(defn- tally-scores [document building]
  (some->> (keys score-mapping)
           (keep #(field-score % document building))
           seq
           (apply +)))

(defn- document-scores [document buildings]
  (some->> buildings
           (keep (fn [building]
                   (when-let [score (tally-scores document building)]
                     (when (pos? score)
                       {:document document
                        :building building
                        :score    score}))))
           seq))

(sc/defn ^:private ^:always-validate find-buildings-for-documents
  :- (sc/maybe #{{:doc-id  ssc/NonBlankStr
                  :vtj-prt ssc/NonBlankStr}})
  "When buildings are matched to documents, the most exact (with the biggest score) match
  wins. After a match, the building is no longer available for other documents. The
  resolution aims to be as unambiguous as possible: if there are multiple _different_
  buildings that could be equally good matches, then no building is selected unless it has
  enough slots for each match."
  ([buildings :- [SourceBuilding]
    documents :- [TargetDoc]]
   (let [all-scores (->> documents
                         (mapcat #(document-scores % buildings))
                         (map (fn [{:keys [document building score]}]
                                {:doc-id          (:doc-id document)
                                 :doc-national-id (:doc-national-id document)
                                 :build-id        (::i building)
                                 :vtj-prt         (:nationalId building)
                                 :score           score}))
                         (sort-by #(* -1 (:score %))))]
     (loop [[{:keys [doc-id build-id score] :as x}
             & xs]         all-scores
            building-slots (->> buildings
                                (map (fn [{:keys [::slots ::i]}]
                                       [i slots]))
                                (into {}))
            result         #{}]
       (let [add-result   #(conj result (select-keys x [:doc-id :vtj-prt]))
             update-slots #(update building-slots build-id dec)
             ;; A match is ambiguous if the document matches some other building(s) with the
             ;; same score OR some other document(s) matches the buiding with the same score
             ;; AND there not enough slots in the related buildings.
             ambiguous?   (fn []
                            (let [solved-doc-ids (set (keep :doc-id result))
                                  xs             (remove (util/fn-> :doc-id solved-doc-ids)
                                                         (-> all-scores set (disj x)))
                                  by-doc         (some (fn [m]
                                                         (and (= doc-id (:doc-id m))
                                                              (= score (:score m))
                                                              (pos? (get building-slots (:build-id m)))))
                                                       xs)
                                  by-build       (count (filter #(and (= (:build-id %) build-id)
                                                                      (= (:score %) score))
                                                                xs))]
                              (or by-doc
                                  (>= by-build (get building-slots build-id)))))
             purge-xs     #(remove (util/fn-> :doc-id (= doc-id)) xs)]

         (cond
           (nil? x)                     (not-empty result)
           (-> building-slots
               (get build-id) pos? not) (recur xs building-slots result)
           (:doc-national-id x)         (recur (purge-xs) (update-slots) result)
           (> score 400)                (recur (purge-xs) (update-slots) (add-result))

           ;; Ambiguous
           (ambiguous?)
           (recur (purge-xs) (update-slots) result)

           :else                        (recur (purge-xs) (update-slots) (add-result)))))))
  ([application :- AppProjection]
   (find-buildings-for-documents (prepare-buildings application)
                                 (prepare-documents application))))

(defn- one-shot
  "If there is only document and building, they are matched."
  [{:keys [buildings] :as application}]
  (when-let [[{vtj-prt :nationalId}
              & _] (and (= 1 (count buildings))
                        (prepare-buildings application))]
    (let [[{:keys [doc-id doc-national-id]} & _
           :as docs] (prepare-documents application)]
      (when (and (nil? doc-national-id) (= 1 (count docs)))
        #{{:doc-id doc-id :vtj-prt vtj-prt}}))))

(sc/defn ^:always-validate update-document-vtj-prts :- (sc/maybe (sc/pred true?))
  "Updates missing `valtakunnallinenNumero` values if suitable buildings are found in the
  application. Returns true if any document is updated (and a sheriff note added)."
  [{app-id :id :as application} :- AppProjection]
  (log/with-logging-context {:applicationId app-id}
    (try
      (if-let [updates (or (one-shot application)
                           (find-buildings-for-documents application))]
        (let [note (->> updates
                        (map #(format "%s (%s)" (:doc-id %) (:vtj-prt %)))
                        (ss/join ", ")
                        (str "Other vtj-prts: "))]
          (doseq [{:keys [doc-id vtj-prt]} updates]
            (mongo/update-by-query :applications
                                   {:_id       app-id
                                    :documents {$elemMatch {:id doc-id}}}
                                   {$set {:documents.$.data.valtakunnallinenNumero.value vtj-prt}}))
          (mongo/update-by-id :applications app-id
                              (conv-util/with-sheriff-note note
                                (conv-util/with-reporting-override)))
          (log/infof note)
          true)
        (log/warn "Other vtj-prts: No documents updated."))
      (catch Exception e
        (log/error "Other vtj-prts fail:" (ex-message e))))))
