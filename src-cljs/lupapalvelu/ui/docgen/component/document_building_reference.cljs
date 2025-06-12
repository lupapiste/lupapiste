(ns lupapalvelu.ui.docgen.component.document-building-reference
  "Provides a simple editor for building reference fields `building-ref` in the accordion."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(rf/reg-event-fx ::update-building-ref.success
  (fn [cofx [_ _resp {:keys [doc-id] :as args}]]
    (nutil/merge-every-fn cofx
                          [[ping/show-ping-fx {:doc-id     doc-id
                                               :field-path [doc-id :building-ref]
                                               :status     :ok}]])))

(rf/reg-event-fx ::update-building-ref
  (fn [{:keys [db]} [_ {:keys [app-id doc-id value] :as args}]]
    {:db (assoc-in db [::docs/documents doc-id :building-ref] value)
     :fx [[:action/command {:name       :update-building-product-catalog-operation
                            :params     {:id  app-id
                                         :doc doc-id
                                         :ref value}
                            :success    [::update-building-ref.success args]}]]}))

(defn op-title [doc]
  (let [schema-name (some-> doc :schema-info :name)
        tunnus      (or (<sub [::value/current-field-value (:id doc) [:tunnus]])
                        (some-> doc :data :tunnus :value ss/blank-as-nil))
        doc-name    (or (<sub [::value/current-field-value (:id doc) [:kaytto :nimi]])
                        (some-> doc :data :kaytto :nimi :value ss/blank-as-nil))]
    (str (common/loc (str schema-name "._group_label"))
         (when doc-name
           (str " - " doc-name))
         (when tunnus
           (str " - " tunnus)))))

(defn- set-title [doc]
  (assoc doc :title (op-title doc)))

(rf/reg-sub ::operation-documents-choices
  :<- [::docs/op-documents]
  (fn [docs]
    (->> docs
         vals
         (map set-title)
         (map (fn [{:keys [id title]}]
                {:value id :label (or title id)}))
         (sort-by (juxt :label :value))
         (into []))))

(defn- label
  [{:keys [id]}]
  [:label.lux.lux--inline.gap--r1-half.align--middle
   {:for id}
   (common/loc :document.building-ref)])

(defn render
  "Render the document building reference editor.
  Shows a select dropdown to change the building-ref to any document with :op in its :schema-info.

  TODO This should later on reference a building and not the operation document."
  [{:keys [doc-id override-auth-source]}]
  (let [id             (str doc-id "-building-ref-select")
        app-id         (<sub [::app/application-id])
        disabled?      (not (<sub [::auth/auth? doc-id :update-building-product-catalog-operation override-auth-source]))
        op-doc-choices (<sub [::operation-documents-choices set-title])
        value          (get-in (<sub [::docs/document doc-id]) [:building-ref])
        on-change      (when-not disabled?
                         #(>evt [::update-building-ref {:doc-id doc-id :value % :app-id app-id}]))]
    [:div.py-4
     [label {:id id}]
     [ui-input/select
      {:id        id
       :class     ["lux" "align--middle" "gap--r1-half" "ml-2"]
       :disabled? disabled?
       :value     (or value "")
       :on-change on-change}
      op-doc-choices]
     [ping/ping {:doc-id     doc-id
                 :field-path [doc-id :building-ref]}]]))
