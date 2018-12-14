(ns lupapalvelu.pate.verdict-common
  "A common interface for accessing verdicts created with Pate and fetched from the backing system"
  (:require [clojure.set :as set]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-helper :as schema-helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.user :as usr]))

;;
;; Helpers
;;

(defn- get-data
  ([verdict]
   (metadata/unwrap-all (get verdict :data)))
  ([verdict k]
   (metadata/unwrap-all (get-in verdict [:data k]))))

;;
;; Predicates
;;

(defn verdict-state
  "If no state key, the verdict is from backend system and thus
  published."
  [{state :state :as verdict}]
  (when verdict
    (if state
      (keyword (metadata/unwrap state))
      :published)))

(defn lupapiste-verdict?
  "Is the verdict created in Lupapiste, either through Pate or legacy interface"
  [verdict]
  (contains? verdict :category))

(defn has-category? [{:keys [category] :as verdict} c]
  (if (lupapiste-verdict? verdict)
    (util/=as-kw category c)
    false))

(defn contract? [verdict]
  (if (lupapiste-verdict? verdict)
    (or (has-category? verdict :contract)
        (has-category? verdict :migration-contract))
    (-> verdict :sopimus)))

(defn legacy? [verdict]
  (if (lupapiste-verdict? verdict)
    (boolean (:legacy? verdict))
    false)) ;; TODO Better to have type enum: pate legacy backing-system

(defn draft? [verdict]
  (cond
    (lupapiste-verdict? verdict) (not (:published verdict))
    (contains? verdict :draft) (boolean (:draft verdict))
    :else false))

(defn published? [verdict]
  (boolean (and verdict (not (draft? verdict)))))

(defn proposal? [verdict]
  (if (lupapiste-verdict? verdict)
    (util/=as-kw (verdict-state verdict) :proposal)
    false))

;; Maybe not the most useful predicate, maybe clean up later?
(defn verdict-code-is-free-text? [verdict]
  (-> (:category verdict)
      legacy/legacy-verdict-schema
      :dictionary :verdict-code
      (contains? :text)))

;;
;; Accessors
;;

(defn first-pk [verdict]
  (get-in verdict [:paatokset 0 :poytakirjat 0]))

(defn latest-pk
  "Poytakirja with the latest paatospvm"
  [verdict]
  (->> verdict
       :paatokset
       (mapcat :poytakirjat)
       (sort-by :paatospvm)
       last))

(defn last-pk [verdict]
  (-> verdict :paatokset first :poytakirjat last))

(defn replaced-verdict-id
  "Returns the id of the verdict replaced by the given verdict, if any"
  [verdict]
  (if (lupapiste-verdict? verdict)
    (-> verdict :replacement :replaces)
    nil))

(defn- get-paivamaarat [verdict]
  (some-> verdict :paatokset first :paivamaarat))

(defn verdict-date [verdict]
  (cond
    (legacy? verdict)
    (get-data verdict :anto)

    (lupapiste-verdict? verdict)
    (get-data verdict :verdict-date)

    :else
    (or (:paatospvm (latest-pk verdict))
        (:paatosdokumentinPvm (get-paivamaarat verdict)))))

(defn verdict-id [verdict]
  (:id verdict))

(defn verdict-modified [verdict]
  (if (lupapiste-verdict? verdict)
    (:modified verdict)
    (:timestamp verdict)))

(defn verdict-published [verdict]
  (if (lupapiste-verdict? verdict)
    (some-> verdict :published :published)
    ;; The verdicts fetched from the backend system are published. The
    ;; actual publishing timestamp is not known, so we just return the
    ;; modified.
    (verdict-modified verdict)
    ))

(defn verdict-category [verdict]
  (if (lupapiste-verdict? verdict)
    (:category verdict)
    "backing-system")) ;; TODO backing system verdicts don't have category information

(defn verdict-giver [verdict]
  (if (lupapiste-verdict? verdict)
    (let [{:keys [data references template]} verdict]
      (if (util/=as-kw (:giver template) :lautakunta)
       (:boardname references)
       (metadata/unwrap (:handler data))))
    (-> verdict latest-pk :paatoksentekija)))

(defn verdict-municipality-permit-id [verdict]
  (if (lupapiste-verdict? verdict)
    (get-data verdict :kuntalupatunnus)
    (:kuntalupatunnus verdict)))

