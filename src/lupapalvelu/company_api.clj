(ns lupapalvelu.company-api
  (:require [lupapalvelu.core :refer [ok fail]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.company :as c]
            [lupapalvelu.user :as u]))

;;
;; Company API:
;;

; Validator: check is user is either :admin or user belongs to requested company

(defn validate-user-is-admin-or-member [{{:keys [role company]} :user {requested-company :company} :data}]
  (if-not (or (= role :admin)
              (= (:id company) requested-company))
    (fail "forbidden")))

;;
;; Basic API:
;;

(defquery company
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-member]
   :parameters [company]}
  (ok :company (c/find-company! {:id company})))

(defcommand company-update
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-member]
   :parameters [company updates]}
  (ok :company (c/update-company! company updates)))

(defquery company-users
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-member]
   :parameters [company]}
  (ok :company-users (u/get-users-by-company company)))

(defcommand company-user
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-member]
   :parameters [company user-id]}
  (ok :company-users (u/get-users-by-company company)))
