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

(defn- can-edit? []
  (state/auth? :edit-pate-verdict))

(defn- can-view? []
  (state/auth? :pate-verdicts))

(defn- can-publish? []
  (state/auth? :publish-pate-verdict))

(defn- can-edit-verdict? [{published :published}]
  (and (can-edit?)
       (not published)))

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
                     :enabled?            (can-edit-verdict? verdict)
                     :published?          (:published verdict)
                     :upload.filedata     (fn [_ filedata & kvs]
                                            (apply assoc filedata
                                                   :target {:type :verdict
                                                            :id   (:id verdict)}
                                                   kvs))
                     :upload.include?     (fn [_ {target :target :as att}]
                                            (= (:id target) (:id verdict)))}}))
  (reset! state/references references))

(rum/defc verdict-section-header < rum/reactive
  [{:keys [schema] :as options}]
  [:div.pate-grid-1.section-header
   {:class (path/css options)}
   (when (and (not (-> schema :buttons? false?))
              (path/enabled? options))
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
  (when (path/enabled? options)
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

(rum/defcs verdict < rum/reactive
  (rum/local false ::wait?)
  [{wait?* ::wait?} {:keys [schema state info _meta] :as options}]
  (let [published (path/react :published info)
        yes-fn    (fn []
                    (reset! wait?* true)
                    (reset! (rum/cursor-in _meta [:enabled?]) false)
                    (service/publish-and-reopen-verdict  @state/application-id
                                                         (path/value :id info)
                                                         reset-verdict))]
    [:div.pate-verdict
     [:div.pate-grid-2
      (when (and (or (path/enabled? options)
                     (rum/react wait?*))
                 (not published))
        [:div.row
         [:div.col-2.col--right
          (components/icon-button {:text-loc :verdict.submit
                                   :class    (common/css :primary :pate-left-space)
                                   :icon     :lupicon-circle-section-sign
                                   :wait?    wait?*
                                   :enabled? (and (path/react :filled? info)
                                                  (can-publish?))
                                   :on-click (fn []
                                               (hub/send "show-dialog"
                                                         {:ltitle          "areyousure"
                                                          :size            "medium"
                                                          :component       "yes-no-dialog"
                                                          :componentParams {:ltext "verdict.confirmpublish"
                                                                            :yesFn yes-fn}}))})
          (components/link-button {:url      (js/sprintf "/api/raw/preview-pate-verdict?id=%s&verdict-id=%s"
                                                         @state/application-id
                                                         (path/value :id info))
                                   :enabled? (and (path/enabled? options)
                                                  (path/react :filled? info))
                                   :disabled published
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
     (components/debug-atom state/current-verdict "Verdict")]))


(defn current-verdict-schema []
  (let [{:keys [schema-version legacy?]} (:info @state/current-verdict)
        category (shared/permit-type->category (js/lupapisteApp.models.application.permitType))]
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
            (rum/react state/auth-fn))
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
    (state/refresh-application-auth-model app-id
                                          #(do (service/refresh-attachments)
                                               (service/open-verdict app-id
                                                                     verdict-id
                                                                     reset-verdict)))))

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
