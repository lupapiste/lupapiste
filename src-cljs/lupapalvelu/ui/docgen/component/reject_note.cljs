(ns lupapalvelu.ui.docgen.component.reject-note
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.user :as user]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

;;; Init

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [_app]}]]
  {:db (assoc db ::data {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::toggles
  :<- [::data]
  :-> :toggles)

(rf/reg-sub ::notes
  :<- [::data]
  :-> :notes)

(rf/reg-sub ::editor-toggle
  :<- [::toggles]
  (fn [data [_ doc-id path]]
    (get-in data [doc-id path])))

(defn set-editor-toggle
  "Show or hide the reject note editor."
  [{:keys [db]} [_ {:keys [doc-id path enabled?]}]]
  {:pre [(string? doc-id) (boolean? enabled?)]}
  {:db (-> db
           ;; Make sure all other editors for the document are closed
           (assoc-in [::data :toggles doc-id] {})
           (assoc-in [::data :toggles doc-id path] enabled?))})

(rf/reg-event-fx ::set-editor-toggle
  set-editor-toggle)

(rf/reg-sub ::modified-note
  :<- [::notes]
  (fn [notes [_ doc-id path]]
    (ss/blank-as-nil (get-in notes [doc-id path]))))

(defn- set-note
  [{:keys [db]} [_ {:keys [doc-id path note]}]]
  {:pre [(string? doc-id) (string? note)]}
  {:db (assoc-in db [::data :notes doc-id path] note)})

(rf/reg-sub ::show-editor?
  (fn [[_ doc-id path]]
    {:pre [(string? doc-id)]}
    [(rf/subscribe [::editor-toggle doc-id path])
     (rf/subscribe [::approvals/rejected? doc-id path])])
  (fn [[toggle-value rejected?]]
    (and toggle-value
         rejected?)))

(rf/reg-sub ::original-note
  (fn [[_ doc-id path]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::approvals/approval doc-id path]))
  (fn [approval]
    (some-> approval :note ss/blank-as-nil)))

(rf/reg-sub ::reject-note
  (fn [[_ doc-id path]]
    {:pre [(string? doc-id)]}
    [(rf/subscribe [::modified-note doc-id path])
     (rf/subscribe [::original-note doc-id path])])
  (fn [[modified-note original-note]]
    (or modified-note original-note)))

(rf/reg-sub ::show-note?
  (fn [[_ doc-id path]]
    {:pre [(string? doc-id)]}
    [(rf/subscribe [::show-editor? doc-id path])
     (rf/subscribe [::reject-note doc-id path])
     (rf/subscribe [::approvals/approved? doc-id path])])
  (fn [[show-editor? note approved?]]
    (and (not show-editor?)
         (not (ss/blank? note))
         (or (not approved?)
             (user/authority?)))))

(rf/reg-event-fx ::reject-doc-note.success
  (fn [cofx [_ _resp args]]
    (nutil/merge-every-fn cofx
                          [[set-note args]])))

(rf/reg-event-fx ::on-blur
  (fn [cofx [_ {:keys [app-id doc-id path note]}]]
    (nutil/merge-every-fn cofx
                          [[command/reject-doc-note-fx
                            (util/assoc-when (util/mmap {:on-success [::reject-doc-note.success (util/mmap doc-id path note)]}
                                                        app-id doc-id note)
                                             :path (some-> path path/path->str))]
                           [set-editor-toggle {:doc-id   doc-id
                                               :path     path
                                               :enabled? false}]])))

(defn- render-note
  [{:keys [doc-id path note-class]}]
  (when (<sub [::show-note? doc-id path])
    [:div
     {:class (or note-class ["reject-note"])}
     [:span {:class [(when (<sub [::approvals/rejected? doc-id path])
                       "reject-note--rejected")]}
      (<sub [::reject-note doc-id path])]]))

(defn- save-button
  [doc-id]
  [:button.primary.reject-edits--input
   ;; blur already handles saving the (modified) value, so this button just closes the UI
   {:on-click (fn [] (>evt [::set-editor-toggle {:doc-id   doc-id
                                                 :enabled? false}]))}
   [:i.lupicon-save]])

(defn- render-editor
  [{:keys [doc-id path disabled? editor-class]}]
  (when (and (<sub [::show-editor? doc-id path])
             (not disabled?))
    (let [app-id (<sub [::app/application-id])]
      [:div
       {:class (or editor-class "reject-note-editor")}
       [:div.reject-edits
        [ui-input/text-input
         {:id          "reject-edit-input"
          :class       "reject-edits--input"
          :debounce-ms docgen-common/default-input-debounce-ms
          :autofocus?  true
          :value       (<sub [::reject-note doc-id path])
          :on-blur     (fn [value]
                         (>evt [::on-blur (util/mmap {:note (ss/trim value)}
                                                     app-id doc-id path)]))}]
        [save-button doc-id]]])))

(defn render
  "Edit or display the rejection note."
  [{:keys [note-class editor-class disabled? doc-id path]}]
  [:<>
   [render-note (util/mmap doc-id path note-class)]
   [render-editor (util/mmap doc-id path disabled? editor-class)]])
