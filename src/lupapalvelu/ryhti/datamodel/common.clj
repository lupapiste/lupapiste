(ns lupapalvelu.ryhti.datamodel.common
  (:require [lupapalvelu.i18n :as i18n]
            [sade.date :as date]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(def always-nil (constantly nil))
(def empty-collection (constantly []))

(defn string->number
  ([v _]
   (string->number v))
  ([^String v]
   (when (and v (seq v))
     (Integer/parseInt v))))

(defn string->double
  ([v _]
   (string->double v))
  ([^String v]
   (when (and v (seq v))
     (Double/parseDouble (ss/replace v #"," ".")))))

(defn ->iso-date
  "Format some date value (see sade.date/zoned-date-time for supported types) to ISO date string"
  ([x _]
   (->iso-date x))
  ([x]
   (let [x (cond-> x
             (and (map? x)
                  (:_value x)) :_value
             (string? x)       ss/blank-as-nil)]
     (when x
       (date/iso-date x :local)))))

(defn ->iso-datetime
  "Format some date value (see sade.date/zoned-date-time for supported types) to ISO datetime string"
  ([x _]
   (->iso-datetime x))
  ([x]
   ;; TODO: Can we give these with offset?
   (date/iso-datetime x)))

(defn loc->language-string [create-loc-key]
  (fn [x _]
    (when x
      {:fin (i18n/localize :fi (create-loc-key x))
       :swe (i18n/localize :sv (create-loc-key x))
       :eng (i18n/localize :en (create-loc-key x))})))

(defn unicode-trim
  "Trim whitespaces from the beginning and end of the string.

  This is uncode aware and also works with \u00A0, i.e. non-breaking space,
  which isn't dropped by regular trim fn."
  ([x _]
   (unicode-trim x))
  ([x]
   (-> x
       (ss/replace #"^\h+" "")
       (ss/replace #"\h+$" ""))))

(defn missing-uuid
  "Use for Ryhti keys that we don't have on Lupapiste model yet.
  Remove after LPK-8245."
  []
  (str (random-uuid)))

(defn point-geometry [[x y]]
  {:geometry {:type        "Point"
              :coordinates [x y]}
   :srid     "3067"})

;; NOTE: Right now the :ryhti and :lupapiste paths are built
;; for Lupapiste -> Ryhti case. It isn't clear if the same paths
;; are useful for Ryhti -> Lupapiste direction, or if we need two pairs (four)
;; paths in some or all cases?

(defn apply-mapping
  [mapping {:keys [from to transform] :as context}]
  (reduce (fn [acc m]
            (let [to-path   (to m)
                  from-path (from m)
                  f         (get m transform (fn identity-2 [a _] a))]
              ;; Ignore with empty to path
              (if to-path
                (let [new-value (f (get-in context from-path) context)
                      new-value (if (some? new-value)
                                  new-value
                                  (:default m))]
                  (if (or (some? new-value)
                          (:nilable? m))
                    (assoc-in acc to-path new-value)
                    ;; Omit `nil`s, unless target is `nilable?`.
                    ;; TODO: would be better to consult the schema
                    acc))
                acc)))
          {}
          mapping))

(defn required-filled
  "Given a `mapping` return a function for checking all the required fields are filled-in.
  Blank strings are considered not filled-in."
  [mapping]
  (if-let [fields (->> mapping
                       (filter :required?)
                       (map (comp first :ryhti))
                       seq)]
    (let [preds (map (fn [field]
                       (fn [entity]
                         (let [value (get entity field)]
                           (if (string? value)
                             (some? (ss/blank-as-nil value))
                             (some? value)))))
                     fields)]
      (apply every-pred preds))
    (constantly true)))

;; NOTE: For documentation, not used currently.
(def TransformContext
  [:map
   ;; Options
   [:from :keyword]
   [:to :keyword]
   [:->ryhti {:optional true} :fn]

   ;; Base data (for Lupapiste -> Ryhti)
   [:application :any]

   ;; Added by recursive mappings
   [:rakennuskohde {:optional true} :any]
   [:rakennuspaikka {:optional true} :any]])

;; NOTES:
;; - Fields on the mapping vectors are written in the same order as on the tietomallit.pdf
;; - There is a comment before the mapping vector to refer to the tietomallit.pdf header.
