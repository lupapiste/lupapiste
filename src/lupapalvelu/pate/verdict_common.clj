(ns lupapalvelu.pate.verdict-common
  "A common interface for accessing verdicts created with Pate and fetched from the backing system"
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]))

;;
;; Predicates
;;

(defn has-category? [{:keys [category]} c]
  (util/=as-kw category c))

(defn contract? [verdict]
  (has-category? verdict :contract))

(defn legacy? [verdict]
  (boolean (:legacy? verdict)))


;;
;; Accessors
;;

(defn replaced-verdict-id
  "Returns the id of the verdict replaced by the given verdict, if any"
  [verdict]
  (-> verdict :replacement :replaces))

(defn verdict-date [verdict]
  (-> verdict :data :verdict-date))

(defn verdict-id [verdict]
  (:id verdict))

(defn verdict-published [verdict]
  (:published verdict))

(defn verdict-modified [verdict]
  (:modified verdict))

(defn verdict-category [verdict]
  (:category verdict))

(defn verdict-giver [{:keys [data references template]}]
  (if (util/=as-kw (:giver template) :lautakunta)
    (:boardname references)
    (:handler data)))
;;
;; Verdict schema
;;

(defn select-inclusions [dictionary inclusions]
  (->> (map util/split-kw-path inclusions)
       (reduce (fn [acc [x & xs]]
                 (update acc
                         x
                         (fn [v]
                           (if (seq xs)
                             (conj v (util/kw-path xs))
                             []))))
               {})
       (reduce-kv (fn [acc k v]
                    (let [dic-value (k dictionary)]
                      (assoc acc
                             k
                             (if (seq v)
                               (assoc dic-value
                                      :repeating (select-inclusions
                                                  (-> dictionary k :repeating)
                                                  v))
                               dic-value))))
                  {})))

(defn verdict-schema [{:keys [category schema-version legacy? template]}]
  (update (if legacy?
            (legacy/legacy-verdict-schema category)
            (verdict-schemas/verdict-schema category schema-version))
          :dictionary
          #(select-inclusions % (map keyword (:inclusions template)))))

;;
;; Verdict summary and list
;;

(defn- title-fn [s fun]
  (util/pcond-> (-> s ss/->plain-string ss/trim)
                ss/not-blank? fun))

(defn verdict-summary-signatures [{:keys [data]}]
  (some->> data :signatures vals
           (map #(select-keys % [:name :date]))
           (sort-by :date)
           seq))

(defn- verdict-section-string [{data :data}]
  (title-fn (:verdict-section data) #(str "\u00a7" %)))

(defn- verdict-string [lang {:keys [data] :as verdict} dict]
  (title-fn (dict data)
            (fn [value]
              (when-let [{:keys [reference-list
                                 select
                                 text]} (some-> (verdict-schema verdict)
                                                :dictionary
                                                dict)]
                (if text
                  value
                  (i18n/localize lang
                                 (:loc-prefix (or reference-list
                                                  select))
                                 value))))))

(defn- verdict-summary-title [verdict lang section-strings]
  (let [id (verdict-id verdict)
        published (verdict-published verdict)
        replaces (replaced-verdict-id verdict)
        rep-string (title-fn replaces
                             (fn [vid]
                               (let [section (get section-strings vid)]
                                 (if (ss/blank? section)
                                   (i18n/localize lang :pate.replacement-verdict)
                                   (i18n/localize-and-fill lang
                                                           :pate.replaces-verdict
                                                           section)))))]
    (->> (cond
           (and (contract? verdict) published)
           [(i18n/localize lang :pate.verdict-table.contract)]

           published
           [(get section-strings id)
            (util/pcond-> (verdict-string lang verdict :verdict-type)
                          ss/not-blank? (str " -"))
            (verdict-string lang verdict :verdict-code)
            rep-string]

           :else
           [(i18n/localize lang :pate-verdict-draft)
            rep-string])
         (remove ss/blank?)
         (ss/join " "))))

(defn verdict-summary [lang section-strings verdict]
  (->> {:id           (verdict-id verdict)
        :published    (verdict-published verdict)
        :modified     (verdict-modified verdict)
        :category     (verdict-category verdict)
        :legacy?      (legacy? verdict)
        :giver        (verdict-giver verdict)
        :replaces     (replaced-verdict-id verdict)
        :verdict-date (verdict-date verdict)
        :title        (verdict-summary-title verdict lang section-strings)
        :signatures   (verdict-summary-signatures verdict)}
       (util/filter-map-by-val some?)))

(defn- section-strings-by-id [verdicts]
  (->> verdicts
       (map (juxt verdict-id verdict-section-string))
       (into {})))

(defn- summaries-by-id [verdicts lang]
  (let [section-strings (section-strings-by-id verdicts)]
    (->> verdicts
         (map (juxt verdict-id #(verdict-summary lang section-strings %)))
         (into {}))))

(defn- add-chain-of-replacements-to-result
  "Adds to result the summary of the given verdict along with the
  chain of replacements. For example, if the given verdict X replaced
  verdict Y, which replaced verdict Z, then X Y and Z would all be
  added."
  [result verdict summaries]
  (concat result
          (loop [{:keys [replaces] :as v} (assoc verdict :replaced? false)
                 sub []]
            (if replaces
              (recur (assoc (get summaries replaces)
                            :replaced? true)
                     (conj sub (-> v (dissoc :replaces))))
              (conj sub (dissoc v :replaces))))))

(sc/defschema VerdictSummary
  {:id           sc/Str
   (sc/optional-key :published)    ssc/Timestamp
   :modified                       ssc/Timestamp
   :category                       sc/Str
   :legacy?                        sc/Bool
   :giver                          sc/Str
   (sc/optional-key :verdict-date) ssc/Timestamp
   :replaced?                      sc/Bool
   :title                          sc/Str
   (sc/optional-key :signatures)   [{:name sc/Str
                                     :date ssc/Timestamp}]})

(sc/defn ^:always-validate verdict-list :- [VerdictSummary]
  [{:keys [lang application]}]
  (let [category (schema-util/application->category application)
        ;; There could be both contracts and verdicts.
        verdicts        (filter #(has-category? % category)
                                (:pate-verdicts application))
        summaries       (summaries-by-id verdicts lang)
        replaced-verdict-ids (->> (vals summaries)
                                  (map :replaces)
                                  (remove nil?)
                                  set)]
    (reduce (fn [result verdict]
              (if (contains? replaced-verdict-ids
                             (verdict-id verdict))
                result
                (add-chain-of-replacements-to-result result verdict summaries)))
            []
            (->> (vals summaries)
                 (sort-by (comp - verdict-modified))))))

;;
;; Work in progress
;;

(defn all-verdicts [application]
  (concat (:pate-verdicts application)
          (:verdicts application)))
