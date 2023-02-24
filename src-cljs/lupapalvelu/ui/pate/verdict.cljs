(ns lupapalvelu.ui.pate.verdict
  "View of an individual Pate verdict."
  (:require [cljs.tools.reader.edn :as edn]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.appeal :as appeal]
            [lupapalvelu.ui.pate.attachments :as att]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn can? [action]
  (and (not @state/verdict-wait?)
       (true?
         (get-in (rum/react state/current-allowed-actions)
                 [(keyword action) :ok]))))

(def can-edit? (partial can? :edit-pate-verdict))
(def can-preview? (partial can? :preview-pate-verdict))

(defn can-publish? []
  (or (can? :publish-pate-verdict)
      (can? :publish-legacy-verdict)))

(def can-propose? (partial can? :publish-verdict-proposal))
(def can-revert? (partial can? :revert-verdict-proposal))

(def can-generate? (partial can? :generate-pate-pdf))

(defn updater
  ([{:keys [path] :as options} value]
   (service/edit-verdict @state/application-id
                         @state/current-verdict-id
                         path
                         value
                         (service/update-changes-and-errors state/current-verdict
                                                            options)))
  ([{:keys [state path] :as options}]
   (updater options (path/value path state))))

(defn reset-verdict-attachments
  [app-id verdict-id]
  (letfn [(callback [{:keys [attachment-ids]}]
            (when (and (= app-id @state/application-id)
                       (= verdict-id @state/current-verdict-id))
              (common/reset-if-needed! state/verdict-attachment-ids attachment-ids)))]
    (if (and app-id verdict-id)
      (service/published-verdict-attachment-ids app-id verdict-id callback)
      (common/reset-if-needed! state/verdict-attachment-ids []))))

