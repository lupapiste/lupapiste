(ns lupapalvelu.ui.common
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [goog.events :as googe]
            [goog.object :as googo]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.docgen.feature.auth :as auth-feature]
            [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as rdomc]
            [reagent.hooks :as hooks]
            [rum.core :as rum]
            [rum.cursor] ; for instance? check
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [goog Uri]
           [goog.async Delay]
           [goog.html SafeHtml]
           [goog.i18n NumberFormat]
           [goog.i18n.NumberFormat Format]))

(defn supported-langs []
  (map keyword js/LUPAPISTE.config.supportedLangs))

(defn ->percentage
  "Returns how many percent `x` is from `total` as int.
  Return nil when percentage cannot be calculated."
  [x total]
  (when (and x total (not (zero? total)))
    (Math/round (* 100 (/ x total)))))

(defn ->percentage-over-limit
  "Returns how many percent `value` exceeds `limit` as a decimal string.
  For example, if limit is 800 and value is 801, returns \"0.13\".
  Returns nil when calculation cannot be performed."
  [value limit]
  (when (and value limit (not (zero? limit)) (> value limit))
    (let [excess     (- value limit)
          percentage (* 100 (/ excess limit))]
      (.toFixed percentage 2))))

(defn format-number [num]
  (-> (NumberFormat. Format/DECIMAL)
      (.format (str num))))

(defn hash-application-id
  []
  (js/pageutil.hashApplicationId))

(defn start-delay
  "Convenience function for `goog.async.Delay`. Function `fun` is called after `ms`
  milliseconds"
  [fun ms]
  (.start (Delay. fun ms)))

(defn get-current-language []
  (.getCurrentLanguage js/loc))

(defn escape-html [text]
  (str (SafeHtml.htmlEscape text)))

(def ^:private do-loc (if (exists? js/loc)
                        js/loc
                        pr-str))

(defn loc [& args]
  (->> (flatten args)
       (remove nil?)
       (map #(if (keyword? %) (name %) %))
       (apply do-loc)))

(defn loc-html-rum [tag & args]
  [tag
   {:dangerouslySetInnerHTML {:__html (apply loc args)}}])

(defn loc-html-reagent [tag & args]
  [tag
   {:dangerouslySetInnerHTML (r/unsafe-html (apply loc args))}])

(defn loc-has-term?
  [term]
  (.hasTerm js/loc term))

(def fi-date-formatter (tf/formatter "d.M.yyyy"))
(def fi-datetime-formatter (tf/formatter "d.M.yyyy HH:mm"))

(defn show-saved-indicator [response]
  (hub/send "indicator" {:style (if (:ok response) "positive" "negative") :message (:text response)}))

(defn format-timestamp [tstamp]
  (tf/unparse fi-date-formatter (doto (t/time-now) (.setTime (tc/to-long tstamp)))))

(defn format-timestamp-with-time [tstamp]
  (tf/unparse fi-datetime-formatter (doto (t/time-now) (.setTime (tc/to-long tstamp)))))

(def ^:private finnish-date-formatter
  (js/Intl.DateTimeFormat "fi" #js {"year"  "numeric"
                                    "month" "2-digit"
                                    "day"   "2-digit"}))

(defn finnish-date
  "Format `js-date` as `dd.mm.yyyy` string.
  `js-date` should be a `Date` object, but an integer timestamp seems to also work."
  [js-date-or-timestamp]
  (.format finnish-date-formatter js-date-or-timestamp))

(defn- js-result->clj [js-result]
  (js->clj js-result :keywordize-keys true))

(defn query [query-name success-fn & {:as options}]
  (-> (js/lpAjax.query (clj->js query-name) (clj->js (or options {})))
      (.success (fn [js-result]
                  (success-fn (js-result->clj js-result))))
      .call))

(defn query-with-error-fn [query-name success-fn error-fn & {:as options}]
  (-> (js/lpAjax.query (clj->js query-name) (clj->js (or options {})))
      (.success (fn [js-result]
                  (success-fn (js-result->clj js-result))))
      (.error (fn [js-result]
                (error-fn (js-result->clj js-result))))
      .call))

(defn post-json [{:keys [path success error params]}]
  (letfn [(result-fn [fun]
            (fn [js-result]
              (fun (js-result->clj js-result))))]
    (cond-> (js/lpAjax.postJson (clj->js path)
                                (clj->js params))
      success (.success (result-fn success))
      error   (.error (result-fn error))
      true    .call)))

(defn post-form [{:keys [raw-name success error complete params]}]
  (letfn [(result-fn [fun]
            (fn [js-result]
              (fun (js-result->clj js-result))))]
    (let [formdata (reduce-kv (fn [acc k v]
                                (doto acc (.append (clj->js k) v))
                                )
                              (js/FormData.)
                              params)]

      (cond-> (js/lpAjax.form (clj->js raw-name)
                              formdata)
        success  (.success (result-fn success))
        error    (.error (result-fn error))
        complete (.complete (result-fn complete))
        true     .call))))

