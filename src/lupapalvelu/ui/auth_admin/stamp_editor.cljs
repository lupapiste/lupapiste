(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command loc] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]))

(defn find-by-id
  [v col]
  (some #(when (= v (:id %)) %) col))

(defn cursor-of-vec->vec-of-cursors
  ;;TODO generalize to maps as well
  ;;rename to cursor-sequence? (sequenceA in Haskell?)
  [an-atom]
  (let [indexes (range 0 (count @an-atom))
        idx->child-cursor (fn [idx] (rum/cursor an-atom idx))]
    (mapv idx->child-cursor indexes)))

(def empty-stamp   {:name     ""
                    :position {:x 0 :y 0}
                    :page     :first
                    :qr-code  true
                    :rows     [[]]})

(def empty-component-state {:stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}
                            :editor {:drag-element nil
                                     :rows [{:row-number 1
                                             :stamps ["Asian tunnus" "Kuntalupatunnus"]
                                             :is-dragged-over false}
                                            {:row-number 2
                                             :stamps ["Hiphei, laitoin tähän tekstiä"
                                                      "Kuluva pvm"
                                                      "juupeli juu"]
                                             :is-dragged-over false}
                                            {:row-number 3
                                             :stamps []
                                             :is-dragged-over false}]}})

(def component-state  (atom empty-component-state))

(def selected-stamp (rum-util/derived-atom
                     [component-state]
                     (fn [state]
                       (or (-> (get-in state [:view :selected-stamp-id])
                               (find-by-id (:stamps state)))
                           empty-stamp))))

(defn- update-stamp-view [id]
  (swap! component-state assoc-in [:view :selected-stamp-id] id))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (query :stamp-templates
          (fn [data]
            (swap! component-state assoc :stamps (:stamps data))
            (when cb (cb data))))))

(rum/defc stamp-select < rum/reactive
  [stamps selection]
  (uc/select update-stamp-view
             "stamp-select"
             (rum/react selection)
             (cons ["" (loc "choose")]
                   (map (juxt :id :name) (rum/react stamps)))))

(rum/defc new-stamp-button [bubble-visible selected-stamp-id]
  [:button.positive
   {:on-click (fn [_]
                (reset! selected-stamp-id nil)
                (reset! bubble-visible true))
    :data-test-id "open-create-stamp-bubble"}
   [:i.lupicon-circle-plus]
   [:span (loc "stamp-editor.new-stamp.button")]])

(defn init
  [init-state props]
  (let [[auth-model] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :auth-models {:global-auth-model auth-model})
    (when (auth/ok? auth-model :stamp-templates) (refresh))
    init-state))

(rum/defc add-here-placeholder < rum/reactive
  [{:keys [is-dragged-over drop-handler on-drag-enter on-drag-leave]}]
  (let [class (if (rum/react is-dragged-over)
                "stamp-row-focus"
                "stamp-row-placeholder")]
    [:button.stamp-row-placeholder
     {:on-drag-enter on-drag-enter
      :on-drag-over (fn [e] (.preventDefault e))
      :on-drag-leave on-drag-leave
      :on-drop drop-handler
      :class class}
     [:span {:style {:pointer-events :none}} "Pudota tähän"]]))

(rum/defc stamp-row-btn [text remove]
  [:button.stamp-row-btn
   [:span text]
   [:i.lupicon-circle-remove
    {:on-click (fn [e] (remove))}]])

(def stamp-templates
  ;; TODO: use the enum from backend for keys
  {:vapaa-teksti    {:text-content "Vapaa teksti"}
   :kuluva-pvm      {:text-content "Kuluva pvm"}
   :paatos-pvm      {:text-content "Päätös pvm"}
   :kuntalupatunnus {:text-content "Kuntalupatunnus"}
   :kayttaja        {:text-content "Käyttäjä"}
   :organisaatio    {:text-content "Organisaatio"}
   :lp-tunnus       {:text-content "LP-tunnus"}
   :rakennustunnus  {:text-content "Rakennustunnus"}})

(defn drop-at
  [a-vec idx]
  (vec (concat (subvec a-vec 0 idx)
               (subvec a-vec (inc idx)))))

(defn indexed
  [a-seq]
  (map vector (range) a-seq))

