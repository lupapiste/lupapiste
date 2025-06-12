(ns lupapalvelu.ui.admin.ad-login.ad-login-settings
  "Lupapiste admin AD login settings; can configure most settings for AD login.
  See `lupapalvelu.ui.auth-admin.ad-login.ad-login-settings` for the organization role-mapping config"
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(def columns [:enabled :forced :trusted-domains :idp-uri :idp-certs :idp-cert-url :organizations :actions])

(rf/reg-event-fx
  ::fetch-ad-logins
  (fn [_]
    {:action/query {:name    :ad-logins
                    :success (fn [{:keys [ad-logins]}]
                               (>evt [:value/set ::ad-logins ad-logins]))}}))

(rf/reg-event-fx
  ::fetch-organization-names
  (fn [_]
    {:action/query {:name :get-organization-names
                    :success (fn [{:keys [names]}]
                               (let [lang-kw (keyword (js/loc.getCurrentLanguage))]
                                 (>evt [:value/set
                                        ::organization-names
                                        (->> names
                                             (util/map-values lang-kw)
                                             (map (fn [[id org-name]]
                                                    {:text  (str (name id) " - " org-name)
                                                     :value (name id)}))
                                             (sort-by :text))])))}}))

(rf/reg-event-fx
  ::create-ad-login
  (fn [_]
    {:action/command {:name    :create-ad-login
                      :success #(>evt [:value/update ::ad-logins conj (:data %)])}}))

(rf/reg-event-fx
  ::remove-ad-login
  (fn [_ [_ ad-login-id]]
    {:action/command {:name    :remove-ad-login
                      :params  {:ad-login-id ad-login-id}
                      :success #(>evt [:value/update ::ad-logins
                                       (fn [ad-logins] ; must return vector for :value/save to work
                                         (->> ad-logins
                                              (remove (comp #{ad-login-id} :id))
                                              vec))])}}))

(rf/reg-event-fx
  ::save-ad-login
  (fn [_ [_ index data]]
    {:action/command {:name    :update-ad-login-settings
                      :params  {:ad-login-id (:id data)
                                :data        (dissoc data :id :unsaved?)}
                      :success #(>evt [:value/update ::ad-logins index dissoc :unsaved?])}}))

(defn- ad-login-organizations
  "The organizations have other configuration data (which the authority admin can edit)
  but here we only care about whether a given organization is included in the ad-login"
  [input-id edit-fn org-configs]
  [reselect/reselect
   (map :id org-configs)
   {:id       input-id
    :multi?   true
    :items    (<sub [:value/get ::organization-names])
    :callback (fn [new-org-ids]
                (let [old-org-ids (set (map :id org-configs))
                      added-ids   (set/difference new-org-ids old-org-ids)
                      removed-ids (set/difference old-org-ids new-org-ids)]
                  (->> org-configs
                       (remove (comp removed-ids :id))
                       (concat (map #(hash-map :id % :role-mapping {}) added-ids))
                       vec
                       edit-fn)))}])

(defn- ad-login-actions [index {:keys [id unsaved?] :as ad-login}]
  [:div.flex--wrap.flex--gap1
   [components/icon-button
    {:icon     :lupicon-remove
     :class    :secondary
     :text-loc :remove
     :on-click (fn []
                 (components/confirm-dialog
                   "areyousure"
                   "admin.ad-login.confirm-remove.message"
                   #(>evt [::remove-ad-login id])))}]
   (when unsaved?
     [components/icon-button
      {:icon     :lupicon-save
       :class    :primary
       :text-loc :save
       :on-click #(>evt [::save-ad-login index ad-login])}])])

(defn- array-textarea
  "Allows user to edit a list of strings as a multiline textarea where each string in the array
  is represented by their own line"
  [input-id edit-fn value placeholder]
  [components/textarea-edit
   (ss/join "\n" value)
   {:id          input-id
    :lines       3
    :placeholder placeholder
    :callback    #(->> (ss/split-lines %)
                       (mapv ss/trim)
                       (remove ss/blank?)
                       edit-fn)}])

(defn ad-login-cell [ad-login index column]
  (let [value    (get ad-login column)
        input-id (str "ad-login-input-" index "-" column)
        edit-fn  (fn [value & [attr]] ; can specify the attribute for cells with multiple fields
                   (>evt [:value/set ::ad-logins index (or attr column) value])
                   (>evt [:value/set ::ad-logins index :unsaved? true]))]
    [:td
     (case column
       :enabled         [components/toggle
                         (boolean value)
                         {:id       input-id
                          :callback edit-fn}]
       :forced          [components/toggle
                         (boolean value)
                         {:id       input-id
                          :callback edit-fn}]
       :trusted-domains [array-textarea input-id edit-fn value "domain-a.com\ndomain-b.com\n..."]
       :idp-certs       [array-textarea input-id edit-fn value "certificate-a\ncertificate-b\n..."]
       :organizations   [ad-login-organizations input-id edit-fn value]
       :actions         [ad-login-actions index ad-login]
       [components/text-edit
        (-> value str ss/trim)
        {:id       input-id
         :callback edit-fn}])]))

(defn ad-login-row [ad-login index]
  (->> columns
       (map #(ad-login-cell ad-login index %))
       (into [:tr])))

(defn ad-login-table []
  (let [ad-logins (<sub [:value/get ::ad-logins])]
    [:table.w--100
     [:thead
      [:tr.bg--gray
       (for [column columns]
         [:th.txt--left.pad--1
          {:key column}
          (loc (util/kw-path :admin.ad-login column))])]]
     (->> ad-logins
          (map-indexed (fn [index ad-login]
                         ^{:key (str "ad-login-" (:id ad-login))}
                         [ad-login-row ad-login index]))
          (into [:tbody]))]))

(defn view []
  (r/with-let [_ (>evt [::fetch-ad-logins])
               _ (>evt [::fetch-organization-names])]
              [:div
               [:h2 (loc :admin.ad-login.header)]
               [ad-login-table]
               [components/icon-button
                {:icon     :lupicon-plus
                 :class    :secondary
                 :text-loc :add
                 :on-click #(>evt [::create-ad-login])}]]))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
