(ns lupapalvelu.application-utils)

(defn location->object [application]
  (let [[x y] (:location application)]
    (assoc application :location {:x x :y y})))
