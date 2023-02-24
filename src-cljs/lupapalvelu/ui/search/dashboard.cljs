(ns lupapalvelu.ui.search.dashboard
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.search.core :as search]
            [lupapalvelu.ui.search.filters :as filters]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]))

(rf/reg-event-fx
  ::create-project
  (fn [_ [_ project-type]]
    (js/pageutil.openPage (case project-type
                            :archiving-project "create-archiving-project"
                            :previous-permit "create-page-prev-permit"
                            "create-part-1"))))

(rf/reg-event-fx
  ::fetch-assignment-count
  (fn [_ _]
    (cond-> {}
      (auth/global-auth? :assignment-count)
      (assoc :action/query {:name :assignment-count
                            :success [:action/store-response :assignmentCount
                                      ::assignment-count]}))))

(defn create-button-definitions [{:keys [role]}]
  (let [regular? (not (or (<sub [:auth/global? :user-is-pure-digitizer])
                          (= role "financialAuthority")))]
    (filter :when
            [{:type     :archiving-project
              :text-loc :digitizer.tools
              :icon     :lupicon-documents
              :when     (<sub [:auth/global? :create-archiving-project])}
             {:type     :inforequest
              :text-loc :newRequest.newInforequest
              :icon     :lupicon-circle-plus
              :when     regular?}
             {:type     :application
              :text-loc :newRequest.newApplication
              :icon     :lupicon-circle-plus
              :when     regular?}
             {:type     :previous-permit
              :text-loc :newRequest.createNewWithPrevPermit
              :icon     :lupicon-circle-plus
              :when     (and regular?
                             (<sub [:auth/global? :create-application-from-previous-permit]))}])))

(defn views [{:keys [role]}]
  (filter identity
          [{:text-loc :navigation.cases
            :value    (cond
                        (<sub [:auth/global? :enable-company-search])
                        :company-applications

                        (= role "authority") :authority-applications
                        :else                :applications)}
           (when (<sub [:auth/global? :enable-foreman-search])
             {:text-loc :applications.search.foremen
              :value    :foreman-applications})
           (when (<sub [:auth/global? :assignments-search])
             {:text-fn  #(let [n (<sub [:value/get ::assignment-count])]
                           (cond-> (loc :application.assignment.search.label)
                            (pos? n) (str " (" n ")")))
              :value :assignments})]))

(defn dashboard []
  (r/with-let [_ (>evt [:auth/refresh])
               _ (>evt [::fetch-assignment-count])
               _ (>evt [::hub/subscribe :assignmentsDataProvider::assignmentCount
                        #(>evt [:value/set ::assignment-count (:count %)])
                        ::assignment-count-hub-id])
               _ (>evt [::hub/subscribe :applicationsDataProvider::sortChanged
                        #(>evt [::search/sort-changed (:sort %)])
                        ::sort-hub-id])
               _ (>evt [::hub/subscribe :areaFilterService::changed
                        #(>evt [::search/refresh ::search/areas])
                        ::areas-hub-id])
               _ (>evt [::hub/subscribe :organizationUsersService::changed
                        #(>evt [::search/refresh ::search/handlers
                                ::search/recipients])
                        ::users-hub-id])
               _ (>evt [::hub/subscribe :organizationTagsService::changed
                        #(>evt [::search/refresh ::search/organization-tags])
                        ::organization-tags-hub-id])
               _ (>evt [::hub/subscribe :operationFilterService::changed
                        #(>evt [::search/refresh ::search/operations])
                        ::operations-hub-id])
               _ (>evt [::hub/subscribe :organizationFilterService::changed
                        #(>evt [::search/refresh ::search/organizations])
                        ::organizations-hub-id])
               _ (>evt [::hub/subscribe :companyTagsService::changed
                        #(>evt [::search/refresh ::search/company-tags])
                        ::company-tags-hub-id])]
    (let [user         (<sub [::session/user])
          user-views   (some-> user views)
          initialized? (<sub [::search/initialized?])]
      (cond
        (nil? user) nil

        (and user (not initialized?))
        (>evt [::search/initialize (map :value user-views)])

        initialized?
        [:div.container.pad--t4.bg--gray
         [:div.flex--between.flex--wrap-xs
          [:h1.gap--r4.gap--v2 (loc :navigation.dashboard)]
          [:div.flex--wrap
           (for [{:keys [type text-loc
                         icon]} (create-button-definitions user)]
             ^{:key type} [components/icon-button
                           {:on-click #(>evt [::create-project type])
                            :text-loc text-loc
                            :icon     icon
                            :test-id  [:applications-create type]
                            :class    :primary.gap--v1.gap--l2}])]]
         (let [items    (map (fn [{:keys [text-fn] :as v}]
                               (cond-> v
                                 text-fn (assoc :text (text-fn))))
                             user-views)
               selected (<sub [::search/view])]
           [:<>
            (when (> (count items) 1)
              [:div.flex--wrap
               [components/toggle-group
                #{selected}
                {:items    items
                 :callback #(>evt [::search/set-view %])
                 :radio?   true
                 :prefix   :plain-bold-tag
                 :class    :gap--v1.gap--r05}]])
            (case selected
              :applications           [filters/search-text]
              :authority-applications [filters/authority-applications]
              :foreman-applications   [filters/foreman-applications]
              :company-applications   [filters/company-applications]
              :assignments            [filters/assignments]
              nil)])]))
    (finally
      (>evt [::hub/unsubscribe ::assignment-count-hub-id ::sort-hub-id
             ::areas-hub-id ::organization-tags-hub-id ::user-hub-id
             ::operations-hub-id ::organizations-hub-id
             ::company-tags-hub-id]))))


(defn mount-component [dom-id]
  (rd/render [dashboard] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id]
  (mount-component dom-id))
