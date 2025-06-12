(ns lupapalvelu.dev.ui-doc.stories.input-stories
  (:require [lupapalvelu.dev.ui-doc.util :as udu :refer [action-fn defmeta defstory]]
            [lupapalvelu.ui2.input :as input]))

(defmeta default
  {:title "Component / Input"
   :component input/input
   :args {:type nil}
   :argTypes {:type {:control {:type "select"}
                     :options ["text" "select" "radio" "checkbox"]}
              :disabled {:type "boolean"}}})

(defstory input [args]
  [input/input
   args])

(defstory select [args]
  [input/select
   (dissoc args :type)
   (for [i (range 10)]
     [:option {:key i :value i} (str i)])])
