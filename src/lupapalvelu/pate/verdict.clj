(ns lupapalvelu.pate.verdict
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.appeal :as appeal]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-meta-fields :as meta]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as krysp]
            [lupapalvelu.climate-report :as climate-report]
            [lupapalvelu.company :as com]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.transformations :as transformations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.logging :as log :refer [error warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pageutil :refer [try-again-page]]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.date :as pate-date]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.tasks :as pate-tasks]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.pate.verdict-util :as pate-vutil]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.user :refer [full-name get-user-by-id]]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.property :as sp]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [slingshot.slingshot :refer [try+]]
            [swiss.arrows :refer :all])
  (:import [java.io InputStream]))

(set! *warn-on-reflection* true)

(defn- update-path [& path-elements]
  (apply util/kw-path :pate-verdicts.$ path-elements))

(defn- data-update-path [& path-elements]
  (apply update-path :data path-elements))

(defn scheduled? [verdict]
  (= :scheduled (vc/verdict-state verdict)))

;; ------------------------------------------
;; Pre-checks
;; Called from multiple api namespaces.
;; ------------------------------------------

(defn attachment-not-in-published-verdict
  "Pre-checker that fails if the attachment has been selected to be part of a published Pate
  verdict. Note that uploaded verdict attachments are not included, since those are
  `:locked` and `:readOnly`. See `lupapalvelu.attachment-api` for usage."
  [{:keys [application data]}]
  (when-let [attachment-id (:attachmentId data)]
    (when-let [locked-ids (some->> (:pate-verdicts application)
                                   (filter vc/published?)
                                   (mapcat #(some-> % :data :selected-attachments))
                                   set
                                   not-empty)]
      (when (contains? locked-ids attachment-id)
        (fail :error.attachment-in-published-verdict)))))

(defn pate-enabled
  "Pre-checker that fails if Pate is not enabled in the application organization scope."
  [{:keys [organization application]}]
  (when (and organization
             (not (some-> (org/resolve-organization-scope (:municipality application) (:permitType application) @organization)
                          :pate
                          :enabled)))
    (fail :error.pate-disabled)))

(def verdict-conditions
  {:editable?        {:error        :error.verdict.not-editable
                      :has-error-fn (fn [{:keys [verdict-state]}]
                                      (and (not= verdict-state :draft)
                                           (not= verdict-state :proposal)))}
   :published?       {:error        :error.verdict.not-published
                      :has-error-fn (fn [{:keys [verdict-state]}] (not= verdict-state :published))}
   :legacy?          {:error        :error.verdict.not-legacy
                      :has-error-fn (fn [{:keys [verdict]}] (not (vc/legacy? verdict)))}
   :modern?          {:error        :error.verdict.legacy
                      :has-error-fn (fn [{:keys [verdict]}] (vc/legacy? verdict))}
   :valid-julkipano? {:error        :error.verdict.no-julkipano-date
                      :has-error-fn (fn [{:keys [verdict application]}]
                                      (let [date-key (if (= :kuulutus (app/bulletin-process application))
                                                       :k-kuulutus :julkipano)]
                                        (if-let [ts (metadata/unwrap (get-in verdict [:data date-key]))]
                                          (not (pate-date/tomorrow-or-later? ts))
                                          true)))} ; true means 'yes, FAIL!'
   :contract?        {:error        :error.verdict.not-contract
                      :has-error-fn (fn [{:keys [verdict]}] (not (vc/contract? verdict)))}
   :allu-contract?   {:error        :error.verdict.not-allu-contract
                      :has-error-fn (fn [{:keys [verdict]}] (util/not=as-kw (:category verdict) :allu-contract))}
   :verdict?         {:error        :error.verdict.contract
                      :has-error-fn (fn [{:keys [verdict]}] (vc/contract? verdict))}
   :not-replaced?    {:error        :error.verdict-replaced
                      :has-error-fn (fn [{:keys [verdict application]}]
                                      (or (get-in verdict [:replacement :replaced-by])
                                          (some #(some-> % :replacement :replaces (= (:id verdict)))
                                                (:pate-verdicts application))))}
   :not-parent?      {:error        :error.verdict.has-child-verdicts
                      :has-error-fn (fn [{:keys [verdict application]}]
                                      (->> (vc/all-published-verdicts application)
                                           (util/find-by-key :parent-verdict (:id verdict))
                                           some?))}
   :proposal?        {:error        :error.verdict.not-proposal
                      :has-error-fn (fn [{:keys [verdict-state]}] (not= verdict-state :proposal))}
   :board?           {:error        :error.verdict.not-board
                      :has-error-fn (fn [{:keys [verdict]}] (not (vc/board-verdict? verdict)))}
   :not-publishing?  {:error        :error.publishing-verdict
                      :has-error-fn (fn [{:keys [verdict]}] (vc/publishing-verdict? verdict))}
   :scheduled?       {:error        :error.command-illegal-state
                      :has-error-fn (fn [{:keys [verdict]}] ((complement scheduled?) verdict))}
   :not-scheduled?   {:error        :error.verdict.is-scheduled
                      :has-error-fn (fn [{:keys [verdict]}] (scheduled? verdict))}
   :valid-category?  {:error        :error.invalid-category
                      :has-error-fn (fn [{:keys [verdict application]}]
                                      (not (vc/allowed-category-for-application? verdict application)))}
   :verdict-exists?  {:error        :error.verdict-not-found
                      :has-error-fn (fn [{:keys [verdict]}]
                                      (not verdict))}})

(assert (every? (fn [[k v]] (and (:error v) (:has-error-fn  v))) verdict-conditions) "Check verdict-conditions keys")

(defn verdict-exists
  "Returns pre-checker that fails if the verdict does not exist. Finds verdict by :verdict-id parameter.
  Additional conditions:
    :editable? fails if the verdict state is NOT draft or proposal
    :published? fails if the verdict has NOT been published
    :legacy? fails if the verdict is a 'modern' Pate verdict
    :modern? fails if the verdict is a legacy verdict
    :valid-julkipano? fails if verdict does not have julkipano date set and it's not tomorrow or later
    :contract? fails if the verdict is not a contract
    :allu-contract? fails if the verdict is not an allu-contract
    :verdict? fails for contracts
    :not-replaced? fails if the verdict has been OR is being replaced
    :not-parent? fails if the verdict is a parent to another verdict
    :proposal? fails if the verdict is not proposal
    :not-publishing? fails if the verdict has state
    publishing-verdict, signing-contract or publishing-proposal
    :not-scheduled? fails if verdict has been scheduled to publish
    :board?  fails if the verdict giver (defined in the verdict
    template) is not board. Only board verdicts support verdict
    proposals."
  [& conditions]
  {:pre [(set/superset? (set (keys verdict-conditions)) (set conditions))]}
  (letfn [(check-condition [condition verdict application]
            (let [fails?-fn (get-in verdict-conditions [condition :has-error-fn])]
              (when (fails?-fn {:verdict       verdict
                                :application   application
                                :verdict-state (vc/verdict-state verdict)})
                (get-in verdict-conditions [condition :error]))))]
    (fn [{:keys [data application]}]
      (when-let [verdict-id (and application (:verdict-id data))]
        (let [verdict        (util/find-by-id verdict-id (:pate-verdicts application))
              all-conditions (conj conditions :valid-category? :verdict-exists?)]
          (when-let [err (->> all-conditions
                              (reduce
                                #(or %1
                                     (check-condition %2 verdict application))
                                nil))]
            (fail err)))))))

(declare pate-kuntagml-integration?)

(defn pate-kuntagml-integration-configured
  "Pre-heck that fails if Pate supported KuntaGML integration is not configured."
  [command]
  (when (and (:application command)
             (not (pate-kuntagml-integration? command)))
    (fail :error.pate-kuntagml-integration)))

(declare command->verdict)

(defn pate-kuntagml-226-requirement
  "Pre-check that fails if Pate integration is configured but the organization does not meet
  the KuntaGML version 2.2.6 requirement. Note that 2.2.6 is only required for modern,
  draft, non-board verdicts."
  [command]
  (when (and (:application command)
             (pate-kuntagml-integration? command)
             (app-utils/check-kuntagml-226-requirement command))
    (let [pred-fn (every-pred vc/draft? vc/lupapiste-verdict?
                              #(vc/has-category? % :r)
                              (complement vc/board-verdict?)
                              (complement vc/legacy?))]
      (when (pred-fn (command->verdict command))
        (fail :error.kuntagml-226-required)))))

(declare command->backing-system-verdict)

(defn backing-system-verdict
  "Pre-check that fails if the target verdict is not a backing system
  verdict. Note that after Pate has taken into use, verdicts array
  includes only the backing system verdicts."
  [{:keys [application] :as command}]
  (when application
    (when-not (command->backing-system-verdict command)
      (fail :error.verdict-not-found))))

;; ------------------------------------------
;; Utility functions
;; ------------------------------------------

