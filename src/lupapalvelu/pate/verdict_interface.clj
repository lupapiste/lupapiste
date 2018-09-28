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

(defn verdict-date
  "Verdict date from latest verdict"
  [{:keys [verdicts] :as application}]
  (let [legacy-ts (some->> verdicts
                           (map (fn [{:keys [paatokset]}]
                                  (map (fn [pt] (map (fn [pk] (get (second pk) :paatospvm (get pk :paatospvm))) (:poytakirjat pt))) paatokset)))
                           (flatten)
                           (remove nil?)
                           (sort)
                           (last))
        pate-ts   (get-in (verdict/latest-published-pate-verdict {:application application}) [:data :verdict-date])]
    (or legacy-ts pate-ts)))

(defn lainvoimainen-date
  "Lainvoimainen date from latest verdict"
  [{:keys [verdicts] :as application}]
  (let [legacy-ts (some->> verdicts
                           (map (fn [{:keys [paatokset]}]
                                  (->> (map #(get-in % [:paivamaarat :lainvoimainen]) paatokset)
                                       (remove nil?)
                                       (first))))
                           (flatten)
                           (remove nil?)
                           (sort)
                           (last))
        pate-ts   (get-in (verdict/latest-published-pate-verdict {:application application}) [:data :lainvoimainen])]
      (or legacy-ts pate-ts)))