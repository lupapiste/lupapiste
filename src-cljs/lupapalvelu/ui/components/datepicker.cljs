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
     (fn [_]
       (.destroy @instance-atom)
       (remove-watch instance-atom :update-instance)
       (reset! instance-atom nil))}))

(defn date-state-mixin
  "Variation of the datepicker-mixin that together with
  initial-value-mixin facilitates easier binding between Pikaday and
  the corresponding client Rum component. See
  lupapalvelu.ui.components.date-edit for an example.

  Options [optional]:

    date-key: Key used for the date in initial-value-mixin.

    [pikaday-options]: Map of Pikaday options that override the
           defaults. Map keys will be camelCased automatically."
  [date-key & [pikaday-options]]
  (letfn [(dispose [state]
            (some-> state ::pikaday deref .destroy)
            (remove-watch (date-key state) :update-instance))
          (bind-pikaday [state]
            ;; Just in case
            (dispose state)
            (let [dom-node        (rum/dom-node state)
                  date*           (date-key state)
                  lang            (js/loc.getCurrentLanguage)
                  default-options {:format           (loc "date.format")
                                   :show-week-number true
                                   :first-day        1
                                   :position         "bottom left"
                                   :field            dom-node
                                   :i18n             (pikaday-i18n)
                                   :on-select        #(reset! date* %)}
                  opts            (opts-transform (merge default-options
                                                         pikaday-options))
                  pikaday*        (atom (js/Pikaday. opts))
                  update-fn       #(when-let [m (js/util.toMoment @date* lang)]
                                     (.setMoment @pikaday* m true))]
        (update-fn)
        (add-watch date* :update-instance update-fn)
        (assoc state ::pikaday pikaday*)))]

    {:did-mount    bind-pikaday
     :did-update   bind-pikaday
     :will-unmount dispose}))


(rum/defcs datepicker < (datepicker-mixin) [_ datepicker-args commit-fn test-id]
  [:input.inspection-date-input {:type    "text"
                                 :on-blur #(commit-fn @(:date-atom datepicker-args))
                                 :data-test-id test-id}])

(rum/defc basic-datepicker [date commit-fn idx]
  (let [date-atom (atom date)]
  (datepicker {:date-atom date-atom
               :pikaday-attrs
               {:format         (loc "date.format")
                :i18n           (pikaday-i18n)
                :showWeekNumber true
                :firstDay       1
                :position       "bottom left"}}
              commit-fn
              (str "inspection-date-input-" idx))))
