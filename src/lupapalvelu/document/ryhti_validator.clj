(ns lupapalvelu.document.ryhti-validator
  "Ryhti-specific validators, specifically for `rakennuskohteen-osa`."
  (:require [lupapalvelu.document.all-schemas]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.shared-tools :as shared-tools]
            [lupapalvelu.document.validator :refer [defvalidator]]
            [sade.strings :as ss]
            [sade.util :as util]))

(def ryhti-schemas
  "Schemas with `rakennuskohteen-osa` field."
  (->> (schemas/get-schemas 1)
       (keep (fn [[schema-name schema]]
               (when (seq (shared-tools/walk-schema schema (fn [{:keys [element]}]
                                                             {:result (util/=as-kw (:name element) :rakennuskohteen-osa)})))
                 schema-name)))
       (into #{})))

(defn- is-duplicate?
  [items k]
  (let [fr (->> items
                vals
                (map k)
                (remove ss/blank?)
                frequencies)]
    (boolean (some (fn [[_k v]] (< 1 v)) fr))))

;; Ryhti schemas have several tables, where each row contains a selector. The
;; user shouldn't choose the same value for more than one row. Ideally we'd like
;; to represent this uniqueness constraint in the schema itself, but we don't have
;; the tools for it yet. As a quick fix, implement individual field validators by hand.

(defvalidator :unique-verkostoliittymatyyppi
  {:doc     "Each `verkostoliittymat` must have unique `verkostoliittymatyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat]]
   :facts   {:ok   [[{:0 {:verkostoliittymatyyppi "kaasu"}
                      :1 {:verkostoliittymatyyppi "sahko"}
                      :2 {:verkostoliittymatyyppi ""}}]]
             :fail [[{:0 {:verkostoliittymatyyppi "kaasu"}
                      :1 {:verkostoliittymatyyppi "sahko"}
                      :2 {:verkostoliittymatyyppi "kaasu"}}]]}}
  (is-duplicate? items :verkostoliittymatyyppi))

(defvalidator :unique-ilmanvaihtotavan-laji
  {:doc     "Each `ilmanvaihtotavan-laji` must have unique `ilmanvaihtotyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :tekniset-tiedot :ilmanvaihtotavan-laji]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :ilmanvaihtotyyppi))

(defvalidator :unique-varustetyyppi
  {:doc     "Each `varuste` must have unique `varustetyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :tekniset-tiedot :varuste]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :varustetyyppi))

(defvalidator :unique-lammonlahdetyyppi
  {:doc     "Each `lammonlahde` must have unique `lammonlahdetyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :energiatiedot :lammonlahde]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :lammonlahdetyyppi))

(defvalidator :unique-lammitystapatyyppi
  {:doc     "Each `lammitystapa` must have unique `lammitystapatyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :energiatiedot :lammitystapa]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :lammitystapatyyppi))

(defvalidator :unique-jaahdytysenergianlahdetyyppi
  {:doc     "Each `jaahdytysenergianlahde` must have unique `jaahdytysenergianlahdetyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :energiatiedot :jaahdytysenergianlahde]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :jaahdytysenergianlahdetyyppi))

(defvalidator :unique-jaahdytystapatyyppi
  {:doc     "Each `jaahdytysenergianlahde` must have unique `jaahdytystapatyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :energiatiedot :jaahdytystapa]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :jaahdytystapatyyppi))

(defvalidator :unique-sahkoenergianlahdetyyppi
  {:doc     "Each `sahkoenergianlahde` must have unique `sahkoenergianlahdetyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :energiatiedot :sahkoenergianlahde]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :sahkoenergianlahdetyyppi))

(defvalidator :unique-kayttotarkoitustyyppi
  {:doc     "Each `kayttotarkoitus` must have unique `tyyppi`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :kayttotiedot :kayttotarkoitus]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :tyyppi))

(defvalidator :unique-rakennusaine
  {:doc     "Each `kantavaRakennusaine` must have unique `rakennusaine`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :materiaalitiedot :kantavaRakennusaine]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :rakennusaine))

(defvalidator :unique-julkisivumateriaali
  {:doc     "Each `julkisivu` must have unique `julkisivumateriaali`."
   :schemas ryhti-schemas
   :fields  [items [:rakennuskohteen-osa :materiaalitiedot :julkisivu]]
   :facts   {:ok   []
             :fail []}}
  (is-duplicate? items :julkisivumateriaali))
