(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command loc] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]))

(def ^:dynamic *debug-edge-detection* false)
(def ^:dynamic *debug-info-divs* false)

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
                                     :debug-data {}
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

(rum/defc placeholder-box [width]
  [:button.stamp-row-btn
   {:style {:width width
            :background-color "black"}}])

(rum/defc stamp-row-btn < rum/reactive
  [{:keys [text remove debug-data row-number row-index]}]
  (let [closest? (= [row-number row-index]
                    (get-in (rum/react debug-data) [:closest :elem]))
        side (get-in (rum/react debug-data) [:closest :side])
        border-side {:left :border-left
                     :right :border-right}
        border-style (if (and closest? *debug-edge-detection*)
                       {(border-side side) "solid red 2px"}
                       {})]
    [:button.stamp-row-btn
     {:style border-style
      :data-row row-number
      :data-row-index row-index}
     [:span text]
     [:i.lupicon-circle-remove
      {:on-click (fn [e] (remove))}]]))

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

;;TODO: needs better name
(defn drop-at
  [a-vec idx]
  (vec (concat (subvec a-vec 0 idx)
               (subvec a-vec (inc idx)))))

(defn indexed
  [a-seq]
  (map vector (range) a-seq))

(defn get-js-fields [object & fields]
  (let [field->pair (fn [field]
                      [(keyword field)
                       (aget object field)])]
    (into {}
          (map field->pair fields))))

(defn abs [x]
  (if (neg? x)
    (- x)
    x))

(defn dist [x y]
  (abs (- x y)))

(defn closest-rect [debug-data]
  "Finds the element closest to mouse position in the current row"
  (let [{:keys [client-x client-y]} (:row-mouse debug-data)
        current-row (:current-row debug-data)
        cursor-inside? (fn [{:keys [top bottom left right]}]
                         (and (<= left client-x right)
                              (<= top client-y bottom)))
        distance (fn [{:keys [top bottom left right] :as rect}]
                   (if (cursor-inside? rect)
                     0
                     (min (dist client-x left)
                          (dist client-x right))))
        rects (get-in debug-data [:row-data current-row])
        by-distance (sort-by distance rects)]
    (first by-distance)))

(defn closest-with-edge [debug-data]
  (let [{:keys [left right] :as closest} (closest-rect debug-data)
        {:keys [client-x]} (:row-mouse debug-data)
        side (if (< (dist client-x left)
                    (dist client-x right))
               :left
               :right)]
    {:side side
     :elem (:position closest)}))

;;TODO: use some cljs library for dom stuff
(defn query-selector-all [query]
  (->> query
       (.querySelectorAll js/document)
       array-seq))

(defn query-selector [query]
  (.querySelector js/document query))

(defn boundaries-from-dom-element [dom-element]
  (let [client-rect (.getBoundingClientRect dom-element)]
    (get-js-fields client-rect
                   "top" "bottom" "left" "right" "width")))

(defn drag-source-boundaries []
  (when-let [elem (query-selector ".drag-source")]
    (boundaries-from-dom-element elem)))

(defn dom-data-for-row [row-number]
  (let [buttons (query-selector-all (str ".stamp-row-btn[data-row='"
                                         row-number
                                         "']"))
        client-rects (map boundaries-from-dom-element buttons)
        client-rects (for [[i rect] (indexed client-rects)]
                       (merge rect
                              {:position [row-number i]}))]
    {row-number client-rects}))

(defn dom-data []
  (let [rows (query-selector-all ".stamp-row")
        row-datas (for [[i _] (indexed rows)]
                    (dom-data-for-row (inc i)))]
    {:row-data (apply merge row-datas)}))

(defn closest->placeholder-position [{:keys [elem side] :as closest}]
  (cond (nil? closest) nil
        (= :left side) elem
        :else          (update-in elem [1] inc)))

(defn with-placeholder [{:keys [buttons debug-data row-number placeholder-width]}]
  (if-not (:closest debug-data)
    buttons
    (let [placeholder-location (closest->placeholder-position
                                 (:closest debug-data))
          placeholder-row? #_false (= row-number (first placeholder-location))
          placeholder-position (second placeholder-location)
          placeholder-element (placeholder-box (or placeholder-width
                                                   7))
          [before after] (split-at placeholder-position buttons)]
      (if-not placeholder-row?
        buttons
        (concat before
                [placeholder-element]
                after)))))

