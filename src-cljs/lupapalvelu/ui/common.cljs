(ns lupapalvelu.ui.common
  (:require [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [goog.events :as googe]
            [goog.object :as googo]
            [lupapalvelu.common.hub :as hub]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]
            [reagent.dom :as rd])
  (:import [goog.async Delay]
           [goog.i18n NumberFormat]
           [goog.i18n.NumberFormat Format]))

(defn ->percentage
  "Returns how many percent `x` is from `total` as int.
  Return nil when percentage cannot be calculated."
  [x total]
  (when (and x total (not (zero? total)))
    (Math/round (* 100 (/ x total)))))

(defn format-number [num]
  (-> (NumberFormat. Format/DECIMAL)
      (.format (str num))))

(defn start-delay
  "Convenience function for `goog.async.Delay`. Function `fun` is called after `ms`
  milliseconds"
  [fun ms]
  (.start (Delay. fun ms)))

(defn get-current-language []
  (.getCurrentLanguage js/loc))

(defn loc [& args]
  (->> (flatten args)
       (remove nil?)
       (map #(if (keyword? %) (name %) %))
       (apply js/loc)))

(defn loc-html [tag & args]
  [tag
   {:dangerouslySetInnerHTML {:__html (apply loc args)}}])

(def fi-date-formatter (tf/formatter "d.M.yyyy"))

(defn show-saved-indicator [response]
  (hub/send "indicator" {:style (if (:ok response) "positive" "negative") :message (:text response)}))

(defn format-timestamp [tstamp]
  (tf/unparse fi-date-formatter (doto (t/time-now) (.setTime (tc/to-long tstamp)))))

(defn query [query-name success-fn & kvs]
  (-> (js/lpAjax.query (clj->js query-name) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))

(defn query-with-error-fn [query-name success-fn error-fn & kvs]
  (-> (js/lpAjax.query (clj->js query-name) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (success-fn (js->clj js-result :keywordize-keys true))))
      (.error (fn [js-result]
                (error-fn (js->clj js-result :keywordize-keys true))))
      .call))

(defmulti command (fn [a & _]
                    (cond
                      (map? a) :map
                      (string? a) :string
                      (keyword? a) :string)))

;; Options [optional]:
;; command: string or keyword
;; [show-saved-indicator?]: boolean
;; [success]: function
;; [error]: function
;; [waiting?]: atom that is set true for the duration of the ajax
;; call. Can be either an individual atom or a list of atoms.
(defmethod command :map
  [{:keys [command show-saved-indicator? success error waiting? on-timeout]} & kvs]
  (letfn [(waiting [flag] (doseq [a (cond
                                      (nil? waiting?) []
                                      (sequential? waiting?) waiting?
                                      :else [waiting?])]
                            (reset! a flag)))
          (with-error-handler-if-given [^js/lpAjax.Call call]
            (if (or error show-saved-indicator?)
              (.error call (fn [js-result]
                             (cond
                               error
                               (error (js->clj js-result
                                               :keywordize-keys true))

                               show-saved-indicator?
                               (js/util.showSavedIndicator js-result))))
              call))
          (on-timeout-handler-if-given [^js/lpAjax.Call call]
            (if on-timeout
              (.onTimeout call (fn []
                                 on-timeout))
              call))]
    (waiting true)
    (-> (js/lpAjax.command (clj->js command) (-> (apply hash-map kvs) clj->js))
        (.success (fn [js-result]
                    (when show-saved-indicator?
                      (js/util.showSavedIndicator js-result))
                    (when success
                      (success (js->clj js-result :keywordize-keys true)))))
        on-timeout-handler-if-given
        with-error-handler-if-given
        (.complete (partial waiting false))
        .call)))

(defmethod command :string
  [command-name success-fn & kvs]
  (apply command (cons {:command               command-name
                        :show-saved-indicator? true
                        :success               success-fn}
                       kvs)))

(defn reset-if-needed!
  "Resets atom with value if needed. Optional parameter value-fn
  transforms values (default identity). True if reset."
  ([atom* value value-fn]
   (let [v (value-fn value)]
     (when (not= (value-fn @atom*) v)
       (do (reset! atom* v)
           true))))
  ([atom* value]
   (reset-if-needed! atom* value identity)))


(defn response->state [state kw]
  (fn [response]
    (swap! state #(assoc % kw (kw response)))))

(defn feature? [feature]
  (boolean (js/features.enabled (name feature))))

(defn css
  "Convenience function for :class definitions. Supports keywords,
  strings, vectors and kw-paths."
  [& classes]
  (->> classes flatten (remove nil?)
       (map util/split-kw-path)
       (apply concat)
       (map name)))

(defn css-flags
  "List of keys with truthy values.
  (css-flags :foo true :bar false) => '(\"foo\")"
  [& flags]
  (->> (apply hash-map flags)
       (filter val)
       keys
       css))

(defn update-css
  "Upserts existing :class definition. Flags use css-flags semantics."
  [attr & flags]
  (update attr :class (fn [cls]
                        (let [s       (ss/join " " (flatten [cls]))
                              old-map (zipmap (map keyword
                                                   (remove ss/blank?
                                                           (ss/split s #"\s+")))
                                              (repeat true))
                              updates (apply hash-map flags)]
                          (->> (merge old-map updates)
                               (into [])
                               flatten
                               (apply css-flags))))))

(defn fuzzy-re
  "Simplified Clojurescript version of sade.strings.fuzzy-re.

  \"hello world\" -> #\"(?mi)^.*hello.*world.*$\""
  [term]
  (let [fuzzy (->> (ss/split (ss/trim term) #"\s+")
                   (map goog.string/regExpEscape)
                   (ss/join ".*"))]
    (re-pattern (str "(?mi)^.*" fuzzy ".*$"))))


(def nbsp {:dangerouslySetInnerHTML {:__html "&nbsp;"}})

(defn empty-label [& cls]
  [:label (assoc nbsp
            :class cls
            :aria-hidden true)])

;; Callthrough for goog.events.getUniqueId.
;; Must be in the global scope.
(def unique-id googe/getUniqueId)

(defn oget
  "Convenience wrapper for `goog.object/get`. `k` can be either string or keyword. Falls
  back to `get` (both with string and keyword), in case the object has been through
  `js->clj`."
  ([obj k]
   (or (googo/get obj (name k))
       (get obj k)
       (get obj (keyword k))))
  ([obj k default]
   (or (oget obj k) default)))

(defn atomize
  "Wraps value into atom. If the value already is either atom or cursor
  it is returned as such."
  [value]
  (if (or (instance? Atom value)
          (instance? rum.cursor.Cursor value))
    value
    (atom value)))

(defn resolve-text
  "Resolves textual representation based on two mutually exclusive
  options:
     text     Text as it is
     text-loc Localisation key for text"
  ([{:keys [text text-loc]} default]
   (or text (loc text-loc) default))
  ([options]
   (resolve-text options nil)))

(defn resolve-aria-label
  "Resolves textual representation based on two mutually exclusive
  options:
     aria-label     Text as it is
     aria-label-loc Localisation key for text

  If neither matches then fall backs to `resolve-text`"
  ([{:keys [aria-label aria-label-loc] :as options} default]
   (or aria-label (loc aria-label-loc) default
       (resolve-text options)))
  ([options]
   (resolve-aria-label options nil)))

(defn resolve-disabled-reactive ;; FIXME remove atomizationz and reactionz
  "Resolves disabled status based on disabled? and enabled?
  options. True if disabled. Both options are optional:

  disabled? If true, button is disabled. Can be either value or atom.

  enabled? If false, button is disabled. Can be either value or
  atom. Nil value is ignored.

  If both disabled? and enabled? are given, the button is disabled if
  either condition results in disabled state."
  [{:keys [enabled? disabled?]}]
  (or (rum/react (atomize disabled?))
      (some-> enabled? atomize rum/react false?)))

(defn resolve-disabled [{:keys [enabled? disabled?]}]
  (or disabled? (some-> enabled? false?)))

(defn required-invalid-attributes
  "Returns map with (optional) `:required`, `:aria-required` and `:aria-invalid` keys based
  on the given options (with `:required?` and `:invalid?`). If `value` is given and
  non-blank it clears the required state. Note that the result cannot be both required and
  invalid. If value is blank, then required overrides invalid."
  [{:keys [required? invalid?]} & [value :as xs]]
  (let [value?       (not-empty xs)
        blank-value? (and value? (ss/blank? value))
        required?    (and required? (or (not value?) blank-value?))]
    (util/assoc-when {}
                     :required required?
                     :aria-required required?
                     :aria-invalid (and invalid? (not required?)))))

(defn open-page
  "Convenience wrapper for pageutil.openPage."
  [page & suffix]
  (js/pageutil.openPage (name page) (apply array (map name suffix))))

(defn test-id
  "Concatenates the given arguments into test-id string.
  hello [world] 3 -> hello-world-3"
  [& xs]
  (->> xs
       flatten
       (remove nil?)
       (map (fn [k]
              (cond-> k (keyword? k) name)))
       (ss/join "-")))

(defn add-test-id
  "Adds data-test-id attribute. The target can be either the attribute
  map or the encompassing component. Extras parts are concatened with
  - If the test-id is nil (or false), the target is returned unchanged
  regardless of extras."
  [[x & xs :as target] testid & extras]
  (let [test-id (when testid
                  (test-id (cons testid extras)))]
    (cond
      (ss/blank? test-id) target
      (map? target) (assoc target :data-test-id test-id)
      (vector? target) (if (-> target second map?)
                         (assoc-in target [1 :data-test-id] test-id)
                         (vec (concat [x {:data-test-id test-id}] xs))))))

(defn prefix-lang
  "Current language is appended to the given keyword prefix:
  :foo -> :foo-fi"
  [prefix]
  (when-not (ss/blank? prefix)
    (->> (map name [prefix (get-current-language)])
         (ss/join "-")
         keyword)))

(defn show-dialog
  "Convenience function for showing dialog without
  boilerplate. Options [optional]:

  [:ltitle or :title]   Dialog title (default :ltitle is :areyousure)
  [:ltext or :text]     Dialog text
  [:size]               Dialog size (default :medium)
  :type                 Dialog type: :ok (default), :yes-no, :react, :location-editor or other.
  [:callback]           Callback function for yes/ok/save action.
  [:cancel-callback]    Callback function for cancel action.
  [:component-params]   Component param map is merged with map that may contain:
                          1) :yesFn/okFn/saveFn when callback is defined. The key depends on :type
                          2) :cancelFn when cancel-callback is defined
                          3) :text when text or ltext is defined
                          4) :renderCallback if type is react and dialog-component is defined.
                        If the dialog required options that are not in this list and are required
                        by dialog component add them here.

                        For :location-editor following attributes are needed as component-params
                        [:x] x-coordinate of selected location
                        [:y] y-coordinate of selected location
                        :center Center of the map as vector [x, y]. It is required for :location-editor!
  [:dialog-component]   If type = :react this needs to contain the React component that will actually render dialog contents,"
  [{:keys [ltitle title
           ltext text
           size type
           callback
           cancel-callback
           dialog-component
           minContentHeight
           component-params]}]
  (let [type (or type :ok)
        component (case type
                    :location-editor (name type)
                    (str (name type) "-dialog"))
        callbackFnKey (cond
                        (util/=as-kw type :ok) :okFn
                        (util/=as-kw type :location-editor) :saveFn
                        :else :yesFn)

        text       (or text (loc ltext))
        renderCallback #(rd/render dialog-component (.getElementById js/document "react-dialog-div"))
        computedComponentParams (util/assoc-when {:text text}
                                                 callbackFnKey callback
                                                 :cancelFn cancel-callback
                                                 :renderCallback (when dialog-component renderCallback))
        options (cond->
                 {:title           (or title (loc (or ltitle :areyousure)))
                  :size            (name (or size :medium))
                  :component       component
                  :componentParams (merge computedComponentParams component-params)}
                 (not-empty minContentHeight) (assoc :minContentHeight minContentHeight)
                 (util/=as-kw type :location-editor) (assoc :minContentHeight "35em"))]
    (hub/send "show-dialog" options)))

(defn ->cljs
  "`js-form` -> `ko.mapping.toJS` -> `js->clj` -> `not-empty`."
  [js-form]
  (some-> js-form
          js/ko.mapping.toJS
          (js->clj :keywordize-keys true)
          not-empty))

(defn application-id
  "Current (hash) application id."
  []
  (js/pageutil.hashApplicationId))
