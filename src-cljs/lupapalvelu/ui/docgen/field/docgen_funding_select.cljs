(ns lupapalvelu.ui.docgen.field.docgen-funding-select
  "The Varke-funding select component.
  Essentially just a checkbox, which invites (or removes) the Varke
  financial authority user to/from the application."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.toggle :as toggle]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- add-invite-fx
  [_ [_ {:keys [app-id doc-id path]}]]
  {:fx [[:action/command {:name    :invite-financial-handler
                          :params  {:id         app-id
                                    :documentId doc-id
                                    :path       path}
                          :success (fn [] (>evt [::confirm.success app-id true]))}]]})

(defn- remove-invite-fx
  [_ [_ {:keys [app-id]}]]
  {:fx [[:action/command {:name    :remove-financial-handler-invitation
                          :params  {:id app-id}
                          :success (fn [] (>evt [::confirm.success app-id false]))}]]})

(rf/reg-event-fx ::confirm.success
  (fn [_ [_ app-id invited?]]
    {:fx [(when invited?
            [:dispatch-later {:ms       1000
                              :dispatch [::notify-housing-office app-id]}])
          [:application/load app-id]]}))

(rf/reg-event-fx ::notify-housing-office
  (fn [_ [_ app-id]]
    {:fx [[:action/command {:name   :notify-organizations-housing-office
                            :params {:id app-id}}]]}))

(rf/reg-event-fx ::confirm
  (fn [cofx [_ {:keys [app-id doc-id path value]}]]
    (let [callback-args (util/mmap {:field-paths [path]
                                    :values      [value]}
                                   app-id doc-id)]
      (nutil/merge-every-fn cofx
                            [(if value
                               [add-invite-fx (util/mmap app-id doc-id path)]
                               [remove-invite-fx (util/mmap app-id)])
                             [command/update-doc-fx
                              (assoc callback-args
                                     :on-success [::docgen-common/on-save-success callback-args]
                                     :on-error [::docgen-common/on-save-error callback-args])]]))))

(rf/reg-event-fx ::funding-select-clicked
  (fn [_ [_ {:keys [value] :as args}]]
    {:dialog/show (-> (if value
                        {:ltitle :hankkeen-kuvaus.rahoitus.add.areysure
                         :ltext  :hankkeen-kuvaus.rahoitus.add.help}
                        {:ltitle :hankkeen-kuvaus.rahoitus.remove.areysure
                         :ltext  :hankkeen-kuvaus.rahoitus.remove.help})
                      (assoc :type :yes-no
                             :size :small
                             :callback #(>evt [::confirm args])))}))

(sc/defn render
  "See `docgen-funding-select[-template.html|-model.js]`.
   Also checkout `docgen-input-model.js`."
  [{:keys [app-id doc-id schema path disabled?]} :- schemas/DocgenFieldArguments]
  (when (<sub [:auth/global? :invite-financial-handler])
    (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
          value         (<sub [::value/current-field-value doc-id path])]
      [:section
       [:span.form-group
        [:div.property-group-header
         [:label.form-label.form-label-property-group
          (common/loc :hankkeen-kuvaus.rahoitus._group_label)]]
        [toggle/toggle {:ltext     (docgen-loc/loc-key doc-i18n-name path schema)
                        :prefix    "buttonbox"
                        :disabled? disabled?
                        :checked?  value
                        :on-change (fn [value]
                                     (>evt [::funding-select-clicked (util/mmap app-id doc-id path value)]))}]]])))
