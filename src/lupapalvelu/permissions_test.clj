(ns lupapalvelu.permissions-test
  (:require [sade.util :refer [find-by-key find-by-id]]))

;; Roles -> permissions

(def permissions-for-roles
  {:global {:applicant {:application/create true}
            :authority {:application/create true
                        :application/submit true}}
   :application {:submitter {:application/read   true
                             :application/modify true
                             :application/submit true}
                 :writer    {:application/read   true
                             :application/modify true
                             :application/submit true}
                 :reader    {:application/read true}}})

;; Applications

(def dummy-apps
  [{:id   "LP-123"
    :auth [{:role :submitter
            :id   "subi"}
           {:role :writer
            :id   "kirjailija"}
           {:role :reader
            :id   "lukija"}]
    :attachments []}])

;; Command permissions

(def create-application-permissions
  {:targets [{:type        :global
              :permissions :application/create}]})

(def submit-application-permissions
  {:targets [{:type        :application
              :parameter   :id
              :permissions :application/submit}]})


;; pipeline

(defn merge-permissions [& permissions]
  (apply merge permissions))

(defn fetch-target-data [ctx {:keys [type parameter]}]
  (let [value (-> ctx :parameters parameter)]
    (case type
     :application (find-by-key parameter value dummy-apps)
     :attachment  (->> (get-in ctx [:target-data :application :attachments])
                       (find-by-key parameter value)))))

(defn role->permissions [type role]
  (get-in permissions-for-roles [type role]))

(defn fetch-target-permissions [{:keys [user] :as ctx} {:keys [type]}]
  (let [target-data (-> ctx :target-data type)]
    (println target-data)
    (case type
      :application (->> target-data :auth (find-by-id (:id user)) :role (role->permissions type))
      :attachment  (->> target-data :auth (find-by-id (:id user)) :role (role->permissions type))
      :user        "jos user.id on target-data.id, niin write")))

(def target-permissions-scope
  {:global      [:user :organization]
   :application [:user :organization :application]
   :attachment  [:user :organization :application :attachment]})

(defn assoc-target-data [ctx {:keys [type] :as target}]
  (as-> ctx $
    (assoc-in $ [:target-data type] (fetch-target-data $ target))
    (assoc-in $ [:permissions type] (fetch-target-permissions $ target))))

(defn merge-permissions-for-target [ctx {:keys [type] :as target}]
  (let [permissions-scope (target-permissions-scope type)]
    (reduce (fn [acc permission-key]
              (merge acc
                     (get-in ctx [:permissions permission-key])))
            {}
            permissions-scope)))

(defn validate-target [ctx {:keys [permissions] :as target}]
  ;; ignore states for now
  (if-not (get (or (some-> (merge-permissions-for-target ctx target) keys set) #{}) permissions)
    (assoc ctx :error {:missing-permissions permissions})
    ctx))

(defn initial-context [user organization parameters]
  {:user user
   :organization organization
   :permissions  {:user (get-in permissions-for-roles
                                [:global (:role user)])
                  :organization {}}
   :parameters parameters})

;; Alusta validointikontekstiB
;; Validointilooppi:
;; - Hae target
;; - Kasaa kayttajan oikeudet targetille
;; - Validoi muut targetin kentat (:props jne)
;; - Lisaa target oikean avaimen alle validointikontekstiin

(defn fetch-and-validate-target [ctx target]
  (if (:error ctx)
    ctx
    (-> ctx
       (assoc-target-data target)
       (validate-target   target))))

(defn authorize-command [user command parameters]
  (reduce fetch-and-validate-target
          (initial-context user {} parameters)
          (:targets command)))
