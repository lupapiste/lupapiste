(ns lupapalvelu.ui.pate.verdicts
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))


(defonce args (atom {}))

(defn loc-key [k]
  ;; Each value is [verdict-key contract-key]
  (let [v (k {:title                [:application.tabVerdict :application.tabVerdict.sijoitussopimus]
              :th-verdict           [:pate.verdict-table.verdict :pate.verdict-table.contract]
              :th-date              [:pate.verdict-table.verdict-date :verdict.contract.date]
              :th-giver             [:pate.verdict-table.verdict-giver :verdict.name.sijoitussopimus]
              :add                  [:application.verdict.add :pate.contract.add]
              :new                  [:pate.verdict-new :pate.verdict-new]
              :fetch                [:verdict.fetch.button :contract.fetch.button]
              :copy                 [:pate.verdict-copy :pate.verdict-copy]
              :description          [:application.verdictDesc :pate.contract.description
                                     :help.YA.verdictDesc.sijoitussopimus]
              :confirm-delete       [:verdict.confirmdelete :pate.contract.confirm-delete]
              :confirm-delete-draft [:pate.delete-verdict-draft
                                     :pate.contract.confirm-delete-draft]
              :no-templates         [:pate.no-verdict-templates :pate.no-contract-templates]
              :template             [:pate-verdict-template :pate.contract.template]
              :default-title        [:pate-verdict :pate.verdict-table.contract]})]
    (if (:contracts? @args)
      (last v)
      (first v))))

(defn- can-delete? [{:keys [id category] :as verdict}]
  (or (state/verdict-auth? id :delete-legacy-verdict)
      (state/verdict-auth? id :delete-pate-verdict)
      (state/verdict-auth? id :delete-verdict)))

(defn- can-replace? [verdict-id]
  (state/verdict-auth? verdict-id :replace-pate-verdict))

(defn- can-sign? [verdict-id]
  (state/react-verdict-auth? verdict-id :sign-pate-contract))

(defn- can-send-signature-request? [verdict-id]
  (state/verdict-auth? verdict-id :send-signature-request))

(defn- contract? [{category :category}]
  (util/=as-kw category :contract))

(defn open-verdict [arg]
  (common/open-page :verdict
                    @state/application-id
                    (get arg :verdict-id arg)))

(rum/defcs new-verdict < rum/reactive
  (rum/local nil ::template)
  [{template* ::template}]
  (let [templates (rum/react state/template-list)]
    (if (empty? templates)
      [:div.pate-note-frame [:div.pate-note (path/loc (loc-key :no-templates))]]
      (let [items (map #(set/rename-keys % {:id :value :name :text})
                       templates)
            selected (rum/react template*)]
        (when-not (util/find-by-key :value selected items)
          (common/reset-if-needed! template*
                                   (:value (or (util/find-by-key :default? true items)
                                               (first items)))))
        [:div.pate-grid-6.pate-bottom-space
         [:div.row
          (layout/vertical {:label (loc-key :template)
                            :align :full}
                           (components/dropdown template*
                                                {:items   items
                                                 :choose? false}))
          (layout/vertical [:button.positive
                            {:on-click #(service/new-verdict-draft @state/application-id
                                                                   @template*
                                                                   open-verdict
                                                                   @state/replacement-verdict)}
                            [:i.lupicon-circle-plus]
                            (if @state/replacement-verdict
                              [:span (common/loc (loc-key :new))]
                              [:span (common/loc (loc-key :add))])])
          (if @state/replacement-verdict
            (layout/vertical [:button.positive
                              {:on-click #(service/copy-verdict-draft @state/application-id
                                                                      open-verdict
                                                                      @state/replacement-verdict)}
                              [:i.lupicon-copy]
                              [:span (common/loc (loc-key :copy))]]))]]))))

(rum/defc new-legacy-verdict []
  (components/icon-button {:icon     :lupicon-circle-plus
                           :text-loc (loc-key :add)
                           :class    [:positive :pate-right-space]
                           :on-click #(service/new-legacy-verdict-draft @state/application-id
                                                                        open-verdict)}))

(rum/defcs check-for-verdict < (rum/local false ::waiting?)
  [{waiting?* ::waiting?}]
  (let [check-fn   #(service/check-for-verdict @state/application-id
                                               waiting?*
                                               (fn [{:keys [verdictCount taskCount]}]
                                                 (common/show-dialog {:ltitle :verdict.fetch.title
                                                                      :text   (common/loc :verdict.verdicts-found-from-backend
                                                                                          (str verdictCount)
                                                                                          (str taskCount))})))
        confirm-fn #(if (some (fn [{:keys [category]}]
                                (util/=as-kw category :backing-system))
                              @state/verdict-list)
                      (common/show-dialog {:type     :yes-no
                                           :ltext (if (:contracts? @args)
                                                    :pate.check-for-contract.confirm
                                                    :pate.check-for-verdict.confirm)
                                           :callback check-fn})
                      (check-fn))]
    (components/icon-button {:icon     :lupicon-download
                             :text-loc  (loc-key :fetch)
                             :wait?     waiting?*
                             :class     [:positive :pate-right-space]
                             :on-click confirm-fn})))

