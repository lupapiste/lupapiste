(ns lupapalvelu.pate.verdict
  (:require [clj-time.core :as time]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-meta-fields :as meta]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as krysp]
            [lupapalvelu.company :as com]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.transformations :as transformations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.tasks :as pate-tasks]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.user :refer [get-user-by-id]]
            [lupapalvelu.verdict :as old-verdict]
            [monger.operators :refer :all]
            [plumbing.core :refer [defnk]]
            [ring.util.codec :as codec]
            [rum.core :as rum]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as sp]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [warnf warn errorf error]]))

;; ------------------------------------------
;; Pre-checks
;; Called from multiple api namespaces.
;; ------------------------------------------

(defn pate-enabled
  "Pre-checker that fails if Pate is not enabled in the application organization scope."
  [{:keys [organization application]}]
  (when (and organization
             (not (-> (org/resolve-organization-scope (:municipality application) (:permitType application) @organization)
                      :pate-enabled)))
    (fail :error.pate-disabled)))

;; TODO: publishing? support
(defn verdict-exists
  "Returns pre-checker that fails if the verdict does not exist.
  Additional conditions:
    :editable? fails if the verdict state is NOT draft or proposal
    :published? fails if the verdict has NOT been published
    :legacy? fails if the verdict is a 'modern' Pate verdict
    :modern? fails if the verdict is a legacy verdict
    :contract? fails if the verdict is not a contract
    :allu-contract? fails if the verdict is not an allu-contract
    :verdict? fails for contracts
    :not-replaced? Fails if the verdict has been OR is being replaced"
  [& conditions]
  {:pre [(set/superset? #{:editable? :published? :legacy? :modern? :contract?
                          :allu-contract? :verdict? :not-replaced? :proposal?}
                        (set conditions))]}
  (let [{:keys [editable? published?
                legacy? modern?
                contract? allu-contract?
                verdict? not-replaced?
                proposal?]} (zipmap conditions (repeat true))]
    (fn [{:keys [data application]}]
      (when-let [verdict-id (:verdict-id data)]
        (let [verdict (util/find-by-id verdict-id
                                       (:pate-verdicts application))
              state (vc/verdict-state verdict)]
          (util/pcond-> (cond
                          (not verdict)
                          :error.verdict-not-found

                          (not (vc/allowed-category-for-application? verdict application))
                          :error.invalid-category

                          (and editable? (and (not= state :draft)
                                              (not= state :proposal)))
                          :error.verdict.not-editable

                          (and published? (not= state :published))
                          :error.verdict.not-published

                          (and legacy? (not (vc/legacy? verdict)))
                          :error.verdict.not-legacy

                          (and modern? (vc/legacy? verdict))
                          :error.verdict.legacy

                          (and contract? (not (vc/contract? verdict)))
                          :error.verdict.not-contract

                          (and allu-contract? (util/not=as-kw (:category verdict) :allu-contract))
                          :error.verdict.not-allu-contract

                          (and verdict? (vc/contract? verdict))
                          :error.verdict.contract

                          (and not-replaced? (or (get-in verdict [:replacement :replaced-by])
                                                 (some #(some-> % :replacement :replaces (= verdict-id))
                                                       (:pate-verdicts application))))
                          :error.verdict-replaced

                          (and proposal? (not= state :proposal))
                          :error.verdict.not-proposal)
                        identity fail))))))

(declare command->backing-system-verdict)

(defn backing-system-verdict
  "Pre-check that fails if the target verdict is not a backing system
  verdict. Note that after Pate has taken into use, verdicts array
  includes only the backing system verdicts."
  [{:keys [application data] :as command}]
  (when application
    (when-not (command->backing-system-verdict command)
      (fail :error.verdict-not-found))))

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

(defn general-handler
  "Returns handler's first and last names if a handler is found. Returns empty
  string if handler is not found."
  [{handlers :handlers}]
  (if-let [{:keys [firstName
                   lastName]} (util/find-first :general handlers)]
    (str firstName " " lastName)
    ""))

(defn- application-deviations [application]
  (-> (domain/get-document-by-name application
                                   "hankkeen-kuvaus")
      :data :poikkeamat :value
      (or "")))

(defn- application-operation [{documents :documents}]
  (let  [{data :data} (first (domain/get-documents-by-subtype documents
                                                              :hankkeen-kuvaus))]
    (or
     (get-in data [:kuvaus :value]) ;; R
     (get-in data [:kayttotarkoitus :value]) ;; YA
     "")))

