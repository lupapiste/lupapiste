(ns lupapalvelu.ui.auth-admin.backing-system.components
  (:require [lupapalvelu.ui.components :as ui]))

(defn link
  [{:keys [text on-click]}]
  [:span "[" (ui/click-link
               {:text  text
                :click on-click}) "]"])

(defn input-with-label
  [{:keys [id input-type class label-text value placeholder on-change required?]
    :or   {value ""}}]
  [:div.flex.flex--column
   [:label.pad--r1
    {:for   id
     :class [(when required? "mandatory")]}
    label-text]
   [:input {:id          id
            :type        input-type
            :value       value
            :class       class
            :placeholder placeholder
            :on-change   (fn [evt] (on-change (-> evt .-target .-value)))}]])

(defn text-with-label
  [opts]
  [input-with-label
   (merge {:input-type :text
           :class      "form-input text long"}
          opts)])

(defn password-with-label
  [opts]
  [input-with-label
   (merge {:input-type :password
           :class      "form-input long"}
          opts)])

(defn checkbox
  [{:keys [id value label disabled? on-change]}]
  (ui/toggle value {:id        id
                    :text      label
                    :disabled? disabled?
                    :callback  on-change}))

(defn checkbox-group
  [{:keys [label options option->id option->label option->checked? on-change]}]
  [:div.width-90
   [:span label]
   (doall
     (for [option options
           :let   [id (option->id option)]]
       ^{:key id}
       [:div.flex.flex--row
        [checkbox {:id        (option->id option)
                   :label     (option->label option)
                   :value     (option->checked? option)
                   :on-change (fn [_]
                                (on-change option))}]]))])

(defn select
  [{:keys [id value disabled? allow-empty? on-change options option->value option->text]
    :or   {option->text  identity
           option->value identity}}]
  (let [as-map  (->> options
                     (map (juxt option->value identity))
                     (into {}))
        value   (option->value value)
        options (->> options
                     (mapv (juxt option->value option->text)))
        options (if allow-empty?
                  (conj options [nil ""])
                  options)]
    (ui/select value options {:id       id
                              :class    "width-90"
                              :disabled disabled?
                              :callback (fn [v]
                                          (on-change (get as-map v)))})))

(defn select-with-label
  [{:keys [id label-text] :as opts}]
  [:div.flex.flex--column
   [:label.pad--r1 {:for id} label-text]
   [select (merge {:id id}
                  (dissoc opts :id :label-text))]])
