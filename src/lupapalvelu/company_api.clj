(ns lupapalvelu.company-api
  (:require [lupapalvelu.core :refer [ok fail]]
            [lupapalvelu.action :refer [defquery defcommand]]))

;;
;; Company API:
;;

; Validator: check is user is either :admin or user belongs to requested company

(defn validate-user-is-admin-or-member [{{:keys [role company]} :user {requested-company :company} :data}]
  (if-not (or (= role :admin)
              (= company requested-company))
    (fail "forbidden")))

;;
;; Basic API:
;;

(defquery company
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-member]
   :parameters [company]}
  (ok :company {:foo "bar" :id company}))