(defn dicts->kw-paths
  [dictionary]
  (->> dictionary
       (map (fn [[k v]]
              (if-let [repeating (:repeating v)]
                (map #(util/kw-path k %) (dicts->kw-paths repeating))
                k)))
       flatten))

(defn published-template-removed-sections
  "Set of removed section ids from a published template."
  [template]
  (or (some->> template
               :data
               :removed-sections
               (map (fn [[k v]]
                      (when v
                        (keyword k))))
               (remove nil?)
               set)
      #{}))

(defn inclusions
  "List if kw-paths that denote the dicts included in the verdict. By
  default, every dict is included, but a dict is excluded if any
  the following are true:

  - Dict schema :template-section refers to a removed template section.

  - Every section that dict belongs to has a :template-section that
    refers to a removed template-section.

  - Dict schema :template-dict refers to template dict that is not in
    the template inclusions. In other words, the template dict is part
    of a removed template section.

  Note: if a :repeating is included, its every dict is included."
  [category published-template]
  (let [removed-tem-secs   (published-template-removed-sections published-template)
        t-inclusions       (->> (:inclusions published-template)
                                (map keyword)
                                set)
        {:keys [dictionary
                sections]} (verdict-schemas/verdict-schema category)
        dic-sec            (schemas/dict-sections sections)
        removed-ver-secs   (->> sections
                                (filter #(contains? removed-tem-secs
                                                    (:template-section %)))
                                (map :id)
                                set)]
    (->> (keys dictionary)
         (remove (fn [dict]
                   (let [{tem-sec :template-section
                          tem-dic :template-dict} (dict dictionary)
                         in-sections              (dict dic-sec)]
                     (or
                      (contains? removed-tem-secs tem-sec)
                      (and (seq in-sections)
                           (empty? (set/difference in-sections removed-ver-secs)))
                      (and tem-dic (not (contains? t-inclusions tem-dic)))))))
         (select-keys dictionary)
         dicts->kw-paths)))

(defn default-verdict-draft
  "Prepares the default draft (for initmap)
  with :category, :schema-version, :template, :data and :references
  keys. Resolves inclusions and initial values from the template."
  [{:keys [category published]}]
  (let [{dic     :dictionary
         version :version} (verdict-schemas/verdict-schema category)
        {:keys [data
                settings]} published
        incs               (inclusions category published)
        included?          #(contains? (set incs) %)]
    {:category       category
     :schema-version version
     :template       {:inclusions incs}
     :data           (reduce-kv (fn [acc dict value]
                                  (let [{:keys [template-dict
                                                repeating]} value
                                        initial-value       (and template-dict
                                                                 (template-dict data))]
                                    (cond
                                      ;; So far only one level repeating can be initialized
                                      (and initial-value repeating)
                                      (reduce (fn [m v]
                                                (reduce-kv (fn [a inner-k inner-v]
                                                             (cond-> a
                                                               (included? (util/kw-path dict inner-k))
                                                               (assoc-in [dict (mongo/create-id) inner-k] inner-v)))
                                                           m
                                                           v))
                                              acc
                                              initial-value)

                                      (and initial-value (included? dict))
                                      (assoc acc dict initial-value)

                                      :else
                                      acc)))
                                {}
                                dic)
     ;; Foremen, plans and reviews are initialized in separate init functions.
     :references (dissoc settings :foremen :plans :reviews :handler-titles)}))


;; Argument map:
;; template:    Published template for the verdict
;; application: Application
;; draft:       Default draft:
;;
;;              template:   Template part of the verdict data. The map
;;                          should contain at least inclusions.
;;
;;              data: Initialized verdict data (from
;;                    default-verdict-drat).
;;
;;              references: References for the data (default is
;;                          published settings)
;;
;; The return value is the modified (if needed) draft.
(defmulti initialize-verdict-draft (util/fn-> :template :category keyword))

;; Initialization helper functions
(defn section-removed? [template section]
  (contains? (published-template-removed-sections (:published template))
             section))

(defn dict-included? [{:keys [draft]} dict]
  (contains? (-> draft :template :inclusions set) dict))

(defn- resolve-requirement-inclusion
  "Dict is either :foremen, :plans or :reviews. Only empty requirement
  is excluded."
  [{:keys [template] :as initmap} dict]
  (let [included (keyword (str (name dict) "-included"))]
    (if (some-> initmap :draft :references dict seq)
      (assoc-in initmap
                [:draft :data included]
                (not (section-removed? template dict)))
      (update-in initmap
                 [:draft :template :inclusions]
                 util/difference-as-kw [dict included]))))

(defn init--foremen
  "Build a multi-select reference-list value according to template
  data. The included toggle is initialized in `init--included-checks`
  above."
  [{:keys [template] :as initmap}]
  (let [t-data  (-> template :published :data)
        updated (reduce (fn [acc code]
                          (let [codename (name code)
                                inc-dict  (keyword (str codename "-included"))
                                included? (inc-dict t-data)
                                selected? (when included?
                                            (code t-data))]
                            (cond-> acc
                              included? (update-in [:draft :references :foremen] conj codename)
                              selected? (update-in [:draft :data :foremen] conj codename))))
                        initmap
                        helper/foreman-codes)]
    (resolve-requirement-inclusion updated :foremen)))

(defn init--requirements-references
  "Plans and reviews are defined in the published template settings."
  [{:keys [template] :as initmap} dict]
  (-<> initmap
       (reduce (fn [acc {:keys [selected] :as item}]
                 (let [id (mongo/create-id)]
                   (cond-> (update-in acc
                                      [:draft :references dict]
                                      conj (-> item
                                               (dissoc :selected)
                                               (assoc :id id)))
                    selected (update-in [:draft :data dict]
                                        conj id))))
               <>
              (get-in template [:published :settings dict]))
      (resolve-requirement-inclusion dict)))

(defn init--verdict-dates
  "Include in verdict only those verdict dates that have been checked in
  the template. Also, if no verdict dates, the automatic-verdict-dates
  toggle is excluded."
  [{:keys [template] :as initmap}]
  (update-in initmap
             [:draft :template :inclusions]
             (fn [incs]
               (let [{:keys [verdict-dates]} (-> template :published :data)]
                 (cond-> (util/difference-as-kw incs
                                                [:automatic-verdict-dates]
                                                helper/verdict-dates)
                   (seq verdict-dates)
                   (util/union-as-kw verdict-dates
                                     [:automatic-verdict-dates]))))))

(defn init--upload
  "Upload is included if the attachments section is not removed and
  upload is checked in the template. Note that the attachments section
  dependence has been resolved earlier."
    [{:keys [template] :as initmap}]
  (update-in initmap
             [:draft :template :inclusions]
             (fn [incs]
               (util/difference-as-kw incs
                                      (when-not (some-> template
                                                        :published
                                                        :data
                                                        :upload)
                                        [:upload])))))

(defn init--verdict-giver-type
  "Adds giver key into template. The value is either lautakunta or
  viranhaltija. Verdict-section and boardname dicts not included for
  viranhaltija verdicts."
  [{:keys [template] :as initmap}]
  (let [giver-type (some-> template :published :data :giver)]
    (cond-> (assoc-in initmap
                      [:draft :template :giver]
                      giver-type)
      (util/=as-kw giver-type :viranhaltija)
      (update-in [:draft :template :inclusions]
                 util/difference-as-kw  [:verdict-section
                                         :boardname]))))

(defn init--dict-by-application
  "Inits given dict (if included) with the app-fn result (if the result
  is non-nil). App-fn takes application as an argument"
  [{:keys [application] :as initmap} dict app-fn]
  (let [value (when (dict-included? initmap dict)
                (app-fn application))]
    (if (nil? value)
      initmap
      (assoc-in initmap [:draft :data dict] value))))

(def buildings-inclusion {:autopaikat [:buildings.rakennetut-autopaikat
                                       :buildings.kiinteiston-autopaikat
                                       :buildings.autopaikat-yhteensa]
                          :vss-luokka [:buildings.vss-luokka]
                          :paloluokka [:buildings.paloluokka]})
(def buildings-inclusion-keys (-> buildings-inclusion vals flatten))

(defn init--buildings
  "Update inclusions according to selected building details."
  [{:keys [template] :as initmap}]
  (cond-> initmap
    (not (section-removed? template :buildings))
    (update-in [:draft :template :inclusions]
               (fn [incs]
                 (let [details (some->  template :published :data)]
                   (reduce-kv (fn [acc k v]
                                (cond-> acc
                                  (k details) (util/union-as-kw v)))
                              (util/difference-as-kw incs
                                                     buildings-inclusion-keys)
                              buildings-inclusion))))))

(defn init--property-id
  [{:keys [application] :as initmap}]
  (let [value (when (dict-included? initmap :propertyIds.property-id)
                {(mongo/create-id) {:property-id (sp/to-human-readable-property-id (:propertyId application))}})]
    (if (nil? value)
      initmap
      (assoc-in initmap [:draft :data :propertyIds] value))))

(defn- integer-or-nil [value]
  (when (integer? value)
    value))

(defn init--permit-period
  "YA verdict start and end dates are copied from tyoaika document"
  [{:keys [application] :as initmap}]
  (let [doc-data (:data (domain/get-document-by-name application
                                                     "tyoaika"))
        starts (and (dict-included? initmap :start-date)
                    (integer-or-nil (get-in doc-data
                                            [:tyoaika-alkaa-ms :value])))
        ends (and (dict-included? initmap :end-date)
                  (integer-or-nil (get-in doc-data
                                          [:tyoaika-paattyy-ms :value])))]
    (cond-> initmap
      starts (assoc-in [:draft :data :start-date] starts)
      ends (assoc-in [:draft :data :end-date] ends))))

(defn init--handler
  "General handler from application or user from command"
  [{:keys [application user] :as initmap}]
    (assoc-in
      initmap [:draft :data :handler]
      (if (ss/not-blank? (general-handler application))
        (general-handler application)
        (str (:firstName user) " " (:lastName user)))))


(defmethod initialize-verdict-draft :r
  [initmap]
  (-> initmap
      (init--dict-by-application :handler general-handler)
      (init--dict-by-application :deviations application-deviations)
      init--foremen
      (init--requirements-references :plans)
      (init--requirements-references :reviews)
      init--verdict-dates
      init--upload
      init--verdict-giver-type
      init--buildings
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)))

(defmethod initialize-verdict-draft :p
  [initmap]
  (-> initmap
      (init--dict-by-application :handler general-handler)
      (init--dict-by-application :deviations application-deviations)
      init--verdict-dates
      init--upload
      init--verdict-giver-type
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)))

