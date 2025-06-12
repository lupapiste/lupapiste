(ns sade.property
  (:require #?(:cljs [goog.string :as goos])
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.municipality :as muni]))

#?(:clj (set! *warn-on-reflection* true))

(def property-id-pattern
  "Regex for property id human readable format"
  #"^(\d{1,3})-(\d{1,3})-(\d{1,4})-(\d{1,4})$")

(def format-str #?(:clj format
                   :cljs goos/format))

(defn to-property-id [human-readable]
  (let [parts (mapv parse-long (rest (re-matches property-id-pattern human-readable)))]
    (apply format-str "%03d%03d%04d%04d" parts)))

(def db-property-id-pattern
  "Regex for splitting db-saved property id to human readable form"
  #"^([0-9]{3})([0-9]{3})([0-9]{4})([0-9]{4})$")

(defn to-human-readable-property-id
  "Returns given `property-id` either in a readable a-b-c-d format or as is, if the
  `property-id` is not in a suitable format."
  [property-id]
  (let [property-id (ss/trim property-id)]
    (or (some->> (ss/blank-as-nil  property-id)
                 (re-matches db-property-id-pattern)
                 rest
                 (map util/->int)
                 (ss/join "-"))
        property-id)))

(defn- take-municipality [[_ s]]
  (let [municipality (ss/zero-pad 3 s)]
    (get muni/municipality-mapping municipality municipality)))

(defn municipality-id-by-property-id [^String property-id]
  (when (and (string? property-id) (> (count property-id) 3))
    (condp re-find (ss/trim property-id)
      property-id-pattern    :>> take-municipality
      db-property-id-pattern :>> take-municipality
      nil)))

(defn ->db-property-id
  "Coerces a (possibly human-readable) property id string to the `db-property-id` format,
   or `nil`."
  [db-or-property-id]
  (let [db-or-property-id (ss/trim db-or-property-id)]
    (when-not (ss/blank? db-or-property-id)
      (cond
        ;; Already ok?
        (re-matches db-property-id-pattern db-or-property-id)
        db-or-property-id
        ;; Coerce if possible
        (re-matches property-id-pattern db-or-property-id)
        (to-property-id db-or-property-id)))))