(defn verdict-signatures [{:keys [signatures] :as verdict}]
  (if (lupapiste-verdict? verdict)
    (some->> signatures
             (map #(select-keys % [:name :date])))
    (some->> signatures
             (map (fn [{:keys [created user]}]
                    {:name    (usr/full-name user)
                     :date created})))))

(defn verdict-section [verdict]
  (if (lupapiste-verdict? verdict)
    (get-data verdict :verdict-section)
    (-> verdict latest-pk :pykala)))

(defn verdict-text [verdict]
  (if (lupapiste-verdict? verdict)
    (if (contract? verdict)
      (get-data verdict :contract-text)
      (get-data verdict :verdict-text))
    (-> verdict last-pk :paatos))) ;; Notice last-pk, this came from bulletins verdict data

(defn verdict-code [verdict]
  (if (lupapiste-verdict? verdict)
    (get-data verdict :verdict-code)
    (some-> verdict first-pk :status str)))

(defn verdict-dates [verdict]
  (if (lupapiste-verdict? verdict)
    (-> verdict
        get-data
        (select-keys [:aloitettava :lainvoimainen :voimassa :anto :raukeamis :valitus :julkipano])
        (assoc :raukeamis nil))
    {:aloitettava   (:aloitettava (get-paivamaarat verdict))
     :anto          (:anto (get-paivamaarat verdict))
     :julkipano     (:julkipano (get-paivamaarat verdict))
     :lainvoimainen (:lainvoimainen (get-paivamaarat verdict))
     :raukeamis     (:raukeamis (get-paivamaarat verdict))
     :valitus       nil
     :voimassa      (:voimassaHetki (get-paivamaarat verdict))}))

;;
;; Lupamaaraykset
;;

(defn get-lupamaaraykset [verdict]
  (some-> verdict :paatokset (get 0) :lupamaaraykset))

(defn- backing->required-review
  "Transforms backing system review requirement to look like Pate's"
  [req-review]
  {:id nil
   :fi (:tarkastuksenTaiKatselmuksenNimi req-review)
   :sv (:tarkastuksenTaiKatselmuksenNimi req-review)
   :en (:tarkastuksenTaiKatselmuksenNimi req-review)
   :type (:katselmuksenLaji req-review)})

(defn- legacy->required-review [[review-id review]]
  {:id review-id
   :fi (:name review)
   :sv (:name review)
   :en (:name review)
   :type (or (schema-helper/review-type-map (-> review :type keyword))
             (schema-helper/review-type-map :ei-tiedossa))})

(defn- pate->required-review [review]
  (update review :type
          #(or (schema-helper/review-type-map (keyword %))
               (schema-helper/review-type-map :ei-tiedossa))))

(defn verdict-required-reviews [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      (->> (get-data verdict :reviews)
           (map legacy->required-review)
           (sort-by :id) ;; This ensures a well defined order after
           (into []))    ;; mapping over hashmap
      (->> (get-data verdict :reviews)
           (map #(util/find-by-id % (-> verdict :references :reviews)))
           (mapv pate->required-review)))
    (mapv backing->required-review
          (-> verdict get-lupamaaraykset :vaaditutKatselmukset))))

(defn- foreman-description [foreman-code]
  (or (schema-helper/foreman-role-map (keyword foreman-code))
      (schema-helper/foreman-role-map nil)))

(defn verdict-required-foremen [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      (->> (get-data verdict :foremen)
           (sort-by first)
           (mapv (comp foreman-description :role second)))
      (mapv foreman-description
            (get-data verdict :foremen)))
    (-> verdict get-lupamaaraykset :vaadittuTyonjohtajatieto)))

(defn- string->required [plan]
  {:id nil
   :fi plan
   :sv plan
   :en plan})

(defn verdict-required-plans [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      []
      (mapv #(util/find-by-id % (-> verdict :references :plans))
            (get-data verdict :plans)))
    (->> verdict get-lupamaaraykset :vaaditutErityissuunnitelmat
         (map string->required))))

(defn verdict-required-conditions [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      (->> (get-data verdict :conditions)
           (sort-by first)
           (mapv (comp :name second)))
      (->> (get-data verdict :conditions)
           (sort-by first)
           (mapv (comp :condition second))))
    (->> verdict get-lupamaaraykset :maaraykset
         (mapv :sisalto))))
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
                #(and (ss/not-blank? %)
                      (not= (first %) \u00a7)) fun))

