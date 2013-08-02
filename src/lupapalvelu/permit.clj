(ns lupapalvelu.permit
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [fail]]))

;;
;; Enum
;;

(defmacro defpermit [permit-name description]
  `(def ~(-> permit-name name symbol) ~(str description) ~(keyword permit-name)))

(defpermit :R  "Rakennusluvat")
(defpermit :YA "Yleisten alueiden luvat")
(defpermit :Y  "Ymparistoluvat")
(defpermit :P  "Poikkeusluvat")

;;
;; Validate
;;

(defn validate-permit-type-is-not [ptype]
  (fn [_ application]
    (let [application-permit-type (domain/permit-type application)]
      (when (= (keyword application-permit-type) (keyword ptype))
        (fail :error.invalid-permit-type :permit-type ptype)))))

(defn validate-permit-type-is [ptype]
  (fn [_ application]
    (let [application-permit-type (domain/permit-type application)]
      (when-not (= (keyword application-permit-type) (keyword ptype))
        (fail :error.invalid-permit-type :permit-type ptype)))))
