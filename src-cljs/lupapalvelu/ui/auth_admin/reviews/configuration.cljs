(ns lupapalvelu.ui.auth-admin.reviews.configuration
  "Configure the claim for rectification text for final reviews."
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.state :as state]
            [goog.functions :as gfunc]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(def ENABLED "rectification-enabled")
(def INFO    "rectification-info")
(def CONTACT "contact")
(def ORG-ID  "organizationId")
(def COMMAND :update-review-pdf-configuration)

(defonce state* (atom {}))

(defn state-cursor [field]
  (rum/cursor-in state* [(keyword field)]))

(def enabled?* (state-cursor ENABLED))
(def info*     (state-cursor INFO))
(def contact*  (state-cursor CONTACT))
(def org-id*   (state-cursor ORG-ID))

(defn noop [& _])

(defn submit [field]
  (some->> (map keyword [ORG-ID field])
           (select-keys @state*)
           (into [])
           flatten
           (concat [COMMAND noop])
           (apply common/command)))

(defn init-state [params]
  (let [cfg (js/ko.unwrap (common/oget params "reviewPdf" {}))]
    (reset! org-id* (js/ko.unwrap (common/oget params ORG-ID)))
    (reset! enabled?* (common/oget cfg ENABLED))
    (reset! info* (common/oget cfg INFO))
    (reset! contact* (common/oget cfg CONTACT))
    (reset! state/auth-fn js/lupapisteApp.models.globalAuthModel.ok)))

(defn field-loc [field]
  (common/loc (util/kw-path :review.auth-admin.review-pdf field)))

(rum/defcs markup-edit < rum/reactive
  {:will-mount (fn [state] (assoc state ::submit-fn (gfunc/debounce submit 1000)))}
  [{submit ::submit-fn} field enabled?]
  (let [contact? (= field CONTACT)
        data*    (if contact? contact* info*)]
    [:div {:class (if contact?
                    "spacerL"
                    "configuration-details")}
     [:label.pate-label.spaced
      (field-loc field)]
     (components/markup-edit (rum/react data*)
                             {:test-id  field
                              :style    (when contact?
                                          {:min-height "6em"
                                           :max-height "6em"
                                           :height     "6em"})
                              :disabled (not enabled?)
                              :callback (fn [v]
                                          (reset! data* v)
                                          (submit field))})]))

(rum/defc review-configuration < rum/reactive
  []
  (let [can-edit? (state/auth? COMMAND)]
    [:div
     [:h2 (common/loc :review.auth-admin.title)]
     (markup-edit CONTACT can-edit?)
     (components/toggle (or @enabled?* false)
                        {:text     (field-loc ENABLED)
                         :id       ENABLED
                         :enabled? can-edit?
                         :prefix   :blockbox
                         :test-id  ENABLED
                         :callback #(do
                                      (reset! enabled?* %)
                                      (submit ENABLED))})
    (when (rum/react enabled?*)
      (markup-edit INFO can-edit?))]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (review-configuration)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params]
  (swap! args assoc
         :dom-id (name domId))
  (init-state params)
  (mount-component))
