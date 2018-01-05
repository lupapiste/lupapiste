(ns lupapalvelu.pate.verdict
  (:require [clj-time.core :as time]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.transformations :as transformations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as krysp]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(defn neighbor-states
  "Application neighbor-states data in a format suitable for verdicts: list
  of property-id, done (timestamp) maps."
  [{neighbors :neighbors}]
  (map (fn [{:keys [propertyId status]}]
         {:property-id propertyId
          :done (:created (util/find-by-key :state
                                            "mark-done"
                                            status))})
       neighbors))

(defn data-draft
  "Kmap keys are draft targets (kw-paths). Values are either kw-paths or
  maps with :fn and :skip-nil? properties. :fn is the source
  (full) data handler function. If :skip-nil? is true the nil value
  entries are skipped (default false and nil value is substituted with
  empty string). The second argument is the published template
  snapshot."
  [kmap {data :data}]
  (reduce (fn [acc [k v]]
            (let [v-fn   (get v :fn identity)
                  value  (if-let [v-fn (get v :fn)]
                           (v-fn data)
                           (get-in data (util/split-kw-path v)))]
              (if (and (nil? value) (:skip-nil? v))
                acc
                (assoc-in acc
                          (util/split-kw-path k)
                          (if (nil? value)
                            ""
                            value)))))
          {}
          kmap))

(defn- map-unremoved-section
  "Map (for data-draft) section only it has not been removed. If
  target-key is not given, source-key is used. Result is key-key map
  or nil."
  [source-data source-key & [target-key]]
  (when-not (some-> source-data :removed-sections source-key)
    (hash-map (or target-key source-key) source-key)))

(defn- template-category [template]
  (-> template :category keyword))

(defmulti initial-draft
  "Creates a map with :data and :references"
  (fn [template & _]
    (template-category template)))

(defn- kw-format [& xs]
  (keyword (apply format (map ss/->plain-string xs))))

(defmethod initial-draft :r
  [{snapshot :published} application]
  {:data       (data-draft
                (merge {:giver        :giver
                        :verdict-code :verdict-code
                        :verdict-text :paatosteksti
                        :bulletinOpDescription :bulletinOpDescription}
                       (reduce (fn [acc kw]
                                 (assoc acc
                                        kw kw
                                        (kw-format "%s-included" kw)
                                        {:fn (util/fn-> (get-in [:removed-sections kw]) not)}))
                               {}
                               [:foremen :plans :reviews])
                       (reduce (fn [acc kw]
                                 (assoc acc
                                        kw  {:fn        #(when (util/includes-as-kw? (:verdict-dates %) kw)
                                                           "")
                                             :skip-nil? true}))
                               {}
                               shared/verdict-dates)
                       (reduce (fn [acc k]
                                 (merge acc (map-unremoved-section (:data snapshot) k)))
                               {}
                               [:conditions :neighbors :appeal :statements :collateral
                                :complexity :rights :purpose]))
                snapshot)
   :references (:settings snapshot)})

(declare enrich-verdict)

(defmulti template-info
  "Contents of the verdict's template property. The actual contents
  depend on the category. Typical keys:

  :exclusions A map where each key matches a verdict dictionary
  key. Value is either true (dict is excluded) or another exclusions
  map (for repeating)."
  template-category)

(defmethod template-info :default [_] nil)

