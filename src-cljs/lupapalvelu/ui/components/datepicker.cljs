(ns lupapalvelu.ui.components.datepicker
  (:require [rum.core :as rum]
            [cljsjs.moment]
            [cljsjs.pikaday]
            [lupapalvelu.ui.common :refer [loc]]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.string :as string]))

(defn pikaday-i18n []
  {:previousMonth (loc "previous")
   :nextMonth (loc "next")
   :months (string/split (loc "months") #",")
   :weekdays (string/split (loc "weekdays") #",")
   :weekdays-short (string/split (loc "short.weekdays") #",")})

(defn- opts-transform [opts]
  (clj->js (transform-keys ->camelCaseString opts)))

(defn datepicker-mixin
  []
  (let [instance-atom (atom nil)]
    {:did-mount
     (fn [state]
       (let [args (first (:rum/args state))
             dom-node (rum/dom-node state)
             pikaday-attrs (:pikaday-attrs args)
             date-atom (:date-atom args)
             default-opts
             {:field            dom-node
              :default-date     @date-atom
              :set-default-date true
              :i18n             (pikaday-i18n)
              :on-select        #(when date-atom (reset! date-atom %))}
             opts (opts-transform (merge default-opts pikaday-attrs))
             instance (js/Pikaday. opts)]
         (reset! instance-atom instance)
         (when date-atom
           (add-watch date-atom :update-instance
                      (fn [new]
                        (.setDate instance new true))))))
     :will-unmount
     (fn [this]
       (.destroy @instance-atom)
       (remove-watch instance-atom :update-instance)
       (reset! instance-atom nil))}))

(rum/defcs datepicker < (datepicker-mixin)
  [state datepicker-args commit-fn]
  [:input {:type    "text"
           :on-blur #(commit-fn @(:date-atom datepicker-args))}])

(rum/defc basic-datepicker [date commit-fn]
  (let [date-atom (atom date)]
  (datepicker {:date-atom date-atom
               :pikaday-attrs
                          {:format         "DD.MM.YYYY"
                           :i18n           (pikaday-i18n)
                           :showWeekNumber true
                           :firstDay       1
                           :position       "bottom left"}}
              commit-fn)))