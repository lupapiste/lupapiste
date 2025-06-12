(ns lupapalvelu.asiointi.control-case
  "Ryhti defines a _building control case_ concept (`RakennusvalvontaAsianTyyppi`),
  which is similar to Lupapiste permit types. In other words, it is a way to group
  similar construction projects, and is reflected in the Ryhti APIs.

  In Lupapiste we
  - expand the building control cases supported by Ryhti with our own custom control cases
  - map our existing permit types and operations into these control cases
  - control cases are visible in the AsiointiAPI interface"
  (:require [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [schema.core :refer [defschema] :as sc]))

(defschema ControlCaseConfig
  {;; Does the control case contain buildings (and apartments)?
   (sc/optional-key ::building-ids?)      sc/Bool
   ;; How does the control case map back to `RakennusvalvontaAsianTyyppi`
   (sc/optional-key ::ryhti-control-case) ryhti-mappings/BuildingControlCase})

(defschema ControlCaseConfigMap {sc/Keyword ControlCaseConfig})

(def control-cases
  (->> {;; 3.2 Rakentamislupa
        :ryhti/building              {::building-ids?      true
                                      ::ryhti-control-case :building}
        ;; 3.3 Poikkeamislupa
        :ryhti/deviation             {::ryhti-control-case :deviation}
        ;; 3.4 Maisematyölupa
        :ryhti/landscape-work        {::ryhti-control-case :landscape-work}
        ;; 3.5 Purkamislupa
        :ryhti/demolition            {::ryhti-control-case :demolition}

        ;; NOTE: At the moment neither building easement nor demolition notice exists in Ryhti.
        ;; We cannot fetch permanent identifiers (PLTs) or validate such applications.

        ;; 3.6 Rakennusrasite
        :lupapiste/building-easement {}
        ;; 3.7 Purkamisilmoitus
        :lupapiste/demolition-notice {}}
       (sc/validate ControlCaseConfigMap)))

(def ControlCase (apply sc/enum (keys control-cases)))

(sc/defn has-building-and-apartment-ids? :- sc/Bool
  [control-case :- ControlCase]
  (-> (get control-cases control-case)
      ::building-ids?
      boolean))

(sc/defn control-case->ryhti-control-case :- (sc/maybe ryhti-mappings/BuildingControlCase)
  [control-case :- ControlCase]
  (-> (get control-cases control-case)
      ::ryhti-control-case))

(sc/defn supports-permanent-permit-id? :- sc/Bool
  "Ryhti can provide permanent permit IDs (PLT) for some control cases"
  [control-case :- ControlCase]
  (some? (control-case->ryhti-control-case control-case)))

(sc/defn control-case->notification-control-case :- sc/Keyword
  [control-case :- ControlCase]
  (keyword (name control-case)))