(rum/defc order-verdict-attachment-prints []
  (components/icon-button {:icon     :lupicon-envelope
                           :text-loc :verdict.orderAttachmentPrints.button
                           :class    :positive.pate-right-space
                           :on-click #(hub/send "order-attachment-prints")}))

(rum/defc print-order-history []
  (components/icon-button {:icon     :lupicon-documents
                           :text-loc :application.printsOrderHistory
                           :class    :positive.pate-right-space
                           :on-click #(hub/send "show-attachment-prints-order-history")}))

(defn- confirm-and-delete-verdict [app-id {:keys [legacy? published] :as verdict}]
  (common/show-dialog {:type     :yes-no
                       :ltext    (if published
                                   (loc-key :confirm-delete)
                                   (loc-key :confirm-delete-draft))
                       :callback #(service/delete-verdict app-id verdict)}))

(defn- confirm-and-replace-verdict [verdict verdict-id]
  (common/show-dialog {:type     :yes-no
                       :ltext    :pate.replace-verdict
                       :callback #(do
                                    (reset! state/verdict-list nil)
                                    (reset! state/verdict-list [verdict])
                                    (reset! state/replacement-verdict verdict-id))}))

(defn- confirm-and-send-signature-request [app-id verdict-id signer-id add-signature?*]
  (common/show-dialog {:type     :yes-no
                       :ltext    :pate.verdict-table.request-signature.confirm
                       :callback #(do
                                    (service/send-signature-request app-id verdict-id signer-id)
                                    (swap! add-signature?* not))}))


(rum/defcs verdict-signatures-row < rum/reactive
  (rum/local ""    ::password)
  (rum/local false ::signing?)
  (rum/local false ::waiting?)
  (rum/local nil   ::bad-password)
  [{password*     ::password
    signing?*     ::signing?
    waiting?*     ::waiting?
    bad-password* ::bad-password} app-id verdict-id signatures title show-sign-button?]
  (let [click-fn (fn []
                   (swap! signing?* not)
                   (reset! password* "")
                   (reset! waiting?* false)
                   (reset! bad-password* nil))]
    [:tr.verdict-signatures
    [:td]
    [:td {:colSpan 3}
     [:div.pate-grid-3
      [:div.row
       [:div.col-1
        [:div
         (when (seq signatures)
           [:div  [:strong (common/loc title)]])
         [:div.tabby
          (map-indexed (fn [i {:keys [name date]}]
                         [:div.tabby__row {:key i}
                          [:div.tabby__cell.tabby--100 name]
                          [:div.tabby__cell.cell--right (js/util.finnishDate date)]])
                       signatures)]]]
       (when (can-sign? verdict-id)
         [:div.col-2.col--right {:key "col-2"}
          (when @signing?*
            [:div [:label (common/loc :signAttachment.verifyPassword)]
             (components/text-and-button password*
                                         (let [bad? (when (= @password* @bad-password*)
                                                      :negative)]
                                           {:input-type   :password
                                            :disabled?    @waiting?*
                                            :autoFocus    true
                                            :class        (common/css-flags :warning bad?)
                                            :button-class (when bad? :negative)
                                            :icon         (if @waiting?*
                                                            :icon-spin.lupicon-refresh
                                                            :lupicon-circle-pen)
                                            :callback     (fn [password]
                                                            (reset! waiting?* true)
                                                            (service/sign-contract app-id
                                                                                   verdict-id
                                                                                   password
                                                                                   #(do
                                                                                      (reset! waiting?* false)
                                                                                      (reset! bad-password*
                                                                                              password))))}))
             [:button.secondary.cancel-signing {:on-click click-fn} (common/loc :cancel)]])])]]]
     (if show-sign-button?
       [:td (when (and (can-sign? verdict-id)
                       (not (rum/react signing?*)))
              (components/icon-button
                {:on-click click-fn
                 :text-loc :verdict.sign
                 :class    :positive
                 :icon     :lupicon-circle-pen}))]
       [:td])]))

(rum/defcs request-signature-row < rum/reactive
  (rum/local "" ::signer)
  (rum/local [] ::parties)
  (rum/local false ::add-signature?)
  [{signer*         ::signer
    parties*        ::parties
    add-signature?* ::add-signature?} app-id id]
   [:tr.verdict-signatures
   [:td {:colSpan 2}]
   [:td {:colSpan 2}
    [:div.row
     [:div.col-1
      (when @add-signature?*
          [:div [:label (common/loc :pate.verdict-table.request-signature.title)]
           (components/dropdown signer*
                                {:items (rum/react parties*)})
           [:button.signature
            {:on-click #(confirm-and-send-signature-request app-id id @signer* add-signature?*)
             :class    :positive}
            (common/loc :pate.verdict-table.send-signature-request)]])]]]
   [:td
     (if @add-signature?*
       [:button {:on-click (fn [_] (swap! add-signature?* not))
                 :class    :secondary}
        (common/loc :cancel)]
       (components/icon-button
         {:on-click (fn [_] (do (swap! add-signature?* not)
                                (service/fetch-application-parties app-id id #(reset! parties* (:parties %)))))
          :text-loc :pate.verdict-table.request-signature
          :class    :secondary
          :icon     :lupicon-circle-plus}))]])

(defn- verdict-table [headers verdicts app-id hide-actions]
  [:table.pate-verdicts-table
   [:thead [:tr (map (fn [header] [:th (common/loc header)]) headers)]]
   [:tbody (map (fn [{:keys [id title published modified
                             verdict-date giver replaced?
                             category signatures signature-requests]
                      :as   verdict}]
                  (list [:tr {:key id}
                         [:td {:class (common/css-flags :replaced replaced?)}
                          [:a {:on-click #(open-verdict id)} (if (ss/blank? title)
                                                               (common/loc (loc-key :default-title))
                                                               title)]]
                         [:td (js/util.finnishDate verdict-date)]
                         [:td giver]
                         [:td (if published
                                (common/loc :pate.published-date (js/util.finnishDate published))
                                (common/loc :pate.last-saved (js/util.finnishDateAndTime modified)))]
                         (if hide-actions
                           [:td]
                           [:td
                            (when (can-delete? verdict)
                              (components/icon-button
                               {:text-loc (cond
                                            (and published
                                                 (util/=as-kw :contract category))
                                            :pate.contract.delete

                                            published
                                            :pate.verdict-table.remove-verdict

                                            :else
                                            :pate.verdict-table.remove-draft)
                                :icon     :lupicon-remove
                                :class    (common/css :secondary)
                                :on-click #(confirm-and-delete-verdict app-id verdict)}))
                            (when (can-replace? id)
                              (components/icon-button
                               {:text-loc :pate.verdict-table.replace-verdict
                                :icon     :lupicon-refresh-section-sign
                                :class    (common/css :secondary)
                                :on-click #(confirm-and-replace-verdict verdict id)}))])]
                        (when (or (seq signatures) (can-sign? id))
                          (rum/with-key (verdict-signatures-row app-id id signatures :verdict.signatures true)
                                        (str id "-signatures")))
                        (when (seq signature-requests)
                          (rum/with-key (verdict-signatures-row app-id id signature-requests :verdict.signature-requests false)
                                        (str id "-signature-request")))
                        (when (and published
                                   (can-send-signature-request? id)
                                   (contract? verdict))
                          (rum/with-key (request-signature-row app-id id) (str id "-request")))))
                verdicts)]])

(rum/defc verdict-list < rum/reactive
  [verdicts app-id replacement-verdict]
  [:div
   (if replacement-verdict
     [:div
      [:div.operation-button-row
       [:button.secondary
        {:on-click #(do
                      (reset! state/replacement-verdict nil)
                      (service/fetch-verdict-list app-id))}
        [:i.lupicon-chevron-left]
        [:span (common/loc :back)]]]]
     [:h2 (common/loc (loc-key :title))])
   (if (empty? verdicts)
     (when-not (state/auth? :new-pate-verdict-draft)
       (common/loc-html :p (loc-key :description)))
     [:div
     (if replacement-verdict
       [:h3.table-title (common/loc :application.tabVerdict.replacement)])
      (verdict-table [(loc-key :th-verdict)
                      (loc-key :th-date)
                      (loc-key :th-giver)
                      :pate.verdict-table.last-edit
                      ""]
                    verdicts
                    app-id
                    replacement-verdict)])
   (when (state/auth? :new-pate-verdict-draft)
     (new-verdict))
   (when (state/auth? :new-legacy-verdict-draft)
     (new-legacy-verdict))
   (when (state/auth? :check-for-verdict)
     (check-for-verdict))
   (when (state/auth? :order-verdict-attachment-prints)
     (order-verdict-attachment-prints))
   (when (state/auth? :attachment-print-order-history)
     (print-order-history))])

(rum/defc verdicts < rum/reactive
  (state/application-model-updated-mixin)
  []
  (when (and (rum/react state/application-id)
             (rum/react state/verdict-list)
             (rum/react state/auth-fn))
    (verdict-list @state/verdict-list @state/application-id @state/replacement-verdict)))

(defn bootstrap-verdicts []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/template-list [])
    (reset! state/verdict-list nil)
    (reset! state/replacement-verdict nil)
    (state/refresh-verdict-auths app-id)
    (state/refresh-application-auth-model app-id
                                          #(when (state/auth? :pate-verdicts)
                                             (service/fetch-verdict-list app-id)
                                             (when (state/auth? :application-verdict-templates)
                                               (service/fetch-application-verdict-templates app-id))))))

(defn mount-component []
  (rum/mount (verdicts)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params]
  (swap! args assoc
         :contracts? (common/oget params :contracts)
         :dom-id (name domId))
  (bootstrap-verdicts)
  (mount-component))
