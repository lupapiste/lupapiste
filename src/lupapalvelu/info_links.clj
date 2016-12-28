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
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as org]))

;; info-link = {linkId mongoid, :text text, :url url, :modified timestamp, :owner user-id}

(defn can-add-links?
  "Check if the user can add new info links"
  [app user]
  (or (usr/user-is-authority-in-organization? user (:organization app))
      (let [statement-givers
            (filter (fn [auth] (= (:role auth) "statementGiver")) (:auth app))]
         (contains? (set (map :id statement-givers)) (:id user)))))

(def can-reorder-links?
   can-add-links?)

(defn- info-links [app]
  (or (:info-links app) []))

(defn- infolinks-last-read [app user]
  (get (:_info-links-seen-by app) (keyword (:id user)) 0))

(defn- take-first [pred lst def]
  (loop [lst lst]
    (cond
      (empty? lst) def
      (pred (first lst)) (first lst)
      :else (recur (rest lst)))))

(defn- pick-link [app link-id]
  (take-first (fn [x] (= (:linkId x) link-id)) (info-links app) {}))

(defn can-edit-link?
  "Check if user can edit a specific info link"
  [app linkid user]
  (or (usr/user-is-authority-in-organization? user (:organization app))
      (= (:id user) (:owner (pick-link app linkid)))))

(defn- order-links [links ids]
  (map (fn [id] (take-first (fn [x] (= (:linkId x) id)) links nil)) ids))

(defn- update-info-links! [app links]
  (mongo/update-by-id :applications (:id app) {$set {:info-links links}}))

(defn info-links-with-flags
  "get the info links and flags whether they are editable and already seen by the given user"
  [app user]
  (let [last-read (infolinks-last-read app user)]
    (map
      (fn [link]
        (-> link
          (assoc :isNew (< last-read (:modified link)))
          (assoc :canEdit (can-edit-link? app (:linkId link) user))
          (dissoc :modified)
          (dissoc :owner)))
      (info-links app))))

(defn add-info-link!
  "add a new info link"
  [app text url timestamp user]
  (let [links (info-links app)
        new-id (mongo/create-id)
        link-node {:linkId new-id :text text :url url :modified timestamp :owner (:id user)}]
    (update-info-links! app (concat links (list link-node)))
    new-id))

(defn delete-info-link!
  "remove an info link"
  [app link-id]
  (let [links (info-links app)
        new-links (remove (fn [x] (= link-id (:linkId x))) (info-links app))]
     (if (= (count links) (count new-links))
       false
       (do
         (update-info-links! app new-links)
         true))))

(defn update-info-link!
  "update and existing info link"
  [app link-id text url timestamp]
  (let [links (info-links app)
        link-node {:linkId link-id :text text :url url :modified timestamp}
        new-links (map (fn [x] (if (= (:linkId x) link-id) link-node x)) links)]
    (if (= links new-links)
      false
      (do
        (update-info-links! app new-links)
        link-id))))

(defn reorder-info-links!
  "set the order of info links"
  [app link-ids]
  (let [links (info-links app)]
    ;; depending on UI needs could just sort the intersection
    (if (= (set link-ids) (set (map :linkId links)))
      (do (update-info-links! app (order-links links link-ids))
          true)
      false)))

;;
;; Organization links
;;

(defn organization-links
  "The fallback language for links is Finnish."
  [org-id user-id lang]
  (let [user (usr/get-user-by-id user-id)]
    (for [{:keys [url name modified]} (-> org-id
                                          org/get-organization
                                          :links)]
      {:url ((keyword lang) url (:fi url ""))
       :text ((keyword lang) name (:fi name ""))
       ;; Unseen links without timestamps are considered new.
       :isNew (> (or modified 1)
                 (get-in user [:seen-organization-links (keyword org-id)] 0))})))

(defn mark-seen-organization-links
  "For organization links the mark-seen information is stored under user."
  [org-id user-id created]
  (mongo/update-by-id :users
                      user-id
                      {$set {(str "seen-organization-links." org-id) created}}))
