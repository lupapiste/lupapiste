(ns lupapalvelu.ui.auth-admin.imports.config
  "Import configuration view. The edited configuration can be either draft, when the
  previews are shown, or imported, when the actual imported rows can be edited."
  (:require ["@dnd-kit/core" :refer [DndContext closestCenter]]
            ["@dnd-kit/sortable" :refer [SortableContext useSortable arrayMove
                                         verticalListSortingStrategy]]
            ["@dnd-kit/utilities" :as utils :refer [CSS]]
            [goog.functions :as goof]
            [goog.string :as goos]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.auth-admin.imports.data.config :as dcfg]
            [lupapalvelu.ui.auth-admin.imports.data.core :as dcore]
            [lupapalvelu.ui.auth-admin.imports.import :as import]
            [lupapalvelu.ui.auth-admin.imports.preview :as preview]
            [lupapalvelu.ui.auth-admin.imports.table :as table]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.dom :as ui-dom]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn error-note
  "Renders the current configuration error (or nothing)."
  []
  (when-let [{:keys [error text]} (<sub [::dcfg/error])]
    [:div.error-note.flex--column
     (when error
       [:div.txt--bold (path/loc error)])
     (when text
       [:div.txt--code text])]))

(defn file-picker
  "File picker and uploader. When the excel file is uploaded, a new draft configuration is
  create on success."
  []
  (let [id                          "excel-import-file-picker"
        {:keys [filename filesize]} (<sub [::dcfg/fileinfo])
        [mode submode]              (<sub [::dcfg/mode])
        error?                      (= submode :error)]
    (if filename
      [:div.flex
       [:div.flex--wrap.flex--column-gap8.flex--row-gap4.pad--2.bd--burgundy.flex--align-center.flex--g0
        [:div.flex.flex--gap2
         [:div.txt--code filename]
         [:div (js/util.sizeString filesize)]]
        [:div.flex.flex--gap4.flex--right
         [components/icon-button {:text-loc :imports.upload
                                  :class    :primary
                                  :icon     :lupicon-upload
                                  :wait?    (and mode (not error?))
                                  :on-click #(>evt [::dcfg/upload-file])}]
         (when (or (nil? mode) error?)
           [:button.tertiary {:on-click #(>evt [::dcfg/remove-file])}
            (common/loc :cancel)])]]]
      [:div
       [:input {:type      "file"
                :id        id
                :disabled  (<sub [::dcfg/disabled?])
                :style     {:display "none"}
                :on-change (fn [e]
                             (when-let [file (-> e .-target .-files (aget 0))]
                               (>evt [::dcfg/select-file file])))
                :accept    ".xlsx"}]
       [:label.btn.primary
        {:for id}
        [:i.lupicon-circle-plus]
        [:span (common/loc :imports.select)]]])))

(defn include-toggle
  "Include toggle for column."
  [column-id]
  [components/toggle (<sub [::dcfg/column column-id :include])
   {:prefix    :blockbox
    :disabled? (<sub [::dcfg/disabled?])
    :callback  #(>evt [::dcfg/toggle-include column-id %])}])

(defn column-viewer-row
  "View for row column."
  [column-id]
  (let [{:strs [setNodeRef transform listeners attributes
                transition]}      (js->clj (useSortable (js-obj "id" column-id )))
        style                     {:transform  (CSS.Transform.toString (clj->js transform))
                                   :transition transition}
        {column-type :type
         included?   :include}    (<sub [::dcfg/column column-id])
        imported?                 (<sub [::dcfg/imported?])
        included?                 (or included? imported?)
        {:keys [type lines unit]} column-type
        dim-cls                   {:class (common/css-flags :line-through.text-gray-500
                                                            (not included?))}
        disabled?                 (<sub [::dcfg/disabled?])
        sortable?                 (<sub [::dcfg/sortable?])]
    [:tr {:style style
          :ref   setNodeRef}
     (when-not imported?
       [:td [include-toggle column-id]])
     [:td dim-cls (<sub [::dcfg/titlestring column-id])]
     [:td dim-cls
      (cond-> (path/loc :imports.type type)
        (and (util/=as-kw :text type) (some-> lines str ss/not-blank?))
        (str " " (common/loc :imports.type.lines.info lines))
        (and (util/=as-kw :number type) unit)
        (str (goos/format " (%s)" (path/loc :unit unit))))]
     [:td
      [:div.flex.flex--gap4.flex--between.flex--align-center
       [:button.primary {:on-click #(>evt [::dcfg/toggle-edit-column column-id true])
                         :disabled (or disabled? (not included?))}
            (path/loc :edit)]
       (when sortable?
         [:div.lupicon-grip-diagonal.cursor-move
          (merge {:role :button}
                 attributes listeners)])]]]))

(defn title-editor
  "Title editor (used both by main and column titles) for `lang`. `title-id` is nil for the
  main title and column id for column titles."
  [lang title-id]
  (let [id  (util/kw-path :title title-id lang)
        cls (when (= lang :fi)
              :required)
        v   (<sub [::dcfg/title lang title-id])
        fun (goof/debounce #(>evt [::dcfg/edit-title lang title-id %]) 200)]
    [:div.flex--column.w--min-40em
     [:label.lux {:for id :class cls} (path/loc (str "in_" (name lang)))]
     [components/text-edit v {:callback  fun
                              :required? (boolean cls)}]]))

(defn titles-editor
  "Title editors for every supported language."
  [title-id]
  (into [:div.flex--column.flex--gap1]
        (for [lang (js/loc.getSupportedLanguages)]
          [title-editor (keyword lang) title-id])))

(defn type-info
  "Help text for the column type."
  [column-id]
  [:div.primary-note.text-wrap
   (path/loc :imports.type (<sub [::dcfg/column-edit-type column-id :type]) :info)])

(defn with-label
  "Label wrapper for `editor`."
  [for-id text-loc editor]
  [:div.flex--column
   [:label.lux {:for for-id} (path/loc text-loc)]
   editor])

(defn lines-editor
  "Lines dropdown for text column."
  [column-id]
  (let [lines (<sub [::dcfg/column-edit-type column-id :lines])
        id    (util/kw-path :lines column-id)]
    (with-label id :imports.type.lines
       [components/dropdown lines
        {:items      (mapv #(hash-map :text % :value %) [5 10 15 20])
         :choose-loc :imports.type.lines.default
         :callback   #(>evt [::dcfg/edit-column column-id :type :lines %])}])))

(def unit-options (mapv #(hash-map :text (path/loc :unit %)
                                   :value %)
                        im-sc/units))

(defn unit-editor
  "Unit dropdown for number column."
  [column-id]
  (let [unit (<sub [::dcfg/column-edit-type column-id :unit])
        id   (util/kw-path :unit column-id)]
    (with-label id :imports.type.unit
      [components/dropdown unit
       {:items      unit-options
        :choose-loc :unit.none
        :sort-by    :text
        :callback   #(>evt [::dcfg/edit-column column-id :type :unit %])}])))

(def type-options (for [t [:text :number :date :backend-id :application-id]]
                    {:value t :text (path/loc :imports.type t)}))

(defn type-editor
  "Type editor consists of the actual type dropdown (or just static text for already
  imported) and type-specific additions (unit, lines)."
  [column-id]
  (let [column-type (<sub [::dcfg/column-edit-type column-id :type :type])
        type-id     (util/kw-path :type column-id)
        imported?   (<sub [::dcfg/imported?])]
    [:div.flex--wrap.flex--gap2.
     (with-label type-id :imports.type.type
       (if imported?
         [:span.txt--bold.pad--v1 (path/loc :imports.type column-type)]
         [components/dropdown column-type
          {:items    type-options
           :sort-by  :text
           :choose?  false
           :callback #(>evt [::dcfg/edit-column column-id :type :type %])}]))
     (when (util/=as-kw :text column-type)
       [lines-editor column-id])
     (when (util/=as-kw :number column-type)
       [unit-editor column-id])]))

(defn column-editor-row
  "Column row editor."
  [column-id]
  [:tr {:id column-id}
   [:td {:col-span (if (<sub [::dcfg/imported?]) 3 4)}
    [:div.flex--column.flex--gap1.pad--2
     [type-info column-id]
     [:div.flex.flex--gap1.flex--align-end
      [:div.flex--column.flex--gap2
       [type-editor column-id]
       [titles-editor column-id]]
      [:div.flex.flex--gap2.pad--l2
       [:button.primary
        {:on-click #(>evt [::dcfg/save-edit-column column-id])
         :disabled (not (<sub [::dcfg/can-save-column? column-id]))}
        (path/loc :save)]
       [:button.secondary
        {:on-click #(>evt [::dcfg/toggle-edit-column column-id false])}
        (path/loc :cancel)]]]]]])

(defn config-row
  "Viewer or editor."
  [column-id]
  (if (<sub [::dcfg/column-editing? column-id])
    [column-editor-row column-id]
    [:f> column-viewer-row column-id]))

(defn main-title
  "Configuration main title viewer/editor."
  []
  (if (<sub [::dcfg/title-editing?])
    [:div.flex--column.flex--gap2.w--max-50em.bd--gray.pad--2
     [titles-editor]
     [:div.flex.flex--gap2.flex--right
      [:button.primary {:disabled (not (<sub [::dcfg/can-save-title?]))
                        :on-click #(>evt [::dcfg/edit-main-title :save])}
       (common/loc :save)]
      [:button.tertiary {:on-click #(>evt [::dcfg/edit-main-title :cancel])}
       (common/loc :cancel)]]]
    [:div.flex.flex--gap2.flex--align-center
     [:h2 (<sub [::dcfg/titlestring])]
     (when-not (<sub [::dcfg/disabled?])
       [components/icon-button {:class      :tertiary
                                :icon-only? true
                                :icon       :lupicon-pen
                                :on-click   #(>evt [::dcfg/edit-main-title :start])}])]))

(defn timestamp
  "Renders timestamp for `k` as a Finnish datetime. If `loc-k` is given, the corresponding
  l10n is prepended to the datetime string."
  [k loc-k]
  (when-let [ts (<sub [::dcfg/timestamp k])]
    [:span (cond->> (date/finnish-datetime ts)
             loc-k (str (common/loc loc-k) " "))]))

(defn config-table
  "Each row in the configuration table represents a configuration column (which can be
  confusing). While each row/column can be always reordered and (for drafts) toggled, but
  actual editing is its separate mode."
  []
  (let [cfg-id    (<sub [::dcfg/config-id])
        imported? (<sub [::dcfg/imported?])
        deleted?  (<sub [::dcfg/deleted?])]
    [:table {:style {:width :fit-content}}
    [:thead
     (into [:tr]
           (for [k (cond->> [:imports.column.title
                             :imports.type.type :admin.actions]
                     (not imported?)
                     (cons :imports.column.include))]
             [:th (path/loc k)]))]
     (into [:tbody {:id (str cfg-id "-columns")}]
           (for [column-id (<sub [::dcfg/sorted-column-ids])]
            [config-row column-id]))
    [:tfoot
     [:tr {:style {:border :none}}
      [:td {:col-span 4 :style {:padding-left 0 :padding-right 0}}
       [:div.fg--gray.flex--row-reverse.flex--between.txt--small
        (if deleted?
          [timestamp :deleted :imports.config.deleted]
          [timestamp :modified :pate.verdict-table.last-edit])
        (when imported?
          [timestamp :imported :imports.imported])]]]]]))

(defn table-sorter
  "Setup drag-n-drop contexts for manual sorting. See also `column-viewer-row`."
  []
  (let [column-ids (<sub [::dcfg/sorted-column-ids])]
    [:> DndContext
     {:collisionDetection closestCenter
      :onDragEnd          (fn [e]
                            (let [active-id (.. e -active -id)
                                  over-id   (.. e -over -id)]
                              (when-not (= active-id over-id)
                                (let [old (.indexOf column-ids active-id)
                                      new (.indexOf column-ids over-id)]
                                  (when-not (= old new)
                                    (>evt [::dcfg/reorder-columns
                                           (arrayMove (clj->js column-ids) old new)]))))))}
     [:> SortableContext
      {:items    column-ids
       :strategy verticalListSortingStrategy}
      [config-table]]]))

(defn delete-mark
  "Either deletes (non-imported) config or toggles the deletion status for imported
  configs."
  []
  (when (<sub [::dcfg/delete-active?])
    (let [imported? (<sub [::dcfg/imported?])
          deleted?  (<sub [::dcfg/deleted?])]
      (cond
        (not imported?)
        [components/icon-button {:text-loc :remove
                                 :class    :negative
                                 :icon     :lupicon-remove
                                 :on-click (fn [_]
                                             (>evt [:dialog/show
                                                    {:text     (common/loc :imports.config.delete)
                                                     :type     :yes-no
                                                     :callback #(>evt [::dcfg/delete-config])}])) }]

        deleted? [:div.flex--column.flex--gap1.flex--align-start
                  [components/icon-button {:text-loc :pate-restore
                                           :class    :secondary
                                           :icon     :lupicon-undo
                                           :on-click #(>evt [::dcfg/mark-config-deleted false])}]
                  [:div.fg--gray.txt--small [timestamp :deleted :imports.config.deleted]]]
        :else    [components/icon-button
                  {:text-loc :remove
                   :class    :negative
                   :icon     :lupicon-remove
                   :on-click (fn [_]
                               (>evt [:dialog/show
                                      {:text     (common/loc :imports.config.mark-deleted)
                                       :type     :yes-no
                                       :callback #(>evt [::dcfg/mark-config-deleted true])}]))}]))))

(defn configurator
  "Component wrapper for main title, configuration table and mark deleted button."
  []
  [:div.flex--column.flex--gap2
   [main-title]
   [:div.flex--wrap.flex--column-gap4.flex--row-gap2
    [table-sorter]
    [delete-mark]]])

(defn view
  "Individual configuration view."
  []
  (when (<sub [::dcfg/visible?])
    [:div.flex--column.flex--gap2
     [components/icon-button {:text-loc :imports.title
                              :class    :secondary.flex--self-start
                              :icon     :lupicon-chevron-left
                              :on-click #(>evt[::dcore/goto-imports])}]
     (when (<sub [::dcfg/config-id])
       [:div.flex--column.flex--gap2
        [configurator]
        [table/imports]
        [preview/previews]
        [import/importer]])]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