(defn neighbor-states
  "Application neighbor-states data in a format suitable for verdicts: list
  of property-id, created (timestamp) and state maps."
  [{neighbors :neighbors}]
  (mapv (fn [{:keys [propertyId status]}]
          (-> (last status)
              (select-keys [:created :state])
              (assoc :property-id propertyId)))
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

(defn- verdict-attachments [{:keys [attachments]}]
  (some->> attachments
           (filter :forPrinting)
           (keep :id)
           seq))

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

(def grouped-template-dict?
  "Values that contain maps that should be kept together and not split into multiple IDs"
  #{:conditions})

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
                                                (if (and (grouped-template-dict? dict)
                                                         (not (empty? v)))
                                                  (assoc-in m [dict (mongo/create-id)] v)
                                                  (reduce-kv (fn [a inner-k inner-v]
                                                               (cond-> a
                                                                 (included? (util/kw-path dict inner-k))
                                                                 (assoc-in [dict (mongo/create-id) inner-k] inner-v)))
                                                             m
                                                             v)))
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

(defn add-parent-verdict
  "Parent verdict is any verdict the new verdict should be a child of,
  currently only applies to rectification-verdicts (their parent is the original
  verdict whose rectification appeals the child verdict addresses)."
  [initmap verdict-parent-id]
  (update initmap :draft #(cond-> %
                            verdict-parent-id
                            (assoc :parent-verdict verdict-parent-id))))

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

(defn- published-template-data [initmap]
  (-> initmap :template :published :data))

(defn init--foremen
  "Build a multi-select reference-list value according to template
  data. The included toggle is initialized in `init--included-checks`
  above."
  [initmap]
  (let [t-data  (published-template-data initmap)
        updated (reduce (fn [acc code]
                          (let [codename (name code)
                                inc-dict  (keyword (str codename "-included"))
                                included? (inc-dict t-data)
                                selected? (when included?
                                            (code t-data))]
                            (cond-> acc
                              ;; Order in references is important as is dictates the order in the UI.
                              included? (update-in [:draft :references :foremen] concat [codename])
                              selected? (update-in [:draft :data :foremen] conj codename))))
                        initmap
                        (or (some->> t-data :tj-order not-empty (map keyword))
                            helper/foreman-codes))]
    (resolve-requirement-inclusion updated :foremen)))

(defn init--requirements-references
  "Plans and reviews are defined in the published template settings."
  [{:keys [template] :as initmap} dict]
  (-<> initmap
       (reduce (fn [acc {:keys [selected] :as item}]
                 (let [id (mongo/create-id)]
                   (cond-> (update-in acc
                                      [:draft :references dict]
                                      concat [(-> item
                                                  (dissoc :selected)
                                                  (assoc :id id))])
                    selected (update-in [:draft :data dict]
                                        conj id))))
               <>
              (get-in template [:published :settings dict]))
      (resolve-requirement-inclusion dict)))

(defn init--verdict-dates
  "Include in verdict only those verdict dates that have been checked in
  the template. Also, if no verdict dates, the automatic-verdict-dates
  toggle is excluded."
  [{:keys [application draft] :as initmap}]
  (update-in initmap
             [:draft :template :inclusions]
             (fn [incs]
               (let [{:keys [verdict-dates
                             kuulutus-dates]} (published-template-data initmap)
                     category                 (:category draft)
                     kuulutus?                (= :kuulutus (app/bulletin-process application))]
                 (cond-> (util/difference-as-kw incs
                                                [:automatic-verdict-dates]
                                                (helper/all-verdict-dates category))
                   ;; Either keep julkipano dates
                   (and (not kuulutus?) (seq verdict-dates))
                   (util/union-as-kw verdict-dates
                                     [:automatic-verdict-dates])
                   ;; .. or kuulutus dates
                   (and kuulutus? (seq kuulutus-dates))
                   (util/union-as-kw kuulutus-dates
                                     [:automatic-verdict-dates]))))))

(defn init--upload
  "Upload is included if the attachments section is not removed and
  upload is checked in the template. Note that the attachments section
  dependence has been resolved earlier."
  [initmap]
  (update-in initmap
             [:draft :template :inclusions]
             (fn [incs]
               (util/difference-as-kw incs
                                      (when-not (:upload (published-template-data initmap))
                                        [:upload])))))

(defn init--verdict-titles
  "Adds `title` and `subtitle` keys into template, if either exists in the actual template data."
  [initmap]
  (let [titles (some-> (published-template-data initmap)
                       (select-keys [:title :subtitle])
                       ss/trimwalk
                       util/strip-blanks
                       not-empty)]
    (cond-> initmap
      titles (update-in [:draft :template] merge titles))))

(defn rectification-addressed?
  "Returns true if there is a rectification verdict that addresses the given rectification appeal"
  [{:keys [id] :as _rectification} pate-verdicts]
  (some #(contains? (some->> % :data :parent-verdict-rectifications metadata/unwrap-all set)
                    id)
        pate-verdicts))

(defn init--parent-verdict-rectifications
  "Adds the rectification appeals related to the parent verdict to the current
  rectification verdict so the user can select which ones are resolved.
  Note that any changes to the appeals after verdict creation will not update
  these (just like references in general work)."
  [{:keys [application] {:keys [parent-verdict]} :draft :as initmap}]
  (if-let [rectifications (->> (:appeals application)
                               (filter #(and (-> % :type (= "rectification"))
                                             (-> % :target-verdict (= parent-verdict))
                                             (not (rectification-addressed? % (:pate-verdicts application)))))
                               seq)]
    (-> initmap
        (assoc-in [:draft :references :parent-verdict-rectifications]
                  (map #(assoc % :rectification-label (appeal/rectification-label %)) rectifications))
        ;; Defaults to all rectifications selected with all appellants listed as well
        (assoc-in [:draft :data :appellants]
                  (->> rectifications (keep (comp ss/blank-as-nil :appellant)) distinct (ss/join ", ")))
        (assoc-in [:draft :data :parent-verdict-rectifications]
                  (map :id rectifications)))
    initmap))

(defn init--copy-from-parent-verdict
  "Copies the default values for the given fields from the verdict's parent verdict.
  Note that these may or may not be editable by the user"
  [{:keys [application] {parent-verdict-id :parent-verdict} :draft :as initmap} & field-keys]
  (if-let [parent-verdict (util/find-by-id parent-verdict-id (vc/all-verdicts application))]
    (update-in initmap [:draft :data] merge (select-keys (:data parent-verdict) field-keys))
    initmap))

(defn init--verdict-giver-type
  "Adds giver key into template. The value is either lautakunta or
  viranhaltija. Verdict-section and boardname dicts not included for viranhaltija
  verdicts. Hoists `bulletin-code-giver` flag, if set in the template."
  [initmap]
  (let [{:keys [giver bulletin-code-giver]} (published-template-data initmap)]
    (cond-> (assoc-in initmap
                      [:draft :template :giver]
                      giver)
      (util/=as-kw giver :viranhaltija)
      (update-in [:draft :template :inclusions]
                 util/difference-as-kw  [:verdict-section
                                         :boardname])

      bulletin-code-giver
      (assoc-in [:draft :template :bulletin-code-giver] true))))

(defn init--dict-by-application
  "Inits given dict (if included) with the app-fn result (if the result
  is non-nil). App-fn takes application as an argument"
  [{:keys [application] :as initmap} dict app-fn]
  (let [value (when (dict-included? initmap dict)
                (app-fn application))]
    (if (nil? value)
      initmap
      (assoc-in initmap [:draft :data dict] value))))

(def buildings-autopaikat (mapv (partial util/kw-path :buildings) verdict-schemas/autopaikat))

(def buildings-inclusion {:autopaikat buildings-autopaikat
                          :vss-luokka [:buildings.vss-luokka]
                          :paloluokka [:buildings.paloluokka]
                          :kokoontumistilanHenkilomaara [:buildings.kokoontumistilanHenkilomaara]})
(def buildings-inclusion-keys (-> buildings-inclusion vals flatten))

(defn init--buildings
  "Update inclusions according to selected building details."
  [{:keys [template] :as initmap}]
  (cond-> initmap
    (not (section-removed? template :buildings))
    (update-in [:draft :template :inclusions]
               (fn [incs]
                 (let [details (published-template-data initmap)]
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
  "General handler from application or user from command.
  If dict is provided also checks whether the handler is included"
  [{:keys [application user] :as initmap} & [dict]]
  (cond-> initmap
    (or (nil? dict)
        (dict-included? initmap dict))
    (assoc-in [:draft :data (or dict :handler)]
              (if (ss/not-blank? (general-handler application))
                (general-handler application)
                (str (:firstName user) " " (:lastName user))))))

(defmethod initialize-verdict-draft :r
  [initmap]
  (-> initmap
      (init--handler :handler)
      (init--dict-by-application :deviations application-deviations)
      init--foremen
      (init--requirements-references :plans)
      (init--requirements-references :reviews)
      init--verdict-dates
      init--upload
      init--verdict-titles
      init--verdict-giver-type
      init--buildings
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)
      (init--dict-by-application :attachments verdict-attachments)))

(defmethod initialize-verdict-draft :p
  [initmap]
  (-> initmap
      (init--handler :handler)
      (init--dict-by-application :deviations application-deviations)
      init--verdict-dates
      init--upload
      init--verdict-titles
      init--verdict-giver-type
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)
      (init--dict-by-application :attachments verdict-attachments)))

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
      init--verdict-titles
      init--verdict-giver-type
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)
      (init--dict-by-application :attachments verdict-attachments)
      init--property-id
      init--permit-period))

(defmethod initialize-verdict-draft :tj
  [initmap]
  (-> initmap
      init--verdict-dates
      init--upload
      init--verdict-titles
      init--verdict-giver-type
      (init--handler :handler)
      (init--dict-by-application :operation application-operation)
      (init--dict-by-application :address :address)
      (init--dict-by-application :attachments verdict-attachments)))

