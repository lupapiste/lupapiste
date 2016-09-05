(ns lupapalvelu.info-links
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]))

;; stub
(defn last-seen-date [app user]
   42)

(defn- take-first [pred lst def]
   (loop [lst lst]
      (cond
         (empty? lst) def
         (pred (first lst)) (first lst)
         :else (recur (rest lst)))))

(defn- free-link-id [links]
   (+ 1 (reduce max 0 (map :linkId links))))

(defn- order-links [links ids]
   (map (fn [id] (take-first (fn [x] (= (:linkId x) id)) links nil)) ids))

(defn info-links [app]
   (or (:info-links app) []))

(defn get-info-link [app link-id]
   (let [links (:info-links app)]
      (take-first (fn [link] (= link-id (:id link))) (info-links app) nil)))

(defn add-info-link! [app text url]
   (let [links (info-links app)
         new-id (free-link-id links)
         foo (println new-id)
         link-node {:linkId new-id :text text :url url}]
      (mongo/update-by-id :applications (:id app) 
         {$set {:info-links (cons link-node links)}})
      new-id))

(defn update-info-links! [app links]
   (mongo/update-by-id :applications (:id app) {$set {:info-links links}}))

(defn delete-info-link! [app link-id]
   (update-info-links! app
      (remove (fn [x] (= link-id (:linkId x))) (info-links app))))

(defn update-info-link! [app link-id text url]
   (let [links (info-links app)
         link-node {:linkId link-id :text text :url url}
         new-links (map (fn [x] (if (= (:linkId x) link-id) link-node x)) links)]
      (if (= links new-links)
         false
         (do
            (update-info-links! app new-links)
            true))))

(defn reorder-info-links! [app link-ids]
   (let [links (info-links app)]
      (if (= (set link-ids) (set (map :linkId links)))
         (do
            (update-info-links! app
               (order-links links link-ids))
            true)
         false)))
         
; test app
; (defn foo [] (domain/get-application-no-access-checking "LP-753-2016-90001"))

