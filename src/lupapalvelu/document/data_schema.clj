(ns lupapalvelu.document.data-schema
  (:require [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [sade.schemas :as ssc]
            [schema.core :as sc]
            [lupapalvelu.document.schemas :as doc-schemas]))

(defmulti coerce-type :type)

(defmulti coerce-subtype :subtype)

(defn- coerce-group-schema [{force-values ::force-values :as elem-schema}]
  (->> (:body elem-schema)
       (map (fn-> (util/assoc-when ::force-values force-values) coerce-type))
       (into {})))

(defn coerce-doc [doc-schema]
  {:id          ssc/ObjectIdStr
   :schema-info (sc/eq (:info doc-schema))
   :created     ssc/Timestamp
   :data        (coerce-group-schema doc-schema)})

(defn doc-data-schema
  ([doc-name]                      (doc-data-schema doc-name nil))
  ([doc-name force-values]         (doc-data-schema doc-name force-values nil))
  ([doc-name force-values version] (-> (cond-> {:name doc-name}
                                         version (assoc :version version))
                                       doc-schemas/get-schema
                                       (util/assoc-when ::force-values force-values)
                                       coerce-doc)))

(defn- data-leaf [{required :required force-values ::force-values :as elem-schema} value-schema]
  (if force-values ;; force-values forces all values to match value-schema
    {(keyword (:name elem-schema)) {:value    value-schema
                                    :modified ssc/Timestamp}}
    (let [elem-key (cond-> (keyword (:name elem-schema))
                     (not required) sc/optional-key)]
      {elem-key
       (sc/if (comp nil? :value)
         {:value    (sc/eq nil)}
         {:value    value-schema
          :modified ssc/Timestamp})})))

(defn- data-group [{repeating :repeating :as elem-schema}]
  (let [elem-key (keyword (:name elem-schema))
        elem-val (coerce-group-schema elem-schema)]
    (if repeating
      {elem-key {ssc/Nat elem-val}}
      {elem-key elem-val})))

(defmethod coerce-subtype :number [{min-value :min max-value :max :as elem-schema}]
  (data-leaf elem-schema (ssc/min-max-valued-integer-string min-value max-value)))

(defmethod coerce-subtype :decimal [{min-value :min max-value :max :as elem-schema}]
  (data-leaf elem-schema (ssc/min-max-valued-decimal-string min-value max-value)))

(defmethod coerce-subtype :digit [elem-schema]
  (data-leaf elem-schema ssc/Digit))

(defmethod coerce-subtype :letter [{case :case :as elem-schema}]
  (data-leaf elem-schema (cond (= :upper case) ssc/UpperCaseLetter (= :lower case) ssc/LowerCaseLetter :else ssc/Letter)))

(defmethod coerce-subtype :tel [elem-schema]
  ;(data-leaf elem-schema ssc/Tel)
    (data-leaf elem-schema sc/Str))

(defmethod coerce-subtype :email [elem-schema]
  (data-leaf elem-schema ssc/Email))

(defmethod coerce-subtype :rakennustunnus [elem-schema]
  (data-leaf elem-schema ssc/Rakennustunnus))

(defmethod coerce-subtype :rakennusnumero [elem-schema]
  (data-leaf elem-schema ssc/Rakennusnumero))

(defmethod coerce-subtype :kiinteistotunnus [elem-schema]
  (data-leaf elem-schema ssc/Kiinteistotunnus))

(defmethod coerce-subtype :y-tunnus [elem-schema]
  (data-leaf elem-schema ssc/FinnishY))

(defmethod coerce-subtype :ovt [elem-schema]
  (data-leaf elem-schema ssc/FinnishOVTid))

(defmethod coerce-subtype :vrk-name [elem-schema]
  (data-leaf elem-schema sc/Str))

(defmethod coerce-subtype :vrk-address [elem-schema]
  (data-leaf elem-schema sc/Str))

(defmethod coerce-subtype nil [elem-schema]
  (data-leaf elem-schema sc/Str))

(defmethod coerce-subtype :recent-year [elem-schema]
  (data-leaf elem-schema (ssc/min-max-valued-integer-string 1950 2015)))

(defmethod coerce-type :string [elem-schema]
  (coerce-subtype elem-schema))

(defmethod coerce-type :text [elem-schema]
  (data-leaf elem-schema sc/Str))

(defmethod coerce-type :select [{other-key :other-key :as elem-schema}]
  (data-leaf elem-schema (apply sc/enum (cond-> (mapv :name (:body elem-schema))
                                          other-key (conj :muu)))))

(defmethod coerce-type :hetu [elem-schema]
  (data-leaf elem-schema ssc/Hetu))

(defmethod coerce-type :date [elem-schema]
  (data-leaf elem-schema (ssc/date-string "dd.MM.yyyy")))

(defmethod coerce-type :time [elem-schema]
  (data-leaf elem-schema ssc/TimeString))

(defmethod coerce-type :maaraalaTunnus [elem-schema]
  (data-leaf elem-schema ssc/Maaraalatunnus))

(defmethod coerce-type :foremanHistory [elem-schema]
  {:value (sc/eq nil)})

(defmethod coerce-type :fillMyInfoButton [elem-schema]
  {:value (sc/eq nil)})

(defmethod coerce-type :calculation [elem-schema]
  {:value (sc/eq nil)})

(defmethod coerce-type :personSelector [elem-schema]
  (data-leaf elem-schema ssc/ObjectIdStr))

(defmethod coerce-type :companySelector [elem-schema]
  (data-leaf elem-schema ssc/ObjectIdStr))

(defmethod coerce-type :buildingSelector [elem-schema]
  {(keyword (:name elem-schema)) (sc/if (comp nil? :value)
                                   {:value    (sc/eq nil)}
                                   {:value    ssc/Rakennusnumero
                                    :source   (sc/enum "krysp" nil)
                                    (sc/optional-key :sourceValue) ssc/Rakennusnumero
                                    :modified ssc/Timestamp})})

(defmethod coerce-type :newBuildingSelector [elem-schema]
  (data-leaf elem-schema (sc/if ss/numeric? ssc/NatString (sc/eq "ei tiedossa"))))

(defmethod coerce-type :linkPermitSelector [elem-schema]
  (data-leaf elem-schema sc/Str))

(defmethod coerce-type :radioGroup [elem-schema]
  (data-leaf elem-schema (apply sc/enum (map :name (:body elem-schema)))))

(defmethod coerce-type :checkbox [elem-schema]
  (data-leaf elem-schema sc/Bool))

(defmethod coerce-type :table [elem-schema]
  (data-group (assoc elem-schema :repeating true)))

(defmethod coerce-type :group [elem-schema]
  (data-group elem-schema))
