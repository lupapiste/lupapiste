(ns lupapalvelu.ryhti.datamodel.issue
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [lupapalvelu.ryhti.datamodel.action :refer [construction-action-mapping]]
            [lupapalvelu.ryhti.datamodel.application :as application]
            [lupapalvelu.ryhti.datamodel.attachment :refer [attachment-mapping]]
            [lupapalvelu.ryhti.datamodel.building :refer [building-site-mapping]]
            [lupapalvelu.ryhti.datamodel.common :refer [->iso-date apply-mapping required-filled]]
            [lupapalvelu.ryhti.datamodel.decision :as decision]
            [lupapalvelu.ryhti.datamodel.project :refer [construction-project-mapping]]
            [lupapalvelu.states :as states]
            [sade.util :as util]))

(defn state->rakvalv-asian-elinkaaren-tila
  [state]
  (let [state (keyword state)]
    (cond
      ;; paatos annettu <= state (and not cancelled)?
      (contains? states/post-verdict-states state)
      (:paatos-annettu ryhti-mappings/rakvalv-asian-elinkaaren-tila)

      ;; submitted <= state < paatos annettu (and not cancelled)?
      (contains? states/post-submitted-states state)
      (:vireilla ryhti-mappings/rakvalv-asian-elinkaaren-tila)

      ;; cancelled?
      (= :canceled state)
      (:kasittely-keskeytetty ryhti-mappings/rakvalv-asian-elinkaaren-tila)

      ;; otherwise must be: state < submitted (and not cancelled)
      :else
      (:ennakkoneuvottelu-tai-valmistelu ryhti-mappings/rakvalv-asian-elinkaaren-tila))))

(def ^:private common-issue-mapping
  [{:ryhti       [:permanentPermitIdentifier]
    :lupapiste   [:application :ryhti :permanentPermitIdentifier]
    :description "Pysyvä rakentamislupatunnus (PLT). Lupapiste hakee tämän itse heti hakemusta avattessa."}

   {:ryhti       [:dateOfInitiation]
    :lupapiste   [:application :submitted]
    :description "Vireilletuloaika, saadaan käsittelyajanlaskennasta"
    :nilable?    true
    :->ryhti     ->iso-date}

   {:ryhti       [:lifeCycleState]
    :lupapiste   [:application :state]
    :->ryhti     (fn [state _] (state->rakvalv-asian-elinkaaren-tila state))
    :description "elinkaaritila, pakollinen, 1"}

   {:ryhti       [:municipalityNumber]
    :lupapiste   [:application :municipality]
    :description "Kuntanumero"}

   {:ryhti       [:buildingSite]
    :lupapiste   [:documents-by-type :location]
    :->ryhti     (fn [docs context]
                   (let [site-docs          (->> docs
                                                 (filterv (fn [doc] (-> doc :data :ryhti :buildingSiteKey :value))))
                         address-fields     (get-in context [:fields-by-flag :ryhti-address?])
                         measurement-fields (get-in context [:fields-by-flag :ryhti-measurements?])]
                     (when (< 1 (count site-docs))
                       (throw (ex-info "Multiple site documents found!" {:site-docs site-docs})))
                     (when (< 1 (count address-fields))
                       (throw (ex-info "Multiple address fields found!" {:address-fields address-fields})))
                     (when (< 1 (count measurement-fields))
                       (throw (ex-info "Multiple measurement fields found!" {:measurement-fields measurement-fields})))
                     ;; NOTE: This is currently random/incorrect if there are multiple of these docs.
                     (when (not-empty site-docs)
                       (apply-mapping building-site-mapping (util/assoc-when context
                                                                             :rakennuspaikka (first site-docs)
                                                                             :address-field  (first address-fields)
                                                                             :measurement-field (first measurement-fields))))))
    :description "Rakennuspaikka, pakollinen, 1"}

   {:ryhti       [:attachment]
    :lupapiste   []
    :->ryhti     (fn [_ {:keys [application] :as context}]
                   (into []
                         (comp (map (fn [attachment]
                                      (apply-mapping attachment-mapping (assoc context :attachment attachment))))
                               (filter (required-filled attachment-mapping)))
                         (remove :archiveOnly (:attachments application))))
    :description "Liiteasiakirja, 0..*"}

   #_
   {:ryhti       [:updateType]
    :lupapiste   nil ;; TODO
    :->ryhti     always-nil
    :description "Päivityksen laji, 0..1"}

   #_
   {:ryhti       [:updateDescription :fin]
    :lupapiste   nil ;; TODO
    :->ryhti     always-nil
    :description "Päivityksen kuvaus, 0..1"}

   ])

