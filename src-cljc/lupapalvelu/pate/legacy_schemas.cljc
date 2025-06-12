(ns lupapalvelu.pate.legacy-schemas
  "Schema instantiations for Pate legacy schemas. The legacy schemas
  should be compatible (enough) with the old verdicts."
  (:require [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- legsub-verdict-dictionary
  [{:keys [verdict-code-schema bulletin? kuulutus? public-notice-required?
           voimassa-required? include-giver?]}]
  (util/strip-nils
    {:kuntalupatunnus {:text      {:i18nkey :verdict.id}
                       :required? true}
     :handler         {:text      {:i18nkey (if include-giver? :pate-verdict.handler :verdict.name)}
                       :required? true}
     :giver           (when include-giver?
                        {:select    {:loc-prefix :pate-verdict.giver
                                     :aria-label :pate-verdict.giver
                                     :items      ["lautakunta" "viranhaltija"]}
                         :required? true})
     :giver-first-name (when include-giver?
                         {:text      {:i18nkey :pate-verdict.giver-first-name}
                          :required? :_computed.giver-is-person?})
     :giver-last-name  (when include-giver?
                         {:text      {:i18nkey :pate-verdict.giver-last-name}
                          :required? :_computed.giver-is-person?})
     :giver-name       (when include-giver?
                         {:text      {:i18nkey :verdict.name}
                          :required? :_computed.giver-is-board?})

     :verdict-code    (schema-util/required verdict-code-schema)
     :verdict-section {:text {:i18nkey :verdict.section
                              :before  :section}}
     :anto            {:date      {:i18nkey :verdict.anto}
                       :required? true}
     :lainvoimainen   {:date {:i18nkey :verdict.lainvoimainen}}
     :verdict-text    {:text {:i18nkey :verdict.text
                              :lines   20}}
     :julkipano       (when bulletin?
                        {:date      {:i18nkey :verdict.julkipano}
                         :required? public-notice-required?})
     :voimassa        (when (and bulletin? (not kuulutus?) voimassa-required?)
                        {:date      {:i18nkey :pate-verdict.voimassa}
                         :required? voimassa-required?})
     :k-verdict-date  (when kuulutus?
                        {:date      {:i18nkey :pate-dates.verdict-date}
                         :required? true})
     :k-kuulutus      (when kuulutus?
                        {:date      {:i18nkey :pate-verdict.k-kuulutus}
                         :required? public-notice-required?})
     :k-lainvoimainen (when kuulutus?
                        {:date {:i18nkey :verdict.lainvoimainen}})
     :k-voimassa      (when (and kuulutus? voimassa-required?)
                        {:date      {:i18nkey :pate-verdict.voimassa}
                         :required? voimassa-required?})}))

(defn legsub-verdict-section
  [{:keys [bulletin? kuulutus? voimassa-required? include-giver?]}]
  {:id   :verdict
   :grid {:columns 12
          :rows    (filterv some?
                            [[{:col   2
                               :align :full
                               :dict  :kuntalupatunnus}
                              {}
                              {:col   4
                               :align :full
                               :dict  :handler}]
                             (when include-giver?
                               [{:col   2
                                 :align :full
                                 :dict  :giver}
                                {}
                                {:col   2
                                 :dict  :giver-first-name
                                 :align :full
                                 :show? :_computed.giver-is-person?}
                                {:col   2
                                 :dict  :giver-last-name
                                 :align :full
                                 :show? :_computed.giver-is-person?}
                                {:col   4
                                 :dict  :giver-name
                                 :align :full
                                 :show? :_computed.giver-is-board?}])
                             [{:dict  :verdict-section
                               :align :full
                               :col   2}
                              {}
                              {:col   4
                               :align :full
                               :dict  :verdict-code}]
                             [{:col   10
                               :align :full
                               :dict  :verdict-text}]
                             (->> [:anto
                                   :lainvoimainen
                                   (when bulletin? :julkipano)
                                   (when (and bulletin? (not kuulutus?) voimassa-required?) :voimassa)
                                   (when (and bulletin? kuulutus?) :k-verdict-date)
                                   (when (and bulletin? kuulutus?) :k-lainvoimainen)
                                   (when (and bulletin? kuulutus?) :k-kuulutus)
                                   (when (and kuulutus? voimassa-required?) :k-voimassa)]
                                  (remove nil?)
                                  (mapv (fn [dict]
                                          {:col  2
                                           :dict dict})))])}})

(defn legsub-verdict
  [{:keys [category ryhti-data-2026] :as opts}]
  (let [opts (merge opts {:public-notice-required? (boolean (and ryhti-data-2026 (#{:r :p} category)))
                          :voimassa-required?      (boolean (and ryhti-data-2026 (= :p category)))})]
    {:dictionary (legsub-verdict-dictionary opts)
     :section    (legsub-verdict-section opts)}))

(defn remove-button [dict]
  {:button {:i18nkey :remove
            :label?  false
            :icon    :lupicon-remove
            :css     [:primary :outline]
            :remove  dict}})

(defn add-button [add-loc dict]
  {:button {:icon    :lupicon-circle-plus
            :i18nkey add-loc
            :label?  false
            :css     :positive
            :add     dict}})

(defn legsub-reviews [review-type-schema]
  {:dictionary {:reviews-title {:css      :pate-label
                                :loc-text :pate-reviews}
                :name-label    {:css      :pate-label.required
                                :loc-text :pate.katselmus}
                :type-label    {:css      :pate-label.required
                                :loc-text :pate.review-type}
                :reviews       {:repeating {:name   {:text      {:label? false}
                                                     :required? true}
                                            :type   (schema-util/required review-type-schema)
                                            :remove (remove-button :reviews)}}
                :add-review    (add-button :pate.add-review :reviews)}
   :section    {:id   :reviews
                :grid {:columns 5
                       :rows    [[{:col  5
                                   :dict :reviews-title}]
                                 {:css :row--extra-tight
                                  :show? :reviews
                                  :row [{:col  2
                                         :dict :name-label}
                                        {:col  1
                                         :dict :type-label}]}
                                 {:css :row--tight
                                  :row [{:col  5
                                         :grid {:columns   5
                                                :repeating :reviews
                                                :rows      [{:css :row--tight
                                                             :row [{:col   2
                                                                    :align :full
                                                                    :dict  :name}
                                                                   {:dict :type}
                                                                   {:dict :remove}]}]}}]}
                                 [{:dict  :add-review
                                   :show? :_meta.editing?}]]}}})

(def legsub-foremen
  {:dictionary {:foremen-title {:css      :pate-label
                                :loc-text :pate-r.foremen}
                :foreman-label {:css      :pate-label.required
                                :loc-text :foreman.role}
                :foremen       {:repeating {:role   {:required? true
                                                     :select    {:label?     false
                                                                 :aria-label :foreman.role
                                                                 :loc-prefix :osapuoli.tyonjohtaja.kuntaRoolikoodi
                                                                 :items      (vals helper/foreman-roles)
                                                                 :sort-by    :text}}
                                            :remove (remove-button :foremen)}}
                :add-foreman   (add-button :pate.add-foreman :foremen)}
   :section    {:id   :foremen
                :grid {:columns 5
                       :rows    [[{:col  5
                                   :dict :foremen-title}]
                                 {:css   :row--extra-tight
                                  :show? [:AND :foremen :_meta.editing?]
                                  :row   [{:col  2
                                           :dict :foreman-label}]}
                                 {:css :row--tight
                                  :row [{:col  5
                                         :grid {:columns   5
                                                :repeating :foremen
                                                :rows      [{:css :row--tight
                                                             :row [{:col   2
                                                                    :align :full
                                                                    :dict  :role}
                                                                   {:dict :remove}]}]}}]}
                                 [{:dict  :add-foreman
                                   :show? :_meta.editing?}]]}}})

(def legsub-conditions-dictionary
  {:conditions-title {:css      :pate-label
                      :loc-text :verdict.muutMaaraykset}
   :condition-label  {:css      :pate-label.required
                      :loc-text :pate-condition}
   :conditions       {:repeating {:name   {:text      {:label? false}
                                           :required? true}
                                  :remove (remove-button :conditions)}}
   :add-condition    (add-button :pate-conditions.add :conditions)})

(defn legsub-conditions [& {:keys [contract? ryhti-data-2026]}]
  {:dictionary (cond-> legsub-conditions-dictionary
                 contract?       (assoc-in [:conditions-title :loc-text] :pate.contract.conditions)
                 contract?       (assoc-in [:condition-label :loc-text] :pate.contract.condition)
                 contract?       (assoc-in [:add-condition :button :i18nkey] :pate.contract.add-condition)
                 ryhti-data-2026 (assoc :type-col-label {:css      :pate-label.required
                                                         :loc-text :pate.condition-type})
                 ryhti-data-2026 (assoc-in [:conditions :repeating :type]
                                           {:select    {:label?      false
                                                        :loc-prefix :suomi.fi.lupamaarayksen-laji
                                                        :items      (->> helper/lupamaarayksen-lajit
                                                                         (remove #(-> % :group (= :katselmukset)))
                                                                         (mapv :items)
                                                                         (reduce into))}
                                            :required? true}))
   :section    {:id   :conditions
                :grid {:columns 5
                       :rows    [[{:col  5
                                   :dict :conditions-title}]
                                 {:css   :row--tight
                                  :show? [:AND :conditions :_meta.editing?]
                                  :row   [{:col  5
                                           :grid {:columns 5
                                                  :rows    [{:css :row--tight
                                                             :row (remove nil? [{:col  2
                                                                                 :dict :condition-label}
                                                                                (when ryhti-data-2026
                                                                                  {:col  2
                                                                                   :dict :type-col-label})])}]}}]}
                                 {:css :row--tight
                                  :row [{:col  5
                                         :grid {:columns   5
                                                :repeating :conditions
                                                :rows      [{:css :row--tight
                                                             :row (remove nil? [{:col   2
                                                                                 :align :full
                                                                                 :dict  :name}
                                                                                (when ryhti-data-2026
                                                                                  {:col   2
                                                                                   :align :full
                                                                                   :dict  :type})
                                                                                {:dict :remove}])}]}}]}
                                 [{:dict  :add-condition
                                   :show? :_meta.editing?}]]}}})

;; Only uploading new attachments. This is because the verdicts can be
;; deleted and we want to avoid inadvertent nuking of attachments.
(def legsub-attachments
  {:dictionary
   {:attachments {:application-attachments {:i18nkey :application.verdict-attachments}}}
   :section {:id      :attachments
             :buttons? false
             :grid    {:columns 7
                       :rows    [[{:col  6
                                   :dict :attachments}]]}}})

(def legsub-bulletin
  {:dictionary {:bulletin-op-description {:text {:i18nkey :phrase.category.toimenpide-julkipanoon}}}
   :section    {:id   :bulletin
                :grid {:columns 6
                       :rows    [[{:col  2
                                   :align :full
                                   :dict :bulletin-op-description}]]}}})

(def ^:private valid-pate-legacy-verdict! (sc/validator schemas/PateLegacyVerdict))

(defn build-legacy-schema [& subschemas]
  (valid-pate-legacy-verdict!
    (assoc (apply schema-util/combine-subschemas subschemas)
           :legacy? true)))

(def r-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    :items      (map (comp keyword str) (range 1 43))
                                                    :sort-by    :text
                                                    :type       :autocomplete}}
                     :bulletin?           true
                     :kuulutus?           true
                     :category            :r
                     :include-giver?      true
                     ;; True for legacy even if no application flag; frontend depends on this being def
                     :ryhti-data-2026     true})
    (legsub-reviews {:select {:loc-prefix :pate.review-type
                              :label?     false
                              :items      helper/available-review-types
                              :sort-by    :text}})
    legsub-bulletin
    legsub-foremen
    (legsub-conditions :ryhti-data-2026 true)
    legsub-attachments
    (verdict-schemas/versub-upload)))

(def legsub-reviews-ya
  (legsub-reviews {:select {:loc-prefix :pate.review-type
                            :label?     false
                            :items      helper/ya-review-types
                            :sort-by    :text}}))

(def ya-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    ;; Myonnetty, hyvaksytty, evatty,
                                                    ;; peruutettu
                                                    :items      [:1 :2 :21 :37]
                                                    :sort-by    :text
                                                    :type       :select}}
                     :bulletin?           true})
    legsub-bulletin
    legsub-reviews-ya
    (legsub-conditions)
    legsub-attachments
    (verdict-schemas/versub-upload {:type-group #"muut" :default :muut.paatosote})))

(def p-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    :items      (map (comp keyword str) (range 1 43))
                                                    :sort-by    :text
                                                    :type       :autocomplete}}
                     :bulletin?           true
                     :kuulutus?           true
                     :category            :p
                     :include-giver?      true
                     ;; True for legacy even if no application flag; frontend depends on this being def
                     :ryhti-data-2026     true})
    legsub-bulletin
    (legsub-conditions :ryhti-data-2026 true)
    legsub-attachments
    (verdict-schemas/versub-upload)))

