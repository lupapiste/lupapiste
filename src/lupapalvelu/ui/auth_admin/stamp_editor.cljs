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
                                     :closest-element []
                                     :debug-data {}
                                     :rows [{:fields [{:type :application-id}
                                                      {:type :backend-id}]
                                             :is-dragged-over false}
                                            {:fields [{:type :custom-text
                                                       :text "Hiphei, laitoin tähän tekstiä"}
                                                      {:type :current-date}]
                                             :is-dragged-over false}
                                            {:fields []
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

(defn get-js-fields [object & fields]
  (let [field->pair (fn [field]
                      [(keyword field)
                       (aget object field)])]
    (into {}
          (map field->pair fields))))

(defn boundaries-from-dom-element [dom-element]
  (-> (.getBoundingClientRect dom-element)
      (get-js-fields "top" "bottom" "left" "right" "width")))

(defn boundaries-from-component [component-state]
  (let [comp     (:rum/react-component component-state)
        dom-node (js/ReactDOM.findDOMNode comp)]
    (boundaries-from-dom-element dom-node)))

(rum/defcs stamp-row-field < rum/reactive (rum/local "inline-block" ::display)
  [local-state {:keys [data remove row-number row-index]}]
  (let [drag-element (rum/cursor-in component-state [:editor :drag-element])
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        {:keys [type text]} (rum/react data)
        rendered-content (if (= type :custom-text)
                           [:input {:placeholder (loc :stamp.custom-text)
                                    :value text
                                    :on-change #(swap! data assoc :text (-> % .-target .-value))
                                    :style {:width (max 150 (Math/floor (* 8 (count text))))}}]
                           [:span (loc (str "stamp." (name type)))])
        display-style (::display local-state)]
    [:div.stamp-row-btn
     {:style {:display @display-style}
      :data-row row-number
      :data-row-index row-index
      :draggable true
      :on-drag-start (fn [e]
                       (reset! drag-element
                               {:type :new
                                :stamp-type key
                                :source-boundaries (boundaries-from-component local-state)})
                       (let [data-transfer (.-dataTransfer e)]
                         (.setData data-transfer "moveField" (util/clj->json {:row row-number
                                                                              :index row-index})))
                       (reset! display-style "none"))
      :on-drag-end (fn [_]
                     (reset! closest-elem [])
                     (reset! display-style "inline-block"))}
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
                    (dom-data-for-row i))]
    {:row-data (apply merge row-datas)}))

(defn closest->placeholder-position [{:keys [elem side] :as closest}]
  (cond (nil? closest) nil
        (= :left side) elem
        :else          (update-in elem [1] inc)))

(defn add-at-index [a-seq index elem]
  (let [[before after] (split-at index a-seq)]
    (vec (concat before
                 [elem]
                 after))))

(defn drop-handler [all-rows-cursor current-row split-pos]
  (fn [e]
    (.preventDefault e)
    (let [dt (.-dataTransfer e)
          new (.getData dt "newField")
          moved (.getData dt "moveField")
          {:strs [type]} (when-not (string/blank? new) (util/json->clj new))
          {:strs [row index]} (when-not (string/blank? moved) (util/json->clj moved))
          field-data (if (and row index)
                       (get-in @all-rows-cursor [row :fields index])
                       {:type (keyword type)})]
      (swap! all-rows-cursor update-in [current-row :fields] add-at-index split-pos field-data)
      (when (and row index)
        (let [drop-index (if (and (= row current-row) (< split-pos index))
                           (inc index)
                           index)]
          (swap! all-rows-cursor update-in [row :fields] drop-at drop-index)))
      (-> e .-dataTransfer (.clearData)))))

(rum/defcs stamp-row < rum/reactive (rum/local 0 ::drag-event-sum)
  [state {:keys [index rows-cursor drag-source debug-data]}]
  (let [stamp-row-cursor (rum/cursor rows-cursor index)
        {:keys [fields]} (rum/react stamp-row-cursor)
        drag-event-sum (::drag-event-sum state)
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        remove-btn (fn [idx] (fn []
                              (swap! stamp-row-cursor
                                     update-in [:fields] drop-at idx)))
        field-buttons (for [[idx _] (indexed fields)]
                        (rum/with-key
                          (stamp-row-field {:data       (rum/cursor-in stamp-row-cursor [:fields idx])
                                            :remove     (remove-btn idx)
                                            :debug-data debug-data
                                            :row-number index
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
                             (let [dt (-> e .-dataTransfer)
                                   op-type (.-type (aget (.-items dt) 0))
                                   effect (case op-type
                                            "newfield" "copy"
                                            "movefield" "move"
                                            nil)]
                               (when effect
                                 (.preventDefault e)
                                 (set! (-> e .-dataTransfer .-dropEffect) effect)))
                             (let [client-x (aget e "clientX")
                                   client-y (aget e "clientY")
                                   data {:row-mouse {:client-x client-x
                                                     :client-y client-y}
                                         :current-row index}
                                   dom-data (dom-data)]
                               (reset! closest-elem (closest-with-edge
                                                      (merge data dom-data)))))
        placeholder-location (closest->placeholder-position (rum/react closest-elem))
        placeholder-row? (= index (first placeholder-location))
        split-pos (second placeholder-location)
        on-drop (drop-handler rows-cursor index split-pos)
        placeholder-element [:div.stamp-row-placeholder
                             {:key :placeholder
                              :style {:width (max placeholder-width 110)}}
                             [:span {:style {:pointer-events :none}} "Pudota tähän"]]
        [before after] (when (number? split-pos) (split-at split-pos field-buttons))]
    (if (and index fields)
      [:div.stamp-row {:on-drag-enter on-drag-enter
                       :on-drag-leave on-drag-leave
                       :on-drag-over mouse-move-handler
                       :on-drop on-drop
                       :data-row-number index}
       [:div.stamp-row-label  [:span (str "Rivi " (inc index))]]
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
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        base-classes "stamp-editor-btn"
        extra-classes (if @drag-source?
                        "drag-source"
                        "")
        all-classes (string/join " " [base-classes extra-classes])]
    [:div
     {:draggable true
      :on-drag-start (fn [e]
                       (reset! drag-element
                               {:type :new
                                :stamp-type key
                                :source-boundaries (boundaries-from-component local-state)})
                       (reset! drag-source? true)

                       (let [data-transfer (.-dataTransfer e)]
                         (.setData data-transfer "newField" (util/clj->json {:type key}))))
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
          (for [[idx _] (indexed (rum/react rows))]
            (rum/with-key
              (stamp-row {:index idx
                          :rows-cursor rows
                          :debug-data debug-data
                          :drag-source drag-element})
              idx))]
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
