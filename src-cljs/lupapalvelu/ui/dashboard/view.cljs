(ns lupapalvelu.ui.dashboard.view
  (:require ["@dnd-kit/dom" :refer [Feedback]]
            ["@dnd-kit/react" :refer [DragDropProvider]]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.dashboard.search :refer [search-box search-results]]
            [lupapalvelu.ui.dashboard.shortcuts :refer [cards]]
            [lupapalvelu.ui.dashboard.state :as state]
            [lupapalvelu.ui.dashboard.widgets.base :refer [base-widget]]
            [lupapalvelu.ui.dashboard.widgets.numbers :as numbers]
            [lupapalvelu.ui.dashboard.widgets.numeric :as numeric]
            [lupapalvelu.ui2.button :refer [button]]
            [lupapalvelu.ui2.dialog :as dialog]
            [lupapalvelu.ui2.drawer :as drawer]
            [lupapalvelu.ui2.form :refer [form-field]]
            [lupapalvelu.ui2.header :refer [header]]
            [lupapalvelu.ui2.icons :as icons]
            [lupapalvelu.ui2.input :refer [input select]]
            [reagent.core :refer [defc]]
            [reagent.hooks :as hooks]))

;;
;; Widget types

(def widget-types
  {:numeric {:name        "Numerical widget"
             :comp        numeric/numeric-widget
             :detail-comp numeric/details
             :edit        nil}

   :numbers {:name        "Numbers"
             :comp        numbers/widget
             :detail-comp numbers/details
             :edit        numbers/edit}})

;; Widget index (grid order / position) is implificitly takes from the widgets vector.

(def WidgetSchema
  [:map
   [:type (into [:enum] (keys widget-types))]
   [:width [:enum "1" "2" "4"]]
   [:height [:enum "1" "2" "4"]]
   [:options
    {:description "Widget type specific option"}
    :any]])

(defc toolbar []
  (let [editing? (<sub [::state/editing?])]
    [:<>
     (when editing?
       [:div.col-span-1.bg-ui-gray-248
        [button
         {:intent   :primary
          :size     :sm
          :on-click (fn [_e] (>evt [::state/add-widget-modal]))}
         "Uusi"]])
     (if editing?
       [:<>
        [button
         {:intent   :secondary
          :size     :sm
          :on-click (fn [_e] (>evt [::state/cancel false]))}
         "Peruuta"]
        [button
         {:intent   :primary
          :size     :sm
          :on-click (fn [_e] (>evt [::state/save false]))}
         "Tallenna"]]
       [button
        {:intent   :tertiary
         :size     :sm
         :on-click (fn [_e] (>evt [::state/start-editing true]))}
        "Muokkaa työpöytää"
        [icons/settings
         {:size 16}]])]))

