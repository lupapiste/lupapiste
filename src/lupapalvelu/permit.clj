(ns lupapalvelu.permit
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [fail]]
            [taoensso.timbre :as timbre :refer [errorf]]))

;;
;; Enum
;;

(defmacro defpermit [permit-name description]
  `(def ~permit-name ~(str description) ~(str permit-name)))

(defpermit R  "Rakennusluvat")
(defpermit YA "Yleisten alueiden luvat")
(defpermit Y  "Ymparistoluvat")
(defpermit P  "Poikkeusluvat")

;;
;; Helpers
;;

(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(not= % nil)]}
  (:permitType application))

;;
;; Validate
;;

(defn validate-permit-type-is-not [validator-permit-type]
  (fn [_ application]
    (let [application-permit-type (permit-type application)]
      (when (= (keyword application-permit-type) (keyword validator-permit-type))
        (fail :error.invalid-permit-type :permit-type validator-permit-type)))))

(defn validate-permit-type-is [validator-permit-type]
  (fn [_ application]
    (let [application-permit-type (permit-type application)]
      (when-not (= (keyword application-permit-type) (keyword validator-permit-type))
        (fail :error.invalid-permit-type :permit-type validator-permit-type)))))
