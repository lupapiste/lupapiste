(ns lupapalvelu.ui.docgen.field.docgen-partial-review-buildings
  "UI component for partially reviewing a building

  Add support for partially reviewing buildings. If the building
  contains apartments, they can be reviewed individually. It is
  possible to add review state, information about the commissioning
  status of the apartment, review date and also review notes for each
  apartment.

  This component requires a specific schema structure defined in docgen schemas
  (see lupapalvelu.tasks/task-katselmus-body).

  Expected schema structure:
  {:name 'rakennus'
   :type :group
   :repeating true
   :uicomponent :docgen-review-buildings
   :body [{:name 'rakennus' :type :group
           :body [{:name 'jarjestysnumero' ...}
                  {:name 'valtakunnallinenNumero' ...}
                  {:name 'apartments' :type :group
                   :body [{:name 'apartment' :type :group
                           :body [{:name 'huoneistoTyyppi' :type :string}
                                  ...]}
                          {:name 'tila' :type :group
                           :body [{:name 'tila':type :select :css [:dropdown] :sortBy :displayname
                                   :whitelist {:roles [:authority] :otherwise :disabled}
                                   :i18nkey 'task-katselmus.rakennus.apartment.tila.tila'
                                   :body [{:name 'osittainen'} {:name 'lopullinen'}]}
                                  {:name 'timestamp' :type :date
                                   :i18nkey 'task-katselmus.rakennus.apartment.tila.timestamp'
                                   :whitelist {:roles [:authority] :otherwise :disabled}}
                                  {:name 'notes' :type :text :max-len 40000 :css []
                                   :i18nkey 'task-katselmus.rakennus.apartment.tila.notes'
                                   :whitelist {:roles [:authority] :otherwise :disabled}
                                   :auth {:disabled [:is-faulty-review]}}
                                  {:name 'kayttoonottava'
                                   :i18nkey 'task-katselmus.rakennus.apartment.tila.kayttoonottava'
                                   :type :checkbox :inputType :checkbox-wrapper
                                   :auth {:enabled [:is-end-review :is-other-review]}
                                   :whitelist {:roles [:authority] :otherwise :disabled}}]}]}
                  ...]}
          {:name 'tila' :type :group
           :body [{:name 'tila' :type :select ...}
                  {:name 'kayttoonottava' :type :checkbox ...}]}]}

  The nested structure is critical:
  - Top-level :body must contain a 'tila' group
  - The 'tila' group's :body must contain both:
    - 'tila' (a select field for status)
    - 'kayttoonottava' (a checkbox for marking building as in use)
  - The buildings may optionally contain apartments but they are not
    required. As the main building schema structure expectation is the
    same as before, this component should be compatible with the
    existing review task documents.
    - 'tila' (select field for status similar to the building level selection)
    - 'kayttoonottava' (a checkbox for marking building as in use, just like for building)
    - 'timestamp' (an optional timestamp for review date)
    - 'notes' (an optional text field for notes)

  The structure describing buildings and apartments is built when
  review task is created. The apartment information is collected from
  the documents that refer to the operation ID connected with the
  buildings that will be included in the review task. If apartment
  information is found, it will be included in the building structure."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.partial-review-buildings :as partial-review-buildings]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def tila "tila")

(def kayttoonottava "kayttoonottava")

(defn- render-child
  [schema {:keys [render-fn label] :as cell-args}]
  (let [schema-body (cond-> schema
                      label (assoc :label label))]
    [render-fn [schema-body] cell-args]))

(defn apartment-field [{:keys [doc-id apartments-path apartment-index]} field-name translate?]
  (let [apartment-path  (conj apartments-path apartment-index :apartment)
        field-value     (<sub [::value/current-field-value doc-id (conj apartment-path (keyword field-name))])
        field-value-str (if (nil? field-value)
                          (common/loc "ei-tiedossa")
                          (if translate?
                            (common/loc (str "huoneistot." field-name "." field-value))
                            field-value))]
    [:div {:style {:padding-top "3px"
                   :padding-bottom "3px"}}
     [:div [:label (common/loc (str "huoneistot." field-name))]]
     [:div [:label field-value-str]]]))

(defn apartment-sidepanel [{:keys [doc-id apartments-path
                                   apartment-commissioned-schema apartment-state-schema
                                   apartment-notes-schema apartment-timestamp-schema
                                   apartment-state-args]} apartment-index]
  (let [apartment-path (conj apartments-path apartment-index :apartment)
        huoneistonumero (<sub [::value/current-field-value doc-id (conj apartment-path :huoneistonumero)])
        porras (<sub [::value/current-field-value doc-id (conj apartment-path :porras)])
        huoneluku (<sub [::value/current-field-value doc-id (conj apartment-path :huoneluku)])
        huoneistoala (<sub [::value/current-field-value doc-id (conj apartment-path :huoneistoala)])
        side-panel-open? (<sub [::partial-review-buildings/side-panel-open?])
        apartment-doc {:doc-id doc-id
                       :apartments-path apartments-path
                       :apartment-index apartment-index}]
    [:div.apartment-info-sidebar
     {:style {:transform (if side-panel-open?
                           "translateX(0%)"
                           "translateX(100%)")}}
     [:div
      [:button.close-apartment-info-sidebar-btn
       {:on-click (fn [_] (>evt [::partial-review-buildings/close-side-panel]))}
       [:i.lupicon-remove]]
      [:h4 (str (common/loc "task-katselmus.huoneisto") " " porras huoneistonumero)]
      [:div (str huoneluku " " (common/loc "task-katselmus.huonetta") ", " huoneistoala (common/loc "unit.m2"))]]
     [:div [render-child apartment-state-schema apartment-state-args]]
     [:div [render-child apartment-commissioned-schema apartment-state-args]]
     [apartment-field apartment-doc "huoneistoTyyppi" true]
     [apartment-field apartment-doc "porras"]
     [apartment-field apartment-doc "huoneistonumero"]
     [apartment-field apartment-doc "jakokirjain"]
     [apartment-field apartment-doc "pysyvaHuoneistotunnus"]
     [:div {:style {:width "8em"}} [render-child apartment-timestamp-schema apartment-state-args]]
     [:div [render-child apartment-notes-schema apartment-state-args]]]))

(defn apartment-box [{:keys [doc-id apartments-path apartment-commissioned-schema apartment-args selected-apartment-index] :as args} apartment-index]
  (let [apartment-path (conj apartments-path apartment-index :apartment)
        huoneistonumero (<sub [::value/current-field-value doc-id (conj apartment-path :huoneistonumero)])
        porras (<sub [::value/current-field-value doc-id (conj apartment-path :porras)])
        huoneluku (<sub [::value/current-field-value doc-id (conj apartment-path :huoneluku)])
        huoneistoala (<sub [::value/current-field-value doc-id (conj apartment-path :huoneistoala)])]
    [:div.apartment
     {:class (when (= apartment-index selected-apartment-index)
               "building-box-selected")
      :on-click (fn [_ ]
                  (>evt [::partial-review-buildings/set-selected-apartment-index apartment-index])
                  (>evt [::partial-review-buildings/open-side-panel]))}
     [:div {:style {:display "flex"}}
      [render-child (assoc apartment-commissioned-schema :label false) (assoc apartment-args :path (conj apartments-path apartment-index :tila))]
      [:div [:h5 (str (common/loc "task-katselmus.huoneisto") " " porras huoneistonumero)]
       [:div (str huoneluku " " (common/loc "task-katselmus.huonetta") ", " huoneistoala (common/loc "unit.m2"))]]]]))

(defn building-section [{:keys [doc-id apartments-path] :as args} section]
  (let [apartment-indexes (<sub [::docs/field-child-keys doc-id apartments-path])]
    [:div.building-section
     [:h4 (common/loc (str "task-katselmus.rakennus." (:name section)))]
     [:div.building-section-grid
      (doall
       (for [apartment-index apartment-indexes]
         (let [selected-apartment-index (<sub [::partial-review-buildings/selected-apartment-index])]
           ^{:key (str "apartment-box-" apartment-index)}
           [apartment-box (assoc args :selected-apartment-index selected-apartment-index) apartment-index])))]]))

(defn apartment-index-map [doc-id base-path building-index]
  (let [apartment-indexes (<sub [::docs/field-child-keys doc-id (conj base-path building-index :rakennus :apartments)])
        apartments-path (conj base-path building-index :rakennus :apartments)]
    (reduce (fn [acc apartment-index]
              (let [state (<sub [::value/current-field-value doc-id (conj apartments-path apartment-index :tila :tila)])
                    commissioned? (<sub [::value/current-field-value doc-id (conj apartments-path apartment-index :tila :kayttoonottava)])
                    apartment-state {:state state :commissioned? commissioned?}]
                (if (every? nil? (vals apartment-state))
                  acc
                  (assoc acc apartment-index apartment-state))))
            {} apartment-indexes)))

(defn building-sections [args]
  (let [default-section {:name "default-section"}]
    ;; Currently there is only the default section as dividing
    ;; building into separate sections isn't implemented yet.
    [building-section args default-section]))

(defn- building-description
  "Compose description from building + optional matched document tunnus."
  [building]
  (let [nat-id (:nationalId building)
        op-id (:operationId building)
        building-doc (<sub [::docs/op-or-nat->related-document op-id nat-id])
        tunnus (get-in building-doc [:data :tunnus :value])
        base-desc (or (:description building) "")
        joined (->> [tunnus base-desc]
                    (ss/join-non-blanks ": "))]
    (or joined "")))

(defn building-description-with-details [building]
  (let [property-id (:propertyId building)]
    [:div
     [:h2 [building-description building]]
     [:div [:p (str (common/loc :task-katselmus.rakennus.kiinteistotunnus) " " property-id)]]]))

(defn building-panel [{:keys [doc-id base-path building] :as args}]
  (let [building-index (<sub [::partial-review-buildings/selected-building-index])
        apartments (apartment-index-map doc-id base-path building-index)
        sections 1 ;; Only 1 section, the default section, supported for now
        total-apartments (count (vals apartments))
        commissioned (count (filter :commissioned? (vals apartments)))
        not-commissioned (- total-apartments commissioned)]
    [:div
     [:div [building-description-with-details building]]
     [:hr]
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :text-align "center"}}
      [:div
       [:div [:h2 sections]]
       [:div [:h3 (common/loc "task-katselmus.rakennus.osaa")]]]
      [:div
       [:div [:h2 total-apartments]]
       [:div [:h3 (common/loc "task-katselmus.rakennus.asuntoja")]]]
      [:div
       [:div [:h2 commissioned]]
       [:div [:h3 (common/loc "task-katselmus.rakennus.kayttoonotettu")]]]
      [:div
       [:div [:h2 not-commissioned]]
       [:div [:h3 (common/loc "task-katselmus.rakennus.ei-kayttoonotettu")]]]]
     [:hr]]))

