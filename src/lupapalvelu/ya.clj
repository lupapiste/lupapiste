(ns lupapalvelu.ya
  "Yleiset alueet"
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as op]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn sijoittaminen? [{:keys [permitSubtype permitType]}]
  (and (= permit/YA permitType) (true? (some #(= (keyword permitSubtype) %) op/ya-sijoituslupa-subtypes))))

(defn- digging-permit? [{:keys [permitType primaryOperation]}]
  (and (= permit/YA permitType)
       (->> (op/get-operation-metadata (:name primaryOperation) :subtypes)
            (some #(= :tyolupa %))
            (true?))))

(def agreement-subtype "sijoitussopimus")
(assert (some #{(keyword agreement-subtype)} op/ya-sijoituslupa-subtypes) (str "agreement-subtype '" agreement-subtype "' is unknown"))

(defn agreement-subtype? [app] (= (:permitSubtype app) agreement-subtype))

(defn- find-verdict [{:keys [verdicts pate-verdicts]} verdict-id]
  (util/find-by-id verdict-id (concat verdicts pate-verdicts)))

(defn check-ya-sijoituslupa-subtype [{{:keys [verdictId]} :data app :application}]
  (when (and (not (ss/blank? verdictId)) (sijoittaminen? app))
    (when-let [verdict (find-verdict app verdictId)]
      (when (and (vc/contract? verdict) (not (agreement-subtype? app)))
        (fail :error.ya-sijoituslupa-invalid-subtype)))))

(defn check-ya-sijoitussopimus-subtype [{{:keys [verdictId]} :data app :application}]
  (when (and (not (ss/blank? verdictId)) (sijoittaminen? app))
    (when-let [verdict (find-verdict app verdictId)]
      (when (and (not (vc/contract? verdict)) (agreement-subtype? app))
        (fail :error.ya-sijoitussopimus-invalid-subtype)))))

(defn- validate-link-agreements-state [link-permit]
  (when-not (app/verdict-given? link-permit)
    (fail :error.link-permit-app-not-in-post-verdict-state)))

(defn- validate-link-agreements-signature [{:keys [verdicts pate-verdicts] :as app}]
  (when (and (agreement-subtype? app)   ;; If agreement, signatures must exist
             ;; Each published contract has initially one signature
             (empty? (filter #(> (count (:signatures %)) 1)
                             (concat verdicts pate-verdicts))))
    (fail :error.link-permit-app-not-signed)))

(defn validate-digging-permit [application]
  (when (digging-permit? application)
    (let [link        (some #(when (= (:type %) "lupapistetunnus") %) (:linkPermitData application))
          link-permit (when link
                        (domain/get-application-no-access-checking (:id link)
                                                                   [:state :verdicts :pate-verdicts
                                                                    :permitType :permitSubtype :primaryOperation]))]
      (when link-permit
        (or
          (validate-link-agreements-state link-permit)
          (validate-link-agreements-signature link-permit))))))
