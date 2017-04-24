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
  [is-dragged-over]
  [:button.stamp-row-placeholder
   {:on-drag-enter (fn [e] (reset! is-dragged-over true))
    :on-drag-leave (fn [e] (reset! is-dragged-over false))
    :style {:border-style (if (rum/react is-dragged-over)
                            :solid
                            :dashed)}}
   [:span {:style {:pointer-events :none}} "Pudota tähän"]])

(rum/defc stamp-row-btn [text]
  [:button.stamp-row-btn
   [:span text]
   [:i.lupicon-circle-remove]])

(rum/defc stamp-row < rum/reactive [cursor #_{:keys [row-number stamps]}]
  (let [{:keys [row-number stamps]} (rum/react cursor)
        stamp-buttons (map stamp-row-btn stamps)
        is-dragged-over (rum/cursor-in cursor [:is-dragged-over])]
    (if (and row-number stamps)
      [:div.stamp-row
       [:button.stamp-row-label  [:span (str "Rivi "
                                             row-number
                                             #_(if (rum/react is-dragged-over)
                                               "Current row"
                                               ""))]]
       stamp-buttons
       (add-here-placeholder is-dragged-over)]
      [:span "error: stamp-row"])))

(rum/defc form-entry [label-string]
  [:span.form-entry
   [:label.form-label.form-label-string label-string]
   [:input.form-input.text]])

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

;; TODO: check validity of parameters with spec
(rum/defc stamp-btn [{:keys [key content drag-element]}]
  [:button.stamp-editor-btn {:draggable true
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

#_(rum/defc stamp-container [& stamp-names]
  [:div {:style {:margin-bottom "5px"}}
   (map stamp-btn stamp-names)])


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
         #_(stamp-container "Vapaa teksti" "Kuluva pvm" "Päätös pvm" "Kuntalupatunnus" "Käyttäjä" "Organisaatio" "LP-tunnus" "Rakennutunnus")
         (stamp-templates-component drag-element)
         [:div "Raahaa ylläolevia leiman sisältökenttiä..."]
         [:div "raahattavana"
          (pr-str (rum/react drag-element))]
         [:div
          "Rows:"
          (for [row (rum/react rows)]
            [:div (pr-str row)])]
         [:div ;;many rows
          (for [[row-index row] (map vector (range) (rum/react rows))]
            (stamp-row (rum/cursor-in rows [row-index])))
          #_(stamp-row {:row-number 1
                      :stamps ["Asian tunnus" "Kuntalupatunnus"]})
          #_(stamp-row {:row-number 2
                      :stamps ["Hiphei, laitoin tähän tekstiä" "Kuluva pvm" "juupeli juu"]})
          #_(stamp-row {:row-number 3
                      :stamps []})]]]
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