(defn reset-verdict [{:keys [verdict references filled]}]
  (reset! state/current-verdict
          (when verdict
            (if-let [tags (:tags verdict)]
              {:tags           (edn/read-string tags)
               :info           {:id (:id verdict)
                                :state "published"}}
              {:state (:data verdict)
               :info  (-> (dissoc verdict :data)
                          (assoc :filled? filled)
                          (update :inclusions #(set (map keyword %))))
               :_meta {:updated             updater
                       :highlight-required? (-> verdict :published not)
                       :enabled?            (state/verdict-auth? (:id verdict) :edit-pate-verdict)
                       :published?          (util/=as-kw :published (:state verdict))
                       :upload.filedata     (fn [_ filedata & kvs]
                                              (apply assoc filedata
                                                     :target {:type :verdict
                                                              :id   (:id verdict)}
                                                     kvs))
                       :upload.include?     (fn [_ {:keys [target]}]
                                              (= (:id target) (:id verdict)))}})))
  (when (:tags verdict)
    (reset-verdict-attachments @state/application-id (:id verdict)))
  (when (and (string? @state/application-id)
             (contains? helper/publishing-states (:state verdict)))
    (service/loop-until-verdict-is-done @state/application-id (:id verdict) reset-verdict))
  (reset! state/references references)
  (reset! state/verdict-wait? false))

(rum/defc verdict-section-header < rum/reactive
  [{:keys [schema] :as options}]
  [:div.pate-grid-1.section-header
   {:class (path/css options)}
   (when (and (not (-> schema :buttons? false?))
              (can-edit?))
     [:div.row.row--tight
      [:div.col-1.col--right
       [:div.verdict-buttons
        [:button.primary.outline
         {:on-click #(path/flip-meta options :editing?)
          :data-id-path (:id-path options)}
         (common/loc (if (path/react-meta options :editing?)
                       :close
                       :edit))]]]])])

(defmethod sections/section-header :verdict
  [options _]
  (verdict-section-header options))

(rum/defc toggle-all < rum/reactive
  [section-ids]
  (when (can-edit?)
    (components/click-link {:click    #(state/toggle-sections)
                            :test-id  :toggle-all
                            :class    :pate-left-space
                            :text-loc (if (seq section-ids)
                                        :pate.close-all
                                        :pate.open-all)})))

(rum/defcs revert-button < (rum/local false ::revert-wait?)
  [{wait?* ::revert-wait?} _meta info no-errors?]
  (let [revert-fn (fn []
                    (reset! state/verdict-wait? true)
                    (state/set-meta-enabled false)
                    (service/revert-proposal @state/application-id
                                             info
                                             {:callback reset-verdict
                                              :waiting  [state/verdict-wait? wait?*]}))]
    (components/icon-button {:text-loc  :pate-proposal.revert
                             :class     (common/css :secondary :pate-left-space)
                             :test-id   :revert-proposal
                             :icon      :lupicon-remove
                             :wait?     wait?*
                             :enabled?  no-errors?
                             :disabled? state/verdict-wait?
                             :on-click  (fn []
                                          (common/show-dialog
                                            {:type     :yes-no
                                             :ltext    "pate-proposal.revert-confirm"
                                             :callback revert-fn}))})))

(rum/defc proposal-button < rum/reactive
  [proposal? no-errors? publish-fn]
  (components/icon-button {:text-loc  (if proposal? :verdict.update.proposal :verdict.proposal)
                           :class     (common/css :primary :pate-left-space)
                           :test-id   :verdict-proposal
                           :icon      :lupicon-document-section-sign
                           :wait?     state/generating-proposal?
                           :enabled?  (and no-errors? (can-propose?))
                           :disabled? state/verdict-wait?
                           :on-click  (fn []
                                        (common/show-dialog
                                          {:type     :yes-no
                                           :ltext    (if proposal?
                                                       "verdict.confirm.proposal.update"
                                                       "verdict.confirm.proposal")
                                           :callback publish-fn}))}))

(rum/defc verdict-publish-button < rum/reactive
  [contract? no-errors? publish-fn]
  (components/icon-button {:text-loc  (if contract?
                                        :pate.contract.publish
                                        :verdict.submit)
                           :class     (common/css :primary :pate-left-space)
                           :test-id   :publish-verdict
                           :icon      (if contract?
                                        :lupicon-undersign
                                        :lupicon-document-section-sign)
                           :wait?     state/generating-verdict?
                           :enabled?  (and no-errors? (can-publish?))
                           :disabled? state/verdict-wait?
                           :on-click  (fn []
                                        (common/show-dialog
                                          {:type     :yes-no
                                           :ltext    (if contract?
                                                       "pate.contract.confirm-publish"
                                                       "verdict.confirmpublish")
                                           :callback publish-fn}))}))

(rum/defc verdict-scheduled-publish-button < rum/reactive
  [id verdic-state no-errors?]
  (components/icon-button {:text-loc  :verdict.submit.scheduled
                           :title-loc :verdict.submit.scheduled.title
                           :class     (common/css :primary :pate-left-space)
                           :test-id   :publish-verdict-scheduled
                           :icon      :lupicon-clock
                           :wait?     state/generating-verdict?
                           :enabled?  (and no-errors?
                                           (can? :scheduled-verdict-publish))
                           :disabled? state/verdict-wait?
                           :on-click  (fn []
                                        (common/show-dialog
                                          {:type     :yes-no
                                           :text     (common/loc :verdict.confirmpublish.scheduled
                                                                 (js/util.finnishDate (:julkipano verdic-state)))
                                           :callback (fn []
                                                       (state/set-meta-enabled false)
                                                       (service/schedule-publishing
                                                         @state/application-id
                                                         id
                                                         {:success (fn [{:keys [state]}]
                                                                     (reset! state/current-verdict-state state)
                                                                     (state/refresh-verdict-auths @state/application-id
                                                                                                  {:verdict-id id})
                                                                     (state/close-open-sections))
                                                          :waiting? [state/verdict-wait?]}))}))}))

(rum/defc rollback-scheduled-verdict-button < rum/reactive
  [id]
  (components/icon-button {:text-loc  :verdict.rollback-scheduled
                           :title     (common/loc :verdict.rollback-scheduled.title (str (js/Date.)))
                           :class     (common/css :primary :pate-left-space)
                           :test-id   :rollback-scheduled
                           :icon      :lupicon-undo
                           :wait?     state/generating-verdict?
                           :enabled?  (:rollback-scheduled-publish @state/current-allowed-actions)
                           :disabled? state/verdict-wait?
                           :on-click  (fn []
                                        (state/set-meta-enabled false)
                                        (common/command
                                          {:command  :rollback-scheduled-publish
                                           :waiting? state/verdict-wait?
                                           :success  (fn [{:keys [state]}]
                                                       (reset! state/current-verdict-state state)
                                                       (state/refresh-verdict-auths @state/application-id
                                                                                    {:verdict-id id}))}
                                          :verdict-id id
                                          :id @state/application-id))}))

(rum/defc verdict-toolbar < rum/reactive
  [current-verdict*]
  (let [{:keys [info _meta state]
         :as   current-verdict} (rum/react current-verdict*)
        {:keys [id category
                published]}     info
        _                       (rum/react state/generating-verdict?)
        _                       (rum/react state/generating-proposal?)
        published               (:published published)
        no-errors?              (not (:_errors state))
        contract?               (util/=as-kw category :contract)
        proposal?               (util/=as-kw (:state info) :proposal)
        scheduled?              (util/=as-kw (:state info) :scheduled)
        board?                  (util/=as-kw (:giver info) :lautakunta)
        publish-fn              (fn [& [proposal?]]
                                  (reset! state/verdict-wait? true)
                                  (state/set-meta-enabled false)
                                  (service/publish-and-reopen-verdict @state/application-id
                                                                      info
                                                                      {:callback  reset-verdict
                                                                       :proposal? (boolean proposal?)
                                                                       :waiting?  [state/verdict-wait?
                                                                                   (if proposal?
                                                                                     state/generating-proposal?
                                                                                     state/generating-verdict?)]}))]
    [:div.pate-grid-2
     (when-not published
       [:div.row
        [:div.col-2.col--right
         [:div
          (components/link-button {:url      (js/sprintf "/api/raw/preview-pate-verdict?id=%s&verdict-id=%s"
                                                         @state/application-id
                                                         id)
                                   :enabled? (and no-errors? (can-preview?))
                                   :test-id  :preview-verdict
                                   :text-loc :pdf.preview})
          (when board?
            (when (can-revert?)
              (revert-button _meta info no-errors?))
            (proposal-button proposal? no-errors? (partial publish-fn true)))
          (when (contains? (:inclusions info) :julkipano)
            (if scheduled?
              (rollback-scheduled-verdict-button id)
              (verdict-scheduled-publish-button id state no-errors?)))
          (verdict-publish-button contract? no-errors? publish-fn)]]])
     (if published
       [:div.row
        [:div.col-2.col--right
         [:span.verdict-published
          (common/loc (if contract?
                        :pate.contract.published
                        :pate.verdict-published)
                      (js/util.finnishDate published))]]]
       [:div.row.row--tight
        [:div.col-1
         (pate-components/required-fields-note-raw current-verdict)]
        [:div.col-1.col--right
         (toggle-all (rum/react state/open-section-ids))
         [:span.saved-info
          (when-let [ts (:modified info)]
            (common/loc :pate.last-saved (js/util.finnishDateAndTime ts)))]
         (when scheduled?
           [:span.scheduled-for-publishing
            (common/loc :pate.scheduled (js/util.finnishDate (:julkipano state)))])]])]))

(rum/defc published-verdict-attachments < (att/attachments-refresh-mixin)
  [att-ids]
  (let [attachments (->> (service/attachments)
                         (filter #(->> % :id (util/includes-as-kw? att-ids))))]
    [:div
     [:h3.pate-published-title (common/loc :application.attachments)]
     (att/attachments-view attachments)]))

(rum/defc published-verdict
  [{:keys [header body]} attachment-ids]
  [:div.published-verdict
   header
   (if (seq body)
     (components/add-key-attrs body "tag-")
     (do
       (js/console.error "No body available in published verdict")
       [:span]))
   (when (seq attachment-ids)
     (published-verdict-attachments attachment-ids))
   (appeal/appeals)])

(rum/defc verdict < rum/reactive
  [verdict* schema* references*]
  (let [schema  (rum/react schema*)
        options (assoc
                  (state/select-keys verdict* [:state :info :_meta])
                  :schema (dissoc schema :dictionary)
                  :dictionary (:dictionary schema)
                  :references references*)]
    [:div.pate-verdict {:id (str "verdict-" ((comp :id deref :info) options))}
     (verdict-toolbar verdict*)
     (sections/sections options :verdict)]))

(rum/defc pate-verdict < rum/reactive
  []
  [:div.container {:role :main}
   [:div.pate-verdict-page {:id "pate-verdict-page"}
   (lupapalvelu.ui.attachment.components/dropzone)
   [:div.operation-button-row
    (components/icon-button {:class    :secondary
                             :icon     :lupicon-chevron-left
                             :text-loc :back
                             :test-id  :back
                             :on-click (fn [_]
                                         ;; In case we have just published a verdict
                                         (service/refresh-attachments)
                                         (js/repository.load @state/application-id)
                                         (common/open-page :application @state/application-id :verdict))})]
   (if (and (rum/react state/current-verdict-id)
            (rum/react state/auth-fn))
     (if-let [tags (rum/react state/verdict-tags)]
       (published-verdict tags (rum/react state/verdict-attachment-ids))
       (verdict state/current-verdict
                state/current-verdict-schema
                state/references))
     [:div.pate-spin {:data-test-id :pate-spin}
      [:i.lupicon-refresh]])]])

(defn bootstrap-verdict []
  (let [[app-id verdict-id] (js/pageutil.getPagePath)]
    (state/reset-application-id app-id)
    (state/refresh-verdict-auths app-id
                                 {:callback #(state/refresh-application-auth-model
                                              app-id
                                              (fn []
                                                (service/refresh-attachments)
                                                (if (state/verdict-auth? verdict-id :published-pate-verdict)
                                                  (service/open-published-verdict app-id
                                                                                  verdict-id
                                                                                  reset-verdict)
                                                  (do (when (state/auth? :application-phrases)
                                                        (do (service/fetch-application-phrases app-id)
                                                            (service/fetch-custom-application-phrases app-id)) )
                                                      (service/open-verdict app-id
                                                                            verdict-id
                                                                            reset-verdict)))))
                                  :verdict-id verdict-id})))

(defn unload []
  ;; use delay as 'defer' - so the components will first unmount
  ;; and state is 'reset' after that
  ;; yes it's a hack, for now..
  (common/start-delay #(reset-verdict nil) nil))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (pate-verdict)
             (.getElementById js/document (:dom-id @args))))


(defn ^:export start [domId _]
  (swap! args assoc :dom-id (name domId))
  (js/hub.onPageUnload "verdict" unload true)
  (bootstrap-verdict)
  (mount-component))
