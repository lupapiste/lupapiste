(ns lupapalvelu.ui.pate.verdicts
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.pate.bulletin-reports :as reports]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [lupapalvelu.ui.rum-util :as rum-util]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defonce args (atom {}))

(defn loc-key [k]
  ;; Each value is [verdict-key contract-key]
  (let [v (k {:title                   [:application.tabVerdict :application.tabVerdict.sijoitussopimus]
              :th-verdict              [:pate.verdict-table.verdict :pate.verdict-table.contract]
              :th-date                 [:pate.verdict-table.verdict-date :verdict.contract.date]
              :th-giver                [:pate.verdict-table.verdict-giver :verdict.name.sijoitussopimus]
              :add                     [:application.verdict.add :pate.contract.add]
              :new                     [:pate.verdict-new :pate.verdict-new]
              :fetch                   [:verdict.fetch.button :contract.fetch.button]
              :copy                    [:pate.verdict-copy :pate.verdict-copy]
              :description             [:application.verdictDesc :pate.contract.description
                                        :help.YA.verdictDesc.sijoitussopimus]
              :confirm-delete          [:verdict.confirmdelete :pate.contract.confirm-delete]
              :confirm-delete-draft    [:pate.delete-verdict-draft
                                        :pate.contract.confirm-delete-draft]
              :no-templates            [:pate.no-verdict-templates :pate.no-contract-templates]
              :template                [:pate-verdict-template :pate.contract.template]
              :fetch-confirm           [:pate.check-for-verdict.confirm :pate.check-for-contract.confirm]
              :default-title           [:pate-verdict :pate.verdict-table.contract]
              :generating-verdict      [:pate-generating-verdict :pate-generating-contract]})]
    (if (:contracts? @args)
      (last v)
      (first v))))

(defn- can-delete? [{:keys [id category] :as verdict}]
  (or (state/verdict-auth? id :delete-pate-verdict)
      (state/verdict-auth? id :delete-verdict)))

(defn- can-replace? [verdict-id]
  (state/verdict-auth? verdict-id :replace-pate-verdict))

(defn- can-sign? [verdict-id]
  (or (state/react-verdict-auth? verdict-id :sign-pate-contract)
      (state/react-verdict-auth? verdict-id :sign-allu-contract)))

(defn- can-send-via-suomifi? [verdict-id]
  (state/verdict-auth? verdict-id :can-send-via-suomifi))

(defn- can-send-signature-request? [verdict-id]
  (state/verdict-auth? verdict-id :send-signature-request))

(defn- contract? [{category :category}]
  (util/=as-kw category :contract))

(defn open-verdict [arg]
  (common/open-page :verdict
                    @state/application-id
                    (get arg :verdict-id arg)))

(rum/defc new-verdict-select < rum/static
  [app-id templates replacement-verdict]
  (let [items                    (->> templates
                                      (map #(set/rename-keys % {:id :value :name :text})))
        default                  (:value (or (util/find-by-key :default? true items)
                                             (first items)))
        [template set-template!] (rum/use-state default)]
    [:div.pate-grid-6.pate-bottom-space
     [:div.row
      (layout/vertical {:col       2
                        :label     (loc-key :template)
                        :align     :full
                        :label-for :select-verdict-template}
                       (components/dropdown
                         template
                         {:items    items
                          :id       :select-verdict-template
                          :choose?  false
                          :callback set-template!}))
      (layout/vertical
        {:col 4}
        [:div (components/icon-button {:on-click #(service/new-verdict-draft app-id
                                                                             template
                                                                             open-verdict
                                                                             replacement-verdict)
                                       :text-loc (if replacement-verdict
                                                   (loc-key :new)
                                                   (loc-key :add))
                                       :class    :primary
                                       :icon     :lupicon-circle-plus})
         (when replacement-verdict
           (components/icon-button {:on-click #(service/copy-verdict-draft app-id
                                                                           open-verdict
                                                                           replacement-verdict)
                                    :class    :primary.gap--l1
                                    :icon     :lupicon-copy
                                    :text-loc (loc-key :copy)}))])]]))

