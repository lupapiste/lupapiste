(ns lupapalvelu.pate.verdict-template-schemas
  (:require [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

;; -----------------------------
;; Verdict template subschemas
;; -----------------------------

(defn- settings-link [& kvs]
  {:link (merge {:text-loc :pate.settings-link
                 :click    :open-settings}
                (apply hash-map kvs))})

(defn build-verdict-template-schema
  "Some addtions to `schema-util/combine-subschemas`: If a subschema has
  a :removable?  flag, then the section is added
  to :removed-sections. The resulting schema is validated."
  [& subschemas]
  (->> subschemas
       (cons {:dictionary
              {:removed-sections
               {:keymap
                (zipmap (reduce (fn [acc subschema]
                                  (cond-> acc
                                    (:removable? subschema)
                                    (concat (map (comp keyword :id)
                                                 (schema-util/subschema-sections subschema)))))
                                []
                                subschemas)
                        (repeat false))}}})
       (apply schema-util/combine-subschemas)
       (sc/validate schemas/PateVerdictTemplate)))


(defn text-subschema
  "Template subschema that only shows text. Used for selecting whether
  the section is included in the verdict. Text section is removable by
  default, but this can be overridden with removable? parameter."
  ([dict loc-text removable?]
   {:dictionary {dict {:loc-text loc-text}}
    :section    (helper/text-section dict)
    :removable? removable?})
  ([dict loc-ext]
   (text-subschema dict loc-ext true)))

(defn temsub-verdict [dates]
  {:dictionary {:language          helper/language-select
                :verdict-dates     {:multi-select {:items           dates
                                                   :sort?           false
                                                   :i18nkey         :pate-verdict-dates
                                                   :item-loc-prefix :pate-verdict}}
                :giver             (schema-util/required helper/verdict-giver-select)
                :verdict-code      {:reference-list {:path       :settings.verdict-code
                                                     :type       :select
                                                     :loc-prefix :pate-r.verdict-code}}
                :verdict-code-link (settings-link :loc-prefix :pate-r.verdict-code)
                :paatosteksti      {:phrase-text {:loc-prefix :pate-verdict
                                                  :category   :paatosteksti}}}
   :section    {:id         :verdict
                :loc-prefix :pate-verdict-template.verdict-info
                :grid       {:columns 12
                             :rows    [[{:col  6
                                         :dict :language}]
                                       [{:col  12
                                         :dict :verdict-dates}]
                                       [{:col  3
                                         :dict :giver}
                                        {:align :full
                                         :col   3
                                         :hide? :*ref.settings.verdict-code
                                         :dict  :verdict-code-link}
                                        {:align :full
                                         :col   3
                                         :show? :*ref.settings.verdict-code
                                         :dict  :verdict-code}]
                                       [{:col  12
                                         :dict :paatosteksti}]]}}})

(def temsub-bulletin
  {:dictionary {:bulletinOpDescription {:phrase-text {:category :toimenpide-julkipanoon
                                                      :i18nkey  :phrase.category.toimenpide-julkipanoon}}}
   :section {:id         :bulletin
             :loc-prefix :bulletin
             :grid       {:columns 1
                          :rows    [[{:col  1
                                      :dict :bulletinOpDescription}]]}}})

(def temsub-foremen
  (->> helper/foreman-codes
       (map (fn [code]
              (let [codename     (name code)
                    included-key (keyword (str codename "-included"))]
                {:toggle-key code

                 :included     {:toggle {:i18nkey  (keyword (str "pate-r.foremen."
                                                               codename))
                                         :label?  false}}
                 :included-key included-key
                 :toggle     {:toggle {:i18nkey :pate.available-in-verdict
                                       :enabled? included-key
                                       :label?   false}}})))

       (reduce (fn [acc {:keys [toggle-key toggle included-key included]}]
                 (-> acc
                     (assoc-in [:dictionary toggle-key] toggle)
                     (assoc-in [:dictionary included-key] included)
                     (update-in [:section :grid :rows]
                                #(conj (vec %)
                                       {:css :row--extra-tight
                                        :row [{:col  2
                                               :dict included-key}
                                              {:col  2
                                               :dict toggle-key}]}))))
               {:section    {:id         :foremen
                             :loc-prefix :pate-r.foremen
                             :grid       {:columns 5
                                          :rows    []}}
                :removable? true})))

(defn settings-dependencies [dict loc-prefix]
  (let [link-dict (-> (name dict) (str  "-link") keyword)]
    {:dictionary {dict      {:repeating {:included {:toggle {:label?    false
                                                             :text-dict :text}}
                                         :text-fi  {:text {:read-only? true}}
                                         :text-sv  {:text {:read-only? true}}
                                         :text-en  {:text {:read-only? true}}
                                         :selected {:toggle {:enabled? :-.included
                                                             :label?   false
                                                             :i18nkey  :pate.available-in-verdict}}}
                             :sort-by   {:prefix :text}}
                  link-dict (settings-link :label? false)}
     :section     {:id         dict
                   :loc-prefix loc-prefix
                   :grid       {:columns 5
                                :rows    [{:hide? dict
                                           :css   :row--extra-tight
                                           :row   [{:col  4
                                                    :dict link-dict}]}
                                          {:css :row--extra-tight
                                           :row [{:col   4
                                                  :show? dict
                                                  :grid  {:columns   4
                                                          :repeating dict
                                                          :rows      [{:css :row--extra-tight
                                                                       :row [{:col  2
                                                                              :dict :included}
                                                                             {:col  2
                                                                              :dict :selected}]}]}}]}]}}
     :removable?  true}))

(def temsub-reviews
  (settings-dependencies :reviews :pate-reviews))

(def temsub-plans
  (settings-dependencies :plans :pate.plans))

(def temsub-conditions ;; Muut lupaehdot
  {:dictionary    {:conditions    {:repeating {:condition        {:phrase-text {:i18nkey  :pate-condition
                                                                                :category :lupaehdot}}
                                               :remove-condition {:button {:i18nkey :remove
                                                                           :label?  false
                                                                           :icon    :lupicon-remove
                                                                           :css     [:primary :outline]
                                                                           :remove  :conditions}}}}
                   :add-condition {:button {:icon    :lupicon-circle-plus
                                            :i18nkey :pate-conditions.add
                                            :label? false
                                            :css     :positive
                                            :add     :conditions}}}
   :section       {:id         :conditions
                   :loc-prefix :phrase.category.lupaehdot
                   :grid       {:columns 1
                                :rows    [[{:grid {:columns   8
                                                   :repeating :conditions
                                                   :rows      [[{:col  6
                                                                 :dict :condition}
                                                                {}
                                                                {:dict :remove-condition}]]}}]
                                          [{:dict :add-condition}]]}}
   :removable? true})

(def temsub-neighbors (text-subschema :neighbors :pate-neighbors.text))

(def temsub-appeal
  {:dictionary {:appeal {:phrase-text {:category :muutoksenhaku
                                       :i18nkey  :pate-appeal-title}}}
   :section    {:id         :appeal
                :loc-prefix :pate-appeal
                :grid       {:columns 1
                             :rows    [[{:dict :appeal }]]}}
   :removable? true})

(def temsub-collateral (text-subschema :collateral :pate-collateral.text))

(def temsub-complexity
  {:dictionary    {:complexity      helper/complexity-select
                   :complexity-text {:phrase-text {:label?   false
                                                   :category :vaativuus}}}
   :section       {:id         :complexity
                   :loc-prefix :pate.complexity
                   :grid       {:columns 1
                                :rows    [[{:dict :complexity }]
                                          [{:id   "text"
                                            :dict :complexity-text}]]}}
   :removable? true})

(def temsub-extra-info (text-subschema :extra-info :pate-extra-info.text))

(def temsub-deviations ;; Poikkeamiset
  (text-subschema :deviations :pate-deviations.text))
(def temsub-rights ;; Rakennusoikeus
  (text-subschema :rights :pate-rights.text))
(def temsub-purpose ;; Kaavan k\u00e4ytt\u00f6tarkoitus
  (text-subschema :purpose :pate-purpose.text))
(def temsub-statements (text-subschema :statements :pate-statements.text))

(def temsub-buildings
  {:dictionary (reduce (fn [acc k]
                         (assoc acc k {:toggle {:i18nkey (util/kw-path
                                                          :pate-buildings.info k)}}))
                       {}
                       [:autopaikat :vss-luokka :paloluokka])
   :section
   {:id         :buildings
    :loc-prefix :pate-buildings
    :grid       {:columns 1
                 :rows    [[{:list {:title   "pate-buildings.info"
                                    :labels? false
                                    :items   (mapv (fn [check]
                                                     {:dict check
                                                      :css  [:pate-condition-box]})
                                                   [:autopaikat :vss-luokka :paloluokka])}}]]}}
   :removable? true})

(def temsub-attachments
  {:dictionary    {:upload {:toggle {:i18nkey :pate.attachments.upload}}}
   :section       {:id         :attachments
                   :loc-prefix :application.verdict-attachments
                   :grid       {:columns 1
                                :rows    [[{
                                            :list       {:labels? false
                                                         :items   [{:dict :upload}]}}]]}}
   :removable? true})


(def r-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict helper/verdict-dates)
                                 temsub-bulletin
                                 temsub-foremen
                                 temsub-reviews
                                 temsub-plans
                                 temsub-conditions
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-complexity
                                 temsub-extra-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-buildings
                                 temsub-attachments))

