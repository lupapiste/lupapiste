(ns lupapalvelu.ui.docgen.field.property-list
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.show-hide :as show-hide]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.map-wrapper :refer [LazyApplicationMap]]
            ;; These can be moved here later, when old docgen is removed
            [lupapalvelu.ui.property-list.property-table :refer [date-field input-field property-id-field select-field select-option-loc]]
            [lupapalvelu.ui.util :refer [cookie-anti-forgery-value]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as str]
            [sade.util :as util]
            [schema.core :as sc]))

(defn get-property-id-field-key [schema]
  (keyword (or (:propertyIdField schema) "kiinteistotunnus")))

(defn normalize-property-id [id]
  (-> (try
        (js/util.prop.toDbFormat id)
        (catch :default _e
          id))
      str/blank-as-nil
      str/trim))

(defn format-property-id [id]
  (-> (try
        (js/util.prop.toHumanFormat id)
        (catch :default _e
          id))
      str/blank-as-nil
      str/trim))

(defn selected-property-ids [{:keys [schema doc-id path] :as _args} child-keys get-field-value]
  (let [property-id-field (get-property-id-field-key schema)]
    ;; NOTE: Property-ids are normalize in db (db format, zeros, no dashes).
    ;; Values written in UI and stored to ::committed, can be in user written format (could have dashes, missing leading zeros).
    (->> (keep (fn [row-id]
                 (normalize-property-id (get-field-value doc-id (conj path row-id property-id-field))))
               child-keys)
         (set))))

(rf/reg-sub ::selected-property-ids
  (fn [[_ {:keys [doc-id path] :as _args}]]
    (rf/subscribe [::docs/field-child-keys doc-id path]))
  (fn [child-keys [_ args]]
    (selected-property-ids args child-keys (fn [doc-id path] (<sub [::value/current-field-value doc-id path])))))

;; map-component follows the location and selectedProperties with useEffect,
;; using identity comparison, and changes trigger side-effects on OpenLayers.
;; Use subs to create stable values.
(rf/reg-sub ::js-selected-property-ids
  (fn [[_ args]]
    (rf/subscribe [::selected-property-ids args]))
  (fn [ids [_]]
    (to-array ids)))

