(ns lupapalvelu.ui.common
  (:require [clojure.string :as s]))

(defn get-current-language []
  (.getCurrentLanguage js/loc))

(defn loc [& args]
  (apply js/loc (map name args)))

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
  [{:keys [command  show-saved-indicator? success]} & kvs]
  (-> (js/ajax.command (clj->js command) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (when show-saved-indicator?
                    (js/util.showSavedIndicator js-result))
                  (when success
                    (success (js->clj js-result :keywordize-keys true)))))
      .call))

(defmethod command :string
  [command-name success-fn & kvs]
  (apply command (cons {:command               command-name
                        :show-saved-indicator? true
                        :success               success-fn }
                       kvs)))


(defn reset-if-needed!
  "Resets atom with value if needed. True if reset."
  [atom* value]
  (when (not= @atom* value)
    (do (reset! atom* value)
        true)))

(defn event->state [state]
  #(reset-if-needed! state (.. % -target -value)))

(defn response->state [state kw]
  (fn [response]
    (swap! state #(assoc % kw (kw response)))))

(defn feature? [feature]
  (boolean (js/features.enabled (name feature))))

(defn css-flags
  "List of keys with truthy values.
  (css-flags :foo true :bar false) => '(\"foo\")"
  [& flags]
  (->> (apply hash-map flags)
       (filter (fn [[k v]] v))
       keys
       (map name)))

(defn fuzzy-re
  "Simplified Clojurescript version of sade.strings.fuzzy-re.

  \"hello world\" -> #\"(?i)^.*hello.*world.*$\""
  [term]
  (let [fuzzy (->> (s/split term #"\s")
                   (map goog.string/regExpEscape)
                   (s/join ".*"))]
    (re-pattern (str "(?i)^.*" fuzzy ".*$"))))
