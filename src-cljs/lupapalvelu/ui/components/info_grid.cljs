(ns lupapalvelu.ui.components.info-grid
  (:require [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(def collapsing-grid-borders
  ["border-t"
   "border-l"
   "border-ui-gray"
   "[&>div]:border-r"
   "[&>div]:border-b"
   "[&>div]:border-ui-gray"])

(def odd-headers
  ["[&>*:nth-child(odd)]:bg-ui-light-gray"
   "[&>*:nth-child(odd)]:font-bold"
   "[&>*:nth-child(even)]:bg-ui-white"])

(def responsive-width
  ["w-full"
   "lg:w-5/6"
   "xl:w-2/3"])

(def content-stretch
  ["grid-cols-1"
   "md:grid-cols-[auto_1fr]"
   "[&>*:nth-child(odd)]:md:whitespace-nowrap"
   "[&>*:nth-child(even)]:w-full"])

(defn- grid-cell
  ([content right-side-content]
   [:div.flex.justify-between
    [:div.py-1.px-3 content]
    (when right-side-content
      right-side-content)])
  ([content]
   (grid-cell content nil)))

(defn info-grid
  "A responsive 2-column grid. On small screens, the grid switches to a 1-column
  layout, positioning content below their respective labels.

   Example:
   [info-grid [{:title              \"Label 1\"
                :content            \"Value 1\"
                :right-side-content [:button {:on-click #( ... }]}
               {:title              \"Label 2\"
                :content            \"Value 2\"}]

   Nil values from the rows vector are removed.

   Right-side-content is an optional fields. Its value can contain content that
   will be positioned on the right side of the value cell.
   Component 'info-grid/square-button' is optimized to be used on the right side
   of the cell.

   TODO: At the moment component is only for static content. Dynamic content
   will require implementing some method of setting ^{:key id} attributes to the
   rows or row cells."
  [rows]
  (into
    [:div.twp.grid.min-h-auto
     (merge {:class (concat collapsing-grid-borders
                            odd-headers
                            responsive-width
                            content-stretch)})]
    (for [{:keys [title content right-side-content]} (keep identity rows)]
      [:<>
       [grid-cell title]
       [grid-cell content right-side-content]])))

(defschema SquareButtonParams {:icon                            sc/Keyword
                               :text                            sc/Str
                               (sc/optional-key :disabled?)     sc/Bool
                               (sc/optional-key :href)          sc/Str
                               (sc/optional-key :label-for)     sc/Str
                               (sc/optional-key :on-click)      (sc/pred fn? 'on-click-fn)
                               (sc/optional-key :aria-expanded) sc/Bool
                               (sc/optional-key :aria-controls) sc/Str})

(def square-button-classes ["btn" "primary" "text-ui-white" "btn-small" "text-center" "block" "rounded-sm"
                            "flex" "items-center" "justify-center" "h-[31px]" "w-[31px]" "min-h-min"])

(sc/defn square-button
  "Square button component designed for use within the info-grid. The button
  requires at least one of the following parameters: `on-click`, `href`, or
  `label-for`.

  - `on-click`: Makes the button a clickable element that triggers the
    provided event handler.
  - `label-for`: Turns the button into a label for an input element,
    such as a hidden file input, allowing it to trigger the input when clicked.
  - `href`: Converts the button into a link that can be used to navigate to the
    specified URL or download a file.

  Regardless of the type, the button maintains a consistent appearance. The
  `disabled?` parameter can be used to visually disable the button and prevent
  interaction.

  The button does not display text directly. Instead, the `text` parameter is
  used for accessibility purposes, serving as the `aria-label` and `title`.
  Only the icon is displayed on the button in standard usage."
  [{:keys [icon text disabled? href on-click label-for aria-expanded aria-controls] :as params} :- SquareButtonParams]
  {:pre [(some #(contains? params %) [:href :on-click :label-for])]}
  (let [element (cond
                  on-click :button
                  disabled? :button
                  label-for :label
                  :else :a)]
    [element (util/strip-nils {:title         text
                               :aria-label    text
                               :class         square-button-classes
                               :on-click      on-click
                               :for           label-for
                               :href          href
                               :disabled      disabled?
                               :aria-expanded aria-expanded
                               :aria-controls aria-controls
                               :tab-index     0})
     [:i.text-sm {:class icon}]]))