(rum/defc stamp-row < rum/reactive
  [{:keys [stamp-row-cursor drag-source debug-data]}]
  (let [{:keys [row-number stamps]} (rum/react stamp-row-cursor)
        remove-btn (fn [idx] (fn []
                              (swap! stamp-row-cursor
                                     update-in [:stamps] drop-at idx)))
        stamp-buttons (for [[idx stamp] (indexed stamps)]
                        (stamp-row-btn {:text stamp
                                        :remove (remove-btn idx)
                                        :debug-data debug-data
                                        :row-number row-number
                                        :row-index idx}))
        drag-content (rum/react drag-source)
        stamp-type (get drag-content :stamp-type :error-stamp-type)
        content (get stamp-templates stamp-type :error-stamp-template)
        text-content (get content :text-content :error-text-content)
        is-dragged-over (rum/cursor-in stamp-row-cursor [:is-dragged-over])
        placeholder-width (get-in (rum/react drag-source)
                                  [:source-boundaries :width])
        buttons-with-placeholder (with-placeholder
                                   {:buttons stamp-buttons
                                    :debug-data (rum/react debug-data)
                                    :placeholder-width placeholder-width
                                    :row-number row-number})
        drop-handler (fn [e]
                       (swap! stamp-row-cursor update :stamps conj text-content)
                       (reset! is-dragged-over false))
        on-drag-enter (fn [e]
                        (.preventDefault e)
                        (reset! is-dragged-over true))
        on-drag-leave (fn [e]
                        (reset! is-dragged-over false))
        mouse-move-handler (fn [e]
                             (let [client-x (aget e "clientX")
                                   client-y (aget e "clientY")
                                   data {:row-mouse {:client-x client-x
                                                     :client-y client-y}
                                         :current-row row-number}
                                   dom-data (dom-data)
                                   closest-data {:closest (closest-with-edge
                                                            (merge data dom-data))}]
                               (swap! debug-data merge data dom-data closest-data)))]
    (if (and row-number stamps)
      [:div.stamp-row {;;:on-mouse-move mouse-move-handler
                       :on-drag-over mouse-move-handler
                       :data-row-number row-number}
       [:button.stamp-row-label  [:span (str "Rivi "
                                             row-number)]]
       buttons-with-placeholder
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
(rum/defcs stamp-btn < (rum/local false)
  [state {:keys [key content drag-element drag-end-cb]}]
  (let [drag-source? (:rum/local state)
        base-classes "stamp-editor-btn"
        extra-classes (if @drag-source?
                        "drag-source"
                        "")
        all-classes (clojure.string/join " " [base-classes extra-classes])
        element-selector (str ".stamp-editor-btn[data-stamp-btn-name='"
                              content
                              "']")
        find-boundaries (fn []
                          (-> element-selector
                              query-selector
                              boundaries-from-dom-element))]
    [:button
     {:draggable true
      :on-drag-start (fn [e]
                       (reset! drag-element
                               {:type :new
                                :stamp-type key
                                :source-boundaries (find-boundaries)})
                       (reset! drag-source? true))
      :on-drag-end (fn [e]
                     (drag-end-cb)
                     (reset! drag-element nil)
                     (reset! drag-source? false))
      :data-stamp-btn-name content
      :class all-classes}
     [:i.lupicon-circle-plus]
     [:span content]]))

(rum/defc stamp-templates-component < rum/reactive
  [{:keys [drag-element drag-end-cb] :as params}]
  [:div
   (for [[a-key {content :text-content}] stamp-templates]
     (stamp-btn (merge params
                       {:key a-key
                        :content content})))])

(rum/defc debug-component < rum/reactive
  [debug-data]
  (let [data (rum/react debug-data)]
    [:div
     [:div "Debug: " (pr-str data)]
     [:div "Placeholder: " (pr-str (closest->placeholder-position
                                     (:closest data)))]]))

(rum/defc header-component < rum/reactive
  []
  [:div.group-buttons
   {:style {:background-color "#f6f6f6"
            :border "1px solid #dddddd"}}
   ;;TODO: onks joku otsikkorivicontainer-luokka josta tulis toi oikee harmaa taustaväri ja muut tyylit niinku haitareissa?
   (form-entry "Leiman nimi:")
   [:button.secondary.is-right
    [:i.lupicon-remove]
    [:span "Poista"]]])

(rum/defc metadata-component < rum/reactive
  []
  [:div.form-group {:style {:width "60%"
                            :display :inline-block}}
   [:label.form-label.form-label-group "Leiman sijainti"]
   [:div
    (form-entry "Oikeasta reunasta (mm)")
    (form-entry "Alareunasta (mm)")]
   [:div
    (form-entry "Leiman tausta") ;;TODO: vaihda dropdowniin
    (form-entry "Leimattava sivu")] ;;TODO: vaihda dropdowniin
   ])

(rum/defc preview-component < rum/reactive
  []
  [:div.form-group {:style {:width "35%"
                            :border "1px solid"
                            :display :inline-block}}
   [:div
    "sisältöä"]])

(defn add-at-index [a-seq index elem]
  (let [[before after] (split-at index a-seq)]
    (vec (concat before
                 [elem]
                 after))))

(defn drag-end [editor-state]
  (when-let [closest (get-in @editor-state
                        [:debug-data :closest])]
    (let [[row col] (closest->placeholder-position closest)
          row-index (dec row)
          source (:drag-element  @editor-state)
          new-content (-> source
                          :stamp-type
                          stamp-templates
                          :text-content)
          add-source-elem (fn [stamps]
                            (add-at-index stamps col new-content))]
      (swap! editor-state
             update-in [:rows row-index :stamps]
             add-source-elem)
      (swap! editor-state
             assoc-in [:debug-data :closest] nil))))

(rum/defc edit-stamp-bubble < rum/reactive
  [visible? editor-state]
  (let [drag-element (rum/cursor editor-state :drag-element)
        debug-data (rum/cursor editor-state :debug-data)
        rows (rum/cursor editor-state :rows)
        drag-end-handler (fn [] (drag-end editor-state))]
    (when (rum/react visible?)
      [:div.edit-stamp-bubble
       (header-component)
       [:div.form-group {:style {:display :block}}
        (metadata-component)
        (preview-component)
        [:div.form-group
         [:label.form-label.form-label-group "Leiman sisältö"]
         (stamp-templates-component {:drag-element drag-element
                                     :drag-end-cb drag-end-handler})
         [:div "Raahaa ylläolevia leiman sisältökenttiä..."]
         (when *debug-info-divs*
           [:div "raahattavana: "
           (pr-str (rum/react drag-element))])
         (when *debug-info-divs*
           [:div
            "Rows:"
            (for [row (rum/react rows)]
              [:div (pr-str row)])])
         [:div ;;many rows
          (for [row-cursor (cursor-of-vec->vec-of-cursors rows)]
            (stamp-row {:stamp-row-cursor row-cursor
                        :debug-data debug-data
                        ;;TODO: use lentes lenses for simple derived atoms?
                        :drag-source drag-element}))]
         (when *debug-info-divs*
           (debug-component debug-data))]]])))

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
