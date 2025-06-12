(ns lupapalvelu.ui.admin.bim.checking-plan-config
  "Lupapiste admin configuration for checking plans (Tarkastussuunnitelma).
  In practice involves uploading and managing checkset (.CSET) files for the Solibri BIM integration"
  (:require [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.attachment.components.upload-button :as att-upload-button]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.util :as util]))

(defn- uploaded-files->object-key
  "Only works when uploading a single file"
  [upload-data]
  (-> upload-data :files js->clj first :object-key))

(defn- update-plan-by-id [plans plan-id updates]
  (map #(cond-> %
          (= (:id %) plan-id) (merge updates))
       plans))

(rf/reg-sub ::plans :-> ::plans)

(rf/reg-event-fx ::fetch-plans
  (fn [_]
    {:action/query {:name    :bim-checking-plans
                    :success (fn [{:keys [bim-checking-plans]}]
                                 (>evt [:value/set ::plans bim-checking-plans]))}}))

(rf/reg-event-fx ::create-plan
  (fn [_ [_ object-key]]
    {:action/command {:name    :create-bim-checking-plan
                      :params  {:object-key object-key}
                      :success (fn [{:keys [bim-checking-plan]}]
                                 (>evt [:value/update ::plans into [bim-checking-plan]]))}}))

(rf/reg-event-fx ::update-file
  (fn [_ [_ plan-id object-key]]
    {:action/command {:name    :update-bim-checking-plan-file
                      :params  {:plan-id    plan-id
                                :object-key object-key}
                      :success (fn [{:keys [updated-file]}]
                                 (>evt [:value/update ::plans update-plan-by-id plan-id {:file updated-file}]))}}))

(rf/reg-event-fx ::update-plan
  (fn [_ [_ plan-id updates]]
    {:action/command {:name    :update-bim-checking-plan
                      :params  {:plan-id plan-id
                                :updates updates}
                      :success (fn [_]
                                 (>evt [:value/update ::plans update-plan-by-id plan-id updates]))}}))

(rf/reg-event-fx ::delete-plan
  (fn [_ [_ plan-id]]
    {:action/command {:name :delete-bim-checking-plan
                      :params {:plan-id plan-id}
                      :success (fn [_]
                                 (>evt [:value/update ::plans (partial remove #(-> % :id (= plan-id)))]))}}))

(defn- dropdown-items [values loc-path]
  (map (fn [value]
         {:value (name value)
          :text  (-> loc-path (util/kw-path value) loc)})
       values))

(defn- failed-upload [message]
  (common/show-dialog {:ltitle :error.unknown
                       :text   (:message message)}))

(defn- upload-button
  "Wrapper for file upload button to reduce repetition"
  [callback-fn]
  [att-upload-button/upload-button
   {:callback {:bad     failed-upload
               :failed  failed-upload
               :success #(callback-fn (uploaded-files->object-key %))}
    :bucket   "bim-files"}])

(defn view []
  (r/with-let [_ (>evt [::fetch-plans])]
    [:div
     [:h2 (loc :admin.bim.checking-plans.header)]
     [:table
      [:thead
       [:tr
        [:th (loc :admin.bim.enabled)]
        [:th (loc :name)]
        [:th (loc :common.description)]
        [:th (loc :application.bim.checking-type)]
        [:th (loc :application.bim.model-type)]
        [:th (loc :attachment.file)]
        [:th (loc :applications.lastModified)]
        [:th {:col-span 2} (loc :admin.actions)]]]
      (->> (<sub [::plans])
           (map (fn [{:keys [title description checking-type model-type enabled]
                      {:keys [filename created]} :file plan-id :id}]
                  (let [edit #(>evt [::update-plan plan-id {%1 %2}])]
                    [:tr
                     [:td [components/toggle enabled
                           {:text-loc :admin.bim.enabled
                            :callback #(edit :enabled (not enabled))}]]
                     [:td [components/text-edit title
                           {:callback #(edit :title %)
                            :on-blur? true}]]
                     [:td [components/textarea-edit description
                           {:lines    3
                            :callback #(edit :description %)
                            :on-blur? true}]]
                     [:td [components/dropdown checking-type
                           {:items    (dropdown-items bim-cp-schema/checking-types :application.bim.checking-type)
                            :callback #(edit :checking-type %)}]]
                     [:td [components/dropdown model-type
                           {:items    (dropdown-items bim-schema/model-types :application.bim.model-type)
                            :callback #(edit :model-type %)}]]
                     [:td [:a {:href (str "/api/raw/download-bim-checking-plan?plan-id=" plan-id)} filename]]
                     [:td (js/util.finnishDateAndTime created)]
                     [:td [upload-button #(>evt [::update-file plan-id %])]]
                     [:td [components/icon-button
                           {:icon     :lupicon-trash
                            :class    :secondary
                            :text-loc :remove
                            :on-click #(components/confirm-dialog :areyousure
                                                                  :admin.bim.delete.confirm
                                                                  (fn [] (>evt [::delete-plan plan-id])))}]]])))
           (into [:tbody]))]
   [upload-button #(>evt [::create-plan %])]]))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