(def temsub-start-info
  (text-subschema :start-info :pate-start-info.text))

(def p-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict helper/verdict-dates)
                                 temsub-bulletin
                                 temsub-conditions
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-start-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-attachments))

(def temsub-reviews-with-phrase
  (-> (settings-dependencies :reviews :pate-reviews)
      (assoc-in [:dictionary :review-info] {:phrase-text {:i18nkey  :pate.review-info
                                                          :category :yleinen}})
      (update-in [:section :grid :rows] conj [{:col   4
                                               :dict  :review-info}])
      (assoc-in [:section :always-included?] true)))

(def temsub-inform-others
  (text-subschema :inform-others :pate-inform-others.text))

(def ya-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict helper/ya-verdict-dates)
                                 temsub-bulletin
                                 temsub-reviews-with-phrase
                                 temsub-plans
                                 temsub-conditions
                                 temsub-inform-others
                                 temsub-appeal
                                 temsub-statements
                                 temsub-attachments))

(def tj-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict helper/tj-verdict-dates)
                                 temsub-appeal
                                 temsub-attachments))

(def temsub-contract
  {:dictionary {:language      helper/contract-language
                :contract-text {:phrase-text {:i18nkey  :verdict.contract.text
                                              :category :sopimus}}}
   :section    {:id         :contract
                :loc-prefix :pate.contract.template.info
                :grid       {:columns 2
                             :rows    [[{:dict :language}]
                                       [{:col  2
                                         :dict :contract-text}]]}}})

(def temsub-contract-conditions ;; Sopimusehdot
  (-> temsub-conditions
      (assoc-in [:dictionary :conditions :repeating :condition :phrase-text :i18nkey]
                :pate.contract.condition)
      (assoc-in [:dictionary :add-condition :button :i18nkey]
                :pate.contract.add-condition)
      (assoc-in [:section :loc-prefix] :pate.contract.conditions)))

(def temsub-contract-attachments
  (assoc-in (text-subschema :attachments :pate.contract.attachments-text)
            [:section :loc-prefix] :verdict.contract.attachments))

(def contract-template-schema
  (build-verdict-template-schema temsub-contract
                                 temsub-reviews
                                 temsub-contract-conditions
                                 temsub-contract-attachments))

(defn verdict-template-schema [category]
  (case (keyword category)
    :r        r-verdict-template-schema
    :p        p-verdict-template-schema
    :ya       ya-verdict-template-schema
    :tj       tj-verdict-template-schema
    :contract contract-template-schema))
