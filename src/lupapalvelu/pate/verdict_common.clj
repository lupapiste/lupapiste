(ns lupapalvelu.pate.verdict-common
  "A common interface for accessing verdicts created with Pate and fetched from the backing system"
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.allu.allu-application :refer [allu-application?]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-helper :as schema-helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :refer [PateVerdict]]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pate.verdict-util :as pate-vutil]
            [lupapalvelu.user :as usr]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

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

(defn legacy? [verdict]
  (if (lupapiste-verdict? verdict)
    (boolean (:legacy? verdict))
    false))

(defn has-category? [{:keys [category] :as verdict} c]
  (if (lupapiste-verdict? verdict)
    (util/=as-kw category c)
    false))

(defn verdict-giver-type
  "Returns giver *type*, either :viranhaltija or :lautakunta
  Will return nil for pre-RYHTI legacy verdicts"
  [{:keys [template] :as verdict}]
  (and (lupapiste-verdict? verdict)
       (keyword (or (when (legacy? verdict)
                      (get-data verdict :giver))
                    (-> template :giver)))))

(defn board-verdict?
  "True if the verdict giver is board. The giver type is selected in
  the verdict template."
  [verdict]
  (boolean (and (lupapiste-verdict? verdict)
                (util/=as-kw (verdict-giver-type verdict) :lautakunta))))

(defn contract? [verdict]
  (if (lupapiste-verdict? verdict)
    (or (has-category? verdict :contract)
        (has-category? verdict :migration-contract))
    (-> verdict :sopimus)))

(defn rectification-verdict? [verdict]
  (has-category? verdict :oik))

(defn kuntagml-verdict?
  "Returns true if the given verdict is supported by KuntaGML"
  [verdict]
  (-> verdict rectification-verdict? not))

(defn draft? [verdict]
  (cond
    (lupapiste-verdict? verdict) (not (:published verdict))
    (contains? verdict :draft) (boolean (:draft verdict))
    :else false))

(defn published? [verdict]
  (boolean (and verdict (not (draft? verdict)))))

(defn replaced-by
  [verdict]
  (some-> verdict :replacement :replaced-by))

(defn replaced?
  "Only Pate verdicts can be replaced."
  [verdict]
  (boolean (replaced-by verdict)))

(defn proposal? [verdict]
  (if (lupapiste-verdict? verdict)
    (util/=as-kw (verdict-state verdict) :proposal)
    false))

(defn publishing-verdict? [verdict]
  (contains? schema-helper/publishing-states-as-keywords (verdict-state verdict)))

;; Maybe not the most useful predicate, maybe clean up later?
(defn verdict-code-is-free-text? [verdict]
  (if (util/=as-kw :oik (:category verdict))
    false ; no legacy schema to check; is not free text
    (-> (:category verdict)
        legacy/legacy-verdict-schema
        :dictionary :verdict-code
        (contains? :text))))

;;
;; Accessors
;;

(defn selected-attachment-ids
  "For modern Pate verdicts returns a list of attachment ids. Each id
  exists and the attachment has been selected from the application
  when the verdict has been prepared. For other type of verdicts
  returns empty."
  [attachments verdict]
  (if-let [ids (some-> verdict :data :selected-attachments not-empty)]
    (let [all-ids (or (some->> attachments (map :id) set) #{})]
      (filter all-ids ids))
    []))

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

(defn replaced-verdict-id
  "Returns the id of the verdict replaced by the given verdict, if any.

   Note: This only works for the latest replacement verdict as the replaced
         verdicts only have the `:replaced-by` field!"
  [verdict]
  (when (lupapiste-verdict? verdict)
    (-> verdict :replacement :replaces)))

(defn- get-paivamaarat [verdict]
  (some-> verdict :paatokset first :paivamaarat))

(defn verdict-date [verdict]
  (cond
    (and (contract? verdict) (legacy? verdict))
    (or (get-data verdict :verdict-date)
        (get-data verdict :anto))

    (contract? verdict)
    (get-data verdict :verdict-date)

    (legacy? verdict)
    (or (get-data verdict :anto)
        (get-data verdict :k-verdict-date))

    (lupapiste-verdict? verdict)
    (get-data verdict :verdict-date)

    :else
    (or (:paatospvm (latest-pk verdict))
        (:paatosdokumentinPvm (get-paivamaarat verdict)))))

(defn lainvoimainen-date [verdict]
  (or (get-data verdict :lainvoimainen)
      (get-data verdict :k-lainvoimainen)))

(defn aloitettava-date [verdict]
  (or (get-data verdict :aloitettava)
      (get-data verdict :k-aloitettava)))

(defn voimassa-date [verdict]
  (or (get-data verdict :voimassa)
      (get-data verdict :k-voimassa)))

(defn bulletin-publish-date [verdict]
  (or (get-data verdict :julkipano)
      (get-data verdict :k-kuulutus)))

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
    (verdict-modified verdict)))

