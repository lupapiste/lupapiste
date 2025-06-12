(ns lupapalvelu.dev.ui-doc.stories.dialog-stories 
  (:require [lupapalvelu.dev.ui-doc.util :refer [defmeta defstory]]
            [lupapalvelu.ui2.button :as button]
            [lupapalvelu.ui2.dialog :as dialog]))

(defmeta default
  {:title "Component / Dialog"
   :component dialog/root
   :args {:position nil
          :size nil}
   :argTypes {:position {:control {:type "select"}
                         :options ["center"]
                         :mapping #js {"center" :center}}
              :size {:control {:type "select"}
                     :options ["md"]
                     :mapping #js {"md" :md}}}})

(defstory dialog [args]
  [dialog/root
   [button/button
    {:el dialog/trigger}
    "Open"]
   [dialog/portal
    [dialog/backdrop]
    [dialog/viewport
     [dialog/popup
      {:class ["p-6"]
       :size (:size args)
       :position (:position args)}
      [dialog/title "Title"]
      [dialog/description "Hello world"]
      [:div
       [button/button
        {:el dialog/close}
        "Close"]]]]]])
