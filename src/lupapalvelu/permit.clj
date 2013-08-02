(ns lupapalvelu.permit
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [fail]]
            [clojure.tools.logging :refer :all]))

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

(errorf "***** permit-type returns always '%s' if not set. Should be set!" R)
(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(not= % nil)]}
  (or (:permitType application) R))

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
