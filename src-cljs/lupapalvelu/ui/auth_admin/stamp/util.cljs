(ns lupapalvelu.ui.auth-admin.stamp.util
  (:require [clojure.string :as string]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.attachment.stamp-schema :as sts]))

(defn find-by-id
  [v col]
  (some #(when (= v (:id %)) %) col))

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
  (let [comp (:rum/react-component component-state)
        dom-node (js/ReactDOM.findDOMNode comp)]
    (boundaries-from-dom-element dom-node)))

(defn drop-at
  [a-vec idx]
  (vec (concat (subvec a-vec 0 idx)
               (subvec a-vec (inc idx)))))

(defn indexed
  [a-seq]
  (map vector (range) a-seq))

(defn dist [x y]
  (abs (- x y)))

(defn closest-rect [position-data]
  "Finds the element closest to mouse position in the current row"
  (let [{:keys [client-x client-y]} (:row-mouse position-data)
        current-row (:current-row position-data)
        cursor-inside? (fn [{:keys [top bottom left right]}]
                         (and (<= left client-x right)
                              (<= top client-y bottom)))
        distance (fn [{:keys [_ _ left right] :as rect}]
                   (if (cursor-inside? rect)
                     0
                     (min (dist client-x left)
                          (dist client-x right))))
        rects (get-in position-data [:row-data current-row])
        by-distance (sort-by distance rects)]
    (first by-distance)))

(defn closest-with-edge [position-data]
  (let [{:keys [left right] :as closest} (closest-rect position-data)
        {:keys [client-x]} (:row-mouse position-data)
        side (if (< (dist client-x left)
                    (dist client-x right))
               :left
               :right)]
    {:side side
     :elem (:position closest)}))


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
        :else (update-in elem [1] inc)))

(defn add-at-index [a-seq index elem]
  (let [[before after] (split-at index a-seq)]
    (vec (concat before
                 [elem]
                 after))))

(defn drop-handler [all-rows-cursor current-row split-pos]
  (fn [e]
    (.preventDefault e)
    (let [dt (.-dataTransfer e)
          data (.getData dt "text")
          {:strs [type row index]} (when-not (string/blank? data) (util/json->clj data))
          field-data (if (and row index)
                       (get-in @all-rows-cursor [row index])
                       (let [kw-type (keyword type)]
                         (merge {:type kw-type}
                                (when (kw-type sts/text-field-types)
                                  {:text ""}))))]
      (swap! all-rows-cursor update-in [current-row] add-at-index split-pos field-data)
      (when (and row index)
        (let [drop-index (if (and (= row current-row) (< split-pos index))
                           (inc index)
                           index)]
          (swap! all-rows-cursor update-in [row] drop-at drop-index))))
    true))
