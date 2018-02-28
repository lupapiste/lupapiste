 (ns lupapalvelu.ui.util
   (:require [goog.i18n.NumberFormatSymbols :as symbols]
             [goog.i18n.NumberFormat :as nf]))

 (defonce elemId (atom 0))
 (defn unique-elem-id
   ([] (unique-elem-id ""))
   ([prefix]
    (str prefix (swap! elemId inc))))

(defn clj->json [ds]
  (->> ds clj->js (.stringify js/JSON)))

(defn json->clj [json]
  (->> json (.parse js/JSON) js->clj))

(def is-ie?
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Trident/.*" agent)))))

(def is-edge?
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Edge/.*" agent)))))

(set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi)

(defn format-currency-value [n]
  (.format (goog.i18n.NumberFormat. (.-CURRENCY goog.i18n.NumberFormat.Format)) n))

(defn get-user-field [fieldName]
  (js/util.getIn js/lupapisteApp.models.currentUser #js [(name fieldName)]))
 