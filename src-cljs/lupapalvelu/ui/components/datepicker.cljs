(ns lupapalvelu.ui.components.datepicker
  (:require [rum.core :as rum]
            [cljsjs.moment]
            [cljsjs.pikaday]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(defonce the-date (atom (js/Date.)))

(defn pikaday-i18n []
  {:previousMonth "Edellinen"
   :nextMonth "Seuraava"
   :months ["Tammikuu","Helmikuu","Maaliskuu","Huhtikuu","Toukokuu","Kesakuu","Heinakuu","Elokuu","Syyskuu","Lokakuu","Marraskuu","Joulukuu"],
   :weekdays ["Sunnuntai","Maanantai","Tiistai","Keskiviikko","Torstai","Perjantai","Lauantai"],
   :weekdays-short  ["Su","Ma","Ti","Ke","To","Pe","Su"]})

(defn- opts-transform [opts]
  (clj->js (transform-keys ->camelCaseString opts)))

(defn datepicker-mixin
  []
  (let [instance-atom (atom nil)]
    {:did-mount
     (fn [state]
       (let [comp (:rum/react-component state)
             args (first (:rum/args state))
             dom-node (js/ReactDOM.findDOMNode comp)
             pikaday-attrs (:pikaday-attrs args)
             date-atom (:date-atom args)
             default-opts
             {:field            dom-node
              :default-date     @date-atom
              :set-default-date true
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
       (remove-watch instance-atom :update-min-date)
       (remove-watch instance-atom :update-max-date)
       (reset! instance-atom nil))
     }))

(rum/defcs datepicker < (datepicker-mixin)
  [state datepicker-args]
  [:input {:type "text"}])

(rum/defc basic-datepicker [date]
  (let [date-atom (atom date)]
  (datepicker {:date-atom date-atom
               :pikaday-attrs
                          {:format         "DD.MM.YYYY"
                           :i18n           (pikaday-i18n)
                           :showWeekNumber true
                           :firstDay       1
                           :position       "bottom left"}})))