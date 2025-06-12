(ns lupapalvelu.ui.docgen.field.docgen-allu-drawings
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn filter-allu-drawings-fx
  [_ [_ app-id kind]]
  (when (ss/blank-as-nil kind)
    {:fx [[:action/command {:name :filter-allu-drawings
                            :params {:id app-id
                                     :kind kind}
                            :success [::filter-allu-drawings.success app-id]}]]}))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-event-fx ::cleanup
  on-unload)

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ {:keys [app-id kind enabled?]}]]
    (cond-> {:db (assoc-in db [::data app-id] {:selected-site nil
                                               :sites         []})}
      enabled? (merge {:fx [[:dispatch [::fetch-sites app-id kind]]
                            [:dispatch [::filter-allu-drawings app-id kind]]]}))))

(rf/reg-event-fx ::fetch-sites
  (fn [_ [_ app-id kind]]
    {:action/query {:name    :allu-sites
                    :params  (cond-> {:id app-id}
                               kind (assoc :kind kind))
                    :success [::fetch-sites.success app-id]
                    :error   [::fetch-sites.error app-id]}}))

(rf/reg-event-fx ::fetch-sites.success
  (fn [{:keys [db]} [_ resp app-id]]
    {:db (assoc-in db [::data app-id :sites] (:sites resp))}))

(rf/reg-event-fx ::fetch-sites.error
  (fn [{:keys [db]} [_ app-id _error]]
    {:db (assoc-in db [::data app-id :sites] [])}))

(rf/reg-event-fx ::filter-allu-drawings
  filter-allu-drawings-fx)

(rf/reg-event-fx ::filter-allu-drawings.success
  (fn [_ [_ _response app-id]]
    {:application/lightload app-id}))

(rf/reg-event-fx ::add-drawing.success
  (fn [_ [_ _response app-id kind]]
    {:fx [[:application/lightload app-id]
          [:dispatch [::fetch-sites app-id kind]]]}))

(rf/reg-event-fx ::add-drawing
  (fn [{:keys [db]} [_ app-id site-id kind]]
    (let [data-path [::data app-id]
          sites     (get-in db (conj data-path :sites))
          site      (util/find-by-key :id site-id sites)
          source    (:source site)]
      {:action/command {:name    :add-allu-drawing
                        :params  {:id     app-id
                                  :kind   source
                                  :siteId site-id}
                        :success [::add-drawing.success app-id kind]}
       :db         (assoc-in db (conj data-path :selected-site) nil)})))

(rf/reg-event-fx ::remove-drawing
  (fn [_ [_ app-id drawing-id kind]]
    {:action/command {:name    :remove-application-drawing
                      :params  {:id        app-id
                                :drawingId drawing-id}
                      :success [::remove-drawing.success app-id kind]}}))

(rf/reg-event-fx ::remove-drawing.success
  (fn [_ [_ _response app-id kind]]
    {:fx [[:application/lightload app-id]
          [:dispatch [::fetch-sites app-id kind]]]}))

