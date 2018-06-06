(ns lupapalvelu.ui.pate.verdict
  "View of an individual Pate verdict."
  (:require [clojure.set :as set]
            [lupapalvelu.pate.legacy :as legacy]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn can? [action]

  (and (not (rum/react state/verdict-wait?))
       (state/verdict-auth? (rum/react state/current-verdict-id)
                            action)))
(def can-edit? (partial can? :edit-pate-verdict))
(def can-preview? (partial can? :preview-pate-verdict))

(defn can-publish? []
  (or (can? :publish-pate-verdict)
      (can? :publish-legacy-verdict)))

(defn updater
  ([{:keys [state path] :as options} value]
   (service/edit-verdict @state/application-id
                         (path/value [:info :id] state/current-verdict)
                         path
                         value
                         (service/update-changes-and-errors state/current-verdict
                                                            options)))
  ([{:keys [state path] :as options}]
   (updater options (path/value path state))))


(defn reset-verdict [{:keys [verdict references filled]}]
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
                     :upload.include?     (fn [_ {target :target :as att}]
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
  [{:keys [schema _meta] :as options}]
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

(rum/defc verdict < rum/reactive
  [{:keys [schema state info _meta] :as options}]
  (let [{:keys [published legacy? id]
         :as   info} @info
        yes-fn     (fn []
                     (reset! state/verdict-wait? true)
                     (reset! (rum/cursor-in _meta [:enabled?]) false)
                     (service/publish-and-reopen-verdict  @state/application-id
                                                          info
                                                          reset-verdict))]
    [:div.pate-verdict
     [:div.pate-grid-2
      (when-not published
        [:div.row
         [:div.col-2.col--right
          (components/icon-button {:text-loc :verdict.submit
                                   :class    (common/css :primary :pate-left-space)
                                   :icon     :lupicon-circle-section-sign
                                   :wait?    state/verdict-wait?
                                   :enabled? (can-publish?)
                                   :on-click (fn []
                                               (hub/send "show-dialog"
                                                         {:ltitle          "areyousure"
                                                          :size            "medium"
                                                          :component       "yes-no-dialog"
                                                          :componentParams {:ltext "verdict.confirmpublish"
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
           (common/loc :pate.verdict-published
                       (js/util.finnishDate published))]]]
        [:div.row.row--tight
         [:div.col-1
          (pate-components/required-fields-note options)]
         [:div.col-1.col--right
          (toggle-all options)
          (pate-components/last-saved options)]])]
     (sections/sections options :verdict)
     #_(components/debug-atom state/current-verdict)
     #_(components/debug-atom state/allowed-verdict-actions)]))


(defn current-verdict-schema []
  (let [{:keys [schema-version legacy?]} (:info @state/current-verdict)
        category (shared/application->category {:permitType (js/lupapisteApp.models.application.permitType)
                                                :permitSubtype (js/lupapisteApp.models.application.permitSubtype)})]
    (if legacy?
      (legacy/legacy-verdict-schema category)
      (shared/verdict-schema category schema-version))))

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
            (rum/react state/auth-fn)
            (rum/react state/allowed-verdict-actions))
     (let [{dictionary :dictionary :as schema} (current-verdict-schema)]
       (verdict (assoc (state/select-keys state/current-verdict
                                          [:state :info :_meta])
                       :schema (dissoc schema :dictionary)
                       :dictionary dictionary
                       :references state/references)))
     [:div.pate-spin [:i.lupicon-circle-section-sign]])]])

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