(defn building-box [{:keys [doc-id base-path building-index status-select-schema checkbox-schema cell-args]}]
  (let [building-path (conj base-path building-index :rakennus)
        nat-id        (<sub [::value/current-field-value doc-id (conj building-path :valtakunnallinenNumero)])
        order-num     (<sub [::value/current-field-value doc-id (conj building-path :jarjestysnumero)])
        property-id   (<sub [::value/current-field-value doc-id (conj building-path :kiinttun)])
        selected-building-index (<sub [::partial-review-buildings/selected-building-index])
        apartment-states (apartment-index-map doc-id base-path building-index)
        apartments-commissioned (filter (fn [[_ state]]
                                          (:commissioned? state))
                                        apartment-states)
        apartments-commissioned-count (count (keys apartments-commissioned))
        apartment-count (count (keys apartment-states))
        percentage (int (* 100.0 (/ apartments-commissioned-count apartment-count)))

        ;; Find matching building from application buildings to get description
        buildings     (<sub [::application/buildings])
        building      (common/find-matching-building buildings nat-id (str order-num))]
    [:div.building-box
     {:class (when (= building-index selected-building-index)
               "building-box-selected")
      :on-click #(>evt [::partial-review-buildings/set-selected-building-index building-index])}
     [:b [building-description building]]
     [:div [render-child status-select-schema cell-args]]
     [:div [render-child checkbox-schema cell-args]]
     (when nat-id
       [:div [:p (str (common/loc :task-katselmus.rakennus.valtakunnallinenNumero) " " nat-id)]])
     (when property-id
       [:div [:p (str (common/loc :task-katselmus.rakennus.kiinteistotunnus) " " property-id)]])
     [:div {:style {:display "flex"}}
      [:progress {:value apartments-commissioned-count :max apartment-count}]
      [:p (str percentage "%")]]
     [:div [:p (str apartment-count " " (common/loc :task-katselmus.rakennus.asuntoa))]]]))

(sc/defn render
  [{:keys [doc-id schema] base-path :path :as args} :- schemas/DocgenFieldArguments]
  (let [tila-schema          (->> schema
                                  :body
                                  (util/find-by-key :name tila))
        status-select-schema (->> tila-schema
                                  :body
                                  (util/find-by-key :name tila))
        checkbox-schema      (->> tila-schema
                                  :body
                                  (util/find-by-key :name kayttoonottava))
        apartment-schema     (->> schema
                                  :body
                                  (util/find-by-key :name "rakennus")
                                  :body
                                  (util/find-by-key :name "apartments")
                                  :body
                                  (util/find-by-key :name "tila"))
        apartment-state-schema (->> apartment-schema
                                    :body
                                    (util/find-by-key :name "tila"))
        apartment-commissioned-schema (->> apartment-schema
                                           :body
                                           (util/find-by-key :name kayttoonottava))
        apartment-notes-schema (->> apartment-schema
                                    :body
                                    (util/find-by-key :name "notes"))
        apartment-timestamp-schema (->> apartment-schema
                                        :body
                                        (util/find-by-key :name "timestamp"))
        buildings            (<sub [::application/buildings])
        building-indexes     (<sub [::docs/field-child-keys doc-id base-path])
        selected-building-index (<sub [::partial-review-buildings/selected-building-index])
        apartment-index      (<sub [::partial-review-buildings/selected-apartment-index])
        mobile-selected-view (<sub [::partial-review-buildings/mobile-active-display])
        apartments-path      (conj base-path selected-building-index :rakennus :apartments)
        apartment-args       (-> args
                                 (dissoc :schema))
        state-args           (-> args
                                 (dissoc :schema)
                                 (assoc :path (conj base-path selected-building-index :rakennus :apartments apartment-index :tila)))]
    [(di/container schema)
     [:div.h2 (common/loc :task-katselmus.rakennukset)]
     [:div.mobile-navigation-panel
      {:style {:margin "10px"}}
      (case mobile-selected-view
        :all-buildings nil
        :building      [:button {:on-click (fn [_] (>evt [::partial-review-buildings/set-selected-building-index nil]))}
                   (common/loc "task-katselmus.rakennus.back-to-all-buildings")]
        :apartment     [:button {:on-click (fn [_] (>evt [::partial-review-buildings/close-side-panel]))}
                    (common/loc "task-katselmus.rakennus.back-to-building")])]
     (if (empty? building-indexes)
       [:span (common/loc :task-katselmus.no-buildings)]
       [:div.main-building-container
        [:div {:class (cond-> ["building-sidebar" "panel"]
                        (= :all-buildings mobile-selected-view) (conj "panel-active-mobile"))}
         [:div (doall (for [building-index building-indexes]
                        (let [cell-args (-> args
                                            (dissoc :schema)
                                            (assoc :path (conj base-path building-index :tila)))]
                          ^{:key building-index}
                          [building-box (util/mmap doc-id
                                                   base-path
                                                   building-index
                                                   checkbox-schema
                                                   status-select-schema
                                                   cell-args)])))]]
        [:div {:class (cond-> ["building-content-container" "panel"]
                        (= :building mobile-selected-view) (conj "panel-active-mobile"))}
         (when selected-building-index
           (let [building (nth buildings selected-building-index)]
             [:div.building-info
              [building-panel (util/mmap doc-id
                                         base-path
                                         building
                                         checkbox-schema
                                         status-select-schema selected-building-index)]]))

         (when selected-building-index
           [building-sections (util/mmap doc-id
                                         base-path
                                         apartments-path
                                         apartment-args
                                         apartment-commissioned-schema)])]
        [:div {:class (cond-> ["apartment-info-sidebar-container" "panel"]
                        (= :apartment mobile-selected-view) (conj "panel-active-mobile"))}
         (when (and selected-building-index apartment-index)
           (let [sidepanel-args {:doc-id                        doc-id
                                 :base-path                     base-path
                                 :apartment-state-args          state-args
                                 :apartments-path               apartments-path
                                 :apartment-commissioned-schema apartment-commissioned-schema
                                 :apartment-state-schema        apartment-state-schema
                                 :apartment-notes-schema        apartment-notes-schema
                                 :apartment-timestamp-schema    apartment-timestamp-schema}]
             [apartment-sidepanel sidepanel-args apartment-index]))]])]))
