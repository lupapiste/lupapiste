(ns lupapalvelu.company-api
  (:require [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.company :as c]
            [lupapalvelu.user :as u]))

;;
;; Company API:
;;

; Validator: check is user is either :admin or user belongs to requested company

(defn validate-user-is-admin-or-company-member [{{:keys [role company]} :user {requested-company :company} :data}]
  (if-not (or (= role :admin)
              (= (:id company) requested-company))
    (fail "forbidden")))

;;
;; Basic API:
;;

(defquery company
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-company-member]
   :parameters [company]}
  [{{:keys [users]} :data}]
  (ok :company (c/find-company! {:id company})
      :users   (and users (u/get-users {:company.id company}))))

(defcommand company-update
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-company-member]
   :parameters [company updates]}
  (ok :company (c/update-company! company updates)))

(defcommand company-user-update
  {:roles [:anonymous]
   :parameters [user-id op value]}
  [{caller :user}]
  (let [target-user (u/get-user-by-id! user-id)]
    (if-not (or (= (:role caller) "admin")
                (and (= (get-in caller [:company :role])
                        "admin")
                     (= (get-in caller [:company :id])
                        (get-in target-user [:company :id]))))
      (fail! :forbidden))
    (c/update-user! user-id (keyword op) value)
    (ok)))

(defcommand company-add-user
  {:roles [:anonymous]
   :parameters [firstName lastName email admin]}
  [{user :user}]
  (if (or (= (:role user) :admin)
          (= (get-in user [:company :role]) :admin))
    )
  (println "company-add-user:" user firstName lastName email admin)
  (ok))
