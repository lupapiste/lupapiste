(ns lupapalvelu.dev.ui-doc.stories.button-stories
  "Hello world"
  (:require [lupapalvelu.dev.ui-doc.components-demo :refer [variants-grid]]
            [lupapalvelu.dev.ui-doc.util :as udu :refer [action-fn defmeta defstory]]
            [lupapalvelu.ui2.button :as button]))

;; TODO: Generate args / argTypes from variants definition
; (assoc (create-demo-options button/variants)
;        :label {:default "Example button"})

(defmeta default
  {:title "Component / Button"
   :component button/button
   :args {:intent nil
          :size nil
          :disabled nil
          :on-click (action-fn "on-click")}
   :argTypes {:intent {:control {:type "select"}
                       :options ["primary" "secondary" "text"]
                       ;; NOTE: Important to use a JS value directly here,
                       ;; else clj->js will convert the keywords to strs
                       :mapping #js {"primary" :primary
                                     "secondary" :secondary
                                     "text" :text}}
              :size {:control {:type "select"}
                     :options ["xs" "sm" "md" "lg"]
                     :mapping #js {"xs" :xs
                                   "sm" :sm
                                   "md" :md
                                   "lg" :lg}}
              :disabled {:type "boolean"}}})

(defstory all-options [args]
  {:args {:label "Example button"}}
  [button/button (dissoc args :label)
   (:label args "Example button")])

(defstory combinations [o]
  [variants-grid
   {:inline true
    ;; TODO: Make this dimensions specification more powerful?
    :dimensions [;; TODO: go over combinations of multiple variants in one row/col
                 ;; [:intent :size]
                 :intent ;; rows
                 :size ;; cols
                 ;; :disabled ;; rows
                 ]
    :spec button/variants
    :comp (fn [options]
            [button/button (merge o options)
             "Example"])}])
