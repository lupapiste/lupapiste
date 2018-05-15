(ns lupapalvelu.pate.legacy
  "Schema instantiations for Pate legacy schemas. The legacy schemas
  should be compatible (enough) with the old verdicts."
  (:require [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

;; Todo: select combobox, autocomplete: verdict-code, foremen

(defn legsub-verdict [verdict-code-schema]
  {:dictionary {:kuntalupatunnus {:text      {:i18nkey :verdict.id}
                                  :required? true}
                :giver           {:text      {:i18nkey :verdict.name}
                                  :required? true}
                :verdict-code    (schema-util/required verdict-code-schema)
                :section         {:text {:i18nkey :verdict.section
                                         :before  :section}}
                :anto            {:date      {:i18nkey :verdict.anto}
                                  :required? true}
                :lainvoimainen   {:date {:i18nkey :verdict.lainvoimainen}}
                :verdict-text    {:text {:i18nkey :verdict.text
                                         :lines   20}}}
   :section    {:id   :verdict
                :grid {:columns 8
                       :rows    [[{:col 2
                                   :align :full
                                   :dict :kuntalupatunnus}]
                                 [{:col 2
                                   :align :full
                                   :dict :giver}
                                  {:dict :section}
                                  {}
                                  {:col   2
                                   :align :full
                                   :dict  :verdict-code}]
                                 [{:dict :anto} {:dict :lainvoimainen}]
                                 [{:col   6
                                   :align :full
                                   :dict  :verdict-text}]]}}})

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

(def legsub-conditions
  {:dictionary {:conditions-title {:css      :pate-label
                                   :loc-text :verdict.muutMaaraykset}
                :condition-label  {:css      :pate-label.required
                                   :loc-text :pate-condition}
                :conditions       {:repeating {:name   {:text      {:label? false}
                                                        :required? true}
                                               :remove (remove-button :conditions)}}
                :add-condition    (add-button :pate-conditions.add :conditions)}
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

(def legsub-attachments (-> shared/versub-attachments
                            (util/dissoc-in [:sections 0 :template-section])
                            (util/dissoc-in [:sections 1 :buttons?])))

(defn build-legacy-schema [& subschemas]
  (sc/validate schemas/PateLegacyVerdict
               (assoc (apply schema-util/combine-subschemas subschemas)
                      :legacy? true)))

(def r-legacy-verdict
  (build-legacy-schema
   (legsub-verdict {:select {:loc-prefix :verdict.status
                             :items      (map (comp keyword str) (range 1 43))
                             :sort-by    :text}})
   (legsub-reviews {:select {:loc-prefix :pate.review-type
                             :label?     false
                             :items      shared/review-types
                             :sort-by    :text}})
   legsub-foremen
   legsub-conditions
   legsub-attachments))

(defn legacy-verdict-schema [category]
  (case (keyword category)
    :r r-legacy-verdict
    (schema-util/pate-assert false "Unsupported legacy category:" category)))
