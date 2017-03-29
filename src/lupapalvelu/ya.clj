(ns lupapalvelu.ya
  "Yleiset alueet common"
  (:require [monger.operators :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.operations :as op]))

(defn sijoitus? [{:keys [permitSubtype permitType]}]
  (and (= "YA" permitType) (true? (some #(= (keyword permitSubtype) %) op/ya-sijoituslupa-subtypes))))

(def agreement-subtype "sijoitussopimus")

(defn update-ya-sijoitus-subtype!
  "When publishing YA verdict with 'agreement' set to true, permitSubtype needs to be set in case of sijoituslupa.
  If YA sijoituslupa and verdict is agreement, sets correct permitSubtype and updates command with updated application.
  Else returns command."
  [{app :application :as command} verdict]
  (if-not (sijoitus? app)
    command
    (if (and (:sopimus verdict) (not= (:permitSubtype app) agreement-subtype))
      (do (action/update-application command {$set {:permitSubtype agreement-subtype}})
          (assoc-in command [:application :permitSubtype] agreement-subtype))
      command)))