(rum/defc stamp-row < rum/reactive
  [{:keys [stamp-row-cursor drag-source]}]
  (let [{:keys [row-number stamps]} (rum/react stamp-row-cursor)
        remove-btn (fn [idx] (fn []
                              (swap! stamp-row-cursor
                                     update-in [:stamps] drop-at idx)))
        stamp-buttons (for [[idx stamp] (indexed stamps)]
                        (stamp-row-btn stamp
                                       (remove-btn idx)))
        drag-content (rum/react drag-source)
        stamp-type (get drag-content :stamp-type :error-stamp-type)
        content (get stamp-templates stamp-type :error-stamp-template)
        text-content (get content :text-content :error-text-content)
        is-dragged-over (rum/cursor-in stamp-row-cursor [:is-dragged-over])
        drop-handler (fn [e]
                       (swap! stamp-row-cursor update :stamps conj text-content)
                       (reset! is-dragged-over false))
        on-drag-enter (fn [e]
                        (.preventDefault e)
                        (reset! is-dragged-over true))
        on-drag-leave (fn [e]
                        (reset! is-dragged-over false))]
    (if (and row-number stamps)
      [:div.stamp-row
       [:button.stamp-row-label  [:span (str "Rivi "
                                             row-number)]]
       stamp-buttons
       (add-here-placeholder {:is-dragged-over is-dragged-over
                              :drop-handler drop-handler
                              :on-drag-enter on-drag-enter
                              :on-drag-leave on-drag-leave})]
      [:span "error: stamp-row"])))

(rum/defc form-entry [label-string]
  [:span.form-entry
   [:label.form-label.form-label-string label-string]
   [:input.form-input.text]])

;; TODO: check validity of parameters with spec
(rum/defc stamp-btn [{:keys [key content drag-element]}]
  [:button.stamp-editor-btn
   {:draggable true
    :on-drag-start (fn [e] (reset! drag-element {:type :new
                                                :stamp-type key}))
    :on-drag-end (fn [e] (reset! drag-element nil))}
   [:i.lupicon-circle-plus]
   [:span content]])

(rum/defc stamp-templates-component < rum/reactive
  [drag-element]
  [:div
   (for [[a-key {content :text-content}] stamp-templates]
     (stamp-btn {:key a-key
                 :content content
                 :drag-element drag-element}a-key content))])

(rum/defc edit-stamp-bubble < rum/reactive
  [visible? editor-state]
  (let [drag-element (rum/cursor editor-state :drag-element)
        rows (rum/cursor editor-state :rows)]
    (when (rum/react visible?)
      [:div.edit-stamp-bubble
       [:div.group-buttons
        {:style {:background-color "#f6f6f6"
                 :border "1px solid #dddddd"}}
        ;;TODO: onks joku otsikkorivicontainer-luokka josta tulis toi oikee harmaa taustaväri ja muut tyylit niinku haitareissa?
        (form-entry "Leiman nimi:")
        [:button.secondary.is-right
         [:i.lupicon-remove]
         [:span "Poista"]]]
       [:div.form-group {:style {:display :block}}
        [:div.form-group {:style {:width "60%"
                                  :display :inline-block}}
         [:label.form-label.form-label-group "Leiman sijainti"]
         [:div
          (form-entry "Oikeasta reunasta (mm)")
          (form-entry "Alareunasta (mm)")]
         [:div
          (form-entry "Leiman tausta") ;;TODO: vaihda dropdowniin
          (form-entry "Leimattava sivu")] ;;TODO: vaihda dropdowniin
         ]
        [:div.form-group {:style {:width "35%"
                                  :border "1px solid"
                                  :display :inline-block}}
         [:div
          "sisältöä"]]
        [:div.form-group
         [:label.form-label.form-label-group "Leiman sisältö"]
         (stamp-templates-component drag-element)
         [:div "Raahaa ylläolevia leiman sisältökenttiä..."]
         [:div "raahattavana"
          (pr-str (rum/react drag-element))]
         [:div
          "Rows:"
          (for [row (rum/react rows)]
            [:div (pr-str row)])]
         [:div ;;many rows
          (for [row-cursor (cursor-of-vec->vec-of-cursors rows)]
            (stamp-row {:stamp-row-cursor row-cursor
                        ;;TODO: use lentes lenses for simple derived atoms?
                        :drag-source drag-element}))]]]
       ;; TODO: Editor here
       ])))

(rum/defc stamp-editor
  [global-auth-model]
  {:init init
   :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  (let [stamps            (rum/cursor component-state :stamps)
        selected-stamp-id (rum/cursor-in component-state [:view :selected-stamp-id])
        bubble-visible    (rum/cursor-in component-state [:view :bubble-visible])
        editor-state      (rum/cursor-in component-state [:editor])]
    [:div
     [:h1 (loc "stamp-editor.tab.title")]
     [:div
      (stamp-select stamps selected-stamp-id)
      (new-stamp-button bubble-visible selected-stamp-id)]
     [:div.row.edit-sta (edit-stamp-bubble bubble-visible
                                           editor-state)]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