(rf/reg-sub ::js-location
  :<- [::app/location-wgs84]
  (fn [[lon lat] _]
    #js [lon lat]))

(rf/reg-event-fx ::map-selection
  (fn [{:keys [db]} [_ args new-selection]]
    (let [{:keys [doc-id path]} args
          child-keys            (docs/field-child-keys (docs/db->field-data db doc-id path))
          selected-properties   (selected-property-ids args child-keys (fn [doc-id path] (committed/db->backend-submitted-value db doc-id path)))
          new-ids               (set/difference new-selection selected-properties)
          remove-ids            (set/difference selected-properties new-selection)]

      ;; NOTE: The difference should always be just one row to add or remove.
      ;; NOTE: Due to how deletion confirm modal works, only single confirm
      ;; modal can be opened, so multiple delete events would be lost.
      {:fx (concat (map (fn [property-id]
                          [:dispatch [::create-property-row args property-id]])
                        new-ids)
                   (map (fn [property-id]
                          [:dispatch [::delete-property args property-id]])
                        remove-ids))})))

(defn create-and-save-row [{:keys [db]} {:keys [app-id doc-id]} path data]
  ;; NOTE: add-group-child doesn't add the values under ::documents, just the key with placeholder.
  ;; values are only stored under ::committed.
  (let [field-data    (docs/db->field-data db doc-id path)
        child-indexes (docs/field-child-keys field-data)
        next-idx      (docs/next-child-id child-indexes)]
    (when-let [db (docs/add-group-child db {:doc-id                doc-id
                                            :path                  path
                                            :next-idx              next-idx
                                            ;; e.g. property doesn't have the :owners field before first one is added?
                                            :ignore-missing-parent true})]
      (let [row-path      (conj path next-idx)
            ;; need to construct path-value pairs for BE updates and the callback
            path-values   (keep (fn [[k v]]
                                  (when v
                                    [(conj row-path (keyword k)) v]))
                                data)
            callback-args {:app-id      app-id
                           :doc-id      doc-id
                           :field-paths (mapv first path-values)
                           :values      (mapv second path-values)}]

        (nutil/merge-every-fn {:db db}
                              ;; NOTE: If value/path-values is empty, there is no need to send the empty update-doc request.
                              ;; JS version always sent the update-doc.
                              (if (seq path-values)
                                [[command/update-doc-fx
                                  (assoc callback-args
                                         :on-success [::docgen-common/on-save-success callback-args]
                                         :on-error   [::docgen-common/on-save-error callback-args])]]
                                []))))))

(rf/reg-event-fx ::create-empty-row
  (fn [cofx [_ args path]]
    (create-and-save-row cofx args path {})))

(rf/reg-event-fx ::create-property-row.error
  (fn [cofx [_ res args path data]]
    (-> cofx
        (create-and-save-row args path data)
        (assoc :indicator/saved res))))

(rf/reg-event-fx ::create-property-row
  (fn [cofx [_ args property-id]]
    (let [{:keys [path schema]}           args
          building-site-information-auth? (auth/application-auth? :building-site-information)
          data                            {(get-property-id-field-key schema) property-id}]
      (if (or (not property-id)
              (not building-site-information-auth?))
        (create-and-save-row cofx args path data)
        {:action/query {:name    :building-site-information
                        :params  {:propertyId property-id}
                        :success [::create-property-row.success args property-id]
                        :error   [::create-property-row.error args path data]}}))))

(rf/reg-event-fx ::create-property-row.success
  (fn [cofx [_ resp args property-id]]
    ;; TODO: Always empty response locally, test this with mock data?
    (let [{:keys [path schema]} args
          ;; Need to use keywords here
          property-id-field   (get-property-id-field-key schema)
          estate-name-field   (keyword (or (:estateNameField schema) "tilanNimi"))
          register-date-field (keyword (or (:registerDateField schema) "rekisterointiPvm"))
          surface-area-field  (keyword (or (:surfaceAreaField schema) "pintaAla"))

          data {property-id-field   property-id
                estate-name-field   (:nimi (:data resp))
                register-date-field (:rekisterointipvm (:data resp))
                surface-area-field  (:maapintaala (:data resp))}]
      (create-and-save-row cofx args path data))))

(rf/reg-event-fx ::delete-row-clicked
  (fn [_ [_ args row-path]]
    {:dialog/show {:type   :yes-no
                   :ltitle :areyousure
                   :size   :medium
                   :ltext  (util/kw-path :kiinteistoLista
                                         (when (-> (butlast row-path) last (util/=as-kw :omistajat))
                                           :omistajat)
                                         :confirm.remove)
                   :event  [::delete-row args row-path]}}))

(rf/reg-event-fx ::delete-property
  (fn [{:keys [db]} [_ args property-id]]
    (let [{:keys [schema doc-id path]} args
          property-id-field (get-property-id-field-key schema)
          child-keys (docs/field-child-keys (docs/db->field-data db doc-id path))
          row-id (some (fn [row-id]
                         (let [v (normalize-property-id (committed/db->backend-submitted-value db doc-id (conj path row-id property-id-field)))]
                           (when (= property-id v)
                             row-id)))
                       child-keys)]
      {:dispatch [::delete-row-clicked args (conj path row-id)]})))

(rf/reg-event-fx ::delete-row
  (fn [{:keys [db]} [_ {:keys [app-id doc-id] :as args} row-path]]
    (let [{remove-cmd :remove
           coll       :collection} (docs/get-document-commands db doc-id)]
      {:action/command {:name    remove-cmd
                        :params  {:id         app-id
                                  :doc        doc-id
                                  :collection coll
                                  :path       (path/stringify-path row-path)}
                        :success [::delete-row.success args row-path]}})))

(rf/reg-event-fx ::delete-row.success
  (fn [{:keys [db]} [_ resp {:keys [doc-id]} row-path]]
    (let [kw-path (path/keywordify-path row-path)]
      (-> {:db (update-in db [::docs/documents doc-id :data] update-in (butlast kw-path) dissoc (last kw-path))}
          ;; Need to handle similar effects to update local state as
          ;; ::docgen-common/on-save-success,
          (committed/clean-committed-fields doc-id row-path)
          (validation/commit-field-errors [nil doc-id resp])))))

(rf/reg-event-fx ::fetch-owners
  (fn [_ [_ args path property-id-value]]
    (when property-id-value
      {:action/query {:name :owners
                      :params {:propertyIds [property-id-value]}
                      :success [::fetch-owners.success args path]}})))

(rf/reg-event-fx ::fetch-owners.success
  (fn [{:keys [db]} [_ resp args path]]
    ;; Add each response owner as a new group item
    ;; TODO: cofx is here both the input and the result (effects),
    ;; figure if this can be cleaned to make more sense.
    (reduce (fn [cofx owner]
              (let [contact (or (:yhteyshenkilo owner)
                                owner)
                    value {:etunimi              (:etunimet contact)
                           :sukunimi             (:sukunimi contact)
                           :nimi                 (:nimi owner)
                           :yritystunnus         (:ytunnus owner)
                           :osoite               (:jakeluosoite contact)
                           :postinumero          (:postinumero contact)
                           :postitoimipaikannimi (:paikkakunta contact)
                           :henkilolaji          (:henkilolaji owner)}
                    new-state (create-and-save-row cofx args path value)]
                ;; One update-doc req per owner. Could be one update-doc request also.
                ;; NOTE: nutil/merge-every-fn didn't work to merge cofx fx + new fxs.
                ;; Merge fxs manually.
                (assoc cofx
                       :db (:db new-state)
                       :fx (into (:fx cofx) (:fx new-state)))))
            {:db db}
            (:owners resp))))

;;
;; Reimplementation of lupapalvelu.ui.property-list.property-table
;;
;; Changes:
;; - no building hiccup with map and into []
;; - no merging all the different types parameters into one props map

(defn item-buttons
  "The edit and remove buttons for a single item (property or field group row)"
  [{:keys [editing? focus? args edit-fn remove-fn]}]
  (let [{:keys [disabled?]} args]
    (when (and (not disabled?)
               (or editing? focus?))
      [:<>
       [components/icon-button
        {:icon     (when-not editing? :lupicon-pen)
         :text-loc (if editing? :edit.stop :edit)
         :class    (if editing? :primary :tertiary)
         :on-click edit-fn}]
       [components/icon-button
        {:icon     :lupicon-trash
         :text-loc :remove
         :class    :secondary
         :on-click remove-fn}]])))

(defn label
  "A per-input label"
  [{:keys [id schema]}]
  (let [{:keys [required i18nkey unit]} schema]
    [:div
     [:label.lux.form-label-string
      {:for   id
       :class (when required "required")}
      (loc i18nkey)
      (when unit (str " (" (loc (str "unit." unit)) ")"))]]))

(defn error-message
  "A warning span that are collected into a list for the user to explain validation errors"
  [error-level error-code {:keys [loc aria-errormessage] {:keys [i18nkey]} :schema}]
  (when (and error-level
             error-code
             (not= error-level :tip))
    (let [field-label (loc i18nkey)
          error-text  (loc (str "error." error-code))]
      [:div.warnings
       {:role       "alert"
        :aria-label (str field-label ": " error-text)}
       [:span.warning
        {:aria-hidden true
         :id          aria-errormessage}
        error-text]])))

(defn property-field
  [{:keys [args path schema _focus? editing? hidden-label?]}]
  (let [{:keys [app-id doc-id disabled?]} args
        field-name    (:name schema)
        schema-type   (:type schema)
        field-path    (conj path (keyword field-name))]
    (when (show-hide/show-field? {:doc-id doc-id
                                  :path   field-path
                                  :schema schema})
      (let [;; This check the schema subtype, for string field, not the field name.
            property-id?  (= (:subtype schema) "kiinteistotunnus")
            value         (cond-> (or (<sub [::value/current-field-value doc-id field-path])
                                      "")
                            property-id? format-property-id)
            backend-value (<sub [::committed/backend-submitted-value doc-id field-path])
            element-id    (str/join "-" (cons doc-id field-path))

            validation (<sub [::validation/field-validation doc-id field-path])
            {error-level :level, error-code :error} (first validation)

            input-opts    {:id                element-id
                           :key               element-id
                           :loc               loc
                           :name              field-name
                           :class             (str "lux " (some-> error-level name))
                           :value             value
                           :schema            schema
                           ;; :test-id           (-> field-path util/kw-path name)
                           ;; at least input-field uses :on-blur
                           :callback          (docgen-common/make-on-blur
                                                {:app-id        app-id
                                                 :doc-id        doc-id
                                                 :backend-value backend-value
                                                 :path          field-path
                                                 :emit-data     nil
                                                 :extra-effects nil})
                           :aria-invalid      (and (some? error-code)
                                                   (not= error-level "tip"))
                           :aria-errormessage (str element-id "_error")}]
        [:div
         (when-not hidden-label?
           [label
            {:schema schema
             :id element-id}])
         (if (or (not editing?) disabled?)
           [:span {:id element-id}
            (cond
              (= schema-type "select") (select-option-loc loc schema value)
              (some? value)            value
              :else                    "")]
           (cond
             (= schema-type "date")   [date-field input-opts]
             (= schema-type "select") [select-field input-opts]
             property-id?             [property-id-field input-opts]
             :else                    [input-field input-opts]))
         [error-message error-level error-code input-opts]]))))

(defn field-group-row
  [{:keys [args row-id path schema]}]
  (r/with-let [editing?* (r/atom false)
               focus?*   (r/atom false)]
    (let [editing? @editing?*
          focus?   @focus?*

          path     (conj path row-id)]
      [:div.form-card.flex--between.flex--gap2
       {:tab-index 0
        :class     (when (or editing? focus?) "focus")
        :on-focus  (fn [_e] (reset! focus?* true))
        :on-blur   (fn [_e] (reset! focus?* false))}
       [:div.flex--wrap.flex--gap1.flex--column-gap2
        (for [field-schema (:body schema)]
          ^{:key (:name field-schema)}
          [property-field
           {:args          args
            :path          path
            :schema        field-schema
            :editing?      editing?
            :focus?        focus?
            :hidden-label? (not editing?)}])]
       [:div.button-panel.flex--right.flex--gap1
        [item-buttons
         {:args      args
          :editing?  editing?
          :focus?    focus?
          :edit-fn   (fn [_e] (swap! editing?* not))
          :remove-fn (fn [_e]
                       (>evt [::delete-row-clicked args path]))}]]])))

(defn field-group-buttons
  [{:keys [args parent-path path focus? editing? schema]}]
  (let [{:keys [disabled? doc-id]} args

        top-schema   (:schema args)
        owners-field (or (:ownersField top-schema) "omistajat")
        property-id-field (or (:propertyIdField schema) "kiinteistotunnus")]
    (when (and (not disabled?)
               (or editing? focus?))
      (let [group-name (:name schema)]
        [:<>
         [components/icon-button
          {:icon     :lupicon-plus
           :text-loc (util/kw-path :kiinteistoLista group-name :_append_label)
           :class    :primary
           :on-click (fn [_e] (>evt [::create-empty-row args path]))}]
         (when (= owners-field (:name schema))
           (let [prop-path (conj parent-path (keyword property-id-field))
                 property-id (<sub [::value/current-field-value doc-id prop-path])
                 fetch-allowed (and property-id
                                    (<sub [:auth/application? :owners]))]
             (when fetch-allowed
               [components/icon-button
                {:icon     :lupicon-refresh
                 :text-loc (util/kw-path :kiinteistoLista group-name :_fetch_label)
                 :class    :secondary
                 :on-click (fn [_e] (>evt [::fetch-owners args path property-id]))}])))]))))

(defn field-group
  [{:keys [path args schema focus? editing?]}]
  (let [{:keys [doc-id]} args

        field-path (conj path (keyword (:name schema)))
        {:keys [i18nkey]} schema
        row-ids (<sub [::docs/field-child-keys doc-id field-path])]
    (when (or focus?
              editing?
              (not-empty row-ids))
      [:div.flex--column.flex--gap1
       [:label.form-label (loc i18nkey)]
       (for [row-id row-ids]
         ^{:key row-id}
         [field-group-row
          {:args   args
           :path   field-path
           :row-id row-id
           :schema schema}])
       [:div.flex--wrap.flex--gap1
        [field-group-buttons
         {:args     args
          :parent-path path
          :path     field-path
          :schema   schema
          :focus?   focus?
          :editing? editing?}]]])))

(defn property-row [{:keys [args row-id]}]
  (r/with-let [editing?* (r/atom false)
               focus?*   (r/atom false)]
    (let [editing? @editing?*
          focus?   @focus?*

          {:keys [schema path]} args

          field-group?   (fn [field-schema] (-> field-schema :type #{"table"}))

          row-path       (conj path row-id)
          fields         (:body schema)
          regular-fields (remove field-group? fields)
          group-fields   (filter field-group? fields)]
      [:div.form-card.flex--column.flex--gap2
       {:tab-index 0
        :class     (when (or @editing?* @focus?*) "focus")
        :on-focus  #(reset! focus?* true)
        :on-blur   #(reset! focus?* false)}
       [:div.flex--between.flex--column-gap7
        [:div.flex--wrap.flex--gap1.flex--column-gap2
         (for [field-schema regular-fields]
           ^{:key (:name field-schema)}
           [property-field
            {:args      args
             :path      row-path
             :schema    field-schema
             :focus?    focus?
             :editing?  editing?}])]

        [:div.button-panel.flex--right.flex--gap1
         [item-buttons
          {:args      args
           :editing?  editing?
           :focus?    focus?
           :edit-fn   (fn [_e] (swap! editing?* not))
           :remove-fn (fn [_e]
                        (>evt [::delete-row-clicked args row-path]))}]]]

       [:div.flex--column.flex--gap3
        (for [field-schema group-fields]
          ^{:key (:name field-schema)}
          [field-group
           {:args      args
            :path      row-path
            :schema    field-schema
            :focus?    focus?
            :editing?  editing?}])]])))

(sc/defn render
  "Native docgen CLJS component

  See docgen-property-list-model.js"
  [{:keys [app-id doc-id schema disabled? path] :as args} :- schemas/DocgenFieldArguments]
  (let [doc-i18n-name  (<sub [::docs/i18n-name doc-id])
        title          (docgen-loc/loc-key-with-suffix :_group_label false doc-i18n-name path schema)
        help           (let [loc-k (docgen-loc/loc-key-with-suffix :help false doc-i18n-name path schema)]
                         (when (common/loc-has-term? loc-k)
                           loc-k))]
    [:div.application-map-container
     [:div.flex--column.flex--gap3
      (when title
        [:div.group-title.strong (loc title)])

      (when help
        [:div (loc help)])

      [:div.react-map-component.extended.property-list
       [:> LazyApplicationMap
        {:applicationID               app-id
         :mapRefresher                "extended"
         :locFn                       loc
         :hideDrawings                true
         :forcedInteractionState      (if disabled? "DEFAULT" "SELECT_PROPERTIES")
         :onSelectedPropertiesChanged (fn [js-selected]
                                        (>evt [::map-selection args (set js-selected)]))
         :selectedProperties          (<sub [::js-selected-property-ids args])

         ;; ApplicationMap component converts from LonLat to the used projection.
         ;; See docmodel.js buildGroupComponent, uses wgs84 instead of the better one.
         :center                      (<sub [::js-location])
         :requestHeaders              #js {"x-anti-forgery-token" (cookie-anti-forgery-value)}}]]

      [:div.flex--column.flex--gap2
       (for [row-id (<sub [::docs/field-child-keys doc-id path])]
         ^{:key row-id}
         [property-row
          {:args   args
           :row-id row-id}])

       (when-not disabled?
         [:div
          [components/icon-button
           {:class    :positive
            :text-loc :kiinteistoLista._append_label
            :icon     :lupicon-plus
            :on-click (fn [_e] (>evt [::create-empty-row args path]))}]])]]]))