(defn- apply-mapping-to-verdict
  [{:keys [context mapping]}]
  ;; TODO: what should we do if there are non-Pate verdicts?
  (when-let [paatos (vif/latest-published-pate-verdict context)]
    (let [result (apply-mapping mapping (assoc context :paatos paatos))
          check  (required-filled mapping)]
      (when (check result)
        result))))

(def ^{:schema gen/BuildingPermitIssue} building-permit-issue-mapping
  ;; 1.2. RakentamislupaAsia
  ;; Lupapiste: Hakemus
  (into common-issue-mapping
        [{:ryhti       [:buildingPermitApplication]
          :lupapiste   []
          :->ryhti     (fn [_ context]
                         [(apply-mapping application/building-permit-application-mapping context)])
          :description "Rakentamislupahakemus, pakollinen, 1..*"}

         {:ryhti       [:decision]
          :lupapiste   []
          :nilable?    true
          :->ryhti     (fn [_ context]
                         (apply-mapping-to-verdict {:context context
                                                    :mapping decision/building-permit-decision-mapping}))
          :description "Rakentamislupapäätös, pakollinen, 1"}

         {:ryhti       [:extensionDecision]
          :lupapiste   []
          :->ryhti     (fn [_ _] [])
          :description "Jatkoaikapäätös, 0..*"}

         {:ryhti       [:changePermit]
          :lupapiste   []
          :->ryhti     (fn [_ _] [])
          :description "Muutoslupa, 0..*"}

         {:ryhti       [:constructionAction]
          :lupapiste   []
          :->ryhti     (fn [_ context]
                         (mapv (fn [doc]
                                 (apply-mapping construction-action-mapping (assoc context
                                                                                   :rakennuskohde doc
                                                                                   :operation-id (-> doc :schema-info :op :id))))
                               (domain/get-documents-by-name (:application context) "uusiRakennuskohde")))
          :description "Rakentamistoimenpide, pakollinen, 1..*"}

         {:ryhti       [:constructionProject]
          :lupapiste   []
          :->ryhti     (fn [_ context]
                         (apply-mapping construction-project-mapping context))
          :description "Rakentamishanke, 0..1"}

         {:ryhti       [:locationPermitObject]
          :lupapiste   []
          :->ryhti     (fn [_ _] [])
          :description "SijoittamislupaKohde, 0..*"}
         ]))

;; TODO:
;; 1.4. dvvPermitIssue - DVV:n lupaAsia

;; TODO
(def landscape-work-permit-issue-mapping
  "2.1. landscapeWorkPermitIssue - MaisematyölupaAsia"
  [])

(def ^{:schema gen/DeviationPermitIssue} deviation-permit-issue-mapping
  "3.1. DeviationPermitIssue - PoikkeamislupaAsia"
  (into common-issue-mapping
        [{:ryhti       [:deviationPermitApplication]
          :lupapiste   []
          :->ryhti     (fn [_ context]
                         ;; NOTE: Unlike building permit, there is only a single application
                         (apply-mapping application/deviation-permit-application-mapping context))
          :description "Poikkeamislupahakemus, pakollinen, 1"}

         {:ryhti       [:deviationPermitDecision]
          :lupapiste   []
          :->ryhti     (fn [_ context]
                         (apply-mapping-to-verdict {:context context
                                                    :mapping decision/deviation-permit-decision-mapping}))
          :nilable?    true
          :description "Poikkeamislupapäätös, pakollinen, 1"}

         {:ryhti       [:deviationObject]
          :lupapiste   []
          :->ryhti     (fn [_ _] [])
          :description "??"}

         ]))

;; TODO
(def ^{:schema gen/DemolitionPermitIssue} demolition-permit-issue-mapping
  "4.1. demolitionPermitIssue - PurkamislupaAsia"
  [])
