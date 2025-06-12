(ns lupapalvelu.ui.tab-panel
  "A component where only one tab is visible at a time"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(rf/reg-sub ::selected-tab
  (fn [db [_ data-path]]
    (get-in db data-path)))

(rf/reg-event-db ::select-tab
  (fn [db [_ data-path tab-key]]
    (assoc-in db data-path tab-key)))

(def tab-item-classes ["flex" "px-4" "h-12" "items-center" "text-base-lg" "rounded-sm" "group"])
(def tab-item-not-selected-classes ["border" "bg-ui-gray-238" "border-ui-gray-221"])
(def tab-item-hover-classes ["hover:border-transparent" "hover:bg-ui-violet" "hover:text-white"])
(def tab-item-selected-classes ["font-bold" "bg-ui-night-sky" "text-white"
                                "[&_span]:bg-white" "[&_span]:text-ui-night-sky"])
(def tab-item-after-classes ["h-7" "w-7" "flex" "justify-center" "items-center" "ml-2" "rounded-2xl" "font-normal"
                             "text-xs" "bg-ui-night-sky" "text-white" "group-hover:bg-white" "group-hover:text-ui-night-sky"])

(defn content-switcher [{:keys [items key->str current on-change class]}]
  [:div.flex
   {:class ["gap-[0.125rem] pb-[0.125rem]" class]}
   (for [{:keys [key after on-select-fn]} items]
     [:button
      {:key      key
       :on-click (fn []
                   (on-change key)
                   (when on-select-fn
                     (on-select-fn)))
       :class    (r/class-names tab-item-classes
                                (if (= current key)
                                  tab-item-selected-classes
                                  tab-item-not-selected-classes)
                                tab-item-hover-classes)}
      (key->str key)
      (when after
        [:span {:class tab-item-after-classes} after])])])

(defschema TabPanelOpts
  {:id                                      schemas/keyword-or-string
   :tabs                                    [{:key                            schemas/keyword-or-string
                                              :content                        sc/Any
                                              (sc/optional-key :after)        sc/Str
                                              (sc/optional-key :on-select-fn) (sc/pred fn? 'on-select-fn)
                                              (sc/optional-key :hidden?)      sc/Bool}]
   :key->str                                (sc/pred fn? 'key->str)
   (sc/optional-key :class)                 sc/Str
   (sc/optional-key :switcher-class)        sc/Str
   (sc/optional-key :tab-class)             sc/Str
   (sc/optional-key :select-first-on-load?) sc/Bool
   (sc/optional-key :data-path)             [sc/Keyword]})

(sc/defn tab-panel
  "A tab-like content switcher with the selected content shown below it
  Opts:
  id                    string or keyword, used for storing the selected tab in re-frame
  tabs                  seq of maps:
                        {:key          (string or keyword, the unique id of the tab)
                         :content      (hiccup, content to be displayed when this tab is selected)
                         :hidden?      (bool, truthy if the tab should not be included
                         :after        (max 3 character string that is displayd in the tab item after
                                        the actual text)
                         :on-select-fn (a function that is executed when item is selected)}
  key->str              fn, takes the tab's key and turns it into a string for the tab label
  class                 optional string, CSS class used for the container div
  switcher-class        optional string, CSS class used for the content switcher
  tab-class             optional string, CSS class for the tab contents container
  select-first-on-load? defines whether the first tab is selected by default or not
  data-path             reframe app-db path where selected-tab is stored
  "
  [{:keys [id tabs key->str class switcher-class tab-class select-first-on-load? data-path]
    :or   {select-first-on-load? true}} :- TabPanelOpts]
  (let [data-path               (or data-path [::selected-tab (keyword id)])
        visible-tabs            (remove :hidden? tabs)
        stored-selected-tab-key (<sub [::selected-tab data-path])
        selected-tab-key        (or (:key (util/find-by-key :key stored-selected-tab-key visible-tabs))
                                    (and select-first-on-load?
                                         (->> visible-tabs first :key)))
        selected-tab            (util/find-first #(= (:key %) selected-tab-key) tabs)]
    [:div.twp
     {:class class}
     [content-switcher
      {:items     (->> visible-tabs (map #(select-keys % [:key :after :on-select-fn])))
       :key->str  key->str
       :current   selected-tab-key
       :on-change #(>evt [::select-tab data-path %])
       :class     (str switcher-class)}]
     [:div.w-full.border-t.border-solid.border-ui-gray-204
      [:div.w-full.border-t.border-solid.border-ui-night-sky
       {:class tab-class}
       (when-not (:hidden? selected-tab)
         (:content selected-tab))]]]))
