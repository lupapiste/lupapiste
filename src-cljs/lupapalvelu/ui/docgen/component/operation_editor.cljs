(ns lupapalvelu.ui.docgen.component.operation-editor
  "See `operation-editor-[template.html, model.js]`"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.component.primary-operation :as primary-operation]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.operation :as operation]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::may-update-op-description?
  (fn may-change-primary-operation-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :update-op-description override-auth-source]))
  :-> identity)

(defn- notify-subscribers-fx
  [_ [_ {:keys [app-id op-id value]}]]
  ;; Some JS listeners expect to see this event
  {:fx [[:hub/send {:event "op-description-changed"
                    :data  {:appId   app-id
                            :op-id   op-id
                            :op-desc (ss/trim value)}}]]})

(rf/reg-event-fx ::update-op-description.success
  (fn [cofx [_ _resp {:keys [doc-id] :as args}]]
    (nutil/merge-every-fn cofx
                          [[notify-subscribers-fx args]
                           [ping/show-ping-fx {:doc-id     doc-id
                                               :field-path [doc-id :operation]
                                               :status     :ok}]])))

(rf/reg-event-fx ::update-op-description.error
  (fn [cofx [_ _resp {:keys [doc-id]}]]
    (nutil/merge-every-fn cofx
                          [[ping/show-ping-fx {:doc-id     doc-id
                                               :field-path [doc-id :operation]
                                               :status     :err}]])))

(defn- update-op-description-fx
  [_ [_ {:keys [app-id op-id value] :as args}]]
  {:fx [[:action/command {:name    :update-op-description
                          :params  {:id    app-id
                                    :op-id op-id
                                    :desc  value}
                          :success [::update-op-description.success args]
                          :error   [::update-op-description.error args]}]]})

(defn- notify-operation-listeners-fx
  [_ [_ {:keys [app-id op-id value]}]]
  ;; NOTE: This is sent to the `accordionService`, which just stores
  ;; the new value. This should help keep the old js consumers of `accordionService` up-to-date.
  ;; NOTE: In the original implementation this was sent by the `OperationEditorModel`,
  ;; which this component replaces.
  {:fx [[:hub/send {:event "accordionService::saveOperationDescription"
                    :data  {:cljs        true
                            :appId       app-id
                            :operationId op-id
                            :description (ss/trim value)}}]]})

(rf/reg-event-fx ::update-op-description
  (fn [cofx [_ args]]
    (nutil/merge-every-fn cofx
                          [[operation/change-description-fx args]
                           [update-op-description-fx args]
                           [notify-operation-listeners-fx args]])))

(defn- make-callback
  [args]
  (fn [value]
    (>evt [::update-op-description (assoc args :value value)])))

(defn- editor
  [{:keys [id doc-id override-auth-source]}]
  (let [app-id    (<sub [::app/application-id])
        op-id     (:id (<sub [::docs/operation doc-id]))
        disabled? (not (<sub [::may-update-op-description? doc-id override-auth-source]))]
    [ui-input/text-input
     (cond-> {:id          id
              :class       ["lux" "align--middle" "long"]
              :debounce-ms docgen-common/default-input-debounce-ms
              :value       (<sub [::operation/current-description doc-id])}
       disabled?       (assoc :disabled? true)
       ;; The legacy implementation persisted the value (almost) immediately
       ;; so let's do that here too.
       (not disabled?) (assoc :on-change (make-callback (util/mmap app-id doc-id op-id))))]))

(defn- label
  [{:keys [id]}]
  [:label.lux.lux--inline.gap--r1-half.align--middle
   {:for   id}
   (common/loc :document.operation-editor.description)])

(defn render
  "Render the operation editor.
  Just a label and an input field for the operation description,
  and a button for marking the operation as the primary operation."
  [{:keys [doc-id override-auth-source]}]
  (let [id   (str doc-id "-op-description-editor")
        path [doc-id :operation]]
    [:div
     [label (util/mmap id)]
     " "
     [editor (util/mmap id doc-id)]
     [ping/ping {:doc-id     doc-id
                 :field-path path
                 :container-class "ping--block"}]
     (when (not (<sub [::app/archiving-project?]))
       [primary-operation/set-as-primary-button (util/mmap doc-id override-auth-source)])]))
