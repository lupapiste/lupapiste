(ns lupapalvelu.ui.location.model-utils
  "BIM model (IFC file) handling utils"
  (:require [clojure.set :as set]
            [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :refer [loc]]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::can-upload-and-modify?
  :<- [:auth/application?
       :init-resumable-upload-for-application
       :link-resumable-upload-for-application
       :set-linked-application-file-metadata-field]
  :-> identity)

(rf/reg-event-fx ::fetch-linked-file-metadatas
  (fn [{db :db} [_]]
    {:action/query {:name    :fetch-linked-file-metadatas-for-application
                    :params  {:id          (router/path-parameter db :app-id)
                              :target-type "bim-model-file"}
                    :success [::fetch-linked-file-metadatas.success]}}))

(rf/reg-event-db ::fetch-linked-file-metadatas.success
  (fn [db [_ {:keys [data]}]]
    (assoc db ::linked-file-metadatas data)))

(rf/reg-event-fx ::update-file-metadata
  (fn [{db :db} [_ {:keys [target-entity]} field value]]
    {:db             (update-in db [::linked-file-metadatas]
                                (fn [all-files]
                                  (mapv
                                    #(if (= (:target-entity %) target-entity)
                                       (assoc-in % [:metadata field] value)
                                       %) all-files)))
     :action/command {:name    :set-linked-application-file-metadata-field
                      :params  {:id                 (:application-id target-entity)
                                :linked-file-target target-entity
                                :field              field
                                :value              value}
                      :success [::update-file-metadata.success]}}))

(rf/reg-event-fx ::update-file-metadata.success
  (fn [_ _]))

(rf/reg-sub ::operation-files
  (fn [{files ::linked-file-metadatas} [_ operation-id]]
    (some->> (seq files)
             (filter #(= operation-id (get-in % [:target-entity :operation-id])))
             (sort-by (comp :uploaded-at first :versions)))))

(rf/reg-sub ::phase-files
  (fn [[_ operation-id _selected-phase]]
    (rf/subscribe [::operation-files operation-id]))
  (fn [files [_ _operation-id selected-phase]]
    (filter (fn [{{:keys [model-phase]} :metadata}]
              (or (= selected-phase (keyword model-phase))
                  (and (= selected-phase :submission-time)
                       (nil? model-phase))))
            files)))

(rf/reg-sub ::phase-file-count
  (fn [[_ operation-id model-phase]]
    (rf/subscribe [::phase-files operation-id model-phase]))
  (fn [files _]
    (count files)))

;; Utility functions

(defn model-type-dropdown-items []
  (map (fn [type]
         {:value (name type)
          :text  (loc (util/kw-path :application.bim.model-type type))})
       bim-schema/model-types))

(defn ->download-url [file-id & {:keys [file-version-id]}]
  (str "/api/raw/linked-file-version?file-id=" file-id
       "&download=true"
       (when file-version-id (str "&file-version-id=" file-version-id))))

(defn format-fi-datetime
  "Returns a map with keys :year, :month, and :day from the given ISO 8601 date string."
  [date]
  (.format (js/moment date) "D.M.YYYY HH.mm"))

(def format-version (partial util/format-keyed-map-as-list "." [:major :minor]))
(def format-user-name (partial util/format-keyed-map-as-list " " [:firstName :lastName]))

(rf/reg-event-fx ::fetch-ifc-data-availability
  (fn [{db :db} [_ pending-version-ids]]
    (when-let [app-id (router/path-parameter db :app-id)]
      (cond-> {:action/query {:name    :ifc-data-file-version-ids
                              :params  {:id app-id}
                              :success [::fetch-ifc-data-availability.success pending-version-ids]}
               :db           db}
        pending-version-ids (update-in [:db ::pending-ifc-version-ids] set/union (set pending-version-ids))))))

(rf/reg-event-fx ::fetch-ifc-data-availability.success
  (fn [{db :db} [_ {:keys [file-version-ids]} pending-version-ids]]
    (let [ready           (set file-version-ids)
          tracked-pending (set/union pending-version-ids
                                     (::pending-ifc-version-ids db))
          still-pending   (set/difference tracked-pending ready)]
      (cond-> {:db (-> db
                       (assoc ::ifc-data-ready-version-ids ready)
                       (assoc ::pending-ifc-version-ids still-pending))}
        (seq still-pending) (assoc :fx [[:dispatch-later {:ms 5000 :dispatch [::fetch-ifc-data-availability still-pending]}]])))))

(rf/reg-sub ::ifc-data-ready?
  (fn [db [_ file-version-id]]
    (contains? (::ifc-data-ready-version-ids db) file-version-id)))
