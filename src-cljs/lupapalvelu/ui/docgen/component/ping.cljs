(ns lupapalvelu.ui.docgen.component.ping
  "The ping component is used to show transient notifications on documents/forms.
  E.g. when a form field was successfully modified."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]))

(defn show-ping-fx
  "Display a ping on the UI for a short while"
  [{:keys [db]} [_ {:keys [doc-id field-path status]}]]
  {:pre [(string? doc-id) (vector? field-path) status]}
  {:db (assoc-in db [::pings doc-id field-path] status)
   :fx [[:dispatch-later {:ms       1000
                          :dispatch [::clear-ping {:doc-id     doc-id
                                                   :field-path field-path}]}]]})

(rf/reg-event-fx ::clear-ping
  (fn [{:keys [db]} [_ {:keys [doc-id field-path]}]]
    {:db (update-in db [::pings doc-id] dissoc field-path)}))

(rf/reg-sub ::pings :-> ::pings)

(rf/reg-sub ::ping-status
  :<- [::pings]
  (fn ping-status-inputfn [pings [_ doc-id field-path]]
    {:pre [doc-id field-path]}
    (get-in pings [doc-id field-path])))

(def ^:private ping-defaults
  {:positive {:class :ping--positive
              :text  :form.saved
              :icon  :lupicon-check}
   :negative {:class :ping--negative
              :text  :form.err
              :icon  :lupicon-warning}})

(defn- status->data
  [status mode]
  (when status
    (let [style (case status
                  :warn :positive
                  :tip  :positive
                  :err  :negative
                  :ok   :positive)]
      (cond-> (get ping-defaults style)
        (= :text mode) (dissoc :icon)
        (= :icon mode) (dissoc :text)))))

(defn ping
  "Display a temporary indicator (e.g. after a successful save).
  * `doc-id` -- ID of the target document.
  * `field-path` -- Path identifying the target field.
  * `mode` -- Optional, one of `:text`, `:icon`, or `:both` (default)."
  [{:keys [doc-id field-path mode container-class]}]
  {:pre [(string? doc-id) (vector? field-path)]}
  [:div.cljs-ping
   {:class container-class}
   (when-let [{:keys [class text icon]} (some-> (<sub [::ping-status doc-id field-path])
                                                (status->data (or mode :both)))]
     ;; TODO: LPK-7712 ping partially hidden if field at the end of the accordion
     [:div.ping-wrapper
      {:aria-hidden true
       :class       class}
      [:div.like-btn
       (when text
         [:span (common/loc text)])
       (when icon
         [:i {:class icon}])]])])
