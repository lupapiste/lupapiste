(ns lupapalvelu.info-links
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! now]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]))

(defn last-seen-date [app user]
   ;; take from app -> _seen_info-links_user -> timestamp
   42)

(defn mark-links-seen! [command]
   (update-application command
      {$set (app/mark-collection-seen-update (:user command) (now) "info-links")}))
   
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

(defn update-info-links! [app links]
   (mongo/update-by-id :applications (:id app) {$set {:info-links links}}))

(defn add-info-link! [app text url]
   (let [links (info-links app)
         new-id (free-link-id links)
         foo (println new-id)
         link-node {:linkId new-id :text text :url url :modified (now)}]
      (update-info-links! app (cons link-node links))
      new-id))

(defn delete-info-link! [app link-id]
   (update-info-links! app
      (remove (fn [x] (= link-id (:linkId x))) (info-links app))))

(defn update-info-link! [app link-id text url]
   (let [links (info-links app)
         link-node {:linkId link-id :text text :url url :modified (now)}
         new-links (map (fn [x] (if (= (:linkId x) link-id) link-node x)) links)]
        (update-info-links! app new-links)
        link-id))

(defn reorder-info-links! [app link-ids]
   (let [links (info-links app)]
      ;; depending on UI needs could just sort the intersection
      (if (= (set link-ids) (set (map :linkId links)))
         (do (update-info-links! app (order-links links link-ids))
             true)
         false)))

