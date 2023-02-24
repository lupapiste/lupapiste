(ns lupapalvelu.pate.verdict-interface
  "Accessor interface for verdict client code. This interface should be
  used (and extended) instead of directly accessing application or
  mongo."
  (:require [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-helper :as schema-helper]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- bs-verdict-date
  "Backing system verdict date (from :paivamaarat map). Note that
  backing system verdicts are always published."
  [verdicts key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (->> (map #(get-in % [:paivamaarat (keyword key)]) paatokset)
                   (remove nil?)
                   (first))))
       (flatten)
       (remove nil?)
       (sort)
       (last)))

(defn- bs-verdict-data
  "Backing system verdict accessor. If there are multiple verdicts,
  returns the first non-nil value for the given key."
  [verdicts key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map key (:poytakirjat pt))) paatokset)))
       (flatten)
       (remove nil?)
       (first)))

(defn all-verdicts
  "All verdicts regardless of state or origin. Unwraps all metadata."
  [{:keys [verdicts pate-verdicts]}]
  (concat verdicts (metadata/unwrap-all pate-verdicts)))

(defn find-verdict
  "Find a verdict by id."
  [application verdict-id]
  (util/find-by-id verdict-id (all-verdicts application)))

(defn verdicts-by-backend-id
  "All verdicts filtered by backend Id."
  [application backendId]
  (->> (all-verdicts application)
       (filter #(= (vc/verdict-municipality-permit-id %)
                   backendId))
       not-empty))

(defn published-kuntalupatunnus
  "Search kuntalupatunnus from backing-system and published legacy
  verdicts. Returns the first one found."
  [{:keys [verdicts pate-verdicts]}]
  ;; Backing system verdict has always kuntalupatunnus
  (or (some-> verdicts first vc/verdict-municipality-permit-id)
      (some->> pate-verdicts
               (filter vc/published?)
               (filter vc/legacy?)
               (map vc/verdict-municipality-permit-id)
               (remove ss/blank?)
               first)))

(defn kuntalupatunnukset
  "Search all backendIds from legacy verdicts and pate verdicts."
  [application]
  (->> (all-verdicts application)
       (map vc/verdict-municipality-permit-id)
       (filter ss/not-blank?)
       distinct))

(defn published-municipality-permit-ids
  "Return the municipality permit ids (aka backend ids, backing system
  ids, kuntalupatunnukset) from all published verdicts."
  [application]
  (->> (all-verdicts application)
       (filter vc/published?)
       (map vc/verdict-municipality-permit-id)
       distinct))

(defn latest-published-verdict-date
  "The latest verdict date (timestamp) of the published application
  verdicts. The first argument is either an application or a list
  of (any kind of) verdicts."
  ([application-or-verdicts]
   (latest-published-verdict-date application-or-verdicts identity))
  ([application-or-verdicts post-process]
   (let [verdicts (if (map? application-or-verdicts)
                    (all-verdicts application-or-verdicts)
                    (metadata/unwrap-all application-or-verdicts))]
     (some->> verdicts
              (filter vc/published?)
              (remove vc/replaced?)
              (map vc/verdict-date)
              (filter integer?)
              seq
              (apply max)
              post-process))))

(defn latest-published-pate-verdict
  "Returns unwrapped published Pate verdict (or nil). If there are
  multiple Pate verdicts, the one with the latest published timestamp
  is returned."
  [{:keys [application]}]
  (some->> (:pate-verdicts application)
           (filter :published)
           (remove vc/replaced?)
           (sort-by (comp :published :published))
           last
           metadata/unwrap-all))

(defn published-verdicts
  "All published (and not replaced) verdicts sorted by published timestamp (ascending)."
  [{:keys [application]}]
  (some->> (all-verdicts application)
           (filter vc/published?)
           (remove vc/replaced?)
           (sort-by vc/verdict-published)))

(defn latest-published-verdict
  "The latest published (and not replaced) verdict according to `vc/verdict-published.`"
  [command]
  (last (published-verdicts command)))

(defn earliest-published-verdict
    "The earliest published (and not replaced) verdict according to `vc/verdict-published.`"
  [command]
  (first (published-verdicts command)))

(defn verdict-negative?
  "Returns boolean or nil (if no verdict code)."
  [verdict]
  (when-let [code (vc/verdict-code verdict)]
    (schema-helper/verdict-code-negative? code)))

(defn handler
  "Resolution:
  1. Pate verdicts: handler for the latest published verdict
  2. Backing system verdicts: first paatoksentekija found."
  [{:keys [verdicts] :as application}]
  (get-in (latest-published-pate-verdict {:application application}) [:data :handler]
          (bs-verdict-data verdicts :paatoksentekija)))

(defn verdict-giver
  "Resolution (differs a bit from `vc/verdict-giver`):
  1. Pate verdicts: verdict giver for the latest published verdict
  2. Backing system verdicts: first paatoksentekija found."
  [{:keys [verdicts] :as application}]
  (or
   (vc/verdict-giver (latest-published-pate-verdict {:application application}))
   (bs-verdict-data verdicts :paatoksentekija)))

(defn lainvoimainen
  "Get lainvoimainen date. Takes optional date formatter as parameter."
  ([application]
    (lainvoimainen application nil))
  ([{:keys [verdicts pate-verdicts] :as application} post-process]
   (let [ts (if (some? pate-verdicts)
              (get-in (latest-published-pate-verdict {:application application})
                      [:data :lainvoimainen])
              (bs-verdict-date verdicts :lainvoimainen))]
     (if post-process
       (post-process ts)
       ts))))