(def kt-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:text {:loc-prefix :verdict.status
                                                  :items      [:verdict.status.43 :verdict.status.44 :pate.verdict-code.paatos-annettu]}}
                     :bulletin?           false})
    (legsub-conditions)
    legsub-attachments
    (verdict-schemas/versub-upload {:type-group #".*" :default :muut.paatosote})))

(def ymp-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:text {:i18nkey :verdict.status}}
                     :bulletin?           false})
    (legsub-conditions)
    legsub-attachments
    (verdict-schemas/versub-upload {:type-group #".*" :default :muut.paatosote})))

(def tj-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    :items      [:1 :2 :21 :37]
                                                    :sort-by    :text
                                                    :type       :autocomplete}}
                     :bulletin?           false})
    legsub-attachments
    (verdict-schemas/versub-upload)))

(def tj-v3-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    :items      [:1 :2 :21 :37]
                                                    :sort-by    :text
                                                    :type       :autocomplete}}
                     :bulletin?           false})
    legsub-attachments
    (verdict-schemas/versub-upload)))


(def legsub-contract
  {:dictionary {:kuntalupatunnus {:text      {:i18nkey :verdict.id}
                                  :required? true}
                :handler         {:text      {:i18nkey :verdict.name.sijoitussopimus}
                                  :required? true}
                :verdict-date    {:date      {:i18nkey :verdict.contract.date}
                                  :required? true}
                :contract-text   {:text {:i18nkey :verdict.contract.text
                                         :lines   20}}}
   :section    {:id   :verdict
                :grid {:columns 12
                       :rows    [[{:col   2
                                   :align :full
                                   :dict  :kuntalupatunnus}
                                  {}
                                  ]
                                 [{:col  2
                                   :dict :verdict-date}
                                  {}
                                  {:col   4
                                   :align :full
                                   :dict  :handler}]
                                 [{:col   10
                                   :align :full
                                   :dict  :contract-text}]]}}})

