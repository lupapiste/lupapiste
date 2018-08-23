(ns lupapalvelu.ui.pate.verdict
  "View of an individual Pate verdict."
  (:require [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [lupapalvelu.ui.pate.published-verdict :as published]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn can? [action]
  (and (not (rum/react state/verdict-wait?))
       (state/react-verdict-auth? (rum/react state/current-verdict-id)
                                  action)))
(def can-edit? (partial can? :edit-pate-verdict))
(def can-preview? (partial can? :preview-pate-verdict))

(defn can-publish? []
  (or (can? :publish-pate-verdict)
      (can? :publish-legacy-verdict)))

(def can-generate? (partial can? :generate-pate-pdf))

(defn updater
  ([{:keys [path] :as options} value]
   (service/edit-verdict @state/application-id
                         (path/value [:info :id] state/current-verdict)
                         path
                         value
                         (service/update-changes-and-errors state/current-verdict
                                                            options)))
  ([{:keys [state path] :as options}]
   (updater options (path/value path state))))

(defonce raw-verdict (atom {}))

(defn reset-verdict [{:keys [verdict references filled]}]
  (reset! raw-verdict verdict)
  (reset! state/current-verdict
          (when verdict
            {:state (:data verdict)
             :info  (-> (dissoc verdict :data)
                        (assoc :filled? filled)
                        (update :inclusions #(set (map keyword %))))
             :_meta {:updated             updater
                     :highlight-required? (-> verdict :published not)
                     :enabled?            (state/verdict-auth? (:id verdict)
                                                               :edit-pate-verdict)
                     :published?          (:published verdict)
                     :upload.filedata     (fn [_ filedata & kvs]
                                            (apply assoc filedata
                                                   :target {:type :verdict
                                                            :id   (:id verdict)}
                                                   kvs))
                     :upload.include?     (fn [_ {:keys [target]}]
                                            (= (:id target) (:id verdict)))}}))
  (reset! state/references references)
  (common/reset-if-needed! state/verdict-wait? false))

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
         {:on-click #(path/flip-meta options :editing?)}
         (common/loc (if (path/react-meta options :editing?)
                       :close
                       :edit))]]]])])

(defmethod sections/section-header :verdict
  [options _]
  (verdict-section-header options))

(rum/defc toggle-all < rum/reactive
  [{:keys [schema _meta]}]
  (when (can-edit?)
    (let [all-sections  (map :id (:sections schema))
          meta-map (rum/react _meta)
          open-sections (filter #(get meta-map (util/kw-path % :editing?))
                                all-sections)]
      [:a.pate-left-space
       {:on-click #(swap! _meta (fn [m]
                                  (->> (or (not-empty open-sections)
                                           all-sections)
                                       (map (fn [id]
                                              [(util/kw-path id :editing?)
                                               (empty? open-sections)]))
                                       (into {})
                                       (merge m))))}
       (common/loc (if (seq open-sections)
                     :pate.close-all
                     :pate.open-all))])))

(rum/defc generate-pdf-link < rum/reactive
  [verdict-id]
  (let [waiting?* (atom false)]
    [:div.pate-published-note
     (components/text-and-link {:text-loc :pate.verdict.generate-pdf
                                :disabled? waiting?*
                                :click (partial service/generate-pdf @state/application-id
                                                verdict-id
                                                waiting?*)})]))

(rum/defc verdict-toolbar < rum/reactive [{:keys [info _meta] :as options}]
  (let [{:keys [published id category] :as info} @info
        contract? (util/=as-kw category :contract)
        yes-fn     (fn []
                     (reset! state/verdict-wait? true)
                     (reset! (rum/cursor-in _meta [:enabled?]) false)
                     (service/publish-and-reopen-verdict @state/application-id info reset-verdict))]
    [:div.pate-grid-2
     (when-not published
       [:div.row
        [:div.col-2.col--right
         (components/icon-button {:text-loc (if contract?
                                              :pate.contract.publish
                                              :verdict.submit)
                                  :class    (common/css :primary :pate-left-space)
                                  :icon     (if contract?
                                              :lupicon-undersign
                                              :lupicon-document-section-sign)
                                  :wait?    state/verdict-wait?
                                  :enabled? (can-publish?)
                                  :on-click (fn []
                                              (hub/send "show-dialog"
                                                        {:ltitle          "areyousure"
                                                         :size            "medium"
                                                         :component       "yes-no-dialog"
                                                         :componentParams {:ltext (if contract?
                                                                                    "pate.contract.confirm-publish"
                                                                                    "verdict.confirmpublish")
                                                                           :yesFn yes-fn}}))})
         (components/link-button {:url      (js/sprintf "/api/raw/preview-pate-verdict?id=%s&verdict-id=%s"
                                                        @state/application-id
                                                        id)
                                  :enabled? (can-preview?)
                                  :text-loc :pdf.preview})]])
     (if published
       [:div.row
        [:div.col-2.col--right
         [:span.verdict-published
          (common/loc (if contract?
                        :pate.contract.published
                        :pate.verdict-published)
                      (js/util.finnishDate published))]
         (when (can-generate?)
           (generate-pdf-link id))]]
       [:div.row.row--tight
        [:div.col-1
         (pate-components/required-fields-note options)]
        [:div.col-1.col--right
         (toggle-all options)
         (pate-components/last-saved options)]])]))

(rum/defc verdict < rum/reactive
  [options]
  [:div.pate-verdict
   (verdict-toolbar options)
   (sections/sections options :verdict)
   (published/published-verdict @raw-verdict)])


(defn current-verdict-schema []
  (let [{:keys [schema-version legacy?
                category]} (:info @state/current-verdict)]
    (if legacy?
      (legacy/legacy-verdict-schema category)
      (verdict-schemas/verdict-schema category schema-version))))

(rum/defc pate-verdict < rum/reactive
  []
  [:div.container
  [:div.pate-verdict-page {:id "pate-verdict-page"}
   (lupapalvelu.ui.attachment.components/dropzone)
   [:div.operation-button-row
    [:button.secondary
     {:on-click #(common/open-page :application @state/application-id :pate-verdict)}
     [:i.lupicon-chevron-left]
     [:span (common/loc :back)]]]
   (if (and (rum/react state/current-verdict-id)
            (rum/react state/auth-fn))
     (let [{dictionary :dictionary :as schema} (current-verdict-schema)]
       (verdict (assoc (state/select-keys state/current-verdict
                                          [:state :info :_meta])
                       :schema (dissoc schema :dictionary)
                       :dictionary dictionary
                       :references state/references)))
     [:div.pate-spin [:i.lupicon-refresh]])]])

(defn bootstrap-verdict []
  (let [[app-id verdict-id] (js/pageutil.getPagePath)]
    (reset-verdict nil)
    (service/fetch-application-phrases app-id)
    (state/refresh-verdict-auths app-id
                                 {:callback #(state/refresh-application-auth-model
                                              app-id
                                              (fn []
                                                (service/refresh-attachments)
                                                (service/open-verdict app-id
                                                                      verdict-id
                                                                      reset-verdict)))
                                  :verdict-id verdict-id})))

(defonce args (atom {}))

(defn mount-component []
  (when (common/feature? :pate)
    (rum/mount (pate-verdict)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId _]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))
    (bootstrap-verdict)
    (mount-component)))
