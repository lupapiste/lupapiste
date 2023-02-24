(ns lupapalvelu.test-util
  (:require [clojure.test :refer [is]]
            [clojure.walk :as walk]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]])
  (:import [clojure.lang ExceptionInfo]
           [java.lang AssertionError]
           [java.time.temporal ChronoUnit]
           [midje.data.metaconstant Metaconstant]))

(sc/defschema MidjeMetaconstant (sc/pred (comp #{Metaconstant} type) "Midje metaconstant"))

(defn replace-in-schema [schema replaceable replacing]
  (walk/postwalk (fn [subschema] (if (= replaceable subschema) replacing subschema)) schema))

(defmacro catch-all
  "Macro for catching all exceptions and returning the thrown value if any.
   Otherwise evaluates to the result of the given expression. Useful for testing
   functions that use throw+ in generative test.check tests.

   If `some-expr` is an expression that does not throw+ anything, catch-all acts
   like an identity function:

     (catch-all some-expr) ;=> some-expr

   If, on the other hand, the given expression uses throw+, then the thrown value
   will be returned.

     (catch-all (throw+ something)) ;=> something"
  [& body]
  `(try+ ~@body
         (catch ~any? error#
           error#)))

(def passing-quick-check
  "A value for checking if a clojure.test.check/quick-check passed, to be used in
   midje facts as the right hand side of an arrow.

   (fact (quick-check 100 some-test-check-property) => passing-quick-check)"
  (just {:result true,
         :pass? true
         :time-elapsed-ms (every-pred integer? pos?)
         :num-tests (every-pred integer? pos?)
         :seed anything}))


(defn xml-datetime-is-roughly?
  "Check if two xml datetimes are roughly the same. Default interval 60 000ms (1 min)."
  [^String date1 ^String date2 & [interval]]
  (let [dt1 (date/zoned-date-time date1)
        dt2 (date/zoned-date-time date2)]
    (<= (Math/abs (.until dt1 dt2 ChronoUnit/MILLIS))
        (or interval 60000))))

(defn dummy-doc [schema-name]
  (let [schema (schemas/get-schema (schemas/get-latest-schema-version) schema-name)
        data   (tools/create-document-data schema (partial tools/dummy-values nil))]
    {:schema-info (:info schema)
     :data        data
     :id          (mongo/create-id)}))

(defmacro assert-validation-error
  "Catches schema validation error and checks that validation is failed in right place."
  [schema-path & body]
  `(try ~@body
        (is false "Did not throw validation error!")
        (catch ExceptionInfo e#
          (is (get-in (.getData e#) [:error ~@schema-path])
              (str "No validation error with schema path " ~schema-path "!")))))

(defmacro assert-assertion-error
  "Catches assertion error and check that there is right parameter name in error message.
  Convenient to use in test-check test to check that pre-check is triggered."
  [param-name & body]
  `(try ~@body
        (is false "Did not throw assertion error!")
        (catch AssertionError e#
          (is (->> (.getMessage e#) (re-matches (re-pattern (str ".*" (name ~param-name) ".*"))))
              (str "Cannot find param name \"" (name ~param-name) "\" in error message!")))))

; FIXME: this is a temporary arrangement due to missing translations.
; It should be the case that test-languages = i18n/languages
(def test-languages (remove #{:en :sv} i18n/languages))

(defn walk-dissoc-keys
  "dissoc given keys from the collection"
  [coll & keys]
  (walk/postwalk #(if (map? %)
                    (apply dissoc % keys)
                    %)
                 coll))

(defn ok? [resp]
  (= (:ok resp) true))

(def fail? (complement ok?))

(defn not-in-text
  "Test passes that every string in `xs` is NOT FOUND in `text`."
  [text & xs]
  (doseq [x xs]
    (fact {:midje/description (str "Must NOT include " x)}
      text =not=> (contains x))))

(defn in-text
  "Test passes if `xs` items match `text`. The matching rules:
   string -> must be found in text
  [string1 string2] -> strings must not be found in text"
  [text & xs]
  (doseq [x xs]
    (if (sequential? x)
      (apply not-in-text text x)
      (fact {:midje/description (str "Must include " x)}
        text => (contains x)))))

(def strong-password "1234QWERasdfZXCV%&/(tyui")