(defmethod initialize-verdict-draft :ya
  [initmap]
  (-> initmap
      init--handler
      init--verdict-dates
      (init--dict-by-application :verdict-type schema-util/ya-verdict-type)
      (init--requirements-references :plans)
      (init--requirements-references :reviews)
      (init--requirements-references :handler-titles)
      init--upload
      init--verdict-giver-type
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)
      init--property-id
      init--permit-period))

(defmethod initialize-verdict-draft :tj
  [initmap]
  (-> initmap
      (init--dict-by-application :handler general-handler)
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)))

(defmethod initialize-verdict-draft :contract
  [initmap]
  (-> initmap
      (init--dict-by-application :handler general-handler)
      (init--requirements-references :reviews)))

(declare enrich-verdict)

(defn user-ref [{user :user}]
  (select-keys user [:id :username]))

(defn wrapped-state [{:keys [created user]} state]
  (metadata/wrap (:username user) created (name state)))

(defnk new-verdict-draft
  [template-id application organization
   timestamp command {replacement-id nil}
   :as options]
  (let [template       (template/verdict-template options)
        {draft :draft} (-> {:template    template
                            :draft       (default-verdict-draft template)
                            :application application
                            :user        (:user command)}
                           initialize-verdict-draft
                           (update-in [:draft :data]
                                      (partial metadata/wrap-all (metadata/wrapper command)))
                           (update-in [:draft]
                                      (fn [draft]
                                        (cond-> (assoc draft
                                                       :id (mongo/create-id)
                                                       :modified timestamp)

                                          replacement-id
                                          (assoc-in [:replacement :replaces]
                                                    replacement-id))))
                           (assoc-in [:draft :state] (wrapped-state command :draft)))]
    (action/update-application command
                               {$push {:pate-verdicts
                                       (sc/validate schemas/PateVerdict draft)}})
    (:id draft)))

(defn copy-verdict-draft [{:keys [application created user] :as command} replacement-id]
  (let [draft (-> (util/find-by-id replacement-id (:pate-verdicts application))
                  (assoc :id (mongo/create-id)
                         :modified created
                         :state (wrapped-state command :draft))
                  (dissoc :published
                          :archive)
                  (assoc-in [:replacement :replaces] replacement-id))
        draft (assoc-in draft[:template :inclusions] (mapv keyword (get-in draft [:template :inclusions])))
        draft (assoc draft :data
                           (dissoc (:data draft)
                                   :attachments
                                   :handler
                                   :verdict-section
                                   :signatures))
        {draft :draft} (-> {:draft draft
                            :application application
                            :user user}
                           init--handler
                           (update-in [:draft :data]
                                      (partial metadata/wrap-all (metadata/wrapper command))))]

    (action/update-application command
                               {$push {:pate-verdicts
                                       (sc/validate schemas/PateVerdict draft)}})
    (:id draft)))

(defn legacy-verdict-inclusions [category]
  (-> category
      legacy/legacy-verdict-schema
      :dictionary
      dicts->kw-paths))

(defn new-legacy-verdict-draft
  "Legacy verdicts do not have templates or references. Inclusions
  contain every schema dict."
  [{:keys [application created] :as command}]
  (let [category   (schema-util/application->category application)
        verdict-id (mongo/create-id)]
    (action/update-application command
                               {$push {:pate-verdicts
                                       (sc/validate schemas/PateVerdict
                                                    {:id       verdict-id
                                                     :modified created
                                                     :state    (wrapped-state command :draft)
                                                     :category (name category)
                                                     :data     {:handler (general-handler application)}
                                                     :template {:inclusions (legacy-verdict-inclusions category)}
                                                     :legacy?  true})}})
    verdict-id))

(declare verdict-schema)

(defn mask-verdict-data [{:keys [user application]} verdict]
  (cond
    (not (auth/application-authority? application user))
    (util/dissoc-in verdict [:data :bulletinOpDescription])
    :default verdict))

(defn command->verdict
  "Gets verdict based on command data. If refresh? is true then the
  application is read from mongo and not taken from command."
  ([{:keys [data application] :as command} refresh?]
   (update (->> (if refresh?
                  (domain/get-application-no-access-checking (:id application)
                                                             {:pate-verdicts 1})
                  application)
                :pate-verdicts
                (util/find-by-id (:verdict-id data))
                metadata/unwrap-all
                (mask-verdict-data command))
           :category keyword))
  ([command]
   (command->verdict command false)))

(defn- verdict-attachment-ids
  "Ids of attachments, whose either target or source is the given
  verdict."
  [{:keys [attachments]} verdict-id]
  (->> attachments
       (filter (fn [{:keys [target source]}]
                 (or (and (util/=as-kw (:type target) :verdict)
                          (= (:id target) verdict-id))
                     (and (util/=as-kw (:type source) :verdicts)
                          (= (:id source) verdict-id)))))
       (map :id)))

(defn delete-verdict [verdict-id {application :application :as command}]
  (action/update-application command
                             {$pull {:pate-verdicts {:id verdict-id}}})
  (att/delete-attachments! application
                           (verdict-attachment-ids application verdict-id)))

(defn delete-verdict-tasks-helper
  "Identifies verdict tasks and their attachments. Separate function
  since called also upon replacing a verdict. Returns a map
  with :task-ids and :task-attachment-ids keys."
  [application verdict-id]
  (let [task-ids (old-verdict/deletable-verdict-task-ids application verdict-id)]
    {:task-ids            task-ids
     :task-attachment-ids (map :id (old-verdict/task-ids->attachments application
                                                                      task-ids))}))

(defn delete-legacy-verdict [{:keys [application user created] :as command}]
  ;; Mostly copied from the old verdict_api.clj.
  (let [{verdict-id :id
         published  :published}            (command->verdict command)
        target                             {:type "verdict"
                                            :id verdict-id} ; key order seems to be significant!
        {:keys [sent state pate-verdicts]} application
        ;; Deleting the only given verdict? Return sent or submitted
        ;; state.  When Pate is in production every backing-system
        ;; verdict (in verdicts) is published.
        step-back?                         (and published
                                                (= 1 (count (filter :published pate-verdicts)))
                                                (empty? (:verdicts application))
                                                (states/verdict-given-states (keyword state)))
        {:keys [task-ids
                task-attachment-ids]}      (delete-verdict-tasks-helper application verdict-id)
        updates                            (merge {$pull {:pate-verdicts {:id verdict-id}
                                                          :comments      {:target target}
                                                          :tasks         {:id {$in task-ids}}}}
                                                  (when step-back?
                                                    (app-state/state-transition-update
                                                     (if (and sent
                                                              (sm/valid-state? application
                                                                               :sent))
                                                       :sent
                                                       :submitted)
                                                     created
                                                     application
                                                     user)))]
      (action/update-application command updates)
      (bulletins/process-delete-verdict (:id application) verdict-id)
      (att/delete-attachments! application
                               (->> task-attachment-ids
                                    (concat (verdict-attachment-ids application verdict-id))
                                    (remove nil?)))
      (appeal-common/delete-by-verdict command verdict-id)
      (when step-back?
        (notifications/notify! :application-state-change command))))