(defmethod initialize-verdict-draft :contract
  [initmap]
  (-> initmap
      (init--handler :handler)
      init--verdict-titles
      (init--requirements-references :reviews)
      (init--dict-by-application :attachments verdict-attachments)))

(defmethod initialize-verdict-draft :oik
  [initmap]
  (-> initmap
      init--verdict-dates
      init--parent-verdict-rectifications
      (init--copy-from-parent-verdict :purpose)))

(declare enrich-verdict)

(defn user-ref [{user :user}]
  (select-keys user [:id :username]))

(defn wrapped-state [{:keys [created user]} state]
  (metadata/wrap (:username user) created (name state)))

(defn new-verdict-draft
  [{:keys [application timestamp command replacement-id rectified-verdict-id foreman-doc-id]
    :as   options}]
  (let [template       (template/verdict-template options)
        {draft :draft} (-> {:template    template
                            :draft       (default-verdict-draft template)
                            :application application
                            :user        (:user command)}
                           (add-parent-verdict rectified-verdict-id)
                           initialize-verdict-draft
                           (update-in [:draft :data]
                                      (partial metadata/wrap-all (metadata/wrapper command)))
                           (update-in [:draft]
                                      (fn [draft]
                                        (cond-> (assoc draft
                                                       :id (mongo/create-id)
                                                       :created timestamp
                                                       :modified timestamp)
                                          replacement-id
                                          (assoc-in [:replacement :replaces]
                                                    replacement-id)
                                          foreman-doc-id
                                          (assoc :document-id foreman-doc-id
                                                 :category "tj-v3")
                                          template
                                          (assoc-in [:template :id] (:id template)))))
                           (assoc-in [:draft :state] (wrapped-state command :draft)))]
    (action/update-application command
                               {$push {:pate-verdicts
                                       (schemas/valid-pate-verdict! draft)}})
    (:id draft)))

