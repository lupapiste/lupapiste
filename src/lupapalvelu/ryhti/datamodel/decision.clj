(ns lupapalvelu.ryhti.datamodel.decision
  (:require [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.datamodel.attachment :refer [attachment-mapping]]
            [lupapalvelu.ryhti.datamodel.common :refer [->iso-date apply-mapping required-filled]]
            [lupapalvelu.ryhti.datamodel.party :refer [engaging-party-mapping]]
            [sade.util :as util]))

(def ^:private common-mapping
  [{:ryhti       [:publicNoticeDate]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (-> verdict vc/bulletin-publish-date ->iso-date))
    :description "Julkaisemispäivämäärä, 0..1"}

   {:ryhti       [:engagingParty]
    ;; TODO: Is this correct? paatoksen-toimitus-rakval would be another option?
    :lupapiste   [:documents-by-subtype :hakija]
    :->ryhti     (fn [hakija-docs context]
                   (->> hakija-docs
                        (mapv (fn [hakija]
                                (apply-mapping engaging-party-mapping (assoc context :hakija hakija))))))
    :description "Hankkeeseen ryhtyvä, 1..*"}

   {:ryhti       [:relatedApplication]
    :lupapiste   nil
    :->ryhti     (fn [_ _] nil)
    :nilable?    true
    :description "Liittyvä hakemus, 0..1"}

   {:ryhti       [:decisionText]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _]
                   {:fin (vc/verdict-text verdict)})
    :description "Päätösteksti, 1"}

   {:ryhti       [:lifeCycleState]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO: Koodisto
                   ;; TODO: Value
                   ;; Myönnetty
                   "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11")
    :description "Elinkaaritila, 1"}

   {:ryhti       [:decisionMakerFirstName]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (vc/decision-maker-first-name verdict))
    :nilable?    true
    :description "Päättäjän etunimi, 0..1"}

   {:ryhti       [:decisionMakerName :fin]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (vc/decision-maker-name verdict))
    :description "Päättäjän nimi, 1"}

   {:ryhti       [:decisionMakerType]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _]
                   (if (vc/board-verdict? verdict)
                     ;; Monijäseninen päätöksentekoelin
                     "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"
                     ;; Viranhaltija
                     "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01"))
    :description "PäättäjänLaji, 1"}

   {:ryhti       [:decisionDate]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (-> verdict vc/verdict-date ->iso-date))
    :description "Päätöspäivämäärä, 1"}

   {:ryhti       [:dateOfDecision]
    ;; FIXME: Same as the previous?
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (-> verdict vc/verdict-date ->iso-date))
    :description "Antopäivämäärä, 1"}

   {:ryhti       [:dateOfValidityOfDecision]
    :lupapiste   [:paatos]
    :->ryhti     (fn [verdict _] (-> verdict vc/lainvoimainen-date ->iso-date))
    :nilable?    true
    :description "Lainvoimaisuuspäivämäärä, 1"}

   {:ryhti       [:decisionDocument]
    :lupapiste   nil
    :->ryhti     (fn [_ {:keys [paatos application]
                         :as   context}]
                   (let [attachment-id (-> paatos :published :attachment-id)
                         attachment    (util/find-by-id attachment-id (:attachments application))
                         result        (apply-mapping attachment-mapping (assoc context :attachment attachment))
                         check         (required-filled attachment-mapping)]
                     (when (check result)
                       result)))
    :nilable?    true
    :description "Liiteasiakirja, 1"}

   ;; TODO: missing (common) Ryhti decision fields

   #_{:ryhti       [:acceptedDeviation]
      :lupapiste   nil
      :description "Myönnetty poikkeaminen, 0..*"}

   #_{:ryhti       [:permitRegulation]
      :lupapiste   [:paatos :ryhti :permitRegulationKeys]
      :description "Lupamääräys, 0..*"}

   #_{:ryhti       [:guidingStatute]
      :lupapiste   nil
      :description "Säädösviite, 0..*"}

   #_{:ryhti       [:relatedPermit]
      :lupapiste   nil
      :description "Liittyvälupa, 0..*"}

   #_{:ryhti       [:decisionArticle]
      :lupapiste   nil ;; TODO
      :description "Päätöspykälä, 0..1"}])

(def ^{:schema gen/BuildingPermitDecision} building-permit-decision-mapping
  ;; 1.6. Rakentamislupapäätös
  (into common-mapping
        [{:ryhti     [:buildingPermitDecisionKey]
          :lupapiste [:paatos :ryhti :buildingPermitDecisionKey]
          :required? true}

         {:ryhti       [:permitApplicationType]
          :lupapiste   nil
          :->ryhti     (fn [_ _]
                         ;; TODO: Koodisto
                         ;; TODO: Value
                         ;; Sekä sijoittamis- että toteuttamislupa
                         "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01")
          :description "Lupatyyppi"}

         {:ryhti       [:constructionToBeStartedBy]
          :lupapiste   [:paatos]
          :->ryhti     (fn [verdict _] (-> verdict vc/aloitettava-date ->iso-date))
          :description "Rakennustyöt aloitettava viimeistään, 1"}

         {:ryhti       [:constructionToBeCompletedBy]
          :lupapiste   [:paatos]
          :->ryhti     (fn [verdict _] (-> verdict vc/voimassa-date ->iso-date))
          :description "Rakennustyöt valmistuttava viimeistään, 1"}

         #_{:ryhti       [:constructionToBeStartedByExtension]
          :lupapiste   nil
          :description "Rakennustyöt aloitettava viimeistään, jatkoa, 0..1"}

         #_{:ryhti       [:constructionToBeCompletedByExtension]
          :lupapiste   nil
          :description "Rakennustyöt valmistuttava viimeistään, jatkoa, 1..1"}

         ]))

(def ^{:schema gen/DeviationPermitDecision} deviation-permit-decision-mapping
  ;; 3.3. deviationPermitDecision - Poikkeamislupapäätös
  (into common-mapping
        [{:ryhti     [:deviationPermitDecisionKey]
          ;; NOTE: Pate always generates a `:buildingPermitDecisionKey`, regardless
          ;; of the permit type. Perhaps it's ok to use the same key for P permits too..
          :lupapiste [:paatos :ryhti :buildingPermitDecisionKey]
          :required? true}

         ;; TODO
         {:ryhti       [:decisionInForceUntil]
          :lupapiste   []
          :->ryhti     (fn [_ _] "")}

         ;; [:expiryDate {:optional true} [:maybe :string]]
         ]))
;; TODO
;; 2.3. landscapeWorkPermitDecision - Maisematyölupapäätös