(defc edit-widget []
  (let [{:keys [new? custom? widget-id state]} (<sub [::state/edit-modal-state])
        widget                                 (if new?
                                                 state
                                                 (<sub [::state/get-widget widget-id]))
        update-fn                              (if new?
                                                 (fn [path v] (>evt [::state/set-edit-modal-state path v]))
                                                 (fn [path v] (>evt [::state/set-value (:id widget) path v])))
        read-only?                             (boolean (:shortcut-key widget))]
    [:div
     {:class ["p-6"]}
     [:div.flex.flex-row.items-center
      [dialog/title
       {:class "text-2xl"}
       (if new?
         (if custom?
           "Uusi kortti"
           "Valitse kortti")
         "Muokkaa korttia")]
      [:div.flex-1]
      [dialog/close {}
       [icons/close
        {:size 24}]]]

     [:div.pt-4

      (cond
        (and new? (not custom?))
        [:<>
         [:div.flex.flex-row
          [:div.flex-1.border-r-1.border-ui-neutral-mid.pr-4
           [:ul.flex.flex-col.gap-2
            ;; TODO: Sort by label?
            (for [[k card] cards]
              [:li.flex.flex-row
               {:key k}
               [button
                {:intent   :secondary
                 :size     :md
                 :on-click (fn [_e] (>evt [::state/add-widget k]))
                 :class    ["flex-1"]}
                (:name (:options card))]])]]
          [:div.flex-1.pl-4
           [button
            {:on-click (fn [_e] (>evt [::state/init-custom-widget]))}
            "Rakenna oma kortti"]]]]

        :else
        [:<>
         (when (:shortcut-key widget)
           [:div.text-sm.px-4.py-2.bg-ui-blue-20.rounded-sm.my-2
            "Käytät ennalta määriteltyä korttia. Jos haluat tehdä muutoksia kortin asetuksiin, "
            [button
             {:intent   :text
              :size     :sm
              :on-click (fn [_e] (>evt [::state/make-copy (:id widget)]))}
             "tee kopio kortista"]
            ". Huomaa, tämän jälkeen muutokset ennalta määriteltyyn korttiin
            eivät vaikuta kopioosi."])
         [form-field
          [:label "Otsikko"]
          [input
           {:value     (:name (:options widget))
            :on-change (fn [e] (update-fn [:options :name] (.. e -target -value)))
            :disabled  read-only?}]]

         [:div.grid.grid-cols-2.gap-4
          [form-field
           [:label "Leveys"]
           [select
            {:value     (:width widget)
             :on-change (fn [e] (update-fn [:width] (.. e -target -value)))
             :disabled  read-only?}
            [:option {:value "1"} "1"]
            [:option {:value "2"} "2"]
            [:option {:value "4"} "4"]]]

          [form-field
           [:label "Korkeus"]
           [select
            {:value     (:height widget)
             :on-change (fn [e] (update-fn [:height] (.. e -target -value)))
             :disabled  read-only?}
            [:option {:value "1"} "1"]
            [:option {:value "2"} "2"]
            [:option {:value "4"} "4"]]]]

         [form-field
          [:label "Vimpain"]
          [select
           {:value     (:type widget)
            :on-change (fn [e]
                         (let [v (.. e -target -value)
                               v (if (= "EMPTY" v) nil (keyword v))]
                           (update-fn [:type] v)))
            :disabled  (or (not new?)
                           read-only?)}
           [:option {:value "EMPTY"} "-"]
           (for [[k _] widget-types]
             [:option {:key k :value k} k])]]

         [:hr
          {:class ["border-ui-neutral-mid"
                   "my-4"]}]

         [:h3.text-lg "Vimpaimen asetukset"]

         (when-let [edit-f (:edit (get widget-types (:type widget)))]
           [edit-f
            widget
            {:read-only? read-only?
             :update-fn  update-fn}])])

      [:div.flex.flex-row.mt-4
       [button
        {:on-click (fn [_e]
                     (if new?
                       (>evt [::state/add-widget])
                       (>evt [::state/close-modal])))}
        "Tallenna"]
       [:div.flex-1]
       (when-not new?
         [button
          {:on-click (fn [_e] (>evt [::state/remove-widget (:id widget)]))}
          "Poista"])]]]))

(defc drawer-content []
  (let [{:keys [widget-id]}   (<sub [::state/drawer-state])
        widget                (<sub [::state/get-widget widget-id])
        {:keys [detail-comp]} (get widget-types (:type widget))]
    [drawer/viewport
     [drawer/popup
      {:width :md}
      [:div.flex.flex-row.border-b-1.p-6.items-center
       {:class "border-ui-neutral-mid"}
       [drawer/title "Bar"]
       [:div.flex-1]
       [:div
        [drawer/close
         [icons/close
          {:size 24}]]]]
      [drawer/content
       {:class ["flex" "flex-col"]}
       (when detail-comp
         [detail-comp widget])]]]))

(defc dashboard-grid []
  (let [widgets      (<sub [::state/widgets])
        widget-order (<sub [::state/widget-order])]
    ;; TODO: Temporary save
    (hooks/use-effect (fn []
                        (state/save-to-local-storage widgets
                                                     widget-order))
                      [widgets widget-order])
    [:> DragDropProvider
     {:on-drag-end (fn [^js e]
                     (let [source (.-source (.-operation e))]
                       (>evt [::state/set-order {:id   (.-id source)
                                                 :from (.-initialIndex source)
                                                 :to   (.-index source)}])))
      :plugins     #js [;; Keep a copy of item being moved.
                        ;; One element is kept in the list at the index where
                        ;; item would be dropped, another is the element floating under cursor.
                        (.configure Feedback #js {:feedback "clone"})]}
     [:div.dashboard-area
      (map-indexed (fn [i id]
                     (let [widget         (-> (get widgets id)
                                              state/apply-shortcut)
                           {:keys [comp]} (get widget-types (:type widget))]
                       (if comp
                         ^{:key (:id widget)}
                         [comp (assoc widget :index i)]
                         ^{:key (:id widget)}
                         [base-widget (assoc widget :index i)
                          "ERROR: Missing component"])))
                   widget-order)]]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defc view []
  (hooks/use-effect (fn []
                      (>evt [::state/initialize]))
                    [])
  (let [{:keys [edit-modal detail-drawer]} (<sub [::state/data])]
    ;; Testing, update all active queries every x seconds
    (hooks/use-effect (fn []
                        (let [interval (js/setInterval (fn [] (>evt [::state/update-all-queries]))
                                                       ;; FIXME: Really something more like 60s?
                                                       ;; Or could we instead push the updates from BE->FE?
                                                       (* 10 1000))]
                          (fn []
                            (js/clearInterval interval)))))
    [:div.flex-1.flex.flex-col
     {:class [;; Enable tailwind reset
              "twp"
              ;; Disable basestyles (h1 etc) from our old sass styles
              "nobase"
              ;; Enable new theme
              "lp-theme"]}
     [:div.bg-canvas-lupapiste.flex-1
      [:div.max-w-7xl.mx-auto.w-full.px-4.py-2
       [:div.flex.flex-row.items-center.mb-4.gap-2
        [header
         {:size :h1}
         "Oma Työpöytä"]
        [:div.flex-1]
        [toolbar]]
       [search-box]
       (case (:name (:data (<sub [:route/match])))
         :dashboard-search [search-results]
         :dashboard [dashboard-grid])]]

     [drawer/root
      {:open           (boolean detail-drawer)
       :on-open-change (fn [open?]
                         (when-not open?
                           (>evt [::state/close-drawer])))
       :swipeDirection "left"}
      [drawer/portal {:class ["nobase" "twp" "lp-theme"]}
       [drawer/backdrop]
       [drawer-content]]]

     [dialog/root
      {:open           (boolean edit-modal)
       :on-open-change (fn [open?]
                         (when-not open?
                           (>evt [::state/close-modal])))}
      [dialog/portal {:class ["nobase" "twp" "lp-theme"]}
       [dialog/backdrop]
       [dialog/popup
        [edit-widget]]]]]))
