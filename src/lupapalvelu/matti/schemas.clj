(ns lupapalvelu.matti.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :refer [body] :as tools]
            [lupapalvelu.matti.shared :as shared]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

;; identifier - KuntaGML-paatoskoodi (yhteiset.xsd)
(def verdict-code-map
  {:annettu-lausunto            "annettu lausunto"
   :asiakirjat-palautettu       "asiakirjat palautettu korjauskehotuksin"
   :ehdollinen                  "ehdollinen"
   :ei-lausuntoa                "ei lausuntoa"
   :ei-puollettu                "ei puollettu"
   :ei-tiedossa                 "ei tiedossa"
   :ei-tutkittu-1               "ei tutkittu"
   :ei-tutkittu-2               "ei tutkittu (oikaisuvaatimusvaatimus tai lupa pysyy puollettuna)"
   :ei-tutkittu-3               "ei tutkittu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"
   :evatty                      "ev\u00e4tty"
   :hallintopakko               "hallintopakon tai uhkasakkoasian k\u00e4sittely lopetettu"
   :hyvaksytty                  "hyv\u00e4ksytty"
   :ilmoitus-tiedoksi           "ilmoitus merkitty tiedoksi"
   :konversio                   "muutettu toimenpideluvaksi (konversio)"
   :lautakunta-palauttanut      "asia palautettu uudelleen valmisteltavaksi"
   :lautakunta-poistanut        "asia poistettu esityslistalta"
   :lautakunta-poydalle         "asia pantu p\u00f6yd\u00e4lle kokouksessa"
   :maarays-peruutettu          "m\u00e4\u00e4r\u00e4ys peruutettu"
   :muutti-evatyksi             "muutti ev\u00e4tyksi"
   :muutti-maaraysta            "muutti m\u00e4\u00e4r\u00e4yst\u00e4 tai p\u00e4\u00e4t\u00f6st\u00e4"
   :muutti-myonnetyksi          "muutti my\u00f6nnetyksi"
   :myonnetty                   "my\u00f6nnetty"
   :myonnetty-aloitusoikeudella "my\u00f6nnetty aloitusoikeudella "
   :osittain-myonnetty          "osittain my\u00f6nnetty"
   :peruutettu                  "peruutettu"
   :puollettu                   "puollettu"
   :pysytti-evattyna            "pysytti ev\u00e4ttyn\u00e4"
   :pysytti-maarayksen-2        "pysytti m\u00e4\u00e4r\u00e4yksen tai p\u00e4\u00e4t\u00f6ksen"
   :pysytti-myonnettyna         "pysytti my\u00f6nnettyn\u00e4"
   :pysytti-osittain            "pysytti osittain my\u00f6nnettyn\u00e4"
   :siirretty-maaoikeudelle     "siirretty maaoikeudelle"
   :suunnitelmat-tarkastettu    "suunnitelmat tarkastettu"
   :tehty-hallintopakkopaatos-1 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (ei velvoitetta)"
   :tehty-hallintopakkopaatos-2 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (asetettu velvoite)"
   :tehty-uhkasakkopaatos       "tehty uhkasakkop\u00e4\u00e4t\u00f6s"
   :tyohon-ehto                 "ty\u00f6h\u00f6n liittyy ehto"
   :valituksesta-luovuttu-1     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
   :valituksesta-luovuttu-2     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"})

(def review-type-map
  {:muu-katselmus             "muu katselmus"
   :muu-tarkastus             "muu tarkastus"
   :aloituskokous             "aloituskokous"
   :paikan-merkitseminen      "rakennuksen paikan merkitseminen"
   :paikan-tarkastaminen      "rakennuksen paikan tarkastaminen"
   :pohjakatselmus            "pohjakatselmus"
   :rakennekatselmus          "rakennekatselmus"
   :lvi-katselmus             "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
   :osittainen-loppukatselmus "osittainen loppukatselmus"
   :loppukatselmus            "loppukatselmus"
   :ei-tiedossa               "ei tiedossa"
   })

(def matti-string {:name "matti-string"
                   :type :string})

(def verdict-text {:name "matti-verdict-text"
                   :type :text})

(def verdict-giver {:name "matti-verdict-giver"
                    :type :select
                    :body [{:name "viranhaltija"}
                           {:name "lautakunta"}]})

(def automatic-vs-manual {:name "automatic-vs-manual"
                          :type :radioGroup
                          :label false
                          :body  [{:name "automatic"}
                                  {:name "manual"}]})

(def verdict-check {:name "matti-verdict-check"
                    :type :checkbox})

(defschema MattiSettingsReview
  {:id      ssc/ObjectIdStr
   :name    {:fi sc/Str
             :sv sc/Str
             :en sc/Str}
   :type    (apply sc/enum (keys review-type-map))
   :deleted sc/Bool})

(defschema MattiSavedSettings
  {:id sc/Str
   :modified ssc/Timestamp
   :draft sc/Any
   :reviews [MattiSettingsReview]})

(defschema MattiSavedTemplate
  {:id       ssc/ObjectIdStr
   :name     sc/Str
   :category (sc/enum :r :p :ya :kt :ymp)
   :deleted  sc/Bool
   :draft    sc/Any ;; draft is copied to version data on publish.
   :modified ssc/Timestamp
   :versions [{:id        ssc/ObjectIdStr
               :published ssc/Timestamp
               :data      sc/Any
               :settings  MattiSavedSettings}]})

(doc-schemas/defschemas 1
  (map (fn [m]
         {:info {:name (:name m)}
          :body (body m)})
       [matti-string verdict-text verdict-check
        verdict-giver automatic-vs-manual]))

;; Schema utils

(defn- resolve-path-schema
  "Resolution result is map with two fixed keys (:path, :data) and one
  depending on the schema type (:schema, :docgen, :reference-list)."
  [data xs]
  (let [path         (mapv keyword xs)
        docgen       (some-> data :schema :docgen)
        reflist      (some-> data :schema :reference-list)
        date-delta   (:date-delta data)
        multi-select (some-> data :schema :multi-select)
        wrap         (fn [id schema data]
                       {id {:schema schema
                            :path   path
                            :data   data}})]
    (cond
      (:grid data) (wrap :section shared/MattiSection data)
      docgen       (wrap :docgen (doc-schemas/get-schema {:name docgen}) docgen)
      date-delta   (wrap :date-delta shared/MattiDateDelta date-delta)
      reflist      (wrap :reference-list shared/MattiReferenceList reflist)
      multi-select (wrap :multi-select shared/MattiMultiSelect multi-select))))

(defn- schema-array
  "[key value] if successful."
  [data]
  (when-let [m (not-empty (select-keys data [:sections :rows :row :items]))]
    (assert (= (count m) 1))
    (first m)))

(defn- resolve-index [xs x]
  (if (re-matches #"^\d+$" x)
    (util/->int x)
    (first (keep-indexed (fn [i data]
                           (when (= x (or (:id data) (:id (first data))))
                             i))
                         xs))))

(defn schema-data
  "Data is the reference schema instance (e.g.,
  shared/default-verdict-template). The second argument is path into
  data. Returns map with remaining path and resolved schema (see
  resolve-path-schema)."
  [data [x & xs :as path]]
  (if (nil? xs)
    (resolve-path-schema data path)
    (let [x      (name x)
          [k v]  (schema-array data)
          grid   (get-in data [:grid :rows])
          list   (get-in data [:list :items])
          schema (:schema data)
          id     (:id data)
          id-ok? (or (nil? id) (= (:id data) x))]
      (cond
        (sequential? data)  (let [i    (resolve-index data x)
                                  item (and i (nth data i))]
                              (when item
                                (schema-data item xs)))
        (and id-ok? k)      (schema-data v path)
        grid                (schema-data grid path)
        (and id-ok? list)   (schema-data list path)
        (and id-ok? schema) (schema-data schema xs)))))

(defmulti validate-resolution :type)

(defmethod validate-resolution :default
  [_]
  :error.invalid-value-path)

(defn schema-error [{:keys [schema path value schema-overrides]}]
  (when (sc/check (st/get-in (get schema-overrides :type schema) path) value)
    :error.invalid-value))

(defmethod validate-resolution :section
  [{:keys [path schema value] :as options}]
  (cond
    (coll? value) :error.invalid-value
    :else (schema-error options)))

(defmethod validate-resolution :date-delta
  [{:keys [path schema value] :as options}]
  (schema-error options))

(defmethod validate-resolution :docgen
  [_]
  ;; TODO: Use the old-school docgen validation if possible
  )

(defn check-items [items data-items]
  (let [v-set (set items)
        d-set (set data-items)]
    (cond
      (not= (count items) (count v-set)) :error.duplicate-items
      (not (set/subset? v-set d-set))    :error.invalid-items)))

(defmethod validate-resolution :multi-select
  [{:keys [path schema data value] :as options}]
  (or (schema-error options)
      (check-items value (:items data))))

(defmethod validate-resolution :reference-list
  [{:keys [path schema data value references] :as options}]
  (or (schema-error options)
      (check-items value (get-in references (:path data)))))

(defn validate-path-value
  "Error message if not valid, nil otherwise."
  [schema-instance data-path value & [{:keys [references schema-overrides]}]]
  (let [resolution (schema-data schema-instance data-path)]
    (validate-resolution (assoc  (some-> resolution vals first)
                                 :type (some-> resolution keys first)
                                 :value value
                                 :references references
                                 :schema-overrides schema-overrides))))