(defn- listify
  "Transforms argument into list if it is not sequential. Nil results
  in empty list."
  [a]
  (cond
    (sequential? a) a
    (nil? a)        '()
    :default        (list a)))

(defn- wrap [command value]
  ((metadata/wrapper command) value))

(defn verdict-update
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
                                                   (wrap command v)))
                                          {}
                                          changes)})))

(defn update-automatic-verdict-dates
  "Returns map of dates (timestamps). While the calculation takes every date into
  account, the result only includes the dates included in the
  template."
  [{:keys [template references verdict-data]}]
  (let [timestamp   (:verdict-date verdict-data)
        date-deltas (:date-deltas references)
        automatic?  (:automatic-verdict-dates verdict-data)]
    (when (and automatic? (integer? timestamp))
      (loop [dates      {}
             [kw & kws] helper/verdict-dates
             latest     timestamp]
        (if (nil? kw)
          (select-keys dates (util/intersection-as-kw helper/verdict-dates
                                                      (:inclusions template)))
          (let [{:keys [delta unit]} (kw date-deltas)
                result (date/parse-and-forward latest
                                               (util/->long delta)
                                               (keyword unit))]
            (recur (assoc dates
                          kw
                          result)
                   kws
                   result)))))))

;; Additional changes to the verdict data.
;; Methods options include category, template, verdict-data, path and value.
;; Changes is called after value has already been updated into mongo.
;; The method result is a changes for verdict data.
(defmulti changes (fn [{:keys [path]}]
                    ;; Dispatcher result: :last-path-part
                    (keyword (last path))))

(defmethod changes :default [_])

(defmethod changes :verdict-date [options]
  (update-automatic-verdict-dates options))

(defmethod changes :automatic-verdict-dates [options]
  (update-automatic-verdict-dates options))

(defn verdict-filled?
  "Have all the required fields been filled. Refresh? argument can force
  the read from mongo (see command->verdict)."
  ([command refresh?]
   (let [{:keys [data] :as verdict} (command->verdict command refresh?)
         schema (vc/verdict-schema verdict)]
     (schemas/required-filled? schema data)))
  ([command]
   (verdict-filled? command false)))

(defn proposal-filled?
  [command]
  (let [{:keys [data] :as verdict} (command->verdict command)
        schema (vc/verdict-schema verdict)]
    (schemas/required-filled? schema data [:verdict-section :verdict-code])))

(defn- app-documents-having-buildings [application]
  (->> application
       app/get-sorted-operation-documents
       tools/unwrapped
       (filter #(util/intersection-as-kw [:valtakunnallinenNumero
                                          :manuaalinen_rakennusnro]
                                         (keys %)))
       (map (partial app/populate-operation-info
                     (app/id-to-operation-map application)))))

(defn- op-description [{primary     :primaryOperation
                        secondaries :secondaryOperations} op]
  (->> (cons primary secondaries)
       (util/find-by-id (:id op))
       :description))

(defn buildings
  "Map of building infos: operation id is key and value map contains
  operation (loc-key), building-id (either national or manual id),
  tag (tunnus), description and order (primary operation is the first)."
  [{primary-op :primaryOperation :as application}]
  (->> application
       app-documents-having-buildings
       (reduce (fn [acc {:keys [schema-info data]}]
                 (let [{:keys [id name] :as op} (:op schema-info)]
                   (assoc acc
                          (keyword id)
                          (util/convert-values
                           {:operation   name
                            :description (op-description application op)
                            :building-id (->> [:valtakunnallinenNumero
                                               :manuaalinen_rakennusnro]
                                              (select-keys data)
                                              vals
                                              (util/find-first ss/not-blank?))
                            :tag         (:tunnus data)
                            :order (if (= id (:id primary-op)) 0 1)}
                           ss/->plain-string))))
               {})))

(defn- building-update-map [{:keys [building-updates]}]
  (reduce (fn [acc building-update]
            (assoc acc (:operationId building-update)
                   (update building-update :nationalBuildingId not-empty)))
          {}
          building-updates))

(defn ->buildings-array
  "Construction of the application-buildings array. This should be
  equivalent to ->buildings-summary function in
  lupapalvelu.backing-system.krysp.building-reader the namespace, but instead of
  the message from backing system, here all the input data is
  originating from PATE verdict."
  [application]
  (let [building-updates (building-update-map application)]
    (->> application
         app-documents-having-buildings
         (util/indexed 1)
         (map (fn [[n {toimenpide :data {op :op} :schema-info}]]
                (let [{:keys [rakennusnro valtakunnallinenNumero mitat kaytto tunnus]} toimenpide
                      description-parts (remove ss/blank? [tunnus (op-description application op)])
                      building-update (get building-updates (:id op))
                      location (when-let [loc (:location building-update)] [(:x loc) (:y loc)])
                      location-wgs84 (when location (coord/convert "EPSG:3067" "WGS84" 5 location))]
                  {:propertyId (or (:propertyId building-update) (:propertyId application))
                   :localShortId (or rakennusnro (when (validators/rakennusnumero? tunnus) tunnus))
                   :nationalId (or valtakunnallinenNumero (:nationalBuildingId building-update))
                   :buildingId (or valtakunnallinenNumero (:nationalBuildingId building-update) rakennusnro)
                   :location-wgs84 location-wgs84
                   :location location
                   :area (:kokonaisala mitat)
                   :index (str n)
                   :description (ss/join ": " description-parts)
                   :operationId (:id op)
                   :usage (or (:kayttotarkoitus kaytto) "")}))))))

(defn edit-verdict
  "Updates the verdict data. Validation takes the template inclusions
  into account. Some updates (e.g., automatic dates) can propagate other
  changes as well. Returns processing result or modified and (possible
  additional) changes."
  [{{:keys [path value]} :data :keys [application created] :as command}]
  (let [{:keys [data category
                template
                references]
         :as verdict} (command->verdict command)
        {:keys [data value path op]
         :as     processed}    (schemas/validate-and-process-value
                                (vc/verdict-schema verdict)
                                path value
                                ;; Make sure that building related
                                ;; paths can be resolved.
                                (update data
                                        :buildings
                                        (fn [houses]
                                          (merge (zipmap (keys (buildings application))
                                                         (repeat {}))
                                                 houses)))
                                references)]
    (if-not data
      processed
      (let [mongo-path (util/kw-path :pate-verdicts.$.data path)]
        (verdict-update command
                        (if (= op :remove)
                          {$unset {mongo-path 1}}
                          {$set {mongo-path (wrap command value)}}))
        (template/changes-response {:modified created
                                    :changes  (let [options {:path         path
                                                             :value        value
                                                             :verdict-data data
                                                             :template     template
                                                             :references   references
                                                             :category     category}
                                                    changed (changes options)]
                                                (verdict-changes-update command changed)
                                                (map (fn [[k v]]
                                                       [(util/split-kw-path k) v])
                                                     changed))}
                                   processed)))))