(defn responsibilities-start-date [verdict]
  (when (lupapiste-verdict? verdict) ;; Verdicts from backend don't have this field
    (get-data verdict :responsibilities-start-date)))

(defn verdict-category [verdict]
  (if (lupapiste-verdict? verdict)
    (:category verdict)
    "backing-system")) ;; TODO backing system verdicts don't have category information

(defn verdict-giver
  "Verdict giver resolution:
  1. Contract: giver (aka the contract signer) except handler for legacy verdicts
  2. Legacy verdict: giver (name or full name), handler if non-RYHTI verdict
  3. Pate verdict: giver through field-code fn, default case is return the giver code
     Can be modified with loc-item-fn to return other data, see verdict-giver-name.
  4. Pate verdict (viranhaltija): combined first-name + last-name from direct fields
  5. Pate verdict (lautakunta): boardname if non-legacy lautakunta verdict
  6. Pate verdict: handler (fallback)
  7. Backing system verdict: paatoksentekija from the poytakirja with
     the latest paatospvm"
  ([verdict loc-item-fn]
   (let [{:keys [references
                 data]}     verdict
         field              #(some-> data % metadata/unwrap
                                     ss/trim ss/blank-as-nil)
         field-code         (fn [field-k ref-k]
                              (when-let [v (field field-k)]
                                (or (loc-item-fn (pate-vutil/loc-item verdict ref-k v))
                                    v)))
         first-name         (field :giver-first-name)
         last-name          (field :giver-last-name)]
     (cond
       (contract? verdict) (if (legacy? verdict)
                             (field :handler)
                             (field :giver))

       (legacy? verdict)
       (if (field :giver) ; RYHTI verdicts have giver as separate field from handler
         (if (board-verdict? verdict)
           (field :giver-name)
           (ss/trim-join " " (field :giver-first-name) (field :giver-last-name)))
         (field :handler))

       (lupapiste-verdict? verdict)
       (or
         (field-code :giver :verdict-givers)
         (when-not (board-verdict? verdict)
           (ss/trim-join " " first-name last-name))
         (when (board-verdict? verdict)
           (:boardname references))
         (field :handler))

       :else
       (-> verdict latest-pk :paatoksentekija))))
  ([verdict]
   (verdict-giver verdict :code)))

(defn resolve-verdict-giver
  "Resolves the verdict-giver from references if :data.giver is an ID reference.
   Returns the verdict-giver map with :first-name, :last-name etc, or nil if not found."
  [verdict]
  (let [unwrapped-verdict (metadata/unwrap-all verdict)
        giver-value       (get-in unwrapped-verdict [:data :giver])
        verdict-givers    (get-in unwrapped-verdict [:references :verdict-givers])]
    (when (and giver-value verdict-givers)
      ;; giver-value could be an ID reference or a direct value
      (pate-vutil/loc-item unwrapped-verdict :verdict-givers giver-value))))

(defn decision-maker-first-name
  "Returns the decision maker's first name for viranhaltija verdicts.
   Returns nil for board verdicts."
  [verdict]
  (when-not (board-verdict? verdict)
    (let [resolved-giver (resolve-verdict-giver verdict)]
      (or (ss/blank-as-nil (:first-name resolved-giver))
          (some-> (get-in verdict [:data :giver-first-name]) metadata/unwrap ss/blank-as-nil)))))