(defmethod template-info :r
  [{snapshot :published}]
  (let [data             (:data snapshot)
        removed?         #(boolean (get-in data [:removed-sections %]))
        building-details (->> [(when-not (:autopaikat data)
                                 [:rakennetut-autopaikat
                                  :kiinteiston-autopaikat
                                  :autopaikat-yhteensa])
                               (map  (fn [kw]
                                       (when-not (kw data) kw))
                                     [:vss-luokka :paloluokka])]
                              flatten
                              (remove nil?))
        exclusions       (-<>> [(filter removed? [:conditions :appeal :statements
                                                  :collateral :rights :purpose])
                                (when (removed? :neighbors)
                                  [:neighbors :neighbor-states])
                                (when (removed? :complexity)
                                  [:complexity :complexity-text])
                                (util/difference-as-kw shared/verdict-dates
                                                       (:verdict-dates data))
                                (if (-> snapshot :settings :boardname)
                                  :contact
                                  :verdict-section)]
                               flatten
                               (remove nil?)
                               (zipmap <> (repeat true))
                               (util/assoc-when <> :buildings (or (removed? :buildings)
                                                                  (zipmap building-details
                                                                          (repeat true))))
                               not-empty)]
    {:exclusions exclusions}))

(defn new-verdict-draft [template-id {:keys [application organization created]
                                      :as   command}]
  (let [template (template/verdict-template @organization template-id)
        draft    (assoc (initial-draft template application)
                        :template (template-info template)
                        :id       (mongo/create-id)
                        :modified created)]
    (action/update-application command
                               {$push {:pate-verdicts
                                       (sc/validate
                                        schemas/PateVerdict
                                        (util/assoc-when draft
                                                         :category (:category template)))}})
    {:verdict  (enrich-verdict command draft)
     :references (:references draft)}))

(defn verdict-summary [verdict]
  (select-keys verdict [:id :published :modified]))

(defn mask-verdict-data [{:keys [user application]} verdict]
  (cond
    (not (auth/application-authority? application user))
    (util/dissoc-in verdict [:data :bulletinOpDescription])
    :default verdict))

(defn command->verdict [{:keys [data application] :as command}]
  (update (->> (util/find-by-id (:verdict-id data) (:pate-verdicts application))
               (mask-verdict-data command))
          :category keyword))

(defn verdict-template-for-verdict [verdict organization]
  (let [{:keys [id version-id]} (:template verdict)]
    (->>  id
          (template/verdict-template organization)
          :versions
          (util/find-by-id version-id))))

(defn delete-verdict [verdict-id command]
  (action/update-application command
                             {$pull {:pate-verdicts {:id verdict-id}
                                     :attachments   {:target.id verdict-id}}}))

(defn- listify
  "Transforms argument into list if it is not sequential. Nil results
  in empty list."
  [a]
  (cond
    (sequential? a) a
    (nil? a)        '()
    :default        (list a)))

(defn- verdict-update
  "Updates application, using $elemMatch query for given verdict."
  [{:keys [data created] :as command} update]
  (let [{verdict-id :verdict-id} data]
    (action/update-application command
                               {:pate-verdicts {$elemMatch {:id verdict-id}}}
                               (assoc-in update
                                         [$set :pate-verdicts.$.modified]
                                         created))))

(defn- verdict-changes-update
  "Write the auxiliary changes into mongo."
  [command changes]
  (when (seq changes)
    (verdict-update command {$set (reduce (fn [acc [k v]]
                                            (assoc acc
                                                   (util/kw-path :pate-verdicts.$.data k)
                                                   v))
                                          {}
                                          changes)})))

(defn update-automatic-verdict-dates [{:keys [category references verdict-data]}]
  (let [datestring  (:verdict-date verdict-data)
        dictionary  (-> shared/settings-schemas category :dictionary)
        date-deltas (:date-deltas references)
        automatic?  (:automatic-verdict-dates verdict-data)]
    (when (and automatic? (ss/not-blank? datestring))
      (loop [dates      {}
             [kw & kws] shared/verdict-dates
             latest     datestring]
        (if (nil? kw)
          dates
          (let [unit   (-> dictionary  kw :date-delta :unit)
                result (date/parse-and-forward latest
                                               ;; Delta could be empty.
                                               (util/->long (kw date-deltas))
                                               unit)]
            (recur (assoc dates
                          kw
                          result)
                   kws
                   result)))))))