(defn- merge-buildings [app-buildings verdict-buildings]
  (reduce (fn [acc [op-id v]]
            (assoc acc op-id (merge {:show-building true}
                                    (op-id verdict-buildings) v)))
          {}
          app-buildings))

(defn command->category [{app :application}]
  (schema-util/application->category app))

(defn statements
  "List of maps with given (timestamp), text (string) and
  status (loc-key part) keys."
  [{statements :statements} given-only?]
  (map (fn [{:keys [given person status]}]
         {:text   (:text person)
          :given  given
          :status status})
       (if given-only?
         (filter :given statements)
         statements)))

(defn enrich-verdict
  "Augments verdict data, but MUST NOT update mongo (this is called from
  query actions, too).  If final? is truthy then the enrichment is
  part of publishing."
  ([{:keys [application]} {:keys [data template]
                           :as   verdict} final?]
   (let [inc-set (->> template
                      :inclusions
                      (map keyword)
                      set)
         addons  (merge
                  (when (:buildings.show-building inc-set)
                    {:buildings (merge-buildings (buildings application)
                                                 (:buildings data))})
                  (when (:neighbors inc-set)
                    {:neighbor-states (neighbor-states application)})
                  (when (:statements inc-set)
                    {:statements (statements application final?)})
                  (when (:bulletin-desc-as-operation data)
                    {:operation (:bulletin-op-description data)}))]
     (assoc verdict :data (merge data addons))))
  ([command verdict]
   (enrich-verdict command verdict false)))