(defn decision-maker-name
  "Returns the decision maker's name. For board verdicts returns the board name (fi).
   For viranhaltija verdicts returns the last name."
  [verdict]
  (let [resolved-giver (resolve-verdict-giver verdict)]
    (if (board-verdict? verdict)
      ;; Current integration only has path for :fin localization.
      (or (ss/blank-as-nil (:fi resolved-giver))
          (get-in verdict [:references :boardname])
          (some-> (get-in verdict [:data :giver]) metadata/unwrap ss/blank-as-nil))
      (or (ss/blank-as-nil (:last-name resolved-giver))
          (some-> (get-in verdict [:data :giver-last-name]) metadata/unwrap ss/blank-as-nil)))))

(defn verdict-municipality-permit-id [verdict]
  (ss/blank-as-nil (if (lupapiste-verdict? verdict)
                     (get-data verdict :kuntalupatunnus)
                     (:kuntalupatunnus verdict))))

(defn verdict-signatures [{:keys [signatures] :as verdict}]
  (if (lupapiste-verdict? verdict)
    (some->> signatures
             (map #(select-keys % [:name :date])))
    (some->> signatures
             (map (fn [{:keys [created user]}]
                    {:name    (usr/full-name user)
                     :date created})))))

(defn verdict-section [verdict]
  (some-> (if (lupapiste-verdict? verdict)
            (-> (get-data verdict :verdict-section))
            (-> verdict latest-pk :pykala))
          str
          ss/trim
          ss/blank-as-nil))

(defn verdict-text [verdict]
  (if (lupapiste-verdict? verdict)
    (if (contract? verdict)
      (get-data verdict :contract-text)
      (get-data verdict :verdict-text))
    (-> verdict latest-pk :paatos)))

(defn verdict-code [verdict]
  (when verdict
    (if (lupapiste-verdict? verdict)
      (get-data verdict :verdict-code)
      (let [pk (latest-pk verdict)]
        (some-> (or (:status pk) (:paatoskoodi pk)) str)))))

(defn verdict-dates [verdict]
  (if (lupapiste-verdict? verdict)
    (-> verdict
        get-data
        (select-keys (schema-helper/all-verdict-dates (:category verdict))))
    (-> (get-paivamaarat verdict)
        (rename-keys {:voimassaHetki :voimassa})
        (select-keys [:aloitettava :anto :julkipano :lainvoimainen :raukeamis :voimassa]))))

(defn verdict-attachment-id [verdict]
  (if (lupapiste-verdict? verdict)
    (some-> verdict :published :attachment-id)
    (when-let [pk (some-> verdict latest-pk)]
      ;; The explicit `attachment-id` is found only in the newer verdicts.
      (or (:attachment-id pk) (:urlHash pk)))))

;;
;; Lupamaaraykset
;;

(defn get-lupamaaraykset [verdict]
  (some-> verdict :paatokset first :lupamaaraykset))

(defn- backing->required-review
  "Transforms backing system review requirement to look like Pate's. Review type also works
  as a fallback for the review name."
  [{r-name :tarkastuksenTaiKatselmuksenNimi r-type :katselmuksenLaji}]
  (let [r-name (or (ss/blank-as-nil r-name) r-type)]
    {:fi   r-name
     :sv   r-name
     :en   r-name
     :type r-type}))

(defn- legacy->required-review [verdict-category [review-id review]]
  {:id review-id
   :fi (:name review)
   :sv (:name review)
   :en (:name review)
   :type (condp = (schema-util/lowkeyword verdict-category)
           :ya (schema-helper/ya-review-type-map (-> review :type keyword))
           (or (schema-helper/review-type-map (-> review :type keyword))
               (schema-helper/review-type-map :ei-tiedossa)))})

(defn- pate->required-review [verdict-category review]
  (update review :type
          (condp = (schema-util/lowkeyword verdict-category)
            :ya #(schema-helper/ya-review-type-map (keyword %))
            #(or (schema-helper/review-type-map (keyword %))
                 (schema-helper/review-type-map :ei-tiedossa)))))

(defn verdict-required-reviews [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      (->> (get-data verdict :reviews)
           (map (partial legacy->required-review (:category verdict)))
           (sort-by :id) ;; This ensures a well defined order after
           (into []))    ;; mapping over hashmap
      (when (get-data verdict :reviews-included)
        (->> (get-data verdict :reviews)
             (map #(util/find-by-id % (-> verdict :references :reviews)))
             (mapv (partial pate->required-review (:category verdict))))))
    (mapv backing->required-review
          (-> verdict get-lupamaaraykset :vaaditutKatselmukset))))

(defn- foreman-description [foreman-code]
  (schema-helper/foreman-role foreman-code))

(defn verdict-required-foremen [verdict]
  (cond
    (legacy? verdict)
    (->> (get-data verdict :foremen)
         (sort-by first)
         (mapv (comp :role second)))

    (lupapiste-verdict? verdict)
    (when (get-data verdict :foremen-included)
      (mapv foreman-description
            (get-data verdict :foremen)))

    :else
    (-> verdict get-lupamaaraykset :vaaditutTyonjohtajat (ss/split #"\s*,\s*"))))

(defn- string->required [plan]
  {:id nil
   :fi plan
   :sv plan
   :en plan})

(defn verdict-required-plans [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      []
      (when (get-data verdict :plans-included)
        (mapv #(util/find-by-id % (-> verdict :references :plans))
              (get-data verdict :plans))))
    (->> verdict get-lupamaaraykset :vaaditutErityissuunnitelmat
         (map string->required))))

(defn- condition-fn
  [content-key deadline-key]
  (fn [source-condition]
    (util/assoc-when
      {:content (get source-condition content-key)}
      :deadline (some->> deadline-key
                         (get source-condition)))))

(sc/defn verdict-required-conditions :- [{:content                    ssc/NonBlankStr
                                          (sc/optional-key :deadline) sc/Int}]
  [verdict]
  (let [conditions (if (lupapiste-verdict? verdict)
                     (if (legacy? verdict)
                       (->> (get-data verdict :conditions)
                            (sort-by first)
                            (map (comp (condition-fn :name nil) second)))
                       (->> (get-data verdict :conditions)
                            (sort-by first)
                            (map (comp (condition-fn :condition :deadline) second))))
                     (->> verdict get-lupamaaraykset :maaraykset
                          (map (condition-fn :sisalto :maaraysPvm))))]
    (->> conditions
         ss/trimwalk
         util/strip-blanks
         (filter :content))))

(def ^:private no-parking-space-requirements
  {:autopaikkojaEnintaan nil
   :autopaikkojaVahintaan nil
   :autopaikkojaRakennettava nil
   :autopaikkojaRakennettu nil
   :autopaikkojaKiinteistolla nil
   :autopaikkojaUlkopuolella nil})

(defn- legacy->parking-space-requirements [verdict]
  no-parking-space-requirements)

(defn- sum-building-values [buildings k]
  (when-let [values (seq (keep k (vals buildings)))]
    (->> values (map util/->int) (apply +))))

(defn- pate->parking-space-requirements [verdict]
  (let [buildings (get-data verdict :buildings)]
    {:autopaikkojaEnintaan      (sum-building-values buildings :autopaikat-enintaan)
     :autopaikkojaVahintaan     (sum-building-values buildings :autopaikat-vahintaan)
     :autopaikkojaRakennettava  (sum-building-values buildings :autopaikat-rakennettava)
     :autopaikkojaRakennettu    (sum-building-values buildings :rakennetut-autopaikat)
     :autopaikkojaKiinteistolla (sum-building-values buildings :kiinteiston-autopaikat)
     :autopaikkojaUlkopuolella  (sum-building-values buildings :kiinteiston-ulkopuoliset-autopaikat)}))

(defn- backing->parking-space-requirements [verdict]
  (merge no-parking-space-requirements
         (util/map-values #(util/->int % nil)
                          (-> verdict get-lupamaaraykset
                              (select-keys (keys no-parking-space-requirements))))))

(defn verdict-parking-space-requirements [verdict]
  (if (lupapiste-verdict? verdict)
    (if (legacy? verdict)
      (legacy->parking-space-requirements verdict)
      (pate->parking-space-requirements verdict))
    (backing->parking-space-requirements verdict)))

(defn verdict-kokoontumistilan-henkilomaara [verdict]
  (if (lupapiste-verdict? verdict)
    (let [buildings (get-data verdict :buildings)]
      (sum-building-values buildings :kokoontumistilanHenkilomaara))
    (util/->int (:kokoontumistilanHenkilomaara (get-lupamaaraykset verdict)) nil)))

(def ^:private no-area-requirements
  {:kerrosala nil
   :kokonaisala nil
   :rakennusoikeudellinenKerrosala nil})

(defn verdict-area-requirements [verdict]
  (if (lupapiste-verdict? verdict)
    no-area-requirements
    (let [requirements (get-lupamaaraykset verdict)]
      {:kerrosala (util/->int (:kerrosala requirements) nil)
       :kokonaisala (util/->int (:kokonaisala requirements) nil)
       :rakennusoikeudellinenKerrosala (some-> (:rakennusoikeudellinenKerrosala requirements)
                                               (util/->double nil)
                                               int)})))

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

(defn pk-verdict-string
  ([pk]
   (pk-verdict-string pk i18n/default-lang))
  ([pk lang]
   (or (some->> pk
                :verdict-code
                (i18n/localize lang :verdict.verdict-code))
       (-> pk :paatoskoodi))))

(defn verdict-string [lang verdict dict]
  (if (lupapiste-verdict? verdict)
    (lupapiste-verdict-string lang verdict dict)
    (-> verdict latest-pk (pk-verdict-string lang))))

(defn allu-agreement-state [verdict]
  {:pre [(has-category? verdict :allu-contract)]}
  (some-> verdict :data :agreement-state metadata/unwrap))

(sc/defn allu-contract-proposal? :- sc/Bool
  "Is the [[PateVerdict]] `verdict` an ALLU contract proposal?"
  [verdict :- PateVerdict]
  (and (has-category? verdict :allu-contract)
       (util/not=as-kw (allu-agreement-state verdict) :final)))

(defn- verdict-summary-title [verdict replacements lang section-strings]
  (let [id         (verdict-id verdict)
        published  (verdict-published verdict)
        replaces   (get replacements id)
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

(defn verdict-giver-name
  "Like `verdict-giver` but returns the corresponding name if the result is code. If
  `include-code?` is true (default false), the code is appended to name: My Name (codex)."
  ([verdict lang include-code?]
   (verdict-giver verdict (fn [{:keys [code first-name last-name]
                                :as   m}]
                            (let [giver (if (board-verdict? verdict)
                                          (get m (keyword lang))
                                          (or (ss/trim-join " " first-name last-name)
                                              (get m (keyword lang)) ;; This is fallback for old viranhaltija templates that have giver list but no names
                                              ))]
                              (or (cond-> giver
                                    (and include-code? (ss/not-blank? giver) (ss/not-blank? code))
                                    (str " (" code ")"))
                                  code)))))
  ([verdict lang]
   (verdict-giver-name verdict lang false))
  ([verdict]
   (verdict-giver-name verdict :fi false)))

(defn- contract-handler [verdict]
  (when (contract? verdict)
    (some->> verdict
             :data
             :handler
             metadata/unwrap
             ss/trim
             ss/blank-as-nil)))

(defn verdict-summary [lang replacements section-strings verdict]
  (->> {:id                 (verdict-id verdict)
        :parent-verdict     (:parent-verdict verdict)
        :published          (verdict-published verdict)
        :verdict-state      (verdict-state verdict)
        :modified           (verdict-modified verdict)
        :category           (verdict-category verdict)
        :legacy?            (legacy? verdict)
        :handler            (contract-handler verdict)
        :giver              (verdict-giver-name verdict lang true)
        :replaced?          (replaced? verdict)
        :verdict-date       (verdict-date verdict)
        :title              (verdict-summary-title verdict replacements lang section-strings)
        :signatures         (verdict-summary-signatures verdict)
        :signature-requests (verdict-summary-signature-requests verdict)
        :proposal?          (proposal? verdict)
        :document-id        (:document-id verdict)}
       (util/filter-map-by-val some?)))

(defn- section-strings-by-id [verdicts]
  (->> verdicts
       (map (juxt verdict-id verdict-section-string))
       (into {})))

(defn- summaries-by-id [verdicts replacements lang]
  (let [section-strings (section-strings-by-id verdicts)]
    (->> verdicts
         (map (juxt verdict-id #(verdict-summary lang replacements section-strings %)))
         (into {}))))

(defn- add-chain-of-replacements-to-result
  "Adds to result the summary of the given verdict along with the
  chain of replacements. For example, if the given verdict X replaced
  verdict Y, which replaced verdict Z, then X Y and Z would all be
  added."
  [result verdict replacements summaries]
  (concat result
          (loop [{:keys [id] :as v} verdict
                 sub                []]
            (if-let [replaced-id (get replacements id)]
              (recur (get summaries replaced-id)
                     (conj sub v))
              (conj sub v)))))

(sc/defschema VerdictSummary
  {:id                                     sc/Str
   (sc/optional-key :parent-verdict)       sc/Str
   (sc/optional-key :published)            sc/Int
   (sc/optional-key :modified)             sc/Int
   :category                               sc/Str
   :legacy?                                sc/Bool
   (sc/optional-key :handler)              sc/Str
   (sc/optional-key :giver)                sc/Str
   (sc/optional-key :verdict-date)         sc/Int
   (sc/optional-key :replaced?)            sc/Bool
   (sc/optional-key :verdict-state)        sc/Any
   :title                                  sc/Str
   (sc/optional-key :signatures)           [{:name sc/Str
                                             :date sc/Int}]
   (sc/optional-key :signature-requests)   [{:name sc/Str
                                             :date sc/Int}]
   (sc/optional-key :proposal?)            sc/Bool
   (sc/optional-key :document-id)          sc/Str})

(defn allowed-category-for-application? [verdict application]
  (or (has-category? verdict (schema-util/application->category application))
      (has-category? verdict :tj-v3)
      ;; HACK:
      (and (allu-application? (:organization application) (:permitType application))
           (has-category? verdict :allu-verdict))
      (has-category? verdict :migration-verdict)
      (has-category? verdict :migration-contract)
      (has-category? verdict :oik)))

(defn- replacements
  "Map of replacement verdict id -> replaced verdict id (for Pate verdicts)"
  [verdicts]
  (->> verdicts
       (filter replaced?)
       (map (juxt replaced-by verdict-id))
       (into {})))

(sc/defn ^:always-validate verdict-list :- [VerdictSummary]
  [{:keys [application]} lang]
  (let [;; There could be both contracts and verdicts.
        verdicts             (concat (->> (:pate-verdicts application)
                                          (filter #(allowed-category-for-application? % application))
                                          (map metadata/unwrap-all))
                                     (some->> application :verdicts
                                              (remove :draft)))
        replacements         (replacements verdicts)
        summaries            (summaries-by-id verdicts replacements lang)
        replaced-verdict-ids (set (vals replacements))]
    (reduce (fn [result verdict]
              (if (contains? replaced-verdict-ids
                             (verdict-id verdict))
                result
                (add-chain-of-replacements-to-result result verdict replacements summaries)))
            []
            (->> (vals summaries)
                 (sort-by (comp - :modified))))))

(defn all-published-verdicts
  "Returns all verdicts from application, which are not draft."
  [{:keys [pate-verdicts verdicts]}]
  (remove draft? (concat pate-verdicts verdicts)))

(defn all-verdicts [application]
  (concat (:pate-verdicts application)
          (:verdicts application)))
