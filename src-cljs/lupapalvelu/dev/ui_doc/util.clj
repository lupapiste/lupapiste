(ns lupapalvelu.dev.ui-doc.util
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]))

(defmacro defstory
  "Register a Storybook story for a component

  Syntax is similar to `clojure.core/defn`.

  The simplest case is a story with a single map argument, where the keys
  are the union of `args` and `argTypes` keysets and the values are
  the corresponding values from the story controls.

  ```clojure
  (defstory simple-button
    [{:keys [label disabled?]}]
    [ui/button {:label     label
                :disabled? disabled?}])
  ```

  `defstory` also supports a 2-arity version, where a context map provides
  additional utilities. At the moment only the `:tr` function is provided.

  ```clojure
  (defstory component-with-labels
    [{:keys [disabled?]} {:keys [tr]}]
    [ui/component {:tr        tr
                   :disabled? disabled?}])
  ```

  An optional docstring will be used as the story description.

  ```clojure
  (defstory simple-button
    \"This is a story for a simple button\"
    [{:keys [label disabled?]}]
    [ui/button {:label     label
                :disabled? disabled?}])
  ```

  An optional map can be given after the argument vector to override (or supplement)
  the default values given in the `dev.ui-doc.util/section` for the story.

  ```clojure
  (defstory disabled-button
    [{:keys [label disabled?]}]
    {:args       {:disabled? true}
     :argTypes   {}
     :parameters {}
     ;; jest script to execute after the story has been loaded
     :play       (udu/click-fn \"some-data-cy-value\")}
    [ui/button {:label     label
                :disabled? disabled?}])
  ```"
  [name & body]
  (let [[doc-string body] (if (string? (first body))
                            [(first body) (rest body)]
                            [nil body])
        [binding body]    [(first body) (rest body)]
        [overrides body]  (if (map? (first body))
                            [(first body) (rest body)]
                            [{} body])
        overrides         (merge overrides
                                 (when (< 1 (count binding))
                                   {::inject-ctx? true})
                                 (when doc-string
                                   {:parameters {:docs {:description {:story doc-string}}}}))]
    (assert (symbol? name))
    (assert (vector? binding))
    (assert (<= 1 (count binding) 2))
    (when env/*compiler*
      (swap!
        env/*compiler*
        update-in
        [::ana/namespaces ana/*cljs-ns* ::storybook :stories]
        (fnil conj []) {:name name}))
    `(def ~(with-meta name {:export true})
       (story
         ~(concat `(fn ~binding)
                  body)
         ~overrides))))

(defmacro defmeta
  [name config]
  (when env/*compiler*
    (swap!
      env/*compiler*
      update-in
      [::ana/namespaces ana/*cljs-ns* ::storybook]
      assoc
      :title (:title config)
      :stories []))
  `(def ~(with-meta name {:export true})
     (-> ~config
         (update :component reagent.core/reactify-component)
         (update :args encode-namespaced-keys)
         (update-in [:parameters :docs :description :component]
                    (fnil identity (-> ~(:component config) var meta meta->doc)))
         ;; TODO: Consider cljs-bean?
         cljs.core/clj->js)))