(defn open-verdict [command]
  (let [{:keys [data state template]
         :as   verdict} (command->verdict command)
        fields        [:id :modified :state :category
                       :schema-version :legacy? :signatures]]
    {:verdict    (assoc (select-keys verdict fields)
                        :data (if (util/=as-kw state :draft)
                                (:data (enrich-verdict command
                                                       verdict))
                                data)
                        :giver (:giver template)
                        :inclusions (:inclusions template))
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

(defn- verdict-attachment-items
  "Type-groups, type-ids and ids of the verdict attachments. These
  include the new, added verdict attachments and the attachments
  corresponding to the given attachments dict.
  Note: Empty attachments are ignored."
  [{:keys [application]} {verdict-id :id data :data} attachments-dict]
  (let [ids (set (attachments-dict data))]
    (->> (:attachments application)
         (filter #(some-> % :latestVersion :fileId ss/not-blank?))
         (filter (fn [{:keys [id target]}]
                   (or (= verdict-id (:id target))
                       (contains? ids id))))
         (map (fn [{:keys [type id]}]
                {:type-group (:type-group type)
                 :type-id    (:type-id type)
                 :id         id})))))

(defn attachment-items
  "Returns a map with the following properties:

    items: Attachment items (`verdict-attachment-items` result)

    update-fn: Function that takes verdict data as argument and
               updates it."
  [{:keys [application] :as command} verdict]
  (let [items (verdict-attachment-items command
                                        verdict
                                        :attachments)]
    {:items     items
     :update-fn (fn [data]
                  (assoc data
                         :attachments
                         (->> (cons (schemas/resolve-verdict-attachment-type application)
                                    items)
                              (group-by #(select-keys % [:type-group
                                                         :type-id]))
                              (map (fn [[k v]]
                                     (assoc k :amount (count v)))))))}))

(defn validate-tasks
  "Log and fail on validation errors. Returns tasks if everything OK."
  [tasks]
  (when-let [errs (->> tasks
                       (map #(tasks/task-doc-validation (-> % :schema-info :name) %))
                       (filter seq)
                       seq)]
    (doseq [err errs
            sub-error err]
      (warnf "PATE task (%s) validation warning - elem locKey: %s, results: %s"
             (get-in sub-error [:document :id])
             (get-in sub-error [:element :locKey])
             (get-in sub-error [:result])))
    (fail! :error.task-not-valid))
  tasks)

(defn- archive-info
  "Convenience info map is stored into verdict for archiving purposes."
  [{:keys [data template references]}]
  (-<>> (select-keys data [:verdict-date :lainvoimainen :anto])
        (util/filter-map-by-val #(and (integer? %) (pos? %)))
        (assoc <>
               :verdict-giver (if (util/=as-kw :lautakunta (:giver template))
                                (:boardname references)
                                (cols/join-non-blanks " "
                                                      (:handler-title data)
                                                      (:handler data))))))

(defn accepted-verdict? [verdict]
  (some #{(keyword (get-in verdict [:data :verdict-code]))} [:hyvaksytty :myonnetty])) ; TODO Which verdict codes are accepted??


(defn link-permit-application [application]
  (->> application
       (meta/enrich-with-link-permit-data)
       (app/get-link-permit-apps)
       (first)))

(defn can-verdict-be-replaced?
  "Modern verdict can be replaced if its published and not already
  replaced (or being replaced). Contracts cannot be replaced."
  [{:keys [pate-verdicts]} verdict-id]
  (when-let [{:keys [published legacy?
                     replacement category]
              :as   verdict} (util/find-by-id verdict-id pate-verdicts)]
    (and published
         (not legacy?)
         (not (vc/contract? verdict))
         (not (vc/proposal? verdict))
         (not (some-> replacement :replaced-by))
         (not (util/find-first #(= (get-in % [:replacement :replaces])
                                   verdict-id)
                               pate-verdicts)))))

(defn replace-verdict [{:keys [application] :as command} old-verdict-id verdict-id]
  (let [{:keys [task-ids
                task-attachment-ids]} (delete-verdict-tasks-helper application
                                                                   old-verdict-id)]
    (action/update-application command
                               {:pate-verdicts {$elemMatch {:id old-verdict-id}}}
                               {$set  {:pate-verdicts.$.replacement {:user        (user-ref command)
                                                                     :replaced-by verdict-id}}
                                $pull {:tasks {:id {$in task-ids}}}})
    (att/delete-attachments! application task-attachment-ids)))

;; Each finalize-- function receives an options map:
;; command:  Original command
;; application: Up-to-date application
;; verdict: Up-to-date verdict
;;
;; Function returns a map (each key is optional):

;; application: updated application, can be nil if not updated. Note
;;     that if only the verdict has been updated, the application does not
;;     need to be updated accordingly (the "old verdict" is never used).
;;
;; verdict: updated verdict, can be nil if not updated.
;;
;; updates: Mongo updates for `verdict-update` ($ denotes verdict
;;     $elemMatch). Updates will be merged in the end.
;;
;; commit-fn: (optional) Function that takes an option map (command,
;;     application, verdict) as argument. The functions are called
;;     after everything else has been successfully finalized.
;;
;; On error, a function can bail out with `action/fail!`.

(defn- verdict->updates
  "[:a :b.c] -> {$set {:pate-verdicts.$.a   a-value
                       :pate-verdicts.$.b.c c-value}}
  Returns :verdict :updates map."
  [verdict & kw-paths]
  {:verdict verdict
   :updates (->> kw-paths
                 (map (fn [kwp]
                        [(util/kw-path :pate-verdicts.$ kwp)
                         (get-in verdict (util/split-kw-path kwp))]))
                 (into {})
                 (hash-map $set))})

(defn finalize--verdict [{:keys [command verdict]}]
  (let [{:keys [created]} command
        verdict           (assoc (enrich-verdict command verdict true)
                                 :state (wrapped-state command :published)
                                 :published {:published (:created command)}
                                 :archive (archive-info verdict))
        data-kws          (map #(util/kw-path :data %)
                               (-> verdict :data keys))]
    (apply verdict->updates verdict
           (concat data-kws
                   [:template.inclusions :state
                    :published.published :archive]))))

(defn finalize--signatures
  [{:keys [command verdict]}]
  (when (vc/contract? verdict)
    (let [{user :user} command
          {data :data} verdict]
      (verdict->updates (assoc verdict
                               :signatures
                               ;; Verdict giver (handler) is the
                               ;; initial, implicit signer
                               [{:name    (:handler data)
                                 :user-id (:id user)
                                 :date    (:verdict-date data)}])
                        :signatures))))


(defn finalize--section
  "Section is generated only for non-board (lautakunta) non-legacy
  verdicts. Section is created into Mongo sequence right away and the
  addition cannot be (safely) undone."
  [{:keys [command application verdict]}]
  (let [{:keys [data template legacy? category]} verdict
        {section :verdict-section}               data
        {giver :giver}                           template]

    (when (and (ss/blank? section)
               (util/not=as-kw giver :lautakunta)
               (not legacy?)
               (not (vc/contract? verdict)))
      (let [verdict (-> verdict
                        (assoc-in [:data :verdict-section]
                                  (next-section (:organization application)
                                                (:created command)
                                                giver))
                        (update-in [:template :inclusions]
                                   #(distinct (conj % :verdict-section))))]
        (verdict->updates verdict
                          :data.verdict-section
                          :template.inclusions)))))

(defn update-waste-documents
  "Updates or adds a waste report document if a waste plan document
  already exists. If the function is called with the optional
  `dry-run?` argument having a truthy value, the function only returns
  the mongo updates that would be executed. This is used for testing."
  [{:keys [application command]} & [dry-run?]]
  (let [transition-updates (transformations/get-state-transition-updates
                            (assoc command :application application)
                            (sm/verdict-given-state application))]
    (when (and (not dry-run?)
               (not-empty (:mongo-updates transition-updates)))
      (action/update-application (assoc command :application application)
                                 (or (:mongo-query transition-updates)
                                     {:_id (:id application)})
                                 (:mongo-updates transition-updates)))
    transition-updates))

(defn finalize--application-state
  "Updates for application state, history and affected documents."
  [{:keys [command application]}]
  (let [state (sm/verdict-given-state application)]
    ;; History, modified and document updates not needed in the application.
    {:application (assoc application :state state)
     :updates     (util/deep-merge (app-state/state-transition-update
                                    state
                                    (:created command)
                                    application
                                    (:user command)))
     :commit-fn update-waste-documents}))

(defn finalize--buildings-and-tasks
  [{:keys [command application verdict]}]
  (let [buildings (->buildings-array application)
        tasks (validate-tasks (pate-tasks/pate-verdict->tasks
                               verdict
                               (:created command)
                               buildings))]
    (cond-> {:application (assoc application :buildings buildings)
             :updates     {$set {:buildings buildings}}}
      (seq tasks) (-> (assoc-in [:application :tasks] tasks)
                      (assoc-in [:updates $push :tasks] {$each tasks})))))

(defn finalize--attachments [{:keys [command application verdict]}]
  (let [{att-items :items
         update-fn :update-fn} (attachment-items command verdict)
        verdict-attachment?    #(util/includes-as-kw? (map :id att-items)
                                                      (:id %))
        verdict-draft-attachment? #(and (verdict-attachment? %)
                                        (some-> % :metadata :draftTarget))
        target                 {:type "verdict"
                                :id   (:id verdict)}]
    (-> (update verdict :data update-fn)
        (verdict->updates :data.attachments)
        (assoc :application
               (update application :attachments
                       #(map (fn [attachment]
                              (util/pcond-> attachment
                                            verdict-attachment?
                                            (assoc :target target)))
                            %)))
        (update-in [:updates $set]
                   merge
                   (att/attachment-array-updates (:id application)
                                                 verdict-attachment?
                                                 :readOnly true
                                                 :locked   true
                                                 :target target)
                   (att/attachment-array-updates (:id application)
                                                 verdict-draft-attachment?
                                                 :metadata.nakyvyys "julkinen"
                                                 :metadata.draftTarget false))
        (assoc :commit-fn (fn [{:keys [command application]}]
                            (tiedonohjaus/mark-app-and-attachments-final! (:id application)
                                                                          (:created command)))))))

(defn finalize--link-permit
  [{:keys [application verdict]}]
  (when (and (app/jatkoaika-application? application)
               (accepted-verdict? verdict))
    {:commit-fn (fn [{:keys [application verdict]}]
                  (app/add-continuation-period
                   (link-permit-application application)
                   (:id application)
                   (get-in verdict [:data :handler])
                   (get-in verdict [:data :voimassa])))}))

(defn finalize--replaced-verdict [{:keys [verdict]}]
  (when-let [replace-verdict-id (get-in verdict [:replacement :replaces])]
    {:commit-fn (fn [{:keys [command verdict]}]
                  (replace-verdict command replace-verdict-id (:id verdict)))}))

(defn finalize--kuntagml
  [{:keys [command application verdict]}]
  (when (and (not (:legacy? verdict))
             (not (vc/contract? verdict))
             (org/krysp-integration? @(:organization command)
                                     (:permitType application)))
    {:commit-fn (fn [{:keys [command application verdict]}]
                  (try+ (krysp/verdict-as-kuntagml (assoc command
                                                          :application application)
                                                   verdict)
                        (catch [:ok false] {text :text}
                          (error (format "KuntaGML failed for verdict %s (permit-type %s)"
                                         (:id verdict) (:permitType application))))))}))

;; Published verdict.pdf is created asynchronously via message
;; queue. If the generation fails, the message is requeued.

(defonce pate-queue "lupapalvelu.pate.queue")
(def pate-session (if (env/feature? :jms)
                    (if-let [conn (jms/get-default-connection)]
                      (-> conn
                          (jms/create-transacted-session)
                          (jms/register-session :consumer))
                      (warn "No JMS connection available"))
                    (warn "JMS feature disabled")))

(defn- pdf--verdict [command verdict]
  (or (some-> verdict :published :attachment-id)
      (when-let [attachment-id (pdf/create-verdict-attachment command verdict)]
        (verdict-update command
                        {$set {:pate-verdicts.$.published.attachment-id attachment-id}})
        true)))

(defn- pdf--proposal [command verdict]
  (if (some-> verdict :proposal :attachment-id)
    (pdf/create-verdict-attachment-version command verdict)
    (when-let [attachment-id (pdf/create-verdict-attachment command verdict)]
      (verdict-update command
                      {$set {:pate-verdicts.$.proposal.attachment-id attachment-id}})
      true)))

(declare pdf--signatures)

(defn create-verdict-pdf
  "Creates verdict PDF base on the data received from the Pate message
  queue."
  [{command ::command mode ::mode}]
  (let [error-fn                 (fn [err]
                                   (do
                                     (warn "Skipping bad message. Cannot create verdict PDF." err)
                                     (.commit pate-session)))
        {app-id     :id
         verdict-id :verdict-id} (:data command)]
    (cond
      (ss/blank? app-id)                     (error-fn "No application id.")
      (ss/blank? verdict-id)                 (error-fn "No verdict id.")
      (not (#{::verdict ::signatures
              ::proposal} mode))             (error-fn (str "Bad mode: " mode))
      :else
      (when-let [command (let [application   (domain/get-application-no-access-checking app-id)
                               command       (assoc command :application application)
                               {error :text} (and ((verdict-exists :published?) command)
                                                  ((verdict-exists :proposal?) command))]
                           (cond
                             (nil? application) (error-fn (str "Bad application id: " app-id))
                             error              (error-fn error)
                             :else              command))]
        (try+
         (let [verdict (command->verdict command)
               fun     (case mode
                         ::verdict    pdf--verdict
                         ::signatures pdf--signatures
                         ::proposal   pdf--proposal)]
           (if (fun command verdict)
             (.commit pate-session)
             (.rollback pate-session)))
         (catch [:error :pdf/pdf-error] _
           (errorf "%s: PDF generation for verdict %s failed."
                   app-id verdict-id)
           (.rollback pate-session))
         (catch Object _
           (errorf "%s: Could not create verdict attachment for verdict %s."
                   app-id verdict-id)
           (.rollback pate-session)))))))

(defonce pate-consumer (when pate-session
                         (jms/create-consumer pate-session
                                              pate-queue
                                              (comp create-verdict-pdf edn/read-string))))

(defn send-command [mode command]
  (->> {::command (select-keys command [:data :user :created])
        ::mode    mode}
       ss/serialize
       (jms/produce-with-context pate-queue)))

(defn finalize--pdf [{:keys [application verdict]}]
  (let [tags    (pdf/verdict-tags application verdict)]
    (-> verdict
        (assoc-in [:published :tags] (ss/serialize tags))
        (verdict->updates :published.tags)
        (assoc :commit-fn (util/fn->> :command (send-command ::verdict))))))

(defn finalize--bulletin [{:keys [verdict]}]
  (when-let [julkipano (-> verdict :data :julkipano)]
    {:commit-fn (fn [{:keys [command application verdict]}]
                  (bulletins/upsert-bulletin-by-id
                    (str (:id application) "_" (:id verdict))
                    (bulletins/create-bulletin
                      (util/assoc-when-pred application util/not-empty-or-nil?
                                            :pate-verdicts [verdict])
                      (:created command)
                      {:verdictGivenAt (-> verdict :data :anto)
                       :bulletinOpDescription (-> verdict :data :bulletin-op-description)
                       :appealPeriodStartsAt julkipano
                       :appealPeriodEndsAt (bulletins/fallback-appeal-end-from-appeal-start julkipano)})))}))

(defn process-finalize-pipeline [command application verdict & finalize--fns]
  (let [{:keys [updates commit-fns verdict]
         :as result} (reduce (fn [acc fun]
                                       (let [{:keys [updates commit-fn]
                                              :as   m} (fun (select-keys acc [:command :application :verdict]))]
                                         (-> acc
                                             (merge (select-keys m [:application :verdict]))
                                             (update :updates util/deep-merge updates)
                                             (update :commit-fns conj commit-fn))))
                                     {:command     command
                                      :application application
                                      :verdict     verdict
                                      :updates     {}
                                      :commit-fns  []}
                                     finalize--fns)]
    (sc/validate schemas/PateVerdict (update verdict :category name))
    (verdict-update command updates)
    (doseq [fun (remove nil? commit-fns)]
      (fun (select-keys result [:command :application :verdict])))))


(defn publish-verdict
  "Publishing verdict does the following:
   1. Finalize and publish verdict
   2. Update application state
   3. Inspection summaries
   4. Other document updates (e.g., waste plan -> waste report)
   5. Construct buildings array
   6. Freeze (locked and read-only) verdict attachments and update TOS details
   7. Create tasks (old ones will be overwritten)
   8. Generate section (for non-board verdicts)
   9. Create PDF/A for the verdict
  10. Update date for continuation applications
  11. Generate KuntaGML
  12. TODO: Assignments?

  If the verdict replaces an old verdict, then
  13. Update old verdict's replacement property
  14. Delete old verdict tasks."
  [{:keys [application] :as command}]
  (process-finalize-pipeline command application (command->verdict command)
                             finalize--verdict
                             finalize--signatures
                             finalize--application-state
                             finalize--buildings-and-tasks
                             inspection-summary/finalize--inspection-summary
                             finalize--attachments
                             finalize--link-permit
                             finalize--replaced-verdict
                             ;; Point of no return (section sequence update)
                             finalize--section
                             finalize--pdf
                             finalize--bulletin
                             finalize--kuntagml))

(defn try-again-page [{:keys [lang data]} {:keys [raw status error]}]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (let [msg (i18n/localize lang error)]
              (rum/render-static-markup
               [:html
                [:head [:title msg]]
                [:body
                 [:div
                  {:style {:margin  "2em 2em"
                           :border  "2px solid red"
                           :padding "1em 1em"}}
                  [:h3 {:style {:margin-top 0}} msg]
                  [:a {:href (format "/api/raw/%s?%s"
                                     (name raw)
                                     (codec/form-encode data))}
                   (i18n/localize lang :pate.try-again)]]]]))})

