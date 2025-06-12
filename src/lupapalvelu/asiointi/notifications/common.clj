(ns lupapalvelu.asiointi.notifications.common
  (:require [lupapiste-commons.states :as states]
            [schema.core :as sc :refer [defschema]]))

(def all-application-states
  "Set of all known application states, regardless of the permit type."
  (reduce (fn [acc [source-state target-states]]
            (-> acc
                (conj source-state)
                (into target-states)))
          #{}
          states/all-transitions-graph))

(def notification-types #{:application-state-change})

(defschema NotificationType (apply sc/enum notification-types))