(defn command
  "Execute command over HTTP

  * `command` -- The command to execute (string or keyword)
  * `show-saved-indicator?` -- Show indicator (boolean)
  * `success` -- Success callback
  * `error` -- Error callback
  * `fail` -- Failure callback
  * `on-timeout` -- Timeout callback
  * `on-complete` -- Complete callback (called after response completes, regardless of success/error/fail)
  * `waiting?` -- Atom(s) to `reset!` when waiting for response"
  [{:keys [command show-saved-indicator? success error fail waiting? on-timeout on-complete]} & {:as options}]
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
          (with-fail-handler-if-given [^js/lpAjax.Call call]
            (if fail
              (.fail call (fn [js-result]
                            (fail (js->clj js-result
                                           :keywordize-keys true))))
              call))
          (on-timeout-handler-if-given [^js/lpAjax.Call call]
            (if on-timeout
              (.onTimeout call (fn []
                                 on-timeout))
              call))
          (on-complete-handler [^js/lpAjax.Call call]
            (if on-complete
              (.complete call (fn []
                                (waiting false)
                                (on-complete)))
              (.complete call (partial waiting false))))]
    (waiting true)
    (-> (js/lpAjax.command (clj->js command) (clj->js (or options {})))
        (.success (fn [^js js-result]
                    (when show-saved-indicator?
                      (js/util.showSavedIndicator js-result))
                    (when-let [newAuthModel (.-authModel js-result)]
                      (when-let [doc-id (some options [:documentId :docId :doc :foreman-doc-id])]
                        ;; Store the updated authModel to JS service
                        (when-let [doc (.. js/lupapisteApp -services -documentDataService (findDocumentById doc-id))]
                          (.. doc -auth (setData newAuthModel)))
                        (>evt [::auth-feature/store-doc-auth-result doc-id (js->clj newAuthModel :keywordize-keys true)])))
                    (when success
                      (success (js->clj js-result :keywordize-keys true)))))
        on-timeout-handler-if-given
        with-error-handler-if-given
        with-fail-handler-if-given
        on-complete-handler
        .call)))

(defn reset-if-needed!
  "Resets atom with value if needed. Optional parameter value-fn
  transforms values (default identity). True if reset."
  ([atom* value value-fn]
   (let [v (value-fn value)]
     (when (not= (value-fn @atom*) v)
       (reset! atom* v)
       true)))
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


(def nbsp "\u00A0")

(defn empty-label [& cls]
  [:label
   {:class cls
    :aria-hidden true}
   nbsp])

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
  [& parts]
  (let [[x & xs] (->> parts flatten (mapv name))]
    (js/pageutil.openPage x (clj->js xs))))

(defn page-path
  "Convenience wrapper for pageutil.getPagePath."
  []
  (js/pageutil.getPagePath))

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

