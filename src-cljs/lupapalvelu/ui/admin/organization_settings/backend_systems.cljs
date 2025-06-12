(ns lupapalvelu.ui.admin.organization-settings.backend-systems
  "Lupapiste admin UI for viewing organization's backend-system urls and
  editing KuntaGML versions for permit types"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn on-visible
  [{:keys [db]} _]
  (let [org-id (router/path-parameter db :org-id)]
    {:fx [[:action/query {:name    :organization-krysp-settings
                          :params  (util/mmap org-id)
                          :success [::organization-krysp-settings.success]}]]}))

(defn on-hidden
  [{:keys [db]} _]
  {:db (dissoc db
               ::data
               ::edited-data
               ::krysp-versions
               ::dmcity-kuntagml-schemas-enabled)})

(rf/reg-event-db ::organization-krysp-settings.success
  (fn [db [_ {:keys [data krysp-versions dmcity-kuntagml-schemas-enabled]}]]
    (assoc db
           ::data data
           ::dmcity-kuntagml-schemas-enabled dmcity-kuntagml-schemas-enabled
           ::krysp-versions krysp-versions)))

(rf/reg-sub ::dmcity-kuntagml-schemas-enabled :-> ::dmcity-kuntagml-schemas-enabled)

(rf/reg-sub ::krysp-versions :-> ::krysp-versions)

(rf/reg-sub ::available-krysp-versions
  :<- [::krysp-versions]
  :<- [::dmcity-kuntagml-schemas-enabled]
  (fn [[krysp-versions dmcity-kuntagml-schemas-enabled] [_ permit-type]]
    (->> krysp-versions
         permit-type
         (remove #(and (not dmcity-kuntagml-schemas-enabled)
                       (re-find #"-dmcity-lp$" %)))
         util/sort-desc
         (mapv #(hash-map :text % :value %)))))

(rf/reg-sub ::organization-krysp-settings :-> ::data)

(rf/reg-event-db ::edit-gml-version
  (fn [db [_ permit-type]]
    (assoc-in db [::edited-data permit-type] (merge {:version ""}
                                                    (get (::data db) permit-type)))))

(rf/reg-event-db ::cancel-edit-gml-version
  (fn [db [_ permit-type]]
    (util/dissoc-in db [::edited-data permit-type])))

(rf/reg-event-fx ::save-edit-gml-version
  (fn [{:keys [db]} [_ permit-type]]
    (let [org-id  (router/path-parameter db :org-id)
          version (get-in db [::edited-data permit-type :version])]
      {:action/command {:name                  :set-organization-permit-type-krysp-version
                        :success               (fn []
                                                 (>evt [:value/set ::data permit-type :version version])
                                                 (>evt [::cancel-edit-gml-version permit-type]))
                        :show-saved-indicator? true
                        :params                (util/assoc-when {:org-id     org-id
                                                                 :permitType permit-type}
                                                                :version (ss/blank-as-nil version))}})))

(rf/reg-event-db ::version-dropdown-on-change
  (fn [db [_ permit-type value]]
    (assoc-in db [::edited-data permit-type :version] value)))

(rf/reg-sub ::edited-data :-> ::edited-data)

(rf/reg-sub ::edited-gml-version
  :<- [::edited-data]
  (fn [edited-data [_ permit-type]]
    (get-in edited-data [permit-type :version])))

(defn edit-gml-version [permit-type version]
  [:div.flex.gap-4.items-center
   [:div [components/dropdown
          version
          {:items    (<sub [::available-krysp-versions permit-type])
           :id       (str "gml-version-dropdown-" (name permit-type))
           :callback #(>evt [::version-dropdown-on-change permit-type %])}]]
   [:button.secondary
    {:on-click #(>evt [::cancel-edit-gml-version permit-type])}
    (common/loc :cancel)]
   [:button.primary
    {:on-click #(>evt [::save-edit-gml-version permit-type])}
    (common/loc :save)]])

(defn view-gml-version [permit-type version]
  [:div.flex.gap-4.items-center
   [:div version]
   [:button.primary
    {:on-click #(>evt [::edit-gml-version permit-type])}
    (common/loc :edit)]])

(defn view []
  (when-let [krysp-settings (<sub [::organization-krysp-settings])]
    [:div.spacerL
     [:h2 (common/loc :admin.organization.backend-system)]
     [:table
      [:thead
       [:tr
        [:th "Lupatyyppi"]
        [:th "URL"]
        [:th "KuntaGML-versio"]]]
      (into [:tbody]
            (for [[permit-type {:keys [url version]}] (vec krysp-settings)]
              [:tr
               [:td permit-type]
               [:td url]
               [:td
                (let [edited-value (<sub [::edited-gml-version permit-type])]
                  (if edited-value
                    [edit-gml-version permit-type edited-value]
                    [view-gml-version permit-type version]))]]))]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
