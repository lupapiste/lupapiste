(ns lupapalvelu.ya
  "Yleiset alueet"
  (:require [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]))

(defn sijoittaminen? [{:keys [permitSubtype permitType]}]
  (and (= permit/YA permitType) (true? (some #(= (keyword permitSubtype) %) op/ya-sijoituslupa-subtypes))))

(def agreement-subtype "sijoitussopimus")
(defn agreement-subtype? [app] (= (:permitSubtype app) agreement-subtype))

(defn check-ya-sijoituslupa-subtype [{{:keys [verdictId]} :data app :application}]
  (when (and (not (ss/blank? verdictId)) (sijoittaminen? app))
    (when-let [verdict (util/find-by-id verdictId (:verdicts app))]
      (when (and (:sopimus verdict) (not (agreement-subtype? app)))
        (fail :error.ya-sijoituslupa-invalid-subtype)))))

(defn check-ya-sijoitussopimus-subtype [{{:keys [verdictId]} :data app :application}]
  (when (and (not (ss/blank? verdictId)) (sijoittaminen? app))
    (when-let [verdict (util/find-by-id verdictId (:verdicts app))]
      (when (and (not (:sopimus verdict)) (agreement-subtype? app))
        (fail :error.ya-sijoitussopimus-invalid-subtype)))))

(defn authority-only [{app :application user :user}]
  (when (and (= (:permitType app) permit/YA)
             (not (usr/user-is-authority-in-organization? user (:organization app))))
    (fail :error.ya-subtype-change-authority-only)))
