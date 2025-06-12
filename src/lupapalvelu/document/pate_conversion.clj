(ns lupapalvelu.document.pate-conversion
  "Convert legacy documents schemas to PATE schemas"
  (:require [sade.strings :as ss]
            [clojure.set :as set]))

; TODO: select, repeating
(def docgen-type-to-pate-type
  {:string :text
   :checkbox :toggle})

(defn sort-by-to-pate-sort [docgen-sort]
  (case docgen-sort
    :displayname :text))

(defn build-select [{:keys [body i18nkey] :as schema}]
  {:select (-> (merge {:items      (map :name body)
                       :loc-prefix (keyword i18nkey)}
                      (select-keys schema [:other-key :sortBy]))
               (update :sortBy sort-by-to-pate-sort)
               (set/rename-keys {:sortBy :sort-by}))})

(defn schema-to-pate-type [schema-part]
  (case (:type schema-part)
    :select (build-select schema-part)
    {(get docgen-type-to-pate-type (:type schema-part) (:type schema-part)) (select-keys schema-part [:i18nkey])}))

(defn schema-to-body-item
  "Recursively converts schema to key-value vectors.
  Leaf data (schema item) is converted from docgen format to PATE format."
  [item]
  (if (= :group (:type item))
    [(:name item) (map schema-to-body-item (:body item))]
    [(:name item) (merge
                    (dissoc item :name :type :required :body :sortBy :other-key :i18nkey)
                    (schema-to-pate-type item)
                    (when (:required item)
                      {:required? true}))]))

(defn paths-to-schema-leafs
  "Flattens paths build by schema-to-body-item to sequence of [k v] pairs where
  path to leaf is the key and leaf data (schema item) is the value."
  [path item]
  (if (map? (second item))                                  ; leaf
    {(keyword (str (ss/join "-" path) "-" (first item))) (second item)}
    (mapcat #(paths-to-schema-leafs (conj path (first item)) %) (second item))))

(defn schema-to-pate-dict
  "Converts schema to PATE dictionary format"               ; see examples below
  [schema]
  ; TODO validate against Dictionary schema
  (into {} (paths-to-schema-leafs [] (schema-to-body-item schema))))


;;  Examples & Playground

(comment


  ; For exapmle: (schema-to-pate-dict lupapalvelu.document.schemas/verkostoliittymat)
  ; =>
  ; {"verkostoliittymat-viemariKytkin" {:i18nkey "viemariKytkin", :checkbox {}},
  ; "verkostoliittymat-vesijohtoKytkin" {:i18nkey "vesijohtoKytkin", :checkbox {}},
  ; "verkostoliittymat-sahkoKytkin" {:i18nkey "sahkoKytkin", :checkbox {}},
  ; "verkostoliittymat-maakaasuKytkin" {:i18nkey "maakaasuKytkin", :checkbox {}},
  ; "verkostoliittymat-kaapeliKytkin" {:i18nkey "kaapeliKytkin", :checkbox {}}}

{:dictionary {:henkilo-userId {:personSelector {} :blacklist [:neighbor] :excludeCompanies true}
              :henkilo-henkilotiedot-etunimi {:text {} :required? true}
              :henkilo-henkilotiedot-sukunimi {:text {} :required? true}
              :henkilo-henkilotiedot-hetu {:text {} :required? true}
              :henkilo-henkilotiedot-turvakieltoKytkin {:toggle {}}}
 :sections [{}]}

  {"data.huoneistot.0.lammitystapa.value" "foo"}
)
