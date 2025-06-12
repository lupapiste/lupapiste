(ns lupapalvelu.ryhti.datamodel.project
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.ryhti.datamodel.common :refer [always-nil apply-mapping required-filled]]
            [lupapalvelu.ryhti.datamodel.party :refer [foreman-mapping planner-mapping]]))

(def construction-project-mapping
  ;; 1.16. Rakentamishanke
  [{:ryhti     [:constructionProjectKey]
    :lupapiste [:application :ryhti :constructionProjectKey]}

   {:ryhti       [:descriptionOfConstructionProject]
    :lupapiste   []
    ;; LanguageString
    :->ryhti     always-nil
    :description "HankkeenKuvaus, 0..1"}

   {:ryhti       [:startDate]
    :lupapiste   []
    :->ryhti     always-nil
    :description "Aloittamispäivämäärä, 0..1"}

   {:ryhti       [:endDate]
    :lupapiste   []
    :->ryhti     always-nil
    :description "Päättymispäivämäärä, 0..1"}

   {:ryhti       [:planner]
    :lupapiste   []
    :->ryhti     (fn [_ {:keys [application] :as context}]
                   (into []
                         (comp (map (fn [doc]
                                      (apply-mapping planner-mapping (assoc context :suunnittelija doc))))
                               ;; TODO: Filter designer-document->approval-state
                               ;; at least if use-designer-approval feature flag is used?
                               (filter (required-filled planner-mapping)))
                         (concat (domain/get-documents-by-name application "paasuunnittelija")
                                 (domain/get-documents-by-name application "suunnittelija"))))
    :description "Suunnittelija, 0..*"}

   {:ryhti       [:foreman]
    :lupapiste   []
    :->ryhti     (fn [_ {:keys [application] :as context}]
                   (into []
                         (comp (map (fn [doc]
                                      (apply-mapping foreman-mapping (assoc context :tyonjohtaja doc))))
                               (filter (required-filled foreman-mapping)))
                         (domain/get-documents-by-name application "tyonjohtaja-v3")))
    :description "Työnjohtaja, 0..*"}

   {:ryhti       [:inspection]
    :lupapiste   []
    :->ryhti     always-nil
    :description "Katselmus, 0..*"}])
