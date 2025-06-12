(ns lupapalvelu.ui.admin.import-export.components
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.util :as ui-util]
            [re-frame.core :as rf]))

(rf/reg-sub ::organization-items
  :<- [::subs/organizations]
  (fn [organizations]
    (let [lang (keyword (common/get-current-language))]
      (->> organizations
           (map (fn [[id names]]
                  {:value id
                   :text  (str (name id) ": " (get names lang))}))
           (sort-by :text)
           (into [])))))

(defn organization-selector
  []
  [:div.w--50
   (when-let [organizations (seq (<sub [::organization-items]))]
     [reselect/reselect
      (<sub [::subs/selected-organization])
      {:id       "import-export-org-select"
       :multi?   false
       :items    organizations
       :callback #(>evt [::subs/organization-selected (name %)])}])
   (when (<sub [::subs/src-is-dst?])
     [:div.primary-note
      "Valittu kohdeorganisaatio vastaa tuontitiedoston lähdeorganisaatiota!"])])

(rf/reg-sub ::download-url
  :<- [::subs/selected-target]
  :<- [::subs/selected-organization]
  :<- [::subs/selected-items]
  (fn [[target organization item-ids]]
    (when (and target organization (seq item-ids))
      (common/make-url "/api/raw/import-export-export-items"
                       {:target   (name target)
                        :org-id   (name organization)
                        :item-ids item-ids}))))

(defn export-button
  []
  (let [url       (<sub [::download-url])
        disabled? (not (<sub [::subs/items-selected?]))]
    [:div
     [:a.btn.primary
      {:href  url
       :class [(when disabled? :disabled)]}
      [:i.lupicon-download]
      [:span (common/loc :download)]]]))

(defn- read-file
  [file callback]
  (let [js-file-reader (js/FileReader.)]
    (set! (.-onload js-file-reader)
          (fn read-complete [evt]
            (let [str  (-> evt .-target .-result)
                  data (ui-util/decode-transit str)]
              (callback data))))
    (.readAsText js-file-reader file)))

(defn file-picker
  []
  (let [id "import-export-file-picker"]
    [:div
     [:input {:type      "file"
              :id        id
              :style     {:display "none"}
              :on-change (fn [e]
                           (when-let [file (-> e .-target .-files (aget 0))]
                             (>evt [::subs/import-file-chosen (.-name file)])
                             (read-file file
                                        (fn [content]
                                          (>evt [::subs/import-uploaded content])))))
              :accept    ".json"}]
     [:label.btn.primary
      {:for id}
      "Valitse tiedosto"]]))

(defn import-file
  []
  [:div.flex.flex--column.flex--gap2
   [file-picker]
   [:div.flex.flex--gap2.flex--wrap
    (when-let [filename (<sub [::subs/import-filename])]
      [:div.flex
       [:span {:style {:margin :auto}}
        (str "Valittu tiedosto: " filename)]])
    (when (<sub [::subs/import-filename-chosen?])
      [:div
       [:a.btn.secondary {:href     ""
                          :on-click (fn [e]
                                      (.preventDefault e)
                                      (>evt [::subs/clear-file]))}
        "Peruuta"]])]
   (when-let [errors (<sub [::subs/import-errors])]
     [:<>
      [:h2 "Virheitä"]
      [:div.flex
       [:pre.bg--gray (pr-str errors)]]])])

(defn replace-checkbox
  []
  (let [id "import-export-replace-checkbox"]
    [:div
     [ui-input/checkbox
      {:id        id
       :checked?  (<sub [::subs/replace?])
       :disabled? (<sub [::subs/import-done?])
       :on-change (fn [checked?] (>evt [::subs/reset-clicked checked?]))}]
     [:label {:for id}
      "Korvaa nykyiset tiedot?"]]))

(defn import-button
  []
  [:div
   [:button.primary
    {:disabled (<sub [::subs/import-done?])
     :on-click (fn [_]
                 (>evt [::subs/run-import]))}
    "Tuo tiedot järjestelmään"]])
