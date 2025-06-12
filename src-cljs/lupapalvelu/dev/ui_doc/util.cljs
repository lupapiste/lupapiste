(ns lupapalvelu.dev.ui-doc.util
  (:require-macros [lupapalvelu.dev.ui-doc.util])
  (:require ["storybook/actions" :as addon-actions]
            ["storybook/test" :as sb-testing]
            [clojure.string :as str]
            [lupapalvelu.dev.ui-doc.i18n :as ud-i18n]
            [reagent.core :as r]
            [sade.util :as util]))

;; TODO: Cljs-bean?
(sb-testing/configure (clj->js {:testIdAttribute "data-cy"}))

(defn- encode-keyword [k]
  (if (namespace k) (str (namespace k) "/" (name k)) k))

(defn encode-namespaced-keys [params]
  (reduce-kv (fn [params k v]
               (let [new-k (if (keyword? k) (encode-keyword k) k)
                     new-v (cond
                             (map? v)     (encode-namespaced-keys v)
                             (keyword? v) (encode-keyword v)
                             :else        v)]
                 (-> params
                     (assoc new-k new-v))))
             {}
             params))

(defn- convert-fn-args
  "Given the `args` of a component, find all top-level functions (= action callbacks)
  and automatically apply `clj->js` to their arguments when invoked."
  [args]
  (reduce-kv
    (fn [args k v]
      (if (fn? v)
        (assoc args k (fn [& xs]
                        ;; TODO: Cljs-bean?
                        (apply v (map clj->js xs))))
        args))
    args
    args))

(defn- set-submaps
  "Given parameters with dot in the name, use them as values in a sub-map

  e.g. :trigger-button-opts.size ... -> {:trigger-button-opts {:size ...}}"
  [args]
  (reduce-kv
    (fn [args k v]
      (let [name-parts (-> k name (str/split #"\."))]
        (if (-> name-parts count (> 1))
          (cond-> args
            true      (dissoc k)
            (some? v) (assoc-in (map keyword name-parts) v))
          args)))
    args
    args))

(defn- params [^js args]
  (-> args
      (js->clj :keywordize-keys true)
      convert-fn-args
      set-submaps))

(defn- template
  "Construct a component with some initial arguments

  Similar to:
  ```js
  const Template = (args) => <SomeComponent {...args} />;
  export const Primary = Template.bind({});
  Primary.args = {
     label: 'Foobar',
  };
  ```"
  ([component]
   component)
  ([^js component {:keys [args argTypes parameters play]}]
   (when (some? args)
     (set! (.-args component) (clj->js args)))
   (when (some? argTypes)
     (set! (.-argTypes component) (clj->js argTypes)))
   (when (some? parameters)
     (set! (.-parameters component) (clj->js parameters)))
   (when (some? play)
     (set! (.-play component) (clj->js play)))
   component))

(defn make-ctx
  [^js context]
  ;; Extract only the locale from globals - avoid js->clj on the full context
  ;; since Storybook v10 context objects can be circular/deeply nested
  (let [lang (-> (.. context -globals -locale)
                 (or "en")
                 keyword)
        tr   (ud-i18n/make-tr lang)]
    (util/mmap tr lang)))

(defn story
  ([component]
   (story component nil))
  ([component overrides]
   (template
     (fn [args context]
       (let [args (params args)]
         (if (::inject-ctx? overrides)
           (r/as-element
             [component args (make-ctx context)])
           (r/as-element
             [component args]))))
     overrides)))


(defn action-fn
  "Return a function that takes no parameters and executing it produces an item in
  the story's actions tab.

  `desc` is the text to show on the actions tab"
  [desc]
  (let [a (addon-actions/action desc)]
    (fn [& _] (a))))

(defn click [ids ^js ctx]
  (let [canvas-element (.-canvasElement ctx)]
    (cond-> (-> canvas-element
                sb-testing/within
                (.findByTestId (first ids))
                (.then (fn [el] (.click sb-testing/userEvent el))))
      (-> ids rest seq) (.then (fn []
                                 (click (rest ids) ctx))))))

(defn click-fn
  "Returns a :play function that clicks on an element or elements with the given data-cy value.

  If multiple ids are given, clicks each in sequence"
  [& ids]
  (fn [ctx]
    (click ids ctx)))

(defn meta->doc [m]
  (str "|symbol|file|\n|--|--|\n|`"
       (:ns m) "/" (:name m)
       "`|`" (:file m) ":" (:line m) "`|\n\n"
       (->> m :arglists (map #(str "`" % "`")) (str/join "\n\n"))
       "\n\n"
       (:doc m)))