(def contract-legacy-verdict
  (build-legacy-schema
   legsub-contract
   legsub-reviews-ya
   (legsub-conditions :contract? true)
   legsub-attachments
   (verdict-schemas/versub-upload {:type-group #".*"
                                   :default :muut.paatosote
                                   :title :verdict.contract.attachments})))


(def legsub-migration-contract
  {:dictionary {:kuntalupatunnus {:text      {:i18nkey :verdict.id}
                                  :required? true}
                :handler         {:text      {:i18nkey :verdict.name}
                                  :required? true}
                :verdict-code    {:select {:loc-prefix :verdict.status
                                           :items      (map (comp keyword str) (range 1 43))
                                           :sort-by    :text
                                           :type :autocomplete}
                                  :required? true}
                :verdict-section {:text {:i18nkey :verdict.section
                                         :before  :section}}
                :anto            {:date      {:i18nkey :verdict.anto}
                                  :required? true}
                :lainvoimainen   {:date {:i18nkey :verdict.lainvoimainen}}
                :contract-text    {:text {:i18nkey :verdict.contract.text
                                         :lines   20}}}
   :section    {:id   :verdict
                :grid {:columns 12
                       :rows    [[{:col   2
                                   :align :full
                                   :dict  :kuntalupatunnus}
                                  {}
                                  {:col   4
                                   :align :full
                                   :dict  :handler}]
                                 [{:dict :verdict-section}
                                  {:col 2}
                                  {:col   4
                                   :align :full
                                   :dict  :verdict-code}]
                                 [{:col   10
                                   :align :full
                                   :dict  :contract-text}]
                                 [{:col 2
                                   :dict :anto}
                                  {:col 2
                                   :dict :lainvoimainen}]]}}})


