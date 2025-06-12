(ns lupapalvelu.ui.dmcity.shared
  (:require [re-frame.core :as rf]
            [sade.util :as util]))

(def permit-types #{:R :P :YA :KT :MM :YI :YM :MAL :VVVL :YL})

(defn flag-loc [flag]
  (cond->> flag
    (not (permit-types flag)) (util/kw-path :dmcity.flag)))

(defn update-data [db f & args]
  (update db ::data #(apply f % args)))

(defn assoc-data [db & args]
  (apply update-data db assoc args))

(defn dissoc-data [db & args]
  (apply update-data db dissoc args))

(defn reset-data [db & args]
  (assoc db ::data (apply hash-map args)))

(defn get-data [db & args]
  (get-in db (cons ::data args)))

(defn access [k]
  #(get-data % k))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-db
  ::clear-data
  (fn [db _]
    (reset-data db)))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::organization-id
  :-> (access :organization-id))

(rf/reg-sub
  ::dmcity-product-loc
  :<- [::organization-id]
  (fn [organization-id]
    (util/kw-path :dmcity
                  (if (re-find #"^092-" (or organization-id ""))
                    :matti :tiera-city))))
