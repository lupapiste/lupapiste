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
  (if-not (or (= role "admin")
              (= (:id company) requested-company))
    (fail "forbidden")))

(defn validate-user-is-admin-or-company-admin [{user :user}]
  (if-not (or (= (get user :role) "admin")
              (= (get-in user [:company :role]) "admin"))
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

(defquery company-search-user
  {:roles [:anonymous]
   :input-validators [validate-user-is-admin-or-company-admin]
   :parameters [email]}
  [{caller :user}]
  (println "company-search-user:" caller)
  (let [user (u/find-user {:email email})]
    (cond
      (nil? user)
      (ok :result :not-found)

      (get-in user [:company :id])
      (ok :result :already-in-company)

      :else
      (ok :result :can-invite))))

(defcommand company-add-user
  {:roles [:anonymous]
   :parameters [firstName lastName email admin]}
  [{user :user}]
  (if-not (or (= (:role user) "admin")
              (= (get-in user [:company :role]) "admin"))
    (fail! :forbidden))
  (c/add-user! {:firstName firstName :lastName lastName :email email}
               (c/find-company-by-id (-> user :company :id))
               (if admin :admin :user))
  (ok))
