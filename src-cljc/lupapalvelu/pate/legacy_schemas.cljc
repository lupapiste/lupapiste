(ns lupapalvelu.pate.legacy-schemas
  "Schema instantiations for Pate legacy schemas. The legacy schemas
  should be compatible (enough) with the old verdicts."
  (:require [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

(defn legsub-verdict [verdict-code-schema]
  {:dictionary {:kuntalupatunnus {:text      {:i18nkey :verdict.id}
                                  :required? true}
                :handler         {:text      {:i18nkey :verdict.name}
                                  :required? true}
                :verdict-code    (schema-util/required verdict-code-schema)
                :verdict-section {:text {:i18nkey :verdict.section
                                         :before  :section}}
                :anto            {:date      {:i18nkey :verdict.anto}
                                  :required? true}
                :lainvoimainen   {:date {:i18nkey :verdict.lainvoimainen}}
                :verdict-text    {:text {:i18nkey :verdict.text
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
                                   :dict  :verdict-text}]
                                 [{:col 2
                                   :dict :anto}
                                  {:col 2
                                   :dict :lainvoimainen}]]}}})

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
                :foremen       {:repeating {:role   {:text      {:label? false
                                                                 :items  (mapv #(util/kw-path :pate-r.foremen %)
                                                                               [:erityis-tj :iv-tj :tj
                                                                                :vastaava-tj :vv-tj])}
                                                     :required? true}
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

(defn legsub-conditions [& [contract?]]
  {:dictionary {:conditions-title {:css      :pate-label
                                   :loc-text (if contract?
                                               :pate.contract.conditions
                                               :verdict.muutMaaraykset)}
                :condition-label  {:css      :pate-label.required
                                   :loc-text (if contract?
                                               :pate.contract.condition
                                               :pate-condition)}
                :conditions       {:repeating {:name   {:text      {:label? false}
                                                        :required? true}
                                               :remove (remove-button :conditions)}}
                :add-condition    (add-button (if contract?
                                                :pate.contract.add-condition
                                                :pate-conditions.add) :conditions)}
   :section    {:id   :conditions
                :grid {:columns 5
                       :rows    [[{:col  5
                                   :dict :conditions-title}]
                                 {:css   :row--extra-tight
                                  :show? [:AND :conditions :_meta.editing?]
                                  :row   [{:col  2
                                           :dict :condition-label}]}
                                 {:css :row--tight
                                  :row [{:col  5
                                         :grid {:columns   5
                                                :repeating :conditions
                                                :rows      [{:css :row--tight
                                                             :row [{:col   2
                                                                    :align :full
                                                                    :dict  :name}
                                                                   {:dict :remove}]}]}}]}
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

(defn build-legacy-schema [& subschemas]
  (sc/validate schemas/PateLegacyVerdict
               (assoc (apply schema-util/combine-subschemas subschemas)
                      :legacy? true)))

(def r-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:select {:loc-prefix :verdict.status
                             :items      (map (comp keyword str) (range 1 43))
                             :sort-by    :text
                             :type :autocomplete}})
   (legsub-reviews {:select {:loc-prefix :pate.review-type
                             :label?     false
                             :items      helper/review-types
                             :sort-by    :text}})
   legsub-foremen
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload)))

(def legsub-reviews-ya
  (legsub-reviews {:select {:loc-prefix :pate.review-type
                            :label?     false
                            :items      helper/ya-review-types
                            :sort-by    :text}}))

(def ya-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:select {:loc-prefix :verdict.status
                             ;; Myonnetty, hyvaksytty, evatty,
                             ;; peruutettu
                             :items      [:1 :2 :21 :37]
                             :sort-by    :text
                             :type       :select}})
   legsub-reviews-ya
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload {:type-group #"muut" :default :muut.paatosote})))

(def p-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:select {:loc-prefix :verdict.status
                             :items      (map (comp keyword str) (range 1 43))
                             :sort-by    :text
                             :type :autocomplete}})
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload)))

(def kt-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:text {:loc-prefix :verdict.status
                           :items      [:verdict.status.43 :verdict.status.44]}})
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload {:type-group #".*" :default :muut.paatosote})))

(def ymp-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:text {:i18nkey :verdict.status}})
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload {:type-group #".*" :default :muut.paatosote})))

(def tj-legacy-verdict
  (build-legacy-schema
    (legsub-verdict {:select {:loc-prefix :verdict.status
                              :items      [:1 :2 :21 :37]
                              :sort-by    :text
                              :type :autocomplete}})
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
   (legsub-conditions true)
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
                             :items      helper/review-types
                             :sort-by    :text}})
   legsub-foremen
   (legsub-conditions true)
   legsub-attachments
   (verdict-schemas/versub-upload)))

(def migration-verdict
  (build-legacy-schema
   (legsub-verdict {:select {:loc-prefix :verdict.status
                             :items      (map (comp keyword str) (range 1 43))
                             :sort-by    :text
                             :type :autocomplete}})
   (legsub-reviews {:select {:loc-prefix :pate.review-type
                             :label?     false
                             :items      helper/review-types
                             :sort-by    :text}})
   legsub-foremen
   (legsub-conditions)
   legsub-attachments
   (verdict-schemas/versub-upload)))


(def allu-contract
  (build-legacy-schema {:dictionary {:handler    {:text      {:i18nkey :verdict.name.sijoitussopimus}
                                                  :required? true}
                                     :agreement-state {:select {:items [:proposal :final]}}}}))

(defn legacy-verdict-schema [category]
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
    (schema-util/pate-assert false "Unsupported legacy category:" category)))
