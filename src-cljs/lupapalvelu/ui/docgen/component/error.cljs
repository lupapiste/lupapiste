(ns lupapalvelu.ui.docgen.component.error
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.error-boundary :as eb]))

(defn- copy-to-clipboard
  [contents]
  (when-let [^js cb (.-clipboard js/navigator)]
    (.writeText cb contents)))

(defn boundary
  [context & children]
  {:pre [(map? context)]}
  [eb/error-boundary
   {:on-error (fn [{:keys [^js error]}]
                (let [error-name (.-name error)
                      message    (.-message error)
                      stack      (.-stack error)]
                  [:div
                   {:class ["my-4" "p-2"
                            "border-solid" "border-ui-red-100" "rounded-sm"]}
                   [:h2 (common/loc :error-boundary.title)]
                   [:div.flex.flex-col
                    [:div.flex-grow
                     [:span error-name ": " message]]
                    [:button.tertiary
                     {:on-click (fn []
                                  (copy-to-clipboard
                                    (pr-str [[:error   error-name]
                                             [:message message]
                                             [:now     (js/Date.)]
                                             [:context context]
                                             [:stack   stack]])))}
                     [:i {:aria-hidden true
                          :class       :lupicon-copy}]
                     [:span
                      (common/loc :error-boundary.copy-to-clipboard)]]]]))}
   (into [:<>] children)])