(defn verdict-summary-signatures [verdict]
  (seq (verdict-signatures verdict)))

(defn verdict-summary-signature-requests [verdict]
  (some->> verdict :signature-requests
           (map #(select-keys % [:name :date]))
           (sort-by :date)
           seq))

(defn- verdict-section-string [verdict]
  (title-fn (verdict-section verdict) #(str "\u00a7" %)))

(defn- lupapiste-verdict-string [lang {:keys [data] :as verdict} dict]
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

(defn verdict-string [lang verdict dict]
  (if (lupapiste-verdict? verdict)
    (lupapiste-verdict-string lang verdict dict)
    (-> verdict latest-pk :paatoskoodi)))

(defn allu-agreement-state [verdict]
  {:pre [(has-category? verdict :allu-contract)]}
  (some-> verdict :data :agreement-state metadata/unwrap))

(defn- verdict-summary-title [verdict lang section-strings]
  (let [id         (verdict-id verdict)
        published  (verdict-published verdict)
        replaces   (replaced-verdict-id verdict)
        proposal?  (util/=as-kw (verdict-state verdict) :proposal)
        rep-string (title-fn replaces
                             (fn [vid]
                               (let [section (get section-strings vid)]
                                 (if (ss/blank? section)
                                   (i18n/localize lang :pate.replacement-verdict)
                                   (i18n/localize-and-fill lang
                                                           :pate.replaces-verdict
                                                           section)))))]
    (->> (cond
           (has-category? verdict :allu-contract)
           [(i18n/localize lang (if (util/=as-kw :final (allu-agreement-state verdict))
                                  :allu.contract
                                  :allu.contract-proposal))]

           (and (contract? verdict) published)
           [(i18n/localize lang :pate.verdict-table.contract)]

           published
           [(get section-strings id)

            (when (lupapiste-verdict? verdict)
              (util/pcond-> (verdict-string lang verdict :verdict-type)
                            ss/not-blank? (str " -")))
            (verdict-string lang verdict :verdict-code)
            rep-string]

           proposal?
           [(i18n/localize lang :pate-verdict-proposal)
            rep-string]

           :else
           [(i18n/localize lang :pate-verdict-draft)
            rep-string])
         (remove ss/blank?)
         (ss/join " "))))

(defn verdict-summary [lang section-strings verdict]
  (->> {:id                 (verdict-id verdict)
        :published          (verdict-published verdict)
        :modified           (verdict-modified verdict)
        :category           (verdict-category verdict)
        :legacy?            (legacy? verdict)
        :giver              (verdict-giver verdict)
        :replaces           (replaced-verdict-id verdict)
        :verdict-date       (verdict-date verdict)
        :title              (verdict-summary-title verdict lang section-strings)
        :signatures         (verdict-summary-signatures verdict)
        :signature-requests (verdict-summary-signature-requests verdict)
        :proposal?          (proposal? verdict)}
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
  {:id                                     sc/Str
   (sc/optional-key :published)            ssc/Timestamp
   :modified                               ssc/Timestamp
   :category                               sc/Str
   :legacy?                                sc/Bool
   (sc/optional-key :giver)                sc/Str
   (sc/optional-key :verdict-date)         ssc/Timestamp
   (sc/optional-key :replaced?)            sc/Bool
   :title                                  sc/Str
   (sc/optional-key :signatures)           [{:name sc/Str
                                             :date ssc/Timestamp}]
   (sc/optional-key :signature-requests)   [{:name sc/Str
                                             :date ssc/Timestamp}]
   (sc/optional-key :proposal?)            sc/Bool})

(defn allowed-category-for-application? [verdict application]
  (or (has-category? verdict (schema-util/application->category application))
      (has-category? verdict :migration-verdict)
      (has-category? verdict :migration-contract)))

(sc/defn ^:always-validate verdict-list :- [VerdictSummary]
  [{:keys [lang application]}]
  (let [category (schema-util/application->category application)
        ;; There could be both contracts and verdicts.
        verdicts        (concat (->> (:pate-verdicts application)
                                     (filter #(allowed-category-for-application? % application))
                                     (map metadata/unwrap-all))
                                ;; TODO: remove draft filter after migration.
                                (some->> application :verdicts
                                         (remove :draft)))
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
                 (sort-by (comp - :modified))))))



;;
;; Work in progress
;;

(defn all-verdicts [application]
  (concat (:pate-verdicts application)
          (:verdicts application)))