(defn- download-pdf
  "Downloads pdf from given path. Shows error page if the PDF is not yet ready"
  [{:keys [user] :as command} path re-call]
  (if-let [attachment-id (some-> (command->verdict command)
                                 (get-in path))]
    (att/output-attachment (att/get-attachment-latest-version-file user
                                                                   attachment-id
                                                                   false)
                           true)
    (try-again-page command  {:status 202 ;; Accepted
                              :error  :pate.verdict.download-not-ready
                              :raw    re-call})))

(defn download-verdict
  "Download verdict PDF."
  [command]
  (download-pdf command [:published :attachment-id] :verdict-pdf))

(defn download-proposal
  "Download verdict proposal PDF."
  [command]
  (download-pdf command [:proposal :attachment-id] :proposal-pdf))

(defn preview-verdict
  "Preview version of the verdict.
  1. Finalize verdict but do not store the changes.
  2. Generate PDF and return it."
  [command]
  (let [{:keys [error
                pdf-file-stream
                filename]} (-<>> (command->verdict command)
                                 (enrich-verdict command <> true)
                                 (pdf/create-verdict-preview command))]
    (if error
      (try-again-page command {:raw    :preview-pate-verdict
                               :status 503 ;; Service Unavailable
                               :error  error})
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "filename=\"%s\"" filename)}
       :body    pdf-file-stream})))

;; ---------------------------------
;; Backing system verdicts
;; The backing system verdicts reside in the verdicts array and never
;; modified by Pate.
;; ---------------------------------

(defnk command->backing-system-verdict
  "Returns the corresponding backing system verdict or nil."
  [application data]
  (util/find-by-id (:verdict-id data) (:verdicts application)))

(defn backing-system--poytakirja-properties
  [application {url-hash :urlHash :as poytakirja}]
  (util/assoc-when (select-keys poytakirja [:status :paatos :pykala
                                            :paatospvm :paatoksentekija])
                   :attachment
                   (when-let [attachment (and url-hash
                                              (util/find-first #(= (get-in % [:target :urlHash])
                                                                   url-hash)
                                                               (:attachments application)))]
                     {:url  (str "/api/raw/latest-attachment-version?attachment-id="
                                 (:id attachment))
                      :text (get-in attachment [:latestVersion :filename])})))

