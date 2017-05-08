(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command loc] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.attachment.stamp-schema :as ss]))

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
                                             :fields [{:type :application-id}
                                                      {:type :backend-id}]
                                             :is-dragged-over false}
                                            {:row-number 2
                                             :fields [{:type :custom-text
                                                       :text "Hiphei, laitoin tähän tekstiä"}
                                                      {:type :current-date}]
                                             :is-dragged-over false}
                                            {:row-number 3
                                             :fields []
                                             :is-dragged-over false}]}})

(defonce component-state  (atom empty-component-state))

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

(rum/defc stamp-row-field < rum/reactive
  [{:keys [data remove debug-data row-number row-index]}]
  (let [closest? (= [row-number row-index]
                    (get-in (rum/react debug-data) [:closest :elem]))
        side (get-in (rum/react debug-data) [:closest :side])
        border-side {:left :border-left
                     :right :border-right}
        border-style (if (and closest? *debug-edge-detection*)
                       {(border-side side) "solid red 2px"}
                       {})
        {:keys [type text]} (rum/react data)
        rendered-content (if (= type :custom-text)
                           [:input {:placeholder (loc :stamp.custom-text)
                                    :value text
                                    :on-change #(swap! data assoc :text (-> % .-target .-value))
                                    :style {:width (max 150 (Math/floor (* 8 (count text))))}}]
                           [:span (loc (str "stamp." (name type)))])]
    [:div.stamp-row-btn
     {:style border-style
      :data-row row-number
      :data-row-index row-index}
     [:div.btn-content
      rendered-content
      [:i.lupicon-circle-remove
       {:on-click (fn [e] (remove))}]]]))

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

(def closest-elem (atom []))

(defn add-at-index [a-seq index elem]
  (let [[before after] (split-at index a-seq)]
    (vec (concat before
                 [elem]
                 after))))

(rum/defcs stamp-row < rum/reactive (rum/local 0 ::drag-event-sum)
  [state {:keys [stamp-row-cursor drag-source debug-data]}]
  (let [{:keys [row-number fields]} (rum/react stamp-row-cursor)
        drag-event-sum (::drag-event-sum state)
        remove-btn (fn [idx] (fn []
                              (swap! stamp-row-cursor
                                     update-in [:fields] drop-at idx)))
        field-buttons (for [[idx _] (indexed fields)]
                        (rum/with-key
                          (stamp-row-field {:data       (rum/cursor-in stamp-row-cursor [:fields idx])
                                            :remove     (remove-btn idx)
                                            :debug-data debug-data
                                            :row-number row-number
                                            :row-index  idx})
                          idx))
        placeholder-width (or (get-in (rum/react drag-source)
                                      [:source-boundaries :width])
                              110)
        on-drag-enter (fn [e]
                        (.preventDefault e)
                        (swap! drag-event-sum inc))
        on-drag-leave (fn [_]
                        (swap! drag-event-sum dec))
        is-dragged-over? (pos? @drag-event-sum)
        mouse-move-handler (fn [e]
                             (let [dt (-> e .-dataTransfer)]
                               (when (.getData dt "stampField")
                                 (.preventDefault e)
                                 (when (= "new" (.getData dt "dragIntent"))
                                   (set! (-> e .-dataTransfer .-dropEffect) "copy"))))
                             (let [client-x (aget e "clientX")
                                   client-y (aget e "clientY")
                                   data {:row-mouse {:client-x client-x
                                                     :client-y client-y}
                                         :current-row row-number}
                                   dom-data (dom-data)]
                               (reset! closest-elem (closest-with-edge
                                                      (merge data dom-data)))))
        placeholder-location (closest->placeholder-position (rum/react closest-elem))
        placeholder-row? (= row-number (first placeholder-location))
        split-pos (second placeholder-location)
        drop-handler (fn [e]
                       (.preventDefault e)
                       (let [{:strs [type]} (-> e .-dataTransfer (.getData "stampField") util/json->clj)
                             field-data {:type (keyword type)}]
                         (swap! stamp-row-cursor update :fields add-at-index split-pos field-data)))
        placeholder-element [:div.stamp-row-placeholder
                             {:key :placeholder
                              :style {:width (max placeholder-width 110)}}
                             [:span {:style {:pointer-events :none}} "Pudota tähän"]]
        [before after] (when (number? split-pos) (split-at split-pos field-buttons))]
    (if (and row-number fields)
      [:div.stamp-row {:on-drag-enter on-drag-enter
                       :on-drag-leave on-drag-leave
                       :on-drag-over mouse-move-handler
                       :on-drop drop-handler
                       :data-row-number row-number}
       [:div.stamp-row-label  [:span (str "Rivi "
                                             row-number)]]
       (if (and placeholder-row? is-dragged-over?)
         (concat before
                 [placeholder-element]
                 after)
         (concat
           field-buttons
           [[:div.stamp-row-placeholder
             {:key :default-placeholder}
             [:span {:style {:pointer-events :none}} "Pudota tähän"]]]))]
      [:span "error: stamp-row"])))

(rum/defc form-entry [label-string]
  [:span.form-entry
   [:label.form-label.form-label-string label-string]
   [:input.form-input.text]])

(rum/defcs field-type-selector < (rum/local false)
  [local-state {:keys [key]}]
  (let [drag-source? (:rum/local local-state)
        drag-element (rum/cursor-in component-state [:editor :drag-element])
        base-classes "stamp-editor-btn"
        extra-classes (if @drag-source?
                        "drag-source"
                        "")
        all-classes (string/join " " [base-classes extra-classes])
        element-selector (str ".stamp-editor-btn[data-stamp-btn-name='"
                              key
                              "']")
        find-boundaries (fn []
                          (-> element-selector
                              query-selector
                              boundaries-from-dom-element))]
    [:div
     {:draggable true
      :on-drag-start (fn [e]
                       (reset! drag-element
                               {:type :new
                                :stamp-type key
                                :source-boundaries (find-boundaries)})
                       (reset! drag-source? true)

                       (let [data-transfer (.-dataTransfer e)]
                         (.setData data-transfer "stampField" (util/clj->json {:type key}))
                         (.setData data-transfer "dragIntent" "new")))
      :on-drag-end (fn [_]
                     (reset! drag-source? false)
                     (reset! closest-elem []))
      :data-stamp-btn-name key
      :class all-classes}
     [:div.btn-content
      [:i.lupicon-circle-plus]
      [:span (loc (str "stamp." (name key)))]]]))

(rum/defc field-types-component < rum/reactive
  []
  [:div
   (for [field-type (concat ss/simple-field-types ss/text-field-types)]
     (rum/with-key
       (field-type-selector {:key field-type})
       field-type))])

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

(rum/defc edit-stamp-bubble < rum/reactive
  [visible? editor-state]
  (let [drag-element (rum/cursor editor-state :drag-element)
        debug-data (rum/cursor editor-state :debug-data)
        rows (rum/cursor editor-state :rows)]
    (when (rum/react visible?)
      [:div.edit-stamp-bubble
       (header-component)
       [:div.form-group {:style {:display :block}}
        (metadata-component)
        (preview-component)
        [:div.form-group
         [:label.form-label.form-label-group "Leiman sisältö"]
         (field-types-component)
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
            (rum/with-key
              (stamp-row {:stamp-row-cursor row-cursor
                          :debug-data debug-data
                          ;;TODO: use lentes lenses for simple derived atoms?
                          :drag-source drag-element})
              (:row-number @row-cursor)))]
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