(def migration-contract
  (build-legacy-schema
   legsub-migration-contract
   (legsub-reviews {:select {:loc-prefix :pate.review-type
                             :label?     false
                             :items      helper/available-review-types
                             :sort-by    :text}})
   legsub-foremen
   (legsub-conditions :contract? true)
   legsub-attachments
   (verdict-schemas/versub-upload)))

(def migration-verdict
  (build-legacy-schema
    (legsub-verdict {:verdict-code-schema {:select {:loc-prefix :verdict.status
                                                    :items      (map (comp keyword str) (range 1 43))
                                                    :sort-by    :text
                                                    :type       :autocomplete}}
                     :bulletin?           false})
    (legsub-reviews {:select {:loc-prefix :pate.review-type
                              :label?     false
                             :items      helper/available-review-types
                              :sort-by    :text}})
    legsub-foremen
    (legsub-conditions)
    legsub-attachments
    (verdict-schemas/versub-upload)))


(def allu-contract
  (build-legacy-schema {:dictionary {:handler    {:text      {:i18nkey :verdict.name.sijoitussopimus}
                                                  :required? true}
                                     :agreement-state {:select {:items [:proposal :final]}}}}))

(defn legacy-verdict-schema
  "Schemas for verdicts made in Lupapiste but without PATE enabled.
  Does not use templates or settings like PATE verdicts proper.
  See `lupapalvelu.pate.verdict-schemas/verdict-schemas` for the PATE equivalent."
  [category]
  (case (keyword category)
    :r                  r-legacy-verdict
    :ya                 ya-legacy-verdict
    :p                  p-legacy-verdict
    :kt                 kt-legacy-verdict
    :ymp                ymp-legacy-verdict
    :contract           contract-legacy-verdict
    :tj                 tj-legacy-verdict
    :migration-contract migration-contract
    :migration-verdict  migration-verdict
    :allu-contract      allu-contract
    :tj-v3              tj-v3-legacy-verdict
    (schema-util/pate-assert false "Unsupported legacy category:" category)))