(defn copy-verdict-draft [{:keys [application created user] :as command} replacement-id]
  (let [draft          (-> (util/find-by-id replacement-id (:pate-verdicts application))
                           (assoc :id (mongo/create-id)
                                  :modified created
                                  :created created
                                  :state (wrapped-state command :draft))
                           (dissoc :published
                                   :archive
                                   :ryhti)
                           (assoc-in [:replacement :replaces] replacement-id)
                           (update-in [:template :inclusions] #(mapv keyword %))
                           (update :data dissoc
                                   :attachments :handler :verdict-section :signatures))
        {draft :draft} (-> {:draft       draft
                            :application application
                            :user        user}
                           init--handler
                           (update-in [:draft :data]
                                      (partial metadata/wrap-all (metadata/wrapper command))))]
    (schemas/valid-pate-verdict! draft)
    (action/update-application command
                               {$push {:pate-verdicts draft}})
    (:id draft)))

(defn legacy-verdict-inclusions
  [{:keys [category application]}]
  (let [process    (app/bulletin-process application)
        inclusions (-> category
                       legacy/legacy-verdict-schema
                       :dictionary
                       dicts->kw-paths)]
    ;; Would be tempting to handle these as sets, but some existing tests
    ;; assert a specific order so let's respect that
    (cond->> inclusions
      (= :julkipano process) (remove #{:k-kuulutus :k-lainvoimainen :k-verdict-date :k-voimassa})
      (= :kuulutus process)  (remove #{:julkipano :lainvoimainen :anto})
      true                   vec)))

(defn new-legacy-verdict-draft
  "Legacy verdicts do not have templates or references. Inclusions
  contain every schema dict."
  ([{:keys [application created] :as command} category & {:as extras}]
   (let [{verdict-id :id
          :as        draft} (->> {:id       (mongo/create-id)
                                  :modified created
                                  :created  created
                                  :state    (wrapped-state command :draft)
                                  :category (name category)
                                  :data     {:handler (general-handler application)}
                                  :template {:inclusions (legacy-verdict-inclusions (util/mmap category application))}
                                  :legacy?  true}
                                 (merge extras)
                                 (schemas/valid-pate-verdict!))]
     (action/update-application command {$push {:pate-verdicts draft}})
     verdict-id))
  ([{:keys [application] :as command}]
   (new-legacy-verdict-draft command (schema-util/application->category application))))

(defn new-legacy-foreman-verdict-draft
  "Legacy verdicts do not have templates or references. Inclusions
  contain every schema dict."
  [{:keys [application created] :as command} foreman-doc-id]
  (new-legacy-verdict-draft command :tj-v3 :document-id foreman-doc-id))

(declare verdict-schema)

(defn mask-verdict-data [verdict {:keys [user application]}]
  (if-not (auth/application-authority? application user)
    (util/dissoc-in verdict [:data :bulletinOpDescription])
    verdict))

(defn command->verdict
  "Gets verdict based on command data. If refresh? is true then the
  application is read from mongo and not taken from command."
  ([{:keys [data application] :as command} refresh?]
   (if-let [verdict (-> (if refresh?
                          (domain/get-application-no-access-checking (:id application) {:pate-verdicts 1})
                          application)
                        (vif/find-verdict (:verdict-id data))
                        (mask-verdict-data command))]
     (update verdict :category keyword)
     (fail! :error.verdict-not-found)))
  ([command]
   (command->verdict command false)))

(defn- verdict-attachment-ids
  "Ids of attachments, whose either target or source is the given
  verdict. Verdict-ref attachments are included too. If the verdict
  data has selected-attachments, those are included as well."
  [{:keys [attachments]} {verdict-id :id :as verdict}]
  (->> attachments
       (filter (fn [{:keys [target source]}]
                 (or (and (util/includes-as-kw? [:verdict :verdict-ref]
                                                (:type target))
                          (= (:id target) verdict-id))
                     (and (util/=as-kw (:type source) :verdicts)
                          (= (:id source) verdict-id)))))
       (map :id)
       (concat (vc/selected-attachment-ids attachments verdict))
       (into [])))

(defn- deletable-verdict-attachment-ids
  "When a verdict is deleted, its attachment is deleted, if:
  - attachment's source is the verdict or..
  - attachment's target is verdict (not verdict-ref) and the
    attachment has draftTarget metadata key.

  In other words, an attachment can be deleted if it is either
  generated by Pate or uploaded directly to the the verdict"
  [{:keys [attachments]} verdict-id]
  {:pre [verdict-id]}
  (->> attachments
       (filter (fn [{:keys [target source metadata]}]
                 (or (and (util/=as-kw :verdict (:type target))
                          (= (:id target) verdict-id)
                          (contains? metadata :draftTarget))
                     (and (util/=as-kw (:type source) :verdicts)
                          (= (:id source) verdict-id)))))
       (map :id)))

(defn delete-verdict-tasks-helper
  "Identifies verdict tasks and their attachments. Separate function
  since called also upon replacing a verdict. Returns a map
  with :task-ids and :task-attachment-ids keys."
  [application verdict-id]
  (let [task-ids (verdict/deletable-verdict-task-ids application verdict-id)]
    {:task-ids            task-ids
     :task-attachment-ids (map :id (verdict/task-ids->attachments application
                                                                  task-ids))}))

(defn verdict-update
  "Updates application, using $elemMatch query for given verdict."
  [{:keys [data created] :as command} updates]
  (let [{verdict-id :verdict-id} data]
    (action/update-application command
                               {:pate-verdicts {$elemMatch {:id verdict-id}}}
                               (assoc-in updates
                                         [$set (update-path :modified)]
                                         created))))

(defn delete-verdict [{:keys [application user created] :as command}]
  ;; Mostly copied from the old verdict_api.clj.
  (let [{verdict-id :id
         published  :published
         :as verdict}            (command->verdict command)
        target                             {:type "verdict"
                                            :id verdict-id} ; key order seems to be significant!
        {:keys [sent state pate-verdicts]} application
        ;; Deleting the only given verdict? Return sent or submitted
        ;; state. Some special circumstances can result in pseudo old
        ;; school verdict drafts.
        step-back?                         (and published
                                                (= 1 (count (filter :published pate-verdicts)))
                                                (empty? (remove :draft (:verdicts application)))
                                                ((conj states/verdict-given-states :appealed) (keyword state)))
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
    (factory/delete-review-assignments (:id application) task-ids)
    (bulletins/process-delete-verdict application verdict-id)
    (att/delete-attachments! application
                             (->> task-attachment-ids
                                  ;; Verdict-ref attachments are not deleted.
                                  (concat (deletable-verdict-attachment-ids application verdict-id))
                                  (remove nil?)))
    (appeal-common/delete-by-verdict command verdict-id)
    ;; Release the replaced verdict
    (when-let [replaced-id (some-> verdict :replacement :replaces)]
      (verdict-update (assoc-in command [:data :verdict-id] replaced-id)
                      {$unset {(update-path :replacement) true}}))
    (when step-back?
      (notifications/notify! :application-state-change command))))

(defn- wrap [command value]
  ((metadata/wrapper command) value))

(defn- verdict-changes-update
  "Write the auxiliary changes into mongo."
  [command changes]
  (when (seq changes)
    (verdict-update command {$set (reduce (fn [acc [key v]]
                                            (assoc acc
                                                   (data-update-path key)
                                                   (wrap command v)))
                                          {}
                                          changes)})))

(defn update-automatic-verdict-dates
  "Returns map of dates (timestamps). While the calculation takes every date into
  account, the result only includes the dates included in the
  template. The dates are calculated cumulatively so changing the delta for a given date will
  affect all subsequent dates.

  By default, every date except verdict-date is calculated but optionally the user
  can also manually set the julkipano date while calculating the rest of the dates normally"
  [{:keys [category template references verdict-data bulletinProcess]}]
  (let [date-deltas   (:date-deltas references)
        automatic?    (:automatic-verdict-dates verdict-data)
        timestamp     (:verdict-date verdict-data)
        verdict-dates (case bulletinProcess
                        :kuulutus (helper/kuulutus-dates category)
                        (helper/verdict-dates category))]
    (when (and automatic? (integer? timestamp))
      (loop [dates      {}
             [kw & kws] verdict-dates
             latest     timestamp]
        (if (nil? kw)
          (select-keys dates (util/intersection-as-kw verdict-dates (:inclusions template)))
          (let [{:keys [delta unit]} (kw date-deltas)
                to-work-day?         (bulletins/is-date-forwarded-to-work-day? kw)
                result               (cond
                                       ;; Manual override for julkipano date, use the given value
                                       (and (= kw :julkipano)
                                            (:manual-julkipano-date verdict-data)
                                            (:julkipano verdict-data))
                                       (-> verdict-data :julkipano date/timestamp)
                                       ;; .. and the same for kuulutus
                                       (and (= kw :k-kuulutus)
                                            (:manual-kuulutus-date verdict-data)
                                            (:k-kuulutus verdict-data))
                                       (-> verdict-data :k-kuulutus date/timestamp)
                                       ;; Delta found, calculate the next date
                                       delta
                                       (pate-date/parse-and-forward latest
                                                                    (util/->long delta)
                                                                    (keyword unit)
                                                                    to-work-day?)
                                       ;; Fallback when schema has changed for existing data or date is missing
                                       :else latest)]
            (recur (assoc dates kw result) kws result)))))))

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

(defmethod changes :julkipano [options]
  (update-automatic-verdict-dates options))

(defmethod changes :k-kuulutus [options]
  (update-automatic-verdict-dates options))

(defmethod changes :manual-julkipano-date [options]
  (update-automatic-verdict-dates options))

(defmethod changes :manual-kuulutus-date [options]
  (update-automatic-verdict-dates options))

(defn inclusions->excludes
  "Computes the excluded dictionary keys from a verdict's template inclusions.
   Returns keys that are in the schema dictionary but not in the inclusions list.
   For legacy verdicts, returns empty list (all required fields should be validated).
   Additionally for R and P categories:
   - When verdict-givers list is non-empty or the giver is not viranhaltija:
     exclude giver-first-name and giver-last-name (user selects from dropdown instead)
   - When verdict-givers list is empty AND giver is viranhaltija: exclude :giver
     (no dropdown available, user enters name manually via first/last name fields)"
  [schema verdict]
  (let [inclusions          (->> verdict :template :inclusions (map name) set)
        dict-keys           (keys (:dictionary schema))
        giver-names?        (pate-vutil/verdict-supports-giver-names? verdict)
        has-verdict-givers? (not-empty (-> verdict :references :verdict-givers))
        viranhaltija?       (pate-vutil/template-viranhaltija? verdict)
        base-excludes       (if (or (:legacy? verdict) (empty? inclusions))
                              []
                              (remove #(inclusions (name %)) dict-keys))]
    (cond->> base-excludes
      (and giver-names?
           (or has-verdict-givers?
               (not viranhaltija?))) (concat [:giver-first-name :giver-last-name])
      (and giver-names?
           (not has-verdict-givers?)
           viranhaltija?)            (concat [:giver])
      true                                   vec)))

(defn verdict-filled?
  "Have all the required fields been filled. Refresh? argument can force
  the read from mongo (see command->verdict)."
  ([command refresh?]
   (let [{:keys [data]
          :as   verdict} (command->verdict command refresh?)
         schema          (vc/verdict-schema verdict)
         excludes        (inclusions->excludes schema verdict)]
     (schemas/required-filled? schema data excludes)))
  ([command]
   (verdict-filled? command false)))

(defn proposal-filled?
  [command]
  (let [{:keys [data category]
         :as   verdict}        (command->verdict command)
        schema                 (vc/verdict-schema verdict)
        excludes               (concat (inclusions->excludes schema verdict)
                                       (helper/all-verdict-dates category)
                                       [:verdict-date :verdict-section :verdict-code])]
    (schemas/required-filled? schema data excludes)))

(defn- app-documents-having-buildings [application]
  (->> application
       app-utils/get-sorted-operation-documents
       (filter (util/fn-> doc-model/get-document-schema :info :building?))
       tools/unwrapped
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
                             :building-id (some (fn [field]
                                                  (ss/blank-as-nil (get data field)))
                                                doc-schemas/building-id-doc-fields)
                             :tag         (:tunnus data)
                             :order       (if (= id (:id primary-op)) 0 1)}
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
  equivalent to
  `lupapalvelu.backing-system.krysp.building-reader/->buildings-summary`,
  but instead of the message from backing system, here all the input
  data is originating from PATE verdict."
  [application]
  (let [building-updates (building-update-map application)]
    (->> application
         app-documents-having-buildings
         (util/indexed 1)
         (map (fn [[n {toimenpide :data {op :op} :schema-info}]]
                (let [{:keys [kaytto tunnus]} toimenpide
                      building-update (get building-updates (:id op))
                      location        (some-> building-update :location ((juxt :x :y)))
                      location-wgs84  (when location (coord/convert "EPSG:3067" "WGS84" 5 location))
                      ;; Some of the fields below can be found in different places depending on when the application
                      ;; was submitted; before 2026 (pre-RYHTI) or after (RYHTI)
                      ;; This is because the building information documents are different for RYHTI and pre-RYHTI apps
                      national-id     (or (:pysyva-rakennustunnus toimenpide) ; found on RYHTI applications
                                          (:valtakunnallinenNumero toimenpide) ; found on pre-RYHTI applications
                                          (:nationalBuildingId building-update))
                      rakennusnro     (or (-> toimenpide :manuaalinen_rakennusnro not-empty) ; RYHTI
                                          (-> toimenpide :rakennusnro)) ; pre-RYHTI
                      kokonaisala     (or (-> toimenpide :rakennuskohteen-osa :ulkokuorentiedot :kokonaisala) ; RYHTI
                                          (-> toimenpide :mitat :kokonaisala))] ; pre-RYHTI
                  {:propertyId     (or (:propertyId building-update)
                                       (:propertyId application))
                   :localShortId   (or rakennusnro
                                       (when (validators/rakennusnumero? tunnus)
                                         tunnus))
                   :nationalId     national-id
                   :buildingId     (or national-id rakennusnro (:buildingId toimenpide))
                   :location-wgs84 location-wgs84
                   :location       location
                   :area           kokonaisala
                   :index          (str n)
                   :description    (or (op-description application op) "")
                   :operationId    (:id op)
                   :usage          (or (:kayttotarkoitus kaytto) "")
                   :building-type  (or (:rakennusluokka kaytto) "")
                   :apartments     (:apartments building-update)}))))))

(defn edit-verdict
  "Updates the verdict data. Validation takes the template inclusions
  into account. Some updates (e.g., automatic dates) can propagate other
  changes as well. Returns processing result or modified and (possible
  additional) changes."
  [{{:keys [path value]} :data :keys [application created] :as command}]
  (let [{:keys [data category
                template
                references]
         :as   verdict}   (command->verdict command)
        {:keys [data value path op]
         :as   processed} (schemas/validate-and-process-value
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
      (let [mongo-path (data-update-path path)]
        (verdict-update command
                        (if (= op :remove)
                          {$unset {mongo-path 1}}
                          {$set {mongo-path (wrap command value)}}))
        (template/changes-response {:modified created
                                    :changes  (let [options {:path            path
                                                             :value           value
                                                             :verdict-data    data
                                                             :template        template
                                                             :references      references
                                                             :category        category
                                                             :bulletinProcess (app/bulletin-process application)}
                                                    changed (changes options)]
                                                (verdict-changes-update command changed)
                                                (mapv (fn [[k v]]
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
  (mapv (fn [{:keys [given person status]}]
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
                        :data (if (util/includes-as-kw? #{:draft :scheduled} state)
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
    (->> (date/zoned-date-time created)
         (.getYear)
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
         ;; Source-linked are ignored because those are generated by Pate.
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
  [command verdict]
  (let [items (verdict-attachment-items command
                                        verdict
                                        :attachments)]
    {:items     items
     :update-fn (fn [data]
                  (assoc data
                         :attachments
                         ;; Pate-generated attachments not included in the count.
                         (->> items
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

;; TODO: kuulutukset?
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
                     replacement]
              :as   verdict} (util/find-by-id verdict-id pate-verdicts)]
    (and published
         (not legacy?)
         (not (vc/contract? verdict))
         (not (vc/proposal? verdict))
         (not (vc/rectification-verdict? verdict))
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
                               {$set  {(update-path :replacement) {:user        (user-ref command)
                                                                   :replaced-by verdict-id}}
                                $pull {:tasks {:id {$in task-ids}}}})
    (att/delete-attachments! application task-attachment-ids)))

(defn verdict-has-unaddressed-rectifications?
  "Returns true if the verdict has any rectifications (oikaisuvaatimus) that
   have not been addressed by a rectification verdict"
  [{:keys [appeals pate-verdicts]} verdict-id]
  (some (fn [{:keys [target-verdict type] :as rectification}]
          (and (= target-verdict verdict-id)
               (= type "rectification")
               (not (rectification-addressed? rectification pate-verdicts))))
        appeals))

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

(defn- verdict-updates
  "Returns a map with the verdict and a Mongo update map for the verdict.
  Each kw-path is transformed into a $set update:
  (verdict-updates {:a :a-value :b {:c c-value}} :a :b.c)
  => {:verdict {...}
      :updates {$set {:pate-verdicts.$.a   :a-value
                      :pate-verdicts.$.b.c :c-value}}}"
  [verdict & kw-paths]
  {:verdict verdict
   :updates (->> kw-paths
                 (map (fn [kwp]
                        [(update-path kwp)
                         (get-in verdict (util/split-kw-path kwp))]))
                 (into {})
                 (hash-map $set))})

(defn finalize--verdict [{:keys [command verdict]}]
  (let [verdict             (assoc (enrich-verdict command verdict true)
                                   :state (wrapped-state command :published)
                                   :published {:published (:created command)}
                                   :archive (archive-info verdict))
        data-kws            (map #(util/kw-path :data %)
                                 (-> verdict :data keys))
        foreman-v3-verdict? (vc/has-category? verdict :tj-v3)]
    (cond-> (apply verdict-updates verdict
                   (concat data-kws
                           [:template.inclusions :state
                            :published.published :archive]))
      foreman-v3-verdict? (assoc :commit-fn verdict/update-foreman-document))))

(defn finalize--signatures
  "Contracts can be automatically signed. The signer is either implicit (legacy contracts)
  or explicit (Pate contracts)."
  [{:keys [command verdict]}]
  (let [{user :user} command
        {data :data} verdict]
    (when-let [signer (ss/blank-as-nil
                        (cond
                          (not (vc/contract? verdict)) nil
                          (vc/legacy? verdict)         (vc/verdict-giver verdict)
                          :else                        (:giver data)))]
      (verdict-updates (assoc verdict
                              :signatures
                              [{:name    signer
                                 ;; The current user regardless of the signer value
                                 :user-id (:id user)
                                 :date    (:verdict-date data)}])
                       :signatures))))

(defn finalize--section
  "Section is generated only for non-board (lautakunta) non-legacy verdicts. Section is created into
  Mongo sequence right away and the addition cannot be (safely) undone.
  Section is not saved immediately to db. finalize pipeline will ensure, that in case of exception,
  the section is persisted to verdict."
  [{:keys [command application verdict dry-run?]}]
  (let [{:keys [data template legacy?]} verdict
        {section :verdict-section}      data
        {giver :giver}                  template]
    (when (and (ss/blank? section)
               (util/not=as-kw giver :lautakunta)
               (not legacy?)
               (not (vc/contract? verdict))
               (not dry-run?))
      (let [section (next-section (:organization application)
                                  (:created command)
                                  giver)]
        (verdict-updates
          (assoc-in verdict [:data :verdict-section] section)
          :data.verdict-section)))))

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
  [{:keys [command application verdict]}]
  (let [state               (sm/verdict-given-state application)
        foreman-v3-verdict? (vc/has-category? verdict :tj-v3)]
    ;; History, modified and document updates not needed in the application.
    (cond-> {:application (assoc application :state state)}
      ;; only apply state transitions etc. if the verdict is not a foreman-v3 verdict
      (not foreman-v3-verdict?)
      (assoc :updates (app-state/state-transition-update
                        state
                        (:created command)
                        application
                        (:user command))
             :commit-fn update-waste-documents))))

(defn- legacy-foremen->documents [current-foremen foremen command]
  (let [missing-foremen (some->> foremen
                                 vals
                                 (map :role)
                                 (remove current-foremen))]
    (map #(foreman/new-foreman-document % command)
         missing-foremen)))

(defn- foremen->documents [current-foremen foremen command]
  (let [missing-foremen (->> (map helper/foreman-role foremen)
                             (remove current-foremen))]
    (map #(foreman/new-foreman-document % command)
         missing-foremen)))

(defn make-foreman-documents [application {{:keys [foremen]} :data :as verdict} command]
  (let [current-foremen (->> (foreman/get-foreman-documents-v3 application)
                             (keep #(get-in % [:data :kuntaRoolikoodi :value]))
                             set)]
    (if (:legacy? verdict)
      (legacy-foremen->documents current-foremen foremen command)
      (foremen->documents current-foremen foremen command))))

(defn finalize--buildings-tasks-and-docs
  [{:keys [command application verdict]}]
  (let [buildings           (->buildings-array application)
        tasks               (validate-tasks (pate-tasks/pate-verdict->tasks
                                              verdict
                                              (:created command)
                                              buildings))
        foreman-docs        (when (env/feature? :new-foreman-invite)
                              (make-foreman-documents application verdict command))]
    (cond-> {:application (assoc application :buildings buildings)
             :updates     {$set {:buildings buildings}}}
      (seq tasks) (-> (assoc-in [:application :tasks] tasks)
                      (assoc-in [:updates $push :tasks] {$each tasks}))
      (seq foreman-docs) (-> (update-in [:application :documents] into foreman-docs)
                             (assoc-in [:updates $push :documents] {$each foreman-docs})))))

(defn- finalize--climate-reports [{:keys [command application]}]
  (let [climate-report-docs (climate-report/make-climate-report-docs command)]
    (when (seq climate-report-docs)
      (-> {:application (assoc application :climate-reports climate-report-docs)
           :updates     {$push {:climate-reports {$each climate-report-docs}}}}))))

(def processor (factory/attachment-assignment-processor identity))

(defn- run-assignment-triggers-for-verdict-attachments!
  [{:keys [user organization application created pred]}]
  (->> application
       :attachments
       (filter pred)
       (run! (fn [{{:keys [type-group type-id]} :type
                   :keys                        [id]}]
               (log/with-logging-context
                 {:attachmentId id}
                 (try
                   (processor (util/mmap {:timestamp        created
                                          :assignment-group "attachments"
                                          :attachment-id    id
                                          :targets          [{:id           id
                                                              :trigger-type (ss/join "." [(name type-group)
                                                                                          (name type-id)])}]}
                                         user application organization))
                   (catch Exception e
                     (log/errorf e "Could not create assignment automatically for verdict attachment"))))))))

(defn finalize--attachments [{:keys [command application verdict]}]
  (let [{att-items :items
         update-fn :update-fn}    (attachment-items command verdict)
        verdict-attachment?       #(util/includes-as-kw? (map :id att-items)
                                                         (:id %))
        verdict-draft-attachment? #(and (verdict-attachment? %)
                                        (some-> % :metadata :draftTarget))
        target                    {:type "verdict"
                                   :id   (:id verdict)}]
    (-> verdict
        (assoc-in [:data :selected-attachments] (get-in verdict [:data :attachments] []))
        (update :data update-fn)
        (verdict-updates :data.attachments :data.selected-attachments)
        (assoc :application
               (-> application
                   (update :attachments
                           #(map (fn [attachment]
                                   (util/pcond-> attachment
                                     verdict-draft-attachment? (assoc :target target)))
                                 %))
                   ;; attachments modified -> remove stale cache just in case
                   domain-att/remove-attachment-map))
        (update-in [:updates $set]
                   merge
                   (att/attachment-array-updates (:id application)
                                                 verdict-draft-attachment?
                                                 :metadata.nakyvyys "julkinen"
                                                 :metadata.draftTarget false
                                                 :target target
                                                 :readOnly true
                                                 :locked   true))
        (assoc :commit-fn (fn [{:keys [command application verdict-attachment-id bulletin-attachment-id
                                       app-at-time-of-verdict-pdf-id]}]
                            (tiedonohjaus/mark-app-and-attachments-final! (:id application)
                                                                          (:created command)
                                                                          verdict-attachment-id
                                                                          bulletin-attachment-id)
                            ;; Run automatic assignment triggers for any created attachments
                            ;; NOTE: The original `application` is stale, it does not actually contain
                            ;; the verdict or bulletin attachments. We also can't really use projection
                            ;; here, as the assignment processor needs the full application
                            (let [application (domain/get-application-no-access-checking
                                                (-> command :application :id))]
                              (run-assignment-triggers-for-verdict-attachments!
                                {:user         (:user command)
                                 :application  application
                                 :organization (force (:organization command))
                                 :created      (:created command)
                                 :pred         (fn [{:keys [id target]}]
                                                 (or
                                                   ;; Handle attachments added directly to the verdict
                                                   (= target {:type "verdict"
                                                              :id   (:id verdict)})
                                                   ;; and the generated PDFs
                                                   (and verdict-attachment-id
                                                        (= verdict-attachment-id id))
                                                   (and bulletin-attachment-id
                                                        (= bulletin-attachment-id id))
                                                   (and app-at-time-of-verdict-pdf-id
                                                        (= app-at-time-of-verdict-pdf-id id))))})))))))

(defn finalize--link-permit
  [{:keys [application verdict]}]
  (when (and (app/jatkoaika-application? application)
               (accepted-verdict? verdict))
    {:commit-fn (fn [{:keys [application verdict]}]
                  (app/add-continuation-period
                   (link-permit-application application)
                   (:id application)
                   (get-in verdict [:data :handler])
                   (or (get-in verdict [:data :voimassa])
                       (get-in verdict [:data :k-voimassa]))))}))

(defn finalize--replaced-verdict [{:keys [verdict]}]
  (when-let [replace-verdict-id (get-in verdict [:replacement :replaces])]
    {:commit-fn (fn [{:keys [command verdict]}]
                  (replace-verdict command replace-verdict-id (:id verdict)))}))

(defn set-sent-value-for-attachments
  "Sets sent value for attachments that were sent backing system."
  [sent-file-ids command]
  (if (and (seq? sent-file-ids) (every? string? sent-file-ids))
    (let [all-attachments     (:attachments (domain/get-application-no-access-checking (-> command :application :id) [:attachments]))
          attachments-updates (att/create-sent-timestamp-update-statements all-attachments sent-file-ids (:created command))]
      (action/update-application command {$set attachments-updates}))
    sent-file-ids))

(defn pate-kuntagml-integration?
  "True if Pate KuntaGML integration is active either via sftp or dmcity. Pate is assumed to
  be enabled in the organization."
  ([organization {permit-type :permitType :as application}]
   (let [organization (force organization)]
     (boolean (and (org/krysp-write-integration? organization permit-type)
                   (or (dm-cfg/target-enabled? organization permit-type)
                       (get-in (org/resolve-organization-scope (:municipality application)
                                                               permit-type
                                                               organization)
                               [:pate :sftp]))))))
  ([{:keys [organization application]}]
   (pate-kuntagml-integration? organization application)))

(defn finalize--kuntagml
  [{:keys [command application verdict]}]
  (let [verdict-id (:id verdict)]
    (when (and (not (:legacy? verdict))
               (not (vc/contract? verdict))
               (vc/kuntagml-verdict? verdict)
               (pate-kuntagml-integration? (:organization command) application))
      {:commit-fn (fn [{:keys [command application verdict]}]
                    (if-let [verdict-attachment (-> (domain/get-application-no-access-checking
                                                      (:id application)
                                                      {:attachments {$elemMatch {:id (-> verdict :published :attachment-id)}}})
                                                    :attachments
                                                    first)]
                      (try+
                        (set-sent-value-for-attachments
                          (krysp/verdict-as-kuntagml (assoc command
                                                            :application (update application :attachments
                                                                                 conj verdict-attachment))
                                                     verdict
                                                     verdict-attachment)
                          command)
                        (catch [:ok false] {text :text}
                          (error (format "KuntaGML failed for verdict %s (permit-type %s). Error: %s"
                                         verdict-id (:permitType application) text)))
                        (catch Exception e
                          (error (format "Something went wrong when generating KuntaGML for %s" verdict-id) e)))
                      (error (format "Can't creat KuntaGML for verdict %s since verdict attachment is missing"
                                     verdict-id))))})))

(defn- extract-lupamaarays-ids [coll]
  (cond->> coll
    (map? coll)         keys
    (some string? coll) (map keyword)))

(defn finalize--ryhti
  "Add the UUIDs for Ryhti integration for the verdict itself and conditions"
  [{:keys [application] {{:keys [conditions reviews plans]} :data :as verdict} :verdict}]
  (when (:ryhti-data-2026 application)
    (verdict-updates
      (assoc verdict :ryhti {:buildingPermitDecisionKey (str (random-uuid))
                             :permitRegulationKeys      (->> [(extract-lupamaarays-ids plans)
                                                              (extract-lupamaarays-ids conditions)
                                                              (extract-lupamaarays-ids reviews)]
                                                             flatten
                                                             distinct
                                                             (remove nil?)
                                                             (reduce (fn [acc condition-id]
                                                                       (assoc acc (name condition-id) (str (random-uuid))))
                                                                     {}))})
      :ryhti.buildingPermitDecisionKey
      :ryhti.permitRegulationKeys)))

(defn finalize--pdf-tags
  [{:keys [application verdict]}]
  (let [tags        (pdf/verdict-tags application verdict false)
        verdict     (assoc-in verdict [:published :tags] (ss/serialize tags))]
    (verdict-updates verdict :published.tags)))

(defn pate-verdict->html
  [application verdict omit-party-info? system]
  {:pre [(map? system)]}
  (if-let [tags (pdf/verdict-tags application verdict omit-party-info? nil)]
    (pdf/verdict-html->pdf (pdf/verdict-tags-html tags) system)
    (error-and-fail!
      (str "Verdict " (:id verdict) " does not have :tags for PDF generation")
      :pate.pdf-verdict-error)))

(defn- new-attachment-version [command verdict attachment-id pdf]
  (if-let [id (->> pdf
                   (pdf/create-verdict-attachment-version command verdict attachment-id)
                   :id)]
    id
    (error-and-fail!
      (str "Attaching new version for verdict proposal " (:id verdict) " did not return attachment id")
      :error.attachment-pdf-generation-failed)))

(defn- new-attachment [command verdict pdf for-bulletin?]
  (if-let [attachment-id (:id (pdf/create-verdict-attachment command verdict pdf for-bulletin?))]
    (let [att-kw (if for-bulletin? :bulletin-attachment-id :attachment-id)
          type   (if (vc/proposal? verdict) :proposal :published)
          result (verdict-updates (assoc-in verdict [type att-kw] attachment-id)
                                  (util/kw-path type att-kw))]
      (assoc result :attachment-id attachment-id))
    (error-and-fail!
      (str "PDF generation for verdict " (:id verdict) " did not return attachment id")
      :error.attachment-pdf-generation-failed)))

(defn create-verdict-pdf
  "Create-verdict-attachment-version is called if PDF-proposal or PDF-signatures are 'updated'
  else create-verdict-attachment is called. In the latter case result is also updated"
  [{:keys [command application verdict]} for-bulletin?]
  (let [command (assoc command :application application)
        pdf     (pate-verdict->html application verdict for-bulletin? (:system command))]
    (when-not (:ok pdf)
      (error-and-fail! (str "PDF generation for verdict " (:id verdict) " failed in muuntaja")
                       :pate.pdf-verdict-error))
    (if-let [proposal-attachment (and (vc/proposal? verdict)
                                      (some-> verdict :proposal :attachment-id))]
      {:attachment-id (new-attachment-version command verdict proposal-attachment pdf)}
      (let [{:keys [updates verdict attachment-id]} (new-attachment command verdict pdf for-bulletin?)
            attachment-kw                           (if for-bulletin? :bulletin-attachment-id :verdict-attachment-id)]
        {:updates      updates
         :verdict      verdict
         attachment-kw attachment-id}))))

(defn create-bulletin-pdf?
  "Only generate the censored version of the verdict PDF for bulletins if we are creating a kuulutus bulletin"
  [{:keys [organization application created]} verdict]
  (and (org/bulletins-enabled? (force organization)
                               (:permitType application)
                               (:municipality application)
                               created)
       (not (vc/proposal? verdict))
       (some-> verdict :data :k-kuulutus))) ; only for kuulutus, not julkipano

(defn app-at-time-of-verdict-pdf [{:keys [command application dry-run?]}]
  (when-not dry-run?
    (let [{:keys [lang created user]} command
          pdf-stream (-> (app-utils/with-masked-person-ids application user)
                         (assoc :app-at-verdict-pdf true)
                         (pdf-export/generate-application-pdfa lang))]
      (if-not pdf-stream
        (do
          (warnf "app-at-time-of-verdict-pdf: PDF stream generation failed for application %s"
                 (:id application))
          nil)
        (with-open [stream pdf-stream]
          (let [verdict-app-type    {:type-group "muut"
                                     :type-id    "paatoksen_aikaiset_hankkeen_tiedot"}
                existing-attachment (first (att/get-attachments-by-type application verdict-app-type))
                attachment-options  (cond-> {:created         created
                                             :attachment-type verdict-app-type
                                             :archive-only?   true
                                             :comment?        false
                                             :state           :ok
                                             :contents        (i18n/localize lang
                                                                             "attachmentType.muut.paatoksen_aikaiset_hankkeen_tiedot")}

                                      existing-attachment
                                      (assoc :original-file-already-linked? true
                                             :attachment-id                 (:id existing-attachment)
                                             :original-file-id              (some-> existing-attachment
                                                                                    :latestVersion
                                                                                    :originalFileId)))
                file-options        {:filename (i18n/localize-and-fill lang
                                                                       :pdf.paatoksen_aikaiset_hankkeen_tiedot.filename
                                                                       (:id application)
                                                                       (date/finnish-datetime created :zero-pad))}]
            (pdf/upload-and-attach-pdf! {:command            command
                                         :pdf                {:pdf-file-stream stream}
                                         :attachment-options attachment-options
                                         :file-options       file-options})))))))


(defn create-verdict-pdfs
  "Generates the PDF/A:s related to the published verdict.
  This includes the verdict PDF itself and possibly a version of it for the bulletin

  We also create the app-at-time-of-verdict-pdf, it is not actually linked to the verdict and it does not update
  the verdict in anyway. It is just a snapshot of the current state of the application when the latest verdict was given.
  Each time a new verdict is given, the previous app-at-time-of-verdict-pdf gets replaced by a new one"
  [{:keys [command verdict] :as result}]
  (let [verdict-pdf-result  (create-verdict-pdf result false)
        bulletin-pdf-result (when (create-bulletin-pdf? command verdict)
                              (create-verdict-pdf result true))
        app-state-pdf       (app-at-time-of-verdict-pdf result)]
    (-> result
        (update :updates util/deep-merge (:updates verdict-pdf-result) (:updates bulletin-pdf-result))
        (merge (select-keys verdict-pdf-result [:verdict :verdict-attachment-id])
               (select-keys bulletin-pdf-result [:bulletin-attachment-id])
               (when app-state-pdf
                 {:app-at-time-of-verdict-pdf-id (:id app-state-pdf)})))))

(defn- bulletin-data
  "Verdict data -> bulletin data. Separate function for easier testing."
  [{:keys [julkipano anto bulletin-op-description operation muutoksenhaku verdict-date
           k-verdict-date k-kuulutus k-muutoksenhaku]}]
  (let [op-desc (-> bulletin-op-description ss/trim not-empty)]
    ;; k-verdict-date applies when creating legacy Pate verdicts
    (cond-> {:verdictDate           (or verdict-date k-verdict-date)
             :bulletinOpDescription (or op-desc
                                        (-> operation ss/trim not-empty))
             :appealPeriodStartsAt  (or julkipano k-kuulutus)
             :appealPeriodEndsAt    (bulletins/calculate-appeal-end
                                      {:muutoksenhaku (or muutoksenhaku k-muutoksenhaku)
                                       :anto          anto
                                       :julkipano     julkipano
                                       :kuulutus      k-kuulutus})}
      ;; Kuulutukset have no `anto` date
      anto    (assoc :verdictGivenAt anto)
      op-desc (assoc :markup? true))))

(defn finalize--bulletin [{:keys [command verdict application dry-run?]}]
  (when (org/bulletins-enabled? (force (:organization command))
                                (:permitType application)
                                (:municipality application)
                                (:created command))
    (when (or (some-> verdict :data :julkipano)
              (some-> verdict :data :k-kuulutus))
      (util/assoc-when
        {:commit-fn (fn [{:keys [command application verdict bulletin-attachment-id]}]
                      ;; If verdict replaces an old one - remove the old verdict from bulletins
                      (when-let [verdict-to-replace (-> verdict :replacement :replaces)]
                        (bulletins/process-delete-verdict application verdict-to-replace))
                      (bulletins/upsert-bulletin-by-id
                        (str (:id application) "_" (:id verdict))
                        (bulletins/create-bulletin
                          (util/assoc-when-pred application util/not-empty-or-nil?
                                                :pate-verdicts [verdict])
                          (:created command)
                          (util/assoc-when (-> verdict :data bulletin-data)
                                           :attachment-id
                                           bulletin-attachment-id))))}
        :extra-data (when dry-run?
                      (let [db-changes (bulletins/create-bulletin
                                         (util/assoc-when-pred application util/not-empty-or-nil?
                                                               :pate-verdicts [verdict])
                                         (:created command)
                                         (-> verdict :data bulletin-data))]
                        {:bulletin {:version (get-in db-changes [$push :versions])
                                    :data    (get db-changes $set)}}))))))

(defn update-verdict-state
  "Set pate-verdict state. new-state must we wrapped with metadata."
  [command verdict-id new-state]
  {:pre [(metadata/wrapped? new-state)]}
  (action/update-application command
                             {:pate-verdicts {$elemMatch {:id verdict-id}}}
                             {$set {(update-path :state) new-state}}))

(defn set-publishing-state-for-verdict
  [{{:keys [verdict-id]} :data {user :username} :user created :created :as command} signing-contract?]
  (let [user                      (or user "set-publishing-state")
        publishing-state          (cond signing-contract?                         helper/signing-contract
                                        (vc/proposal? (command->verdict command)) helper/publishing-proposal
                                        :else                                     helper/publishing-verdict)
        wrapped-publishing-state  (metadata/wrap user created publishing-state)]
    (update-verdict-state command verdict-id wrapped-publishing-state)))

(defn clear-publishing-state-for-verdict
  "If verdict has publishing-state previous state of the verdict is restored"
  [{{:keys [verdict-id]} :data application :application :as command}]
  (let [current-state (:state (command->verdict command true))]
    (when (contains? helper/publishing-states current-state)
      (let [wrapped-old-state (->> application
                                   :pate-verdicts
                                   (filter #(= verdict-id (:id %)))
                                   first
                                   :state)]
        (update-verdict-state command verdict-id wrapped-old-state)))))

(defn- panic-update-section
  "Force update section to verdict, if it has been already sequenced in finalize--section."
  [command updates]
  (when-let [section (get-in updates [$set (data-update-path :verdict-section)])]
    (warnf "Panic updating section %s to verdict %s" section (get-in command [:data :verdict-id]))
    (verdict-update command {$set {(data-update-path :verdict-section) section}})))

(defn process-finalize-pipeline
  "PDF is generated and added to db after schema validation but before adding other changes to db"
  [{:keys [dry-run? command application verdict]} & finalize--fns]
  (try+
    ;; Validating schema here would help to catch hard-to-understand errors in finalize-fns, which expect the verdict
    ;; to be "valid", but currently the wrapping/unwrapping mechanisms make it unclear if it is possible.
    ;; If this is called with verdict which is obtained from `command->verdict` the verdict is "unwrapped"
    ;; and so validating here gives error because the schema expects data to be wrapped...
    ;;
    ;; (sc/validate schemas/PateVerdict (update verdict :category name))
    (let [{:keys [verdict updates]
           :as   result} (reduce (fn [acc fun]
                                   (let [{:keys [updates commit-fn extra-data]
                                          :as   m} (fun (select-keys acc [:command :application :verdict :dry-run?]))]
                                     (-> acc
                                         (merge (select-keys m [:application :verdict]))
                                         (update :updates util/deep-merge updates)
                                         (update :extra-data util/deep-merge extra-data)
                                         (update :commit-fns conj commit-fn))))
                                 {:command     command
                                  :application application
                                  :verdict     verdict
                                  :dry-run?    dry-run?
                                  :updates     {}
                                  :commit-fns  []
                                  :extra-data  {}}
                                 finalize--fns)]
      (schemas/valid-pate-verdict! (update verdict :category name))
      (if dry-run?
        result
        (try+
          (let [{:keys [updates commit-fns] :as result} (create-verdict-pdfs result)]
            (verdict-update command updates)
            (doseq [fun (remove nil? commit-fns)]
              (fun (select-keys result [:command
                                        :application
                                        :verdict
                                        :verdict-attachment-id
                                        :bulletin-attachment-id
                                        :app-at-time-of-verdict-pdf-id])))
            (ok))
          (catch [:text "pate.pdf-verdict-error"] _
            (panic-update-section command updates)
            (fail :error.attachment-pdf-generation-failed))
          (catch Exception e
            (panic-update-section command updates)
            (throw e)))))
    (finally
      (clear-publishing-state-for-verdict command))))

(def verdict-finalize-fns
  [finalize--verdict
   finalize--signatures
   finalize--application-state
   finalize--ryhti
   finalize--buildings-tasks-and-docs
   finalize--climate-reports
   inspection-summary/finalize--inspection-summary
   finalize--attachments
   finalize--link-permit
   finalize--replaced-verdict
   finalize--section
   finalize--pdf-tags
   finalize--bulletin
   finalize--kuntagml])

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
   9. Create PDF/A for the verdict (adds generated attachment to db)
  10. Update date for continuation applications
  11. Generate KuntaGML
  11. Publish bulletin
  12. Generate a PDF/A for the bulletin (adds generated attachment to db)

  If the verdict replaces an old verdict, then
  13. Update old verdict's replacement property
  14. Delete old verdict tasks.

  KuntaGML is generated after changes are applied to db

  Returns :ok true/false."
  [{:keys [application] :as command}]
  (set-publishing-state-for-verdict command false)
  (->> verdict-finalize-fns
       (apply process-finalize-pipeline
              {:command command
               :application application
               :verdict (command->verdict command)})))

(defn- validate-results [pipeline-result pdf]
  (cond
    (empty? (:updates pipeline-result)) :error.verdict.no-updates
    (not (:ok pdf)) (or (:error pdf) (:text pdf))))

(defn schedule-publish
  "Sets up the verdict to be published at a future date by the publish-scheduled-verdicts batchrun.
  Also does a dry run of the publishing process to check for any problems beforehand."
  [{:keys [application user created system] :as command}]
  (let [verdict        (command->verdict command)
        new-state      :scheduled
        dry-run-result (->> verdict-finalize-fns
                            (apply process-finalize-pipeline
                                   {:dry-run?    true
                                    :command     command
                                    :application application
                                    :verdict     verdict}))
        dry-run-pdf    (pate-verdict->html application (:verdict dry-run-result) false system)]
    (try
      (if-let [error (validate-results dry-run-result dry-run-pdf)]
        (fail error)
        (do
          (update-verdict-state command (:id verdict) (metadata/wrap (:username user) created new-state))
          (ok :state new-state)))
      (finally
        (when-let [^InputStream is (:pdf-file-stream dry-run-pdf)]
          ; discard generated PDF stream
          (.close is))))))

(defn- download-pdf
  "Downloads pdf from given path. Shows error page if the PDF is not yet ready"
  [{:keys [user system] :as command} path re-call]
  (if-let [attachment-id (some-> (command->verdict command)
                                 (get-in path))]
    (att/output-attachment (att/get-attachment-latest-version-file system
                                                                   user
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
  (let [{:keys [error pdf-file-stream
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

(defn- preview-bulletin-data
  [{:keys [created application verdict bulletin]}]
  (let [get-start       (some-fn :appealPeriodStartsAt :proclamationStartsAt)
        get-end         (some-fn :appealPeriodEndsAt :proclamationEndsAt)
        start           (some-> (get-start (:version bulletin))
                                date/start-of-day
                                date/timestamp)
        end             (some-> (get-end (:version bulletin))
                                date/end-of-day
                                date/timestamp)
        inclusions      (-> verdict :template :inclusions)
        verdict-process (cond
                          ;; `nil` if template doesn't specify any bulletin dates
                          (util/includes-as-kw? inclusions :k-kuulutus) :kuulutus
                          (util/includes-as-kw? inclusions :julkipano)  :julkipano)]
    (-> {}
        ;; It seems we never check if the dates are sane
        ;; -> Only display the publish period if it is valid
        (merge (when (and start end
                          ;; e.g., muutoksenhaku date is before kuulutus date
                          (<= start end)
                          ;; e.g., muutoksenhaku date is in the past
                          (<= created end))
                 {:publish-start start
                  :publish-end   end}))
        (util/assoc-when :process (or (-> bulletin :version :bulletinProcess)
                                      verdict-process)
                         :description (when bulletin
                                        (or (-> bulletin :data :bulletinOpDescription)
                                            (-> application :bulletinOpDescription))))
        not-empty)))

(defn preview-verdict-data
  "Provide preview data about the verdict draft"
  [{:keys [application created] :as command}]
  (let [verdict (command->verdict command)]
    ;; Some finalize steps may fail depending on the verdict template
    ;; settings if user has not chosen a language for the verdict draft
    (if (and (not (vc/legacy? verdict))
             (ss/blank? (cols/language verdict)))
      (ok)
      (let [{:keys              [verdict application]
             {:keys [bulletin]} :extra-data} (->> verdict-finalize-fns
                                                  (apply process-finalize-pipeline
                                                         {:dry-run?    true
                                                          :command     command
                                                          :application application
                                                          :verdict     (command->verdict command)}))]
        (ok (util/assoc-when
              {}
              :bulletin (preview-bulletin-data (util/mmap created application verdict bulletin))))))))

;; ---------------------------------
;; Backing system verdicts
;; The backing system verdicts reside in the verdicts array and are never
;; modified by Pate.
;; ---------------------------------

(defn command->backing-system-verdict
  "Returns the corresponding backing system verdict or nil."
  [{:keys [application data]}]
  (util/find-by-id (:verdict-id data) (:verdicts application)))

(defn backing-system--poytakirja-properties
  [{:keys [attachments]}
   {url-hash :urlHash att-id :attachment-id :as poytakirja}]
  (util/assoc-when (-> poytakirja
                       (select-keys [:status :pykala
                                     :paatospvm :paatoksentekija])
                       (assoc :paatos (vc/pk-verdict-string poytakirja)))
                   :attachment
                   (when-let [attachment (cond
                                           att-id
                                           (util/find-by-id att-id attachments)

                                           url-hash
                                           (util/find-first #(= (get-in % [:target :urlHash])
                                                                url-hash)
                                                            attachments))]
                     {:url  (str "/api/raw/latest-attachment-version?attachment-id="
                                 (:id attachment))
                      :text (get-in attachment [:latestVersion :filename])})))

(defn backing-system--paatos-properties
  "Properties for an individual paatos (there can multiple within a
  verdict.). In addition, each paatos can include multiple
  poytakirjas."
  [{:keys [lupamaaraykset paivamaarat]}]
  (-> (update lupamaaraykset :maaraykset (partial map :sisalto))
      (update :vaaditutKatselmukset (partial map :tarkastuksenTaiKatselmuksenNimi))
      (merge paivamaarat)))

(defn backing-system--tags [application verdict]
  (let [kuulutus? (= :kuulutus (app/bulletin-process application))]
    (reduce (fn [acc paatos]
              (concat acc
                      (mapcat #(cols/content (backing-system--poytakirja-properties application %)
                                             layouts/backing-poytakirja-layout)
                              (:poytakirjat paatos))
                      (cols/content (backing-system--paatos-properties paatos)
                                    (if kuulutus?
                                      layouts/backing-paatos-layout-kuulutus
                                      layouts/backing-paatos-layout))))
            (cols/content verdict layouts/backing-kuntalupatunnus-layout)
            (:paatokset verdict))))

(defn published-verdict-details
  "Map of id, published, tags and attachment ids (if available)"
  [{:keys [application] :as command}]
  ;; TODO: LPK-6598: localize the verdict results
  (if-let [verdict (command->backing-system-verdict command)]
    {:id        (:id verdict)
     :published (:timestamp verdict)
     :tags      (ss/serialize {:body (backing-system--tags application verdict)})}
    (let [{:keys [id published]} (command->verdict command)]
      (assoc published :id id))))

(defn published-verdict-attachment-ids
  "Verdict attachments (either via target or source) for the given
  published verdict. In addition to :attachment-ids
  returns :attachment-missing? if the verdict attachment is not
  generated yet."
  [{:keys [application] :as command}]
  (let [verdict (or (command->backing-system-verdict command)
                    (command->verdict command))]
    {:attachment-ids (verdict-attachment-ids application verdict)}))

;; ----------------------------
;; Signatures
;; ----------------------------

(defn user-can-sign? [{:keys [application user] :as command}]
  (let [{sigs :signatures} (command->verdict command)
        {com-id :id}       (auth/auth-via-company application user)]
    (not-any? (fn [{:keys [user-id company-id name]}]
                (or (and (= user-id (:id user))
                         (= name (full-name user)))
                    (and com-id
                         (= com-id company-id))))
              sigs)))

(defn create-signature
  [{:keys [application user created]}]
  (let [person           (full-name user)
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
  [{:keys [user created application system] :as command}]
  (try+
    (set-publishing-state-for-verdict command true)
    (let [signature           (create-signature command)
          verdict             (command->verdict command)
          tags                (-> verdict :published :tags
                                  edn/read-string
                                  (update :body update-signature-section verdict signature))
          updated-application (if (and (transition-to-assignmentSigned-state? application)
                                       (sm/valid-state? application :agreementSigned))
                                (assoc application :state "agreementSigned") ; I think state is needed because we generate attachments
                                application)
          command             (assoc command :application updated-application)
          attachment-id       (get-in verdict [:published :attachment-id])
          pdf                 (pdf/verdict-html->pdf (pdf/verdict-tags-html tags) system)]
      (if (:ok pdf)
        (pdf/create-verdict-attachment-version command verdict attachment-id pdf)
        (error-and-fail! "Signing contract failed when generating PDF with muuntaja" (:text pdf)))
      (verdict-update command
                      (util/deep-merge
                        {$push {(util/kw-path :pate-verdicts.$.signatures) signature}
                         $set  {(util/kw-path :pate-verdicts.$.published.tags) (ss/serialize tags)}}
                        (when (transition-to-assignmentSigned-state? application)
                          (app-state/state-transition-update :agreementSigned
                                                             created
                                                             application
                                                             user))
                        (when (sign-requested? command)
                          {$pull {(util/kw-path :pate-verdicts.$.signature-requests) {:user-id (:id user)}}})))
      true)
    (finally (clear-publishing-state-for-verdict command))))

(defn- signer-ids [data]
  (->> data
       (map :user-id)
       (remove nil?)
       set))

(defn signature-request-parties
  "List of maps where `:value` is user id and `:text` user's friendly name. The source data
  is application auth and only writer persons are included."
  [{:keys [user application] :as command}]
  (let [verdict      (command->verdict command)
        reserved-ids (set/union (signer-ids (:signatures verdict))
                                (signer-ids (:signature-requests verdict))
                                #{(:id user)})]
    (into []
          (for [{:keys [id type role invite]
                 :as   auth} (:auth application)
                :let         [writer? (-> (set [(keyword role) (some-> invite :role keyword)])
                                          (set/intersection roles/default-authz-writer-roles)
                                          not-empty)
                              available? (not (reserved-ids id))
                              person? (util/not=as-kw type :company)]
                :when        (and writer? available? person?)]
            {:value id :text (full-name auth)}))))

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
    (verdict-update command {$push {(update-path :signature-requests) request}})))

;; ----------------------------
;; Proposal
;; ----------------------------

(defn finalize--proposal [{:keys [command verdict]}]
  (let [old-proposal (get verdict :proposal)
        verdict       (assoc (enrich-verdict command verdict true)
                        :state (wrapped-state command :proposal)
                        :proposal (assoc old-proposal :proposed (:created command)))
        data-kws          (map #(util/kw-path :data %)
                               (-> verdict :data keys))]
    (apply verdict-updates verdict
           (concat data-kws [:state :proposal.proposed]))))

(defn finalize--proposal-pdf-tags
  [{:keys [application verdict]}]
  (let [tags    (pdf/verdict-tags application verdict false)
        verdict (assoc-in verdict [:proposal :tags] (ss/serialize tags))]
    (-> verdict
        (verdict-updates :proposal.tags))))

(defn publish-verdict-proposal
  "Publishing verdict proposal does the following:
    1. Updates verdict state to proposal
    2. Generates PDF/A for verdict proposal"
  [{:keys [application] :as command}]
  (process-finalize-pipeline {:command command
                              :application application
                              :verdict (command->verdict command)}
                             finalize--proposal
                             finalize--proposal-pdf-tags))

(defn revert-verdict-proposal
  "Remove published proposal (pdf and proposal key). Return verdict into
  draft state."
  [{:keys [application] :as command}]
  (let [{:keys [proposal]} (command->verdict command)]
    (when-let [att-id (:attachment-id proposal)]
      (att/delete-attachments! application [att-id]))
    (verdict-update command
                    {$set   {(update-path :state) (wrapped-state command :draft)}
                     $unset {(update-path :proposal) true}})))
