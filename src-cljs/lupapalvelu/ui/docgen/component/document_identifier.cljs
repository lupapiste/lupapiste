(ns lupapalvelu.ui.docgen.component.document-identifier
  "Provides a simple editor for identifier fields (iow. 'Tunnus') in the accordion.
  See `document-identifier-[template.html, model.js]`"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.identifiers :as identifiers]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::highlight-required?
  (fn highlight-requiredp-inputfn [[_ doc-id path]]
    {:pre [(string? doc-id) (vector? path)]}
    [(rf/subscribe [::identifiers/identifier doc-id])
     (rf/subscribe [::value/current-field-value doc-id path])])
  (fn [[identifier value]]
    (and (:required? identifier)
         (nil? (ss/blank-as-nil value)))))

(rf/reg-sub ::may-update-doc-identifier?
  (fn may-update-doc-identifier-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :update-doc-identifier override-auth-source]))
  :-> identity)

(rf/reg-event-fx ::update-identifier
  (fn [cofx [_ {:keys [app-id doc-id identifier value on-success on-error]}]]
    (nutil/merge-every-fn cofx
                          [;; Also update the field so that the UI updates immediately
                           [value/fields-edited-fx
                            {:doc-id      doc-id
                             :field-paths [[identifier]]
                             :values      [value]}]
                           ;; Send update command to backend
                           [command/update-doc-identifier-fx
                            {:id         app-id
                             :doc-id     doc-id
                             :identifier identifier
                             :value      value
                             :on-success on-success
                             :on-error   on-error}]
                           ;; The identifier editor is show when a) the editor is toggled
                           ;; visible or b) the value is missing. In the latter case we want to
                           ;; make sure the editor remains open when the initial value is written,
                           ;; otherwise the editor might suddenly close when neither case no longer
                           ;; applies.
                           [identifiers/toggle-fx {:doc-id   doc-id
                                                   :enabled? true}]])))

(defn- notify-identifier-listeners-fx
  [_ [_ doc-id path value]]
  ;; NOTE: The attachment service resets the attachment types if
  ;; the document identifier is modified
  ;; NOTE: This is also sent to the `accordionService`, which just stores
  ;; the new identifier value (due to the `cljs` flag). This should help
  ;; keep the old js consumers of `accordionService` up-to-date.
  ;; NOTE: In the original implementation this was sent by the `DocumentIdentifierModel`,
  ;; which this component replaces.
  {:fx [[:hub/send {:event "accordionService::saveIdentifier"
                    :data  {:cljs  true
                            :docId doc-id
                            :key   (first path)
                            :value (ss/trim value)}}]]})

(defn- make-callback
  [{:keys [app-id doc-id path]}]
  (fn [value]
    ;; The identifier is essentially just a field in the schema,
    ;; just like any other field. So we can leverage the existing processing
    ;; for fields and values.
    (let [on-success-args {:app-id        app-id
                           :doc-id        doc-id
                           :field-paths   [path]
                           :values        [value]
                           ;; Unlike the legacy implementation, we notify the attachment
                           ;; service _after_ the new identifier has been persisted
                           :extra-effects [[notify-identifier-listeners-fx doc-id path value]]}
          args            {:app-id     app-id
                           :doc-id     doc-id
                           :identifier (first path)
                           :value      value
                           :on-success [::docgen-common/on-save-success on-success-args]
                           :on-error   [::docgen-common/on-save-error on-success-args]}]
      (>evt [::update-identifier args]))))

(defn- editor
  [{:keys [id doc-id path override-auth-source]}]
  (let [app-id    (<sub [::app/application-id])
        disabled? (not (<sub [::may-update-doc-identifier? doc-id override-auth-source]))
        focus-ref (focus/use-focus doc-id path)]
    [ui-input/text-input
     (cond-> {:id          id
              :class       ["lux" "align--middle"]
              :max-length  (:max-length (<sub [::identifiers/identifier doc-id]))
              :required?   (<sub [::highlight-required? doc-id path])
              :debounce-ms docgen-common/default-input-debounce-ms
              :value       (<sub [::value/current-field-value doc-id path])
              :ref         focus-ref}
       disabled?       (assoc :disabled? true)
       ;; The legacy implementation persisted the value (almost) immediately
       ;; so let's do that here too.
       (not disabled?) (assoc :on-change (make-callback (util/mmap app-id doc-id path))))]))

(defn- label
  [{:keys [id doc-id path]}]
  [:label.lux.lux--inline.gap--r1-half.align--middle
   {:for   id
    :class [(when (<sub [::highlight-required? doc-id path])
              :required)]}
   (common/loc :document.operation-identifiers.identifier)])

(defn render
  "Render the identifier editor.
  Just a label and an input field."
  [{:keys [doc-id override-auth-source]}]
  (let [id   (str doc-id "-identifier-input")
        path (<sub [::identifiers/identifier-path doc-id])]
    [:div
     [label (util/mmap id doc-id path)]
     " "
     [:f> editor (util/mmap id doc-id path override-auth-source)]
     [ping/ping {:doc-id     doc-id
                 :field-path path}]]))