(defn backing-system--paatos-properties
  "Properties for an individual paatos (there can multiple within a
  verdict.). In addition, each paatos can include multiple
  poytakirjas."
  [{:keys [lupamaaraykset paivamaarat] :as paatos}]
  (-> (update lupamaaraykset :maaraykset (partial map :sisalto))
      (update :vaaditutKatselmukset (partial map :tarkastuksenTaiKatselmuksenNimi))
      (merge paivamaarat)))

(defn backing-system--tags [application verdict]
  (reduce (fn [acc paatos]
            (concat acc
                    (mapcat #(cols/content (backing-system--poytakirja-properties application %)
                                           layouts/backing-poytakirja-layout)
                            (:poytakirjat paatos))
                    (cols/content (backing-system--paatos-properties paatos)
                                  layouts/backing-paatos-layout)))
          (cols/content verdict layouts/backing-kuntalupatunnus-layout)
          (:paatokset verdict)))

(defnk published-verdict-details
  "Map of id, published, tags and attachment ids (if available)"
  [application :as command]
  (let [{verdict-id :id
         :as        details} (if-let [verdict (command->backing-system-verdict command)]
                               {:id        (:id verdict)
                                :published (:timestamp verdict)
                                :tags      (ss/serialize {:body (backing-system--tags application verdict)})}
                               (let [{:keys [id published]} (command->verdict command)]
                                 (assoc published :id id)))]
    (assoc details
           :attachment-ids (some->> (:attachments application)
                                    (filter (fn [{:keys [source target]}]
                                              (or (= (:id source) verdict-id)
                                                  (= (:id target) verdict-id))))
                                    (map :id)))))

;; ----------------------------
;; Signatures
;; ----------------------------

(defn user-person-name
  "Firstname Lastname. Blank name parts are omitted."
  [user]
  (->> user
       ((juxt :firstName :lastName))
       (map ss/trim)
       (remove ss/blank?)
       (ss/join " ")))

(defn user-can-sign? [{:keys [application user] :as command}]
  (let [{sigs :signatures} (command->verdict command)
        {com-id :id}       (auth/auth-via-company application user)]
    (not-any? (fn [{:keys [user-id company-id name]}]
                (or (and (= user-id (:id user))
                         (= name (user-person-name user)))
                    (and com-id
                         (= com-id company-id))))
              sigs)))

(defn create-signature
  [{:keys [application user created]}]
  (let [person           (user-person-name user)
        {company-id :id} (auth/auth-via-company application
                                                user)]
    (cond-> {:user-id (:id user)
             :date created
             :name person}
      company-id (assoc
                  :company-id company-id
                  ;; Get the up-to-date company name just in case
                  :name (->> (com/find-company-by-id company-id)
                             :name
                             (format "%s, %s" person))))))

(defn- update-signature-section
  "Updates the signatures section with a new signature. Other sections
  are untouched. If the signatures section does not exist (e.g.,
  migration-contract) it is added."
  [sections verdict signature]
  (let [verdict (update verdict :signatures concat [signature])
        entry   (cols/entry-row (:left-width (layouts/pdf-layout verdict))
                                {:lang       (cols/language verdict)
                                 :signatures (pdf/signatures {:verdict verdict})}
                                (first layouts/entry--contract-signatures))]

    (if (some (util/fn->> second :id (util/=as-kw :signatures))
              sections)
        (map (fn [[_ attr & _ :as section]]
               (if (util/=as-kw (:id attr) :signatures)
                 entry
                 section))
             sections)
        (concat sections [entry]))))

(defn- pdf--signatures [command verdict]
  (pdf/create-verdict-attachment-version command verdict)
  true)

(defn- sign-requested? [{:keys [user] :as command}]
  (->> (command->verdict command)
       :signature-requests
       (filter #(= (:user-id %) (:id user)))
       first))

(defn- transition-to-assignmentSigned-state? [application]
  (and (sm/valid-state? application :agreementSigned)
       (util/not=as-kw (:state application)
                       :agreementSigned)))

(defn sign-contract
  "Sign the contract
   - Update verdict signatures
   - Update tags but only the signature part
   - Change application state to agreementSigned if needed
   - Generate new contract attachment version."
  [{:keys [user created application] :as command}]
  (let [signature (create-signature command)
        verdict (command->verdict command)
        tags (-> verdict :published :tags
                 edn/read-string
                 (update :body update-signature-section verdict signature)
                 ss/serialize)]
    (verdict-update command
                    (util/deep-merge
                     {$push {(util/kw-path :pate-verdicts.$.signatures) signature}
                      $set {(util/kw-path :pate-verdicts.$.published.tags) tags}}
                     (when (transition-to-assignmentSigned-state? application)
                       (app-state/state-transition-update :agreementSigned
                                                          created
                                                          application
                                                          user))
                     (when (sign-requested? command)
                       {$pull {(util/kw-path :pate-verdicts.$.signature-requests) {:user-id (:id user)}}})))
    (send-command ::signatures command)))

(defn- signer-ids [data]
  (->> data
       (map :user-id)
       (remove nil?)
       (map keyword)))

(defn parties [{:keys [user] :as command}]
  (let [signed-id     (signer-ids (get-in (command->verdict command) [:signatures]))
        requested-id  (signer-ids (get-in (command->verdict command) [:signature-requests]))
        parties   (->> (get-in command [:application :auth])
                       (filter #(not ((set (concat signed-id requested-id (:id user))) (keyword (:id %))))))]
    (->> parties
         (mapv (fn [auth] {:value (:id auth) :text (user-person-name auth)})))))

(defn- create-request-email-model [command conf recipient]
  (merge (notifications/create-app-model command conf recipient)
         {:recipient-email (:email recipient)
          :inviter-email (-> command :user :email)}))

(notifications/defemail :pate-signature-request
  {:subject-key     "pate.signature.request"
   :recipients-fn   (fn [{:keys [data]}] [(get-user-by-id (:signer-id data))])
   :model-fn        create-request-email-model})

(defn add-signature-request [{:keys [application created data] :as command}]
  (let [signer        (get-user-by-id (:signer-id data))
        request       (create-signature {:application application :user signer :created created})]
    (verdict-update command {$push {:pate-verdicts.$.signature-requests request}})))

;; ----------------------------
;; Proposal
;; ----------------------------

(defn finalize--proposal [{:keys [command verdict]}]
  (let [verdict           (assoc (enrich-verdict command verdict true)
                            :state (wrapped-state command :proposal)
                            :proposal {:proposed (:created command)})
        data-kws          (map #(util/kw-path :data %)
                               (-> verdict :data keys))]
    (apply verdict->updates verdict
           (concat data-kws [:state :proposal.proposed]))))

(defn finalize--proposal-pdf [{:keys [application verdict]}]
  (let [tags    (pdf/verdict-tags application verdict)]
    (-> verdict
        (assoc-in [:proposal :tags] (ss/serialize tags))
        (verdict->updates :proposal.tags)
        (assoc :commit-fn (util/fn->> :command (send-command ::proposal))))))

(defn publish-verdict-proposal
  "Publishing verdict proposal does the following:
    1. Updates verdict state to proposal
    2. Generates PDF/A for vedict proposal"
  [{:keys [application] :as command}]
  (process-finalize-pipeline command application (command->verdict command)
                             finalize--proposal
                             finalize--proposal-pdf))
