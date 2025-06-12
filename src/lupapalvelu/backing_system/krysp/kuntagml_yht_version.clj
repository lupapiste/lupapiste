(ns lupapalvelu.backing-system.krysp.kuntagml-yht-version
  (:require [lupapalvelu.permit :as permit]
            [lupapalvelu.publish-dates :as pd]
            [sade.core :refer [def-] :as sade]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]
            [schema.core :as sc]))

(def- rakval-yht {"2.1.2" "2.1.0"
                  "2.1.3" "2.1.1"
                  "2.1.4" "2.1.2"
                  "2.1.5" "2.1.3"
                  "2.1.6" "2.1.5"
                  "2.1.8" "2.1.5"
                  "2.2.0" "2.1.6"
                  "2.2.2" "2.1.8"
                  "2.2.3" "2.1.9"
                  "2.2.4" "2.1.9"
                  "2.2.6" "2.1.11"})

(def- ya-yht {"2.1.2" "2.1.0"
              "2.1.3" "2.1.3"
              "2.2.0" "2.1.5"
              "2.2.1" "2.1.6"
              "2.2.3" "2.1.8"
              "2.2.4" "2.1.9"
              "2.2.6" "2.1.11"})

(def- poik-yht {"2.1.2" "2.1.0"
                "2.1.3" "2.1.1"
                "2.1.4" "2.1.2"
                "2.1.5" "2.1.3"
                "2.2.0" "2.1.5"
                "2.2.1" "2.1.6"
                "2.2.3" "2.1.8"
                "2.2.4" "2.1.9"
                "2.2.6" "2.1.11"})

(def- ymp-yht {"2.1.2" "2.1.3"
               "2.2.1" "2.1.6"
               "2.2.3" "2.1.8"
               "2.2.4" "2.1.9"
               "2.2.6" "2.1.11"})

(def- vvvl-yht {"2.1.3" "2.1.3"
                "2.2.1" "2.1.6"
                "2.2.3" "2.1.8"
                "2.2.4" "2.1.9"
                "2.2.6" "2.1.11"})

(def- kt-yht {"0.9"   "2.1.3"
              "0.9.1" "2.1.4"
              "0.9.2" "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.5"
              "1.0.2" "2.1.6"
              "1.0.5" "2.1.8"
              "1.0.6" "2.1.9"
              "1.0.8" "2.1.11"})

(def- mm-yht {"0.9"   "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.6"
              "1.0.3" "2.1.8"
              "1.0.6" "2.1.11"})

(def- yht-version
      {:R rakval-yht
       :P poik-yht
       :YA ya-yht
       :MAL ymp-yht
       :YI ymp-yht
       :YL ymp-yht
       :YM ymp-yht
       :VVVL vvvl-yht
       :KT kt-yht
       :MM mm-yht})

(defn get-yht-version [permit-type ns-version]
  {:pre [(permit/valid-permit-type? (name permit-type))
         ((-> yht-version keys set) (keyword permit-type))]}
  (get-in yht-version [(keyword permit-type) ns-version] "0.0.0"))

(defn match-version?
  "True if the `candidate` version string either matches or is higher than the `target`.
  Version strings are expected to be in the form `\"a.b.c\"`, where the fields `a`,
  `b`, and `c` are integers, but the number of fields does not need to match."
  [target candidate]
  (cond
    ;; Maintain semantics of the earlier version comparison
    (= target candidate) true
    (nil? target)        true
    (nil? candidate)     false
    :else                (let [tparts (util/parse-version target)
                               cparts (util/parse-version candidate)
                               diff   (util/compare-colls tparts cparts)]
                           ;; Target is smaller or equal to candidate? -> matches
                           (<= diff 0))))

(defn effective-permit-type-for-krysp [application]
  (some-> application :permitType util/make-kw
          ;; ARK -> R, due to digitized applications
          (util/pcond-> #{:ARK} ((constantly :R)))))

(defn organization-krysp-version [organization permit-type]
  (get-in organization [:krysp (keyword permit-type) :version]))

(defn application-krysp-version
  ([organization {:keys [submitted] :as application} now]
   (let [permit-type (effective-permit-type-for-krysp application)]
     (when-let [krysp-version-in-db (organization-krysp-version organization permit-type)]
       (if (and (pd/ryhti-2025-permit-type? permit-type)
                (not (env/feature? :ignore-kuntagml-publish-date))
                (date/after? now pd/ryhti-2025-publish-date)
                (match-version? "2.2.6" krysp-version-in-db)
                submitted
                (date/before? submitted pd/ryhti-2025-publish-date)
                (-> organization :force-kuntagml-226-for-pre-2025 not))
         "2.2.4"
         krysp-version-in-db))))
  ([organization application]
   (application-krysp-version organization application (sade/now))))

(sc/defschema KryspVersionString
  (sc/constrained sc/Str #(re-matches #"^\d+\.\d+\.\d+(-dmcity-lp|-lp)?$" %) "Krysp Version String"))

(def version-checker (sc/checker KryspVersionString))

(defn valid-krysp-version-string?
  "Returns true if version is a valid KRYSP version string"
  [version]
  (nil? (version-checker version)))