(rum/defc new-legacy-verdict < rum/static
  [app-id]
  (components/icon-button {:icon     :lupicon-circle-plus
                           :class    :primary
                           :text-loc (loc-key :add)
                           :test-id  :new-legacy-verdict
                           :on-click #(service/new-legacy-verdict-draft app-id open-verdict)}))

(rum/defc terminal-state-verdict-dialog
  "Special options dialog the user encounters when they are fetching verdicts for an application
  that is already in a terminal state (most likely :ready)"
  [callback]
  (let [close-dialog (fn [event]
                       (.preventDefault event)
                       (hub/send "close-dialog"))
        checkboxes   (->> [:update-bulletin :remove-verdict-attachment :send-notifications]
                          (map (fn [k]
                                 {:param k
                                  :state (rum/use-state false)})))
        update-state (fn [[value set-fn]] (-> value not boolean set-fn))]
    [:form {:on-submit (fn [event]
                         (close-dialog event)
                         (callback (reduce (fn [acc {:keys [param state]}]
                                             (assoc acc param (first state)))
                                           {}
                                           checkboxes)))}
     [:div.row [:p (js/loc "verdict.fetch.terminal-state.help")]]
     (->> checkboxes
          (map (fn [{:keys [param state] [checked?] :state}]
                 (let [id (str "terminal-state-dialog-" (name param))]
                   [:div.row
                    [:input {:id        id
                             :type      :checkbox
                             :checked   checked?
                             :on-change #(update-state state)}]
                    [:label {:for id}
                     (js/loc (str "verdict.fetch.terminal-state." (name param)))]])))
          (into [:<>]))
     [:div.button-group
      [:button.btn-dialog.positive {:type "submit"} (js/loc "verdict.fetch.title")]
      [:button.btn-dialog {:on-click close-dialog} (js/loc "cancel")]]]))

(defn- check-for-verdict-confirmation [callback]
  (cond
    ;; Fetching fixed versions of verdicts when app is in a terminal state has special options
    (state/auth? :check-for-verdict-fix)
    (common/show-dialog {:type             :react
                         :dialog-component (terminal-state-verdict-dialog callback)})
    ;; Confirm overwriting of existing verdicts in non-terminal states
    (some #(-> % :category (util/=as-kw :backing-system)) @state/verdict-list)
    (common/show-dialog {:type     :yes-no
                         :ltext    (loc-key :fetch-confirm)
                         :callback callback})
    ;; No confirmation at all when first time fetching
    :else
    (callback)))

(rum/defcs check-for-verdict < (rum/local false ::waiting?)
  [{waiting?* ::waiting?}]
  (let [check-fn (fn [& [fix-options]]
                   (service/check-for-verdict
                     @state/application-id
                     waiting?*
                     (fn [{:keys [verdictCount taskCount]}]
                       (common/show-dialog {:ltitle :verdict.fetch.title
                                            :text   (common/loc :verdict.verdicts-found-from-backend
                                                                (str verdictCount)
                                                                (str taskCount))}))
                     fix-options))]
    (components/icon-button {:icon     :lupicon-download
                             :class    :primary
                             :test-id  :fetch-verdict
                             :text-loc (loc-key :fetch)
                             :wait?    waiting?*
                             :on-click #(check-for-verdict-confirmation check-fn)})))

(rum/defc order-verdict-attachment-prints []
  (components/icon-button {:icon     :lupicon-envelope
                           :class    :primary
                           :text-loc :verdict.orderAttachmentPrints.button
                           :test-id  :test-order-attachment-prints
                           :on-click #(hub/send "order-attachment-prints")}))

(rum/defc print-order-history []
  (components/icon-button {:icon     :lupicon-documents
                           :class    :primary
                           :text-loc :application.printsOrderHistory
                           :test-id  :test-open-prints-order-history
                           :on-click #(hub/send "show-attachment-prints-order-history")}))

(defn- confirm-and-replace-verdict [verdict verdict-id]
  (common/show-dialog {:type     :yes-no
                       :ltext    :pate.replace-verdict
                       :callback #(do
                                    (reset! state/verdict-list nil)
                                    (reset! state/verdict-list [verdict])
                                    (reset! state/replacement-verdict verdict-id))}))

(defn- confirm-and-send-signature-request [app-id verdict-id signer-id callback]
  (common/show-dialog {:type     :yes-no
                       :ltext    :pate.verdict-table.request-signature.confirm
                       :callback #(do
                                    (service/send-signature-request app-id verdict-id signer-id)
                                    (callback))}))

(defn- confirm-and-deliver-verdict [verdict-id]
  (hub/send "show-dialog" {:ltitle "suomifi-messages.recipient-info.title"
                           :component "recipient-data-dialog"
                           :componentParams {:data (-> @state/verdict-recipient
                                                       (assoc :verdict-id verdict-id)
                                                       clj->js)
                                             :onVerdictSent (fn []
                                                              (do
                                                                (swap! state/verdict-sent? assoc verdict-id true) ;; Implement a robust check for this in LPK-4291
                                                                (common/show-dialog {:ltitle :suomifi-messages.send-verdict.sent-title
                                                                                     :ltext :suomifi-messages.send-verdict.sent})))}}))

(rum/defcs verdict-signatures-row < rum/reactive
  (rum/local ""    ::password)
  (rum/local false ::signing?)
  (rum/local false ::waiting?)
  (rum/local nil   ::bad-password)
  [{password*     ::password
    signing?*     ::signing?
    waiting?*     ::waiting?
    bad-password* ::bad-password} app-id verdict-id signatures category title show-sign-button?]

  (let [cancel-fn (fn []
                    (swap! signing?* not)
                    (reset! password* "")
                    (reset! waiting?* false)
                    (reset! bad-password* nil))
        bad?      (rum-util/derived-atom [password* bad-password*]
                                         (fn [pw bad-pw]
                                           (= pw bad-pw)))
        submit-pw (fn [password]
                    (reset! waiting?* true)
                    (service/sign-contract app-id
                                           verdict-id
                                           password
                                           category
                                           #(do
                                              (reset! waiting?* false)
                                              (reset! bad-password* password))))]
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
          (for [[i {:keys [name date]}] (map-indexed #(vector %1 %2) signatures)]
            [:div.tabby__row {:key i}
             [:div.tabby__cell.tabby--100
              (common/add-test-id {} :signature i :name)
              name]
             [:div.tabby__cell.cell--right
              (common/add-test-id {} :signature i :date)
              (js/util.finnishDate date)]])]]]
       (when (can-sign? verdict-id)
         [:div.col-2.col--right {:key "col-2"}
          (when @signing?*
            (let [input-id (common/unique-id "password-input")]
              [:div
               [:label {:for input-id}
                (common/loc :signAttachment.verifyPassword)]
               [:span.text-and-button
                (components/text-edit @password*
                                      {:disabled  @waiting?*
                                       :class     :no-error-bg
                                       :type      :password
                                       :id        input-id
                                       :callback  #(reset! password* %)
                                       :on-key-up #(when (= (.-keyCode %) 13)
                                                     (submit-pw @password*))
                                       :test-id   [:password :input]
                                       :autoFocus true
                                       :invalid?  @bad?})
                (components/icon-button {:class      (if @bad? :negative :primary)
                                         :disabled?  (ss/blank? @password*)
                                         :test-id    [:password :button]
                                         :text-loc   :verdict.sign
                                         :icon-only? true
                                         :on-click   #(submit-pw @password*)
                                         :wait?      waiting?*
                                         :icon       (if @bad? :lupicon-circle-attention :lupicon-circle-pen)})]
               [:button.secondary.cancel-signing
                (common/add-test-id {:on-click cancel-fn} :cancel-signing)
                (common/loc :cancel)]]))])]]]
     (if show-sign-button?
       [:td (when (and (can-sign? verdict-id)
                       (not @signing?*))
              (components/icon-button
                {:on-click cancel-fn
                 :text-loc :verdict.sign
                 :class    :positive
                 :icon     :lupicon-circle-pen
                 :test-id  :sign-contract}))]
       [:td])]))

(rum/defc request-signature-row
  [app-id id signatures]
  (let [[signer set-signer!]    (rum/use-state "")
        [parties set-parties!]  (rum/use-state nil)
        [request? set-request!] (rum/use-state false)
        close                   #(set-request! false)]
    (rum/use-effect! (fn []
                       (service/fetch-application-parties app-id id
                                                          #(some-> % :parties not-empty set-parties!)))
                     [signatures])

    (when parties
      [:tr.verdict-signatures
       [:td {:colSpan 2}]
       [:td {:colSpan 2}
        [:div.row
         [:div.col-1
          (when request?
            (let [dd-id (common/unique-id "singer-dropddown")]
              [:div
               [:label {:for dd-id}
               (common/loc :pate.verdict-table.request-signature.title)]
               (components/dropdown signer {:id       dd-id
                                            :items    parties
                                            :class :align--middle
                                            :callback set-signer!})
               [:button.primary.align--middle.gap--l1.gap--r1
                {:on-click #(confirm-and-send-signature-request app-id id signer close)
                 :disabled (ss/blank? signer)}
                (common/loc :pate.verdict-table.send-signature-request)]
               [:button.secondary {:on-click close}
                (common/loc :cancel)]]))]]]
      [:td
       (when-not request?
         (components/icon-button
           {:on-click #(set-request! true)
            :text-loc :pate.verdict-table.request-signature
            :class    :secondary
            :icon     :lupicon-circle-plus}))]])))

;; TODO optimize re-renders when auths reload, there are(can-delete?), (can-sign?) etc
;; contains rum/react calls, which make UI blink when verdicts is deleted
(defn- verdict-table [headers verdicts app-id hide-actions]
  [:table#verdicts-table.pate-verdicts-table
   [:thead [:tr (map (fn [header] [:th (common/loc header)]) headers)]]
   (into
     [:tbody]
     (for [[i {:keys [id title published modified
                      verdict-date giver replaced?
                      category signatures signature-requests
                      proposal? verdict-state]
               :as   verdict}] (map-indexed vector verdicts)]
       (rum/fragment
         [:tr {:key id}
          [:td {:class (common/css-flags :replaced replaced?)}
           (components/click-link (merge {:click   #(open-verdict id)
                                          :test-id [:verdict-link i]}
                                         (cond
                                           (contains? helper/publishing-states verdict-state)
                                           {:text-loc (loc-key :generating-verdict)}

                                           (ss/blank? title)
                                           {:text-loc (loc-key :default-title)}

                                           :else
                                           {:text title})))]
          [:td (common/add-test-id {} :verdict-date i)
           (when-not proposal? (js/util.finnishDate verdict-date))]
          [:td (common/add-test-id {} :verdict-giver i) giver]
          [:td (common/add-test-id {} :verdict-published i)
           (cond
             published (common/loc :pate.published-date (js/util.finnishDate published))
             modified  (common/loc :pate.last-saved (js/util.finnishDateAndTime modified))
             :else     (common/loc :not-known))]
          (when-not hide-actions
            [:td.verdict-buttons
             (when (can-delete? verdict)
               (components/icon-button
                 {:text-loc (cond
                              (and published
                                   (util/=as-kw :contract category))
                              :pate.contract.delete

                              published
                              :pate.verdict-table.remove-verdict

                              proposal?
                              :pate.verdict-table.remove-proposal

                              :else
                              :pate.verdict-table.remove-draft)
                  :icon     :lupicon-remove
                  :test-id  (common/test-id :verdict-delete i)
                  :class    :secondary
                  :on-click #(common/show-dialog {:type     :yes-no
                                                  :ltext    (cond
                                                              published (loc-key :confirm-delete)
                                                              proposal? :pate.delete-proposal
                                                              :else     (loc-key :confirm-delete-draft))
                                                  :callback (fn []
                                                              (swap! state/verdict-list (comp vec (partial remove (fn [v] (= (:id v) id)))))
                                                              (service/delete-verdict app-id verdict))})}))

             (when (can-replace? id)
               (components/icon-button
                 {:text-loc :pate.verdict-table.replace-verdict
                  :test-id  (common/test-id :verdict-replace i)
                  :icon     :lupicon-refresh-section-sign
                  :class    :secondary
                  :on-click #(confirm-and-replace-verdict verdict id)}))
             (when (can-send-via-suomifi? id)
               (components/icon-button
                 {:on-click  #(confirm-and-deliver-verdict id)
                  :class     :primary
                  :text-loc  :suomifi-messages.send-verdict
                  :disabled? (get (rum/react state/verdict-sent?) id)
                  :icon      :lupicon-envelope}))])]

         (when (or (seq signatures) (can-sign? id))
           (rum/with-key (verdict-signatures-row app-id id signatures category
                                                 :verdict.signatures true)
             (str id "-signatures")))
         (when (seq signature-requests)
           (rum/with-key (verdict-signatures-row app-id id signature-requests category
                                                 :verdict.signature-requests false)
             (str id "-signature-request")))
         (when (and published
                    (can-send-signature-request? id)
                    (contract? verdict))
           (rum/with-key (request-signature-row app-id id signatures) (str id "-request"))))))])

(rum/defc verdict-list < rum/reactive
  [verdicts app-id replacement-verdict]
  [:div
   (if replacement-verdict
     [:div
      [:div.operation-button-row
       (components/icon-button {:on-click #(do
                                             (reset! state/replacement-verdict nil)
                                             (service/fetch-verdict-list app-id))
                                :class    :secondary
                                :icon     :lupicon-chevron-left
                                :text-loc :back})]]
     [:h2 (common/loc (loc-key :title))])
   (if (empty? verdicts)
     (when-not (state/auth? :new-pate-verdict-draft)
       (common/loc-html :p (loc-key :description)))
     [:div
     (when replacement-verdict
       [:h3.table-title (common/loc :application.tabVerdict.replacement)])
      (verdict-table (cond-> [(loc-key :th-verdict)
                               (loc-key :th-date)
                               (loc-key :th-giver)
                               :pate.verdict-table.last-edit]
                       (not replacement-verdict) (conj :admin.actions))
                    verdicts
                    app-id
                    replacement-verdict)])
   (when (state/auth? :new-pate-verdict-draft)
     (if-let [templates (seq (rum/react state/template-list))]
       (new-verdict-select app-id templates  (rum/react state/replacement-verdict))
       [:div.pate-note-frame [:div.pate-note (path/loc (loc-key :no-templates))]]))
   (when-not replacement-verdict
     [:div.flex--wrap.flex--gap2
      (when (state/auth? :new-legacy-verdict-draft)
        (new-legacy-verdict app-id))
      (when (or (state/auth? :check-for-verdict)
                (state/auth? :check-for-verdict-fix))
        (check-for-verdict))
      (when (state/auth? :order-verdict-attachment-prints)
        (order-verdict-attachment-prints))
      (when (state/auth? :attachment-print-order-history)
        (print-order-history))])])

(rum/defc verdicts < rum/reactive
  (state/application-model-updated-mixin)
  []
  (when (and (rum/react state/application-id)
             (rum/react state/verdict-list)
             (rum/react state/auth-fn)
             (rum/react state/allowed-verdict-actions))
    (let [replacing-id (rum/react state/replacement-verdict)]
      [:div
       (verdict-list @state/verdict-list @state/application-id replacing-id)
       (when-not replacing-id
         (reports/verdict-bulletins @state/application-id))])))

(defn bootstrap-verdicts []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/template-list [])
    (reset! state/verdict-list nil)
    (reset! state/replacement-verdict nil)
    (reset! state/verdict-bulletins [])
    (reset! state/org-id (js/ko.unwrap js/lupapisteApp.models.application.organization()))
    (reset! state/application-state (js/ko.unwrap js/lupapisteApp.models.application.state()))
    (state/refresh-verdict-auths app-id)
    (state/reset-application-id app-id)
    (state/refresh-application-auth-model app-id
                                          (fn []
                                            (service/initialize-suomifi-settings app-id)
                                            (when (state/auth? :pate-verdicts)
                                              (service/loop-fetch-verdict-list app-id)
                                              (when (state/auth? :application-verdict-templates)
                                                (service/fetch-application-verdict-templates app-id)))
                                            (service/fetch-verdict-bulletins app-id)))))

(defn mount-component []
  (rum/mount (verdicts)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params]
  (swap! args assoc
         :contracts? (common/oget params :contracts)
         :dom-id (name domId))
  (bootstrap-verdicts)
  (mount-component))