;; Additional changes to the verdict data.
;; Methods options include category, template, verdict-data, path and value.
;; Changes is called after value has already been updated into mongo.
;; The method result is a changes for verdict data.
(defmulti changes (fn [{:keys [category path]}]
                    ;; Dispatcher result: [:category :last-path-part]
                    [category (keyword (last path))]))

(defmethod changes :default [_])

(defmethod changes [:r :verdict-date]
  [options]
  (update-automatic-verdict-dates options))

(defmethod changes [:r :automatic-verdict-dates]
  [options]
  (update-automatic-verdict-dates options))


(defn- strip-exclusions
  "Removes excluded target keys from dictionary schema."
  [exclusions dictionary]
  (cond
    (nil? exclusions)  dictionary
    (true? exclusions) nil

    :else
    (reduce (fn [acc [k v]]
              (let [excluded (k exclusions)]
                (if (-> v keys first (= :repeating))
                  (if-let [dic (strip-exclusions excluded
                                                 (-> v vals first))]
                    (assoc acc k {:repeating dic})
                    acc)
                  (util/assoc-when acc k (when-not excluded
                                           v)))))
            {}
            dictionary)))

(defn edit-verdict
  "Updates the verdict data. Validation takes the template exclusions
  into account. Some updates (e.g., automat dates) can propagate other
  changes as well. Returns errors or modified and (possible
  additional) changes."
  [{{:keys [verdict-id path value]} :data
    organization                    :organization
    application                     :application
    created                         :created
    :as                             command}]
  (let [{:keys [data category
                template
                references]} (command->verdict command)
        schema               (update (category shared/verdict-schemas)
                                     :dictionary
                                     (partial strip-exclusions (:exclusions template)))]
    (if-let [error (schemas/validate-path-value
                    schema
                    path value
                    references)]
      {:errors [[path error]]}
      (let [path    (map keyword path)
            updated (assoc-in data path value)]
        (verdict-update command {$set {(util/kw-path :pate-verdicts.$.data
                                                     path)
                                       value}})
        {:modified created
         :changes  (let [options {:path         path
                                  :value        value
                                  :verdict-data updated
                                  :template     template
                                  :references   references
                                  :category     category}
                         changed (changes options)]
                     (verdict-changes-update command changed)
                     (map (fn [[k v]]
                            [(util/split-kw-path k) v])
                          changed))}))))

(defn buildings
  "Map of building infos: operation id is key and value map contains
  operation (loc-key), building-id (either national or manual id),
  tag (tunnus) and description."
  [{:keys [documents] :as application}]
  (->> documents
       tools/unwrapped
       (filter (util/fn-> :data (contains? :valtakunnallinenNumero)))
       (map (partial app/populate-operation-info
                     (app/get-operations application)))
       (reduce (fn [acc {:keys [schema-info data]}]
                 (let [{:keys [id name
                               description]} (:op schema-info)]
                   (assoc acc
                          (keyword id)
                          (util/convert-values
                           {:operation   name
                            :description description
                            :building-id (->> [:valtakunnallinenNumero
                                               :manuaalinen_rakennusnro]
                                              (select-keys data)
                                              vals
                                              (util/find-first ss/not-blank?))
                            :tag         (:tunnus data)}
                           ss/->plain-string))))
               {})))

(defn- merge-buildings [app-buildings verdict-buildings defaults]
  (reduce (fn [acc [op-id v]]
            (assoc acc op-id (merge defaults (op-id verdict-buildings) v)))
          {}
          app-buildings))

(defn- building-defaults
  "Verdict building defaults (keys with empty values). Nil if building
  or every detail is not to be included in the verdict."
  [exclusions]
  (when-not (true? (:buildings exclusions))
    (let [subkeys (remove #(get-in exclusions [:buildings %])
                          [:rakennetut-autopaikat
                           :kiinteiston-autopaikat
                           :autopaikat-yhteensa
                           :vss-luokka
                           :paloluokka])]
      (when (seq subkeys)
        (assoc (zipmap subkeys (repeat ""))
               :show-building true)))))


