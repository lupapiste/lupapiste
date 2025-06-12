(ns lupafront.dev
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.interceptor :as rfi]
            [re-frame.loggers :as rfl]
            [re-frisk.core :as re-frisk]
            [re-frisk.utils :as re-frisk-utils]
            [rum.core]
            [schema.core :as sc]))

(enable-console-print!)

(sc/set-fn-validation! true)

(rum.core/set-warn-on-interpretation! true)

;;; Workaround for https://github.com/flexsurfer/re-frisk/issues/86

(defn- safe-compare
  [a b]
  (try
    (compare a b)
    (catch :default _
      1)))

;; Might as well implement some DX features if we're monkey patching re-frisk
(def ^boolean ignore-nils?
  "Set to true if you want to hide `nil` returning subscriptions in re-frisk"
  false)

(defn better-sort-map
  [value checkbox-val checkbox]
  (if (and checkbox-val (map? value))
    (try
      (cond->> value
        ignore-nils? (filter (fn [[_k v]]
                               (some? v)))
        :true        (into (sorted-map-by safe-compare)))
      (catch :default _
        (do
          (reset! checkbox false)
          value)))
    value))

(set! re-frisk-utils/sort-map better-sort-map)

(re-frisk/enable)

;; More readable schema errors within re-frame handlers for dev:
(defn log-rf-schema-error [^js/Error original-error re-frame-error]
  (rfl/console :error
               "Error handling re-frame event: "
               (:event-v (ex-data re-frame-error))
               "\n"
               ;; Clean the Schema message (remove ANSI colors, fix fx name)
               (-> (.-message original-error)
                   (str/replace #"action-(command|query)" ":action/$1")
                   (str/replace #"\033\[0(;33)?m" ""))))

(rf/reg-event-error-handler
  (fn [original-error re-frame-error]
    (case (:type (ex-data  original-error))
      :schema.core/error (log-rf-schema-error original-error re-frame-error)

      (rfi/default-error-handler original-error re-frame-error))))
