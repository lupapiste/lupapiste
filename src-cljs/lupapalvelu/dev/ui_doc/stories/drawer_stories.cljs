(ns lupapalvelu.dev.ui-doc.stories.drawer-stories 
  (:require [lupapalvelu.dev.ui-doc.util :refer [defmeta defstory]]
            [lupapalvelu.ui2.button :as button]
            [lupapalvelu.ui2.drawer :as drawer]))

(defmeta default
  {:title "Component / Drawer"
   :component drawer/root
   :args {:from :left
          :width :md}
   :argTypes {:from {:control {:type "select"}
                     :options ["left" "right"]
                     :mapping #js {"left" :left
                                   "right" :right}}
              :width {:control {:type "select"}
                      :options ["md"]
                      :mapping #js {"md" :md}}
              :modal {:type "boolean"}}})

(defstory drawer [args]
  [drawer/root
   {}
   [button/button
    {:el drawer/trigger}
    "Open"]
   [drawer/portal
    [drawer/backdrop]
    [drawer/viewport
     {:from (:from args)}
     [drawer/popup
      {:width (:width args)
       :from (:from args)}
      [drawer/content
       [drawer/title "Title"]
       [drawer/description "Hello world"]
       [:div
        [button/button
         {:el drawer/close}
         "Close"]]]]]]])