(defmulti enrich-verdict
  "Augments verdict data, but MUST NOT update mongo (this is called from
  query actions, too)."
  (fn [{app :application} & _]
    (shared/permit-type->category (:permitType app))))

(defmethod enrich-verdict :default [_ verdict _]
  verdict)

(defmethod enrich-verdict :r
  [{:keys [application]} {:keys [data template]:as verdict}]
  (let [{:keys [exclusions]} template
        addons (merge
                ;; Buildings added only if the buildings section is in
                ;; the template AND at least one of the checkboxes has
                ;; been selected.
                (when-let [defaults (building-defaults exclusions)]
                  {:buildings (merge-buildings (buildings application)
                                               (:buildings data)
                                               defaults)})
                ;; Neighbors added if in the template
                (when-not (:neighbors exclusions)
                  {:neighbor-states (neighbor-states application)}))]
    (assoc-in verdict [:data] (merge data addons))))

(defn open-verdict [{:keys [application] :as command}]
  (let [{:keys [data published] :as verdict} (command->verdict command)]
    {:verdict  (assoc (select-keys verdict [:id :modified :published])
                      :data (if published
                              data
                              (:data (enrich-verdict command
                                                     verdict))))
     :references (:references verdict)}))

(defn- next-section [org-id created verdict-giver]
  (when (and org-id created verdict-giver
             (ss/not-blank? (name org-id))
             (ss/not-blank? (name verdict-giver)))
    (->> (util/to-datetime-with-timezone created)
         (time/year)
         (vector "verdict" (name verdict-giver) (name org-id))
         (ss/join "_")
         (mongo/get-next-sequence-value)
         (str))))

(defn- insert-section [org-id created {{section :verdict-section giver :giver} :data :as verdict}]
  (cond-> verdict
    (ss/blank? section) (assoc-in [:data :verdict-section]
                                  (next-section org-id created giver))))

(defn publish-verdict
  "Publishing verdict does the following:
   1. Finalize and publish verdict
   2. Update application state
   3. Inspection summaries
   4. Other document updates (e.g., waste plan -> waste report)
   5. Freeze (locked and read-only) verdict attachments and update TOS details
   6. TODO: Create tasks
   7. Generate section
   8. Create PDF/A for the verdict
   9. TODO: Generate KuntaGML
  10. TODO: Assignments?"
  [{:keys [created application user organization] :as command}]
  (let [verdict    (->> (enrich-verdict command
                                        (command->verdict command))
                        (insert-section (:organization application) created))
        next-state (sm/verdict-given-state application)
        att-ids    (->> (:attachments application)
                        (filter #(= (-> % :target :id) (:id verdict)))
                        (map :id))]
    (verdict-update command
                    (util/deep-merge
                     {$set (merge
                            {:pate-verdicts.$.data      (:data verdict)
                             :pate-verdicts.$.published created}
                            (att/attachment-array-updates (:id application)
                                                          (comp #{(:id verdict)} :id :target)
                                                          :readOnly true
                                                          :locked   true))}
                     (app-state/state-transition-update next-state
                                                        created
                                                        application
                                                        user)))
    (inspection-summary/process-verdict-given application)
    (when-let [doc-updates (not-empty (transformations/get-state-transition-updates command next-state))]
      (action/update-application command
                                 (:mongo-query doc-updates)
                                 (:mongo-updates doc-updates)))
    (tiedonohjaus/mark-app-and-attachments-final! (:id application)
                                                  created)
    (pdf/create-verdict-attachment command
                                   (assoc verdict :published created))

    ;; KuntaGML
    (when (org/krysp-integration? @organization (:permitType application))
      (-> (assoc command :application (domain/get-application-no-access-checking (:id application)))
          (krysp/verdict-as-kuntagml verdict))
      nil)))
