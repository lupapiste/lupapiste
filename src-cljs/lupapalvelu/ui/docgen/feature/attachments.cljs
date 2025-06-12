(ns lupapalvelu.ui.docgen.feature.attachments
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]))

(defn attachment->clj [^js attachment]
  (let [;; Attachment is usually an obserable, so unwrap into a regular JS object.
        attachment (.unwrap js/ko attachment)]
    {:id (.-id attachment)
     :target (when-let [t (.-target attachment)]
               {:type (.-type t)
                :id (.-id t)})
     :latestVersion {:filename (some-> (.-latestVersion attachment) .-filename)}}))

(rf/reg-event-db ::new-data
  (fn [db [_ new-data]]
    (assoc-in db [::attachments :data] new-data)))

;; Store KO objects outside the app-db. Otherwise we re-frame runs into problems
;; comparing app-db values when advanced optimizations are used.
(def *ko-state (atom {}))

(defn- cleanup
  []
  (let [{:keys [^js computed ^js subscription]} (deref *ko-state)]
    (when computed
      (.dispose computed))
    (when subscription
      (.dispose subscription))
    (reset! *ko-state {})))

(defn initialize-attachments-subscription
  []
  (cleanup)
  ;; service might not exist, e.g with bulletin views
  (when-let [^js att (.. js/lupapisteApp -services -attachmentsService)]
    (let [;; Subscribing to ObserableArray directly would only follow changes
          ;; like adding or removing items.
          ;; This computed also follow each item in the array (unwrap in the ->clj).
          ;; This might not be the most performant solution...?
          o (.pureComputed js/ko (fn [] (into [] (map attachment->clj (.attachments att)))))
          s (.subscribe o (fn [new-value]
                                  (>evt [::new-data new-value])))]
      (reset! *ko-state {:computed     o
                         :subscription s})
      (>evt [::new-data (.peek o)]))))

(rf/reg-fx ::initialize-attachments-subscription
  (fn []
    (initialize-attachments-subscription)))

(rf/reg-fx ::cleanup-attachments-subscription
  cleanup)

(defn on-app-loaded [_ [_ {:keys [_app]}]]
  {:fx [[::initialize-attachments-subscription]]})

(defn on-unload [{:keys [db]}]
  {:fx [[::cleanup-attachments-subscription]]
   :db (dissoc db ::attachments)})

(rf/reg-sub ::feature-data
  :-> ::attachments)

(rf/reg-sub ::attachments
  :<- [::feature-data]
  :-> :data)

(rf/reg-sub ::get-by-target
  :<- [::attachments]
  (fn [attachments [_ target-type target-id]]
    ;; NOTE: Could also create index by target type & id sub and then get from that?
    (some (fn [a]
            (when (and (= target-type (:type (:target a)))
                       (= target-id (:id (:target a))))
              a))
          attachments)))
