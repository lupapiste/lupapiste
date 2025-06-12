(ns lupapalvelu.dev.ui-doc.components-demo)

(defn create-demo-options
  "Given variants builder specification, create
  options list for the component demo."
  [spec]
  (reduce (fn [acc [k vs]]
            (assoc acc k {:default (get (:defaults spec) k)
                          :values (keys vs)}))
          {}
          (:variants spec)))

(defn to-str [v]
  (cond
    (keyword? v) (name v)
    (boolean? v) (if (true? v) "true" "false")
    :else (or v "EMPTY")))

(defn labels* [spec [current :as _variants] i show-label?]
  (let [vs (keys (get (:variants spec) current))
        row? (= (mod i 2) 0)]
    [:div.grid.mx-1
     {:style {(if row? :grid-template-rows :grid-template-columns)
              (if show-label?
                (str "auto repeat(" (count vs) ", 1fr)")
                (str "repeat(" (count vs) ", 1fr)"))}}
     (when show-label?
       [:span "-"])
     (for [v vs]
       [:span 
        {:key v}
        current ": " (to-str v)])]))

(defn variants* [{:keys [spec comp] :as props} [current :as dimensions] i options]
  (if (seq dimensions)
    (let [vs (keys (get (:variants spec) current))
          row? (= (mod i 2) 0)
          first-option-of-the-first-variant? (and (= 1 i)
                                                  (= (first (keys (get (:variants spec) (first (:variants props)))))
                                                     (get options (first (:variants props)))))
          show-label? (or (= i 0)
                          first-option-of-the-first-variant?)
          label-col? (and (= i 1)
                          (seq (rest dimensions)))]
      [:div.grid.flex-1
       {:style {(if row? :grid-template-rows :grid-template-columns)
                (if label-col?
                  (str "auto repeat(" (count vs) ", 1fr)")
                  (str "repeat(" (count vs) ", 1fr)"))}}
       (when label-col?
         [labels* spec (rest dimensions) (inc i) show-label?])
       (for [v vs]
         [:div
          {:key v
           :class [(if (= 0 i)
                     "py-1 border-b-1 border-ui-gray-170 last:border-b-0"
                     (if row?
                       "my-1"
                       "mx-1 flex flex-col"))]}
          (when show-label?
            [:span current ": " (to-str v)])
          [variants* props (rest dimensions) (inc i) (assoc options current v)]])])
    [:div
     [comp options]]))

(defn variants-grid [props]
  [:div
   {:class [(when-not (:inline props)
              "p-4 border border-1 border-ui-gray-170")]}
   [variants* props (:dimensions props) 0 {}]])

