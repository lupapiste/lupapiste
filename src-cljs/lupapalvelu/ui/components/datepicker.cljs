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
  "Given a clojure map of options, return a js object for a pikaday constructor argument."
  (clj->js (transform-keys ->camelCaseString opts)))

(defn datepicker-mixin
  []
  (let [instance-atom (atom nil)
        date-atom (atom nil)]
    {:did-mount
     (fn [state]
       (let [comp (:rum/react-component state)
             args (first (:rum/args state))
             dom-node (js/ReactDOM.findDOMNode comp)
             pikaday-attrs (:pikaday-attrs args)
             _ (println (:type (first args)))
             date-atom (atom nil)
             _ (reset! date-atom the-date)
             default-opts
             {:field            dom-node
              :default-date     @date-atom
              :set-default-date true
              :on-select        #(when date-atom (reset! date-atom %))}
             opts (opts-transform (merge default-opts pikaday-attrs))
             instance (js/Pikaday. opts)]
         (reset! instance-atom instance)
         ;(.addEventListener dom-node "input" (fn [] (when (= "" (.-value dom-node))
         ;                                            (reset! date-atom nil))))
         ; This code could probably be neater
         (when date-atom
           (add-watch date-atom :update-instance
                      (fn [key ref old new]
                        ; final parameter here causes pikaday to skip onSelect() callback
                        (.setDate instance new true))))
         (when min-date-atom
           (add-watch min-date-atom :update-min-date
                      (fn [key ref old new]
                        (.setMinDate instance new)
                        ; If new max date is less than selected date, reset actual date to max
                        (if (< @date-atom new)
                          (reset! date-atom new)))))
         (when max-date-atom
           (add-watch max-date-atom :update-max-date
                      (fn [key ref old new]
                        (.setMaxDate instance new)
                        ; If new max date is less than selected date, reset actual date to max
                        (if (> @date-atom new)
                          (reset! date-atom new)))))))
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

(rum/defc basic-datepicker []
  (datepicker {:date-atom the-date
               :pikaday-attrs
                          {:date-atom      date-atom
                           :format         "DD.MM.YYYY"
                           :i18n           (pikaday-i18n)
                           :showWeekNumber true
                           :firstDay       1
                           :position       "bottom left"}}))