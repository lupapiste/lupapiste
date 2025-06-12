(ns lupapalvelu.publish-dates
  (:require [sade.date :as date]
            [sade.env :as env]))

(def ryhti-2025-publish-date
  "First set of Ryhti features are published in 2025"
  (date/zoned-date-time (or (env/value :ryhti-2025-publish-date)
                            "1.1.2025")))

(def bim-data-source-date
  "User may choose between traditional document-based data or BIM-model"
  (date/zoned-date-time "1.1.2026"))

(def ryhti-2025-permit-type?
  "Only certain permit types should be coerced to KuntaGML 2.2.6"
  #{:R :P :YA :MAL :VVVL :YI :YM :YL})

(defn pre-ryhti-2025-application?
  "Returns truthy if the given application should be handled as if it was submitted before 2025.
  This includes linked permits such as continuation permits for pre-2025 apps even if the continuation permit
  itself was submitted post-2024."
  [{:keys [submitted pre-ryhti-2025-link-permit created] {operation :name} :primaryOperation}]
  (or pre-ryhti-2025-link-permit
      (and (nil? submitted)
           (= operation "aiemmalla-luvalla-hakeminen") ; paper permit; probably submitted pre-2025
           (some-> created (date/before? ryhti-2025-publish-date))) ; not perfect but should grab the majority of cases
      (some-> submitted ; just in case the original app is a draft or something
              (date/before? ryhti-2025-publish-date))))

(defn pre-ryhti-2025-link-permit?
  "Returns truthy if the given application is a type of link permit that should follow the base application
  wrt. the ryhti-2025 rules above.
  This includes change-, continuation-, and encumbrance permits"
  [{:keys [permitSubtype operation-name]}]
  (or (some-> permitSubtype name (= "muutoslupa"))
      (#{"raktyo-aloit-loppuunsaat"
         "rasite-tai-yhteisjarjestely"} operation-name)))
