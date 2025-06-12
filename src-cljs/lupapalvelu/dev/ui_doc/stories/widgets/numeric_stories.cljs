(ns lupapalvelu.dev.ui-doc.stories.widgets.numeric-stories
  (:require [lupapalvelu.dev.ui-doc.util :as udu :refer [defmeta defstory]]
            [lupapalvelu.ui.dashboard.widgets.numeric :as numeric]
            [lupapalvelu.ui2.input :as input]))

(defmeta default
  {:title "Widget / Numeric"
   :component input/input
   :args {:width "2"
          :height "2"
          :value 7}
   :argTypes {:value {:type "number"}}})

(defstory numeric [args]
  [numeric/element
   args])

(defstory trend [args]
  [numeric/element
   (assoc args :trend "12%")])
