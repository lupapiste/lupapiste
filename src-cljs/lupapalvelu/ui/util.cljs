 (ns lupapalvelu.ui.util
   (:require [cognitect.transit :as transit]
             [goog.i18n.NumberFormat]
             [goog.i18n.NumberFormatSymbols]
             [sade.strings :as ss]))

 (defonce elemId (atom 0))
 (defn unique-elem-id
   ([] (unique-elem-id ""))
   ([prefix]
    (str prefix (swap! elemId inc))))

(defn clj->json [ds]
  (->> ds clj->js (.stringify js/JSON)))

(defn json->clj [json]
  (->> json (.parse js/JSON) js->clj))

(defn is-ie?
  []
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Trident/.*" agent)))))

(defn is-edge?
  []
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Edge/.*" agent)))))

(set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi)

(defn format-currency-value [n]
  (.format (goog.i18n.NumberFormat. (.-CURRENCY goog.i18n.NumberFormat.Format)) n))

(defn get-user-field [fieldName]
  (js/util.getIn js/lupapisteApp.models.currentUser #js [(name fieldName)]))

(defn valid-email?
  [email]
  (boolean (some-> (ss/blank-as-nil email)
                   (js/util.isValidEmailAddress email))))

(defn invalid-email? [required? email]
  (if (ss/blank? email)
    required?
    (not (valid-email? email))))

(defn decode-transit [s]
  (when (string? s)
    (transit/read (transit/reader :json) s)))

(defn cookie-anti-forgery-value
  "Get the anti-forgery value from current cookie for http requests."
  []
  (.cookie js/$ "anti-csrf-token"))