(rf/reg-event-fx ::remove-drawing-with-confirmation
  (fn [_ [_ app-id drawing]]
    {:dialog/show {:type             :yes-no
                   :ltitle           :areyousure
                   :size             :medium
                   :component-params {:text  (common/loc "allu.remove-drawing-confirmation"
                                                         (:name drawing))
                                      :yesFn #(>evt [::remove-drawing app-id (:id drawing)])}}}))

(rf/reg-event-fx ::locate-drawing
  (fn [_ [_ drawing-id map-id]]
    {:hub/send {:event "allu-map-locate"
                :data  {:drawingId drawing-id
                        :mapId     map-id}}}))

(rf/reg-event-db ::set-selected-site
  (fn [db [_ app-id site-id]]
    (assoc-in db [::data app-id :selected-site] site-id)))

(rf/reg-sub ::selected-site
  (fn [db [_ app-id]]
    (get-in db [::data app-id :selected-site])))

(rf/reg-sub ::sites
  (fn [db [_ app-id]]
    (get-in db [::data app-id :sites])))

(rf/reg-sub ::site-options
  (fn [[_ app-id]]
    [(rf/subscribe [::sites app-id])])
  (fn [[sites]]
    (mapv (fn [{:keys [id name allu-section source]}]
            {:value id
             :text (if allu-section
                     (str name " (" (common/loc "document.allu-section") " " allu-section ")")
                     name)
             :group (common/loc (util/kw-path :allu.application-kind source))})
          sites)))

(defn- site-selector
  [{:keys [app-id kind disabled?]}]
  (let [selected-site (<sub [::selected-site app-id])
        site-options (<sub [::site-options app-id])]
    [:div.tabby
     [:div.tabby__row
      [:div.tabby__cell.tabby--100
       [reselect/reselect
        selected-site
        {:id         (str "allu-site-selector-" app-id (when kind (str "-" (name kind))))
         :class      "select lux w--100 w--max-40em"
         :callback   #(>evt [::set-selected-site app-id %])
         :disabled?  disabled?
         :choose-loc :choose
         :items      site-options}]]
      [:div.tabby__cell
       [components/icon-button
        {:icon      :lupicon-circle-plus
         :class     :primary
         :disabled? (not selected-site)
         :text-loc  :allu.add-site
         :on-click  #(>evt [::add-drawing app-id selected-site kind])}]]]]))

(defn- drawings-table
  [{:keys [drawings ltitle delete-fn locate-fn]}]
  (when (seq drawings)
    [:div
     (when ltitle
       [:h3.drawings-table-title (common/loc ltitle)])
     [:table.drawings-table
      [:tbody
       (for [{:keys [id name allu-section]} drawings]
         (let [title (if allu-section
                       (str name " (" (common/loc "document.allu-section") " " allu-section ")")
                       name)]
           [:tr {:key id}
            [:td.drawing--name title]
            (when locate-fn
              [:td.drawing--icon
               [components/icon-button
                {:icon :lupicon-viewfinder
                 :class :tertiary
                 :aria-label (common/loc "allu.locate-on-map")
                 :on-click #(locate-fn {:id id})}]])
            (when delete-fn
              [:td.drawing--icon
               [components/icon-button
                {:icon :lupicon-remove
                 :class :tertiary
                 :aria-label (common/loc "allu.remove-row")
                 :on-click #(delete-fn {:id id :name name})}]])]))]]]))

(defn allu-drawings-kind
  [doc-id schema]
  (let [kind (:kind schema)]
    (if (string? kind)
      kind
      (let [kind-doc-name (:document kind)
            kind-schema-path (:path kind)
            kind-doc-id (or (when kind-doc-name
                              (<sub [::docs/doc-id-by-name kind-doc-name]))
                            doc-id)
            kind-path (when kind-schema-path
                        (util/split-kw-path kind-schema-path))]
        (when (and kind-doc-id kind-path)
          (<sub [::value/current-field-value kind-doc-id kind-path]))))))

(defn view
  [{:keys [app-id doc-id path schema disabled?] :as _args}]
  (let [kind                        (allu-drawings-kind doc-id schema)
        map-id                      (get-in schema [:map]) ;; map id is "allu-map"
        location-type-path          (conj (pop path) :location-type)
        selected-type               (<sub [::value/current-field-value doc-id location-type-path])
        enabled?                    (and (not disabled?) (<sub [:auth/application? :allu-sites]))
        allu-drawings               (<sub [::application/allu-drawings kind])
        user-drawings               (<sub [::application/user-drawings])
        show-allu?                  (not= selected-type "custom")
        show-user?                  (not= selected-type "fixed")
        delete-fn                   (when enabled?
                                      #(>evt [::remove-drawing app-id (:id %) kind]))
        delete-with-confirmation-fn (when enabled?
                                      #(>evt [::remove-drawing-with-confirmation app-id %]))
        locate-fn                   (when map-id
                                      #(>evt [::locate-drawing (:id %) map-id]))]
    (react/useEffect
      (fn []
        (>evt [::init {:app-id   app-id
                       :kind     kind
                       :enabled? enabled?}])
        (fn []
          (>evt [::cleanup app-id])))
      #js [app-id kind enabled?])

    [:div.allu-drawings
     (when show-allu?
       [:<>
        [site-selector {:app-id    app-id
                        :kind      kind
                        :disabled? disabled?}]
        [drawings-table {:drawings  allu-drawings
                         :ltitle    :allu.locations
                         :delete-fn delete-fn
                         :locate-fn locate-fn}]])
     (when show-user?
       [drawings-table {:drawings  user-drawings
                        :ltitle    :allu.user-locations
                        :delete-fn delete-with-confirmation-fn
                        :locate-fn locate-fn}])]))

(sc/defn render
  [args :- schemas/DocgenFieldArguments]
  [:f> view args])