(defc dialog-wrapper [{:keys [callback]} dialog-component]
  (hooks/use-effect (fn []
                      (callback))
                    [])
  dialog-component)

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
  (let [type                    (or type :ok)
        component               (case type
                                  :location-editor (name type)
                                  (str (name type) "-dialog"))
        callbackFnKey           (cond
                                  (util/=as-kw type :ok) :okFn
                                  (util/=as-kw type :location-editor) :saveFn
                                  :else :yesFn)
        text                    (or text (loc ltext))
        renderCallback          (fn [react-mounted-callback]
                                  ;; This root is used just once,
                                  ;; this element gets removed from the DOM when dialog is closed.
                                  (let [root (rdomc/create-root (.getElementById js/document "react-dialog-div"))]
                                    (rdomc/render root [dialog-wrapper {:callback react-mounted-callback} dialog-component])
                                    (fn cleanup []
                                      (.unmount root))))
        computedComponentParams (util/assoc-when {:text text}
                                                 callbackFnKey callback
                                                 :cancelFn cancel-callback
                                                 :renderCallback (when dialog-component renderCallback))
        options                 (cond->
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

(defn ->js-options
  "Transform `options` map into JavaScript object with camelCase property names."
  [options]
  (clj->js (csk-extras/transform-keys csk/->camelCaseString options)))

(defn normalize
  "The NFC normalization is prudent when `s` may contain combining umlauts (e.g., a + (char 776) -> ä).
   See also `sade.strings/normalize`."
  [s]
  (some-> s (.normalize "NFC")))

(def ^:const country-codes "See `iso-country-codes.countries`"
  ["AFG" "ALA" "ALB" "DZA" "ASM" "AND" "AGO" "AIA" "ATA" "ATG" "ARG" "ARM"
   "ABW" "AUS" "AUT" "AZE" "BHS" "BHR" "BGD" "BRB" "BLR" "BEL" "BLZ" "BEN"
   "BMU" "BTN" "BOL" "BES" "BIH" "BWA" "BVT" "BRA" "IOT" "BRN" "BGR" "BFA"
   "BDI" "KHM" "CMR" "CAN" "CPV" "CYM" "CAF" "TCD" "CHL" "CHN" "CXR" "CCK"
   "COL" "COM" "COG" "COD" "COK" "CRI" "CIV" "HRV" "CUB" "CUW" "CYP" "CZE"
   "DNK" "DJI" "DMA" "DOM" "ECU" "EGY" "SLV" "GNQ" "ERI" "EST" "ETH" "FLK"
   "FRO" "FJI" "FIN" "FRA" "GUF" "PYF" "ATF" "GAB" "GMB" "GEO" "DEU" "GHA"
   "GIB" "GRC" "GRL" "GRD" "GLP" "GUM" "GTM" "GGY" "GIN" "GNB" "GUY" "HTI"
   "HMD" "VAT" "HND" "HKG" "HUN" "ISL" "IND" "IDN" "IRN" "IRQ" "IRL" "IMN"
   "ISR" "ITA" "JAM" "JPN" "JEY" "JOR" "KAZ" "KEN" "KIR" "PRK" "KOR" "KWT"
   "KGZ" "LAO" "LVA" "LBN" "LSO" "LBR" "LBY" "LIE" "LTU" "LUX" "MAC" "MKD"
   "MDG" "MWI" "MYS" "MDV" "MLI" "MLT" "MHL" "MTQ" "MRT" "MUS" "MYT" "MEX"
   "FSM" "MDA" "MCO" "MNG" "MNE" "MSR" "MAR" "MOZ" "MMR" "NAM" "NRU" "NPL"
   "NLD" "NCL" "NZL" "NIC" "NER" "NGA" "NIU" "NFK" "MNP" "NOR" "OMN" "PAK"
   "PLW" "PSE" "PAN" "PNG" "PRY" "PER" "PHL" "PCN" "POL" "PRT" "PRI" "QAT"
   "REU" "ROU" "RUS" "RWA" "BLM" "SHN" "KNA" "LCA" "MAF" "SPM" "VCT" "WSM"
   "SMR" "STP" "SAU" "SEN" "SRB" "SYC" "SLE" "SGP" "SXM" "SVK" "SVN" "SLB"
   "SOM" "ZAF" "SGS" "SSD" "ESP" "LKA" "SDN" "SUR" "SJM" "SWZ" "SWE" "CHE"
   "SYR" "TWN" "TJK" "TZA" "THA" "TLS" "TGO" "TKL" "TON" "TTO" "TUN" "TUR"
   "TKM" "TCA" "TUV" "UGA" "UKR" "ARE" "GBR" "USA" "UMI" "URY" "UZB" "VUT"
   "VEN" "VNM" "VGB" "VIR" "WLF" "ESH" "YEM" "ZMB" "ZWE"])

(defn- country-sort-fn
  "Finland, Sweden, then the rest in an alphabetical order."
  [a b]
  (cond
    (= (:value a) "FIN") -1
    (= (:value b) "FIN") 1
    (= (:value a) "SWE") -1
    (= (:value b) "SWE") 1
    :else
    (js/util.localeComparator (:text a) (:text b))))

(defn country-items
  "List of `:text`, `:value` pairs where values are country codes (e.g., FIN) and texts
  country names in the current language. The items are sorted: FIN, SWE, ... rest
  alphabetically."
  []
  (->> country-codes
       (map #(hash-map :value %
                       :text (loc (util/kw-path :country %))))
       (sort-by identity country-sort-fn)))

(defn sizeString
  ([file-size-bytes no-of-decimals]
   (js/util.sizeString file-size-bytes no-of-decimals))
  ([file-size-bytes]
   (sizeString file-size-bytes nil)))

(defn make-url
  "Construct an URL including parameters"
  [url params]
  (let [uri (Uri. url)]
    (run! (fn [[k v]]
            (.setParameterValue uri (name k) (if (coll? v)
                                               (ss/join "," v)
                                               v)))
          params)
    (.toString uri)))

(defn find-matching-building
  "Find an application building that matches the document row data.

  Matching order
  1. Both national ID and index match
  2. National ID matches
  3. Index matches

  Returns the matching building or nil."
  [buildings nat-id index-str]
  (let [index (when index-str (ss/trim index-str))]
    (when (or nat-id index)
      (or
        ;; Both nat-id and index match
        (when (and nat-id index)
          (some #(when (and (= (:nationalId %) nat-id)
                            (= (:index %) index))
                   %)
                buildings))
        ;; nat-id matches or no nat-id match just index
        (some #(when (if nat-id
                       (= (:nationalId %) nat-id)
                       (= (:index %) index))
                 %)
              buildings)))))
