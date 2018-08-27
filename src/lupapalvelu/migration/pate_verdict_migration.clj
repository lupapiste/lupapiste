(ns lupapalvelu.migration.pate-verdict-migration
  (:require [clojure.walk :refer [postwalk]]
            [sade.core :refer [def-]]
            [sade.util :as util]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-util :as schema-util]))

;;
;; Post-walk related helpers
;;

;; The type exists only as an implemention detail for `accessor-key?`

(defn- access [accessor-key]
  {::access accessor-key})

(defn- accessor-key? [x]
  (boolean (::access x)))

(defn- wrap-accessor
  "Wraps accessor function with metadata/wrap"
  [timestamp]
  (fn [accessor]
    (fn [& args]
      (some->> (apply accessor args)
               (metadata/wrap "Verdict draft Pate migration" timestamp)))))


;;
;; Helpers for accessing relevant data from current verdicts
;;

(defn- get-in-verdict [path]
  (fn [_ verdict]
    (get-in verdict path)))

(defn- get-in-poytakirja [key]
  (get-in-verdict (conj [:paatokset 0 :poytakirjat 0] key)))

(defn get-in-paivamaarat [key]
  (get-in-verdict (conj [:paatokset 0 :paivamaarat] key)))

(def- wrapper-accessors
  "Contains functions for accessing relevant Pate verdict data from
  current verdict drafts. These return the raw values but are
  subsequently to be wrapped with relevant metadata."
  {:handler         (get-in-poytakirja :paatoksentekija)
   :kuntalupatunnus (get-in-verdict [:kuntalupatunnus])
   :verdict-section (get-in-poytakirja :pykala)
   :verdict-code    (comp str (get-in-poytakirja :status))
   :verdict-text    (get-in-poytakirja :paatos)
   :anto            (get-in-paivamaarat :anto)
   :lainvoimainen   (get-in-paivamaarat :lainvoimainen)
   :reviews         (constantly "TODO")
   :foremen         (constantly "TODO")
   :conditions      (constantly "TODO")})

(def- non-wrapper-accessors
  "Contains accessor funtions whose values are not wrapped in metadata."
  {:id       (get-in-verdict [:id])
   :modified (get-in-verdict [:timestamp])
   :user     (constantly "TODO")
   :category (fn [application _] (schema-util/application->category application))
   })

(defn- accessors [timestamp]
  (merge (util/map-values (wrap-accessor timestamp)
                          wrapper-accessors)
         non-wrapper-accessors))

(defn- fetch-with-accessor
  "Given the `application` under migration, the source `verdict` and
  current `timestamp`, returns a function for accessing desired data
  from the `application` and `verdict`. Used with `postwalk`."
  [application verdict timestamp]
  (let [accessor-functions (accessors timestamp)]
    (fn [x]
      (if (accessor-key? x)
        ((get accessor-functions (::access x)) application verdict)
        x))))


;;
;; Core migration functionality
;;

(def verdict-migration-skeleton
  "This map describes the shape of the migrated verdict. When building the
   migrated verdict, `(access :x)` will be replaced by calling the accessor
   function found under the key :x in the accessor function map. See `accessors`."
  {:id       (access :id)
   :modified (access :modified)
   :user     (access :user)
   :category (access :category)
   :data {:handler         (access :handler)
          :kuntalupatunnus (access :kuntalupatunnus)
          :verdict-section (access :verdict-section)
          :verdict-code    (access :verdict-code)
          :verdict-text    (access :verdict-text)
          :anto            (access :anto)
          :lainvoimainen   (access :lainvoimainen)
          :reviews         (access :reviews)
          :foremen         (access :foremen)
          :conditions      (access :conditions)}
   :template "TODO"
   :legacy? true})

(defn ->pate-legacy-verdict [application verdict timestamp]
  (-> (postwalk (fetch-with-accessor application
                                     verdict
                                     timestamp)
                verdict-migration-skeleton)
      util/strip-nils))
