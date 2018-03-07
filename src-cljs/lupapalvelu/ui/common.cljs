(ns lupapalvelu.ui.common
  (:require [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [clojure.string :as s]
            [clojure.string :as str]
            [goog.events :as googe]
            [goog.object :as googo]
            [rum.core :as rum]))

(defn get-current-language []
  (.getCurrentLanguage js/loc))

(defn loc [& args]
  (apply js/loc (map name args)))

(defn loc-html [tag & args]
  [tag
   {:dangerouslySetInnerHTML {:__html (apply loc args)}}])

(def fi-date-formatter (tf/formatter "d.M.yyyy"))

(defn format-timestamp [tstamp]
  (tf/unparse fi-date-formatter (doto (t/time-now) (.setTime (tc/to-long tstamp)))))

(defn query [query-name success-fn & kvs]
  (-> (js/ajax.query (clj->js query-name) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))


(defmulti command (fn [a & _]
                    (cond
                      (map? a)     :map
                      (string? a)  :string
                      (keyword? a) :string)))

(defmethod command :map
  [{:keys [command  show-saved-indicator? success error]} & kvs]
  (letfn [(with-error-handler-if-given [call]
            (if error
              (.error call (fn [js-result]
                             (when error
                               (error (js->clj js-result :keywordize-keys true)))))
              call))]
    (-> (js/ajax.command (clj->js command) (-> (apply hash-map kvs) clj->js))
        (.success (fn [js-result]
                    (when show-saved-indicator?
                      (js/util.showSavedIndicator js-result))
                    (when success
                      (success (js->clj js-result :keywordize-keys true)))))
        with-error-handler-if-given
        .call)))

(defmethod command :string
  [command-name success-fn & kvs]
  (apply command (cons {:command               command-name
                        :show-saved-indicator? true
                        :success               success-fn }
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

(defn event->state [state]
  #(reset-if-needed! state (.. % -target -value)))

(defn response->state [state kw]
  (fn [response]
    (swap! state #(assoc % kw (kw response)))))

(defn feature? [feature]
  (boolean (js/features.enabled (name feature))))

(defn css
  "Convenience function for :class defintions."
  [& classes]
  (->> classes flatten (remove nil?) (map name)))

(defn css-flags
  "List of keys with truthy values.
  (css-flags :foo true :bar false) => '(\"foo\")"
  [& flags]
  (->> (apply hash-map flags)
       (filter (fn [[k v]] v))
       keys
       css))

(defn update-css
  "Upserts existing :class definition. Flags use css-flags semantics."
  [attr & flags]
  (update attr :class (fn [cls]
                        (let [s       (s/join " " (flatten [cls]))
                              old-map (zipmap (map keyword
                                                   (remove s/blank?
                                                           (s/split s #"\s+")))
                                              (repeat true))
                              updates (apply hash-map flags)]
                          (->> (merge old-map updates)
                               (into [])
                               flatten
                               (apply css-flags)))) ))

(defn fuzzy-re
  "Simplified Clojurescript version of sade.strings.fuzzy-re.

  \"hello world\" -> #\"(?mi)^.*hello.*world.*$\""
  [term]
  (let [fuzzy (->> (s/split term #"\s")
                   (map goog.string/regExpEscape)
                   (s/join ".*"))]
    (re-pattern (str "(?mi)^.*" fuzzy ".*$"))))


(def nbsp {:dangerouslySetInnerHTML {:__html "&nbsp;"}})

(defn empty-label [& cls]
  [:label (assoc nbsp
                 :class cls)])

(defn open-oskari-map [{id :id [x y] :location municipality :municipality}]
  (let [features "addPoint=0&addArea=0&addLine=0&addCircle=0&addEllipse=0"
        params   [(str "build=" js/LUPAPISTE.config.build)
                  (str "id="  id)
                  (str "coord=" x  "_" y)
                  "zoomLevel=12"
                  (str "lang="  (js/loc.getCurrentLanguage))
                  (str "municipality="  municipality)
                  features]
        url      (str "/oskari/fullmap.html?" (str/join "&" params))]
    (js/window.open url)))

;; Callthrough for goog.events.getUniqueId.
;; Must be in the global scope.
(def unique-id googe/getUniqueId)

(defn oget
  "Convenience wrapper for goog.object/get. k can be either string or
  keyword."
  [obj k]
  (googo/get obj (name k)))

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

(defn resolve-disabled
  "Resolves disabled status based on disabled? and enabled?
  options. True if disabled. Both options are optional:

  disabled? If true, button is disabled. Can be either value or atom.

  enabled? If false, button is enabled. Can be either value or
  atom. Nil value is ignored.

  If both disabled? and enabled? are given, the button is disabled if
  either condition results in disabled state."
  [{:keys [enabled? disabled?] :as options}]
  (or (rum/react (atomize disabled?))
      (some-> enabled? atomize rum/react false?)))
