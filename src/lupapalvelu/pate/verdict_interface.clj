(ns lupapalvelu.pate.verdict-interface
  "Accessor interface for verdict clien code. This interface should be
  used (and extended) instead of directly accessing application or
  mongo."
  (:require [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn all-verdicts
  "All verdicts regardless of state or origin."
  [{:keys [verdicts pate-verdicts]}]
  (concat verdicts pate-verdicts))

(defn published-kuntalupatunnus
  "Search kuntalupatunnus from backing-system and published legacy
  verdicts. Returns the first one found."
  [{:keys [verdicts pate-verdicts]}]
  ;; Backing system verdict has always kuntalupatunnus
  (or (some-> verdicts first :kuntalupatunnus)
      (some->> pate-verdicts
               (filter vc/published?)
               (filter vc/legacy?)
               (map (util/fn-> :data :kuntalupatunnus metadata/unwrap))
               (remove ss/blank?)
               first)))
