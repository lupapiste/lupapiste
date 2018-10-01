(ns lupapalvelu.pate.verdict-interface
  "Accessor interface for verdict clien code. This interface should be
  used (and extended) instead of directly accessing application or
  mongo."
  (:require [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- legacy-date [verdicts key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (->> (map #(get-in % [:paivamaarat (keyword key)]) paatokset)
                   (remove nil?)
                   (first))))
       (flatten)
       (remove nil?)
       (sort)
       (last)))

(defn- legacy-data [verdicts key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map key (:poytakirjat pt))) paatokset)))
       (flatten)
       (remove nil?)
       (first)))

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

(defn kuntalupatunnukset
  "Search all backendIds form legacy verdicts and pate verdicts."
  [{:keys [verdicts pate-verdicts]}]
  (or (some->> verdicts
               (map :kuntalupatunnus)
               (remove nil?))
      (some->> pate-verdicts
               (map (util/fn-> :data :kuntalupatunnus metadata/unwrap))
               (remove ss/blank?))))

(defn verdict-date
  "Verdict date from latest verdict"
  ([application]
    (verdict-date application nil))
  ([{:keys [verdicts] :as application} post-process]
   (let [legacy-ts (some->> verdicts
                            (map (fn [{:keys [paatokset]}]
                                   (map (fn [pt] (map (fn [pk] (get (second pk) :paatospvm (get pk :paatospvm))) (:poytakirjat pt))) paatokset)))
                            (flatten)
                            (remove nil?)
                            (sort)
                            (last))
         pate-ts   (get-in (verdict/latest-published-pate-verdict {:application application}) [:data :verdict-date])
         ts        (or legacy-ts pate-ts)]
     (if post-process
       (post-process ts)
       ts))))

(defn handler
  "Get verdict handler."
  [{:keys [verdicts pate-verdicts] :as application}]
  (if (some? pate-verdicts)
    (get-in (verdict/latest-published-pate-verdict {:application application}) [:data :handler])
    (legacy-data verdicts :paatoksentekija)))

(defn lainvoimainen
  "Get lainvoimainen date. Takes optional date formatter as parameter."
  ([application]
    (lainvoimainen application nil))
  ([{:keys [verdicts pate-verdicts] :as application} post-process]
   (let [ts (if (some? pate-verdicts)
                 (get-in (verdict/latest-published-pate-verdict {:application application}) [:data :lainvoimainen])
                 (legacy-date verdicts :lainvoimainen))]
     (if post-process
       (post-process ts)
       ts))))
