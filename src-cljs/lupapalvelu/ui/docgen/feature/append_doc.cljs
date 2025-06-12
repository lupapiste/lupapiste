(ns lupapalvelu.ui.docgen.feature.append-doc
  "Some documents permit additional copies"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::last-document?
  (fn [[_ doc-id doc-ids]]
    {:pre [(string? doc-id)
           (vector? doc-ids)]}
    [(rf/subscribe [::docs/documents])
     (rf/subscribe [::docs/schema-info doc-id])])
  (fn [[documents {schema-name :name}] [_ doc-id doc-ids]]
    (= (->> doc-ids
            (filter (fn [id]
                      (= (-> (get documents id) :schema :info :name)
                         schema-name)))
            last)
       doc-id)))

(rf/reg-sub ::show-button?
  (fn [[_ doc-id doc-ids _disabled? override-auth-source]]
    {:pre [(string? doc-id)
           (vector? doc-ids)]}
    [(rf/subscribe [::auth/auth? doc-id :create-doc override-auth-source])
     (rf/subscribe [::docs/schema-info doc-id])
     (rf/subscribe [::last-document? doc-id doc-ids])])
  (fn [[auth-ok? {:keys [repeating no-repeat-button]} last-document?]
       [_ _doc-id _doc-ids disabled? _override-auth-source]]
    (and auth-ok?
         repeating
         (not no-repeat-button)
         (not disabled?)
         last-document?)))

(rf/reg-event-fx ::append-doc-clicked
  (fn [_ [_ app-id schema-name]]
    {:fx [[:action/command {:name    :create-doc
                            :params  {:id         app-id
                                      :schemaName schema-name
                                      :collection :documents}
                            :success [::create-doc.success app-id]
                            :error   [::create-doc.error schema-name]}]]}))

(rf/reg-event-fx ::create-doc.error
  (fn [_ [_ {:keys [text]} schema-name]]
    (when (util/=as-kw text :error.only-one-active)
      (let [schema-text (common/loc (util/kw-path schema-name :_group_label))
            modal-text  (common/loc :document.party.only-one-active schema-text)]
        {:fx [[:dialog/show {:ltitle :error.dialog.title
                             :text   modal-text
                             :size   :small}]]}))))

(rf/reg-event-fx ::create-doc.success
  (fn [_ [_ _resp app-id]]
    ;; The original JS implementation in `docgen.js` built the new document
    ;; quite elaborately, which involves among other things updating
    ;; the accordion and auth models of sibling documents.
    ;; Instead, we'll just refresh the application.
    {:fx [[:application/load app-id]]}))

(defn append-doc-button
  [{:keys [app-id doc-id]}]
  (let [{schema-name :name} (<sub [::docs/schema-info doc-id])]
    [:button.secondary
     {:on-click (fn [] (>evt [::append-doc-clicked app-id schema-name]))}
     [:i.lupicon-circle-plus]
     [:span (common/loc (util/kw-path schema-name :_append_label))]]))
