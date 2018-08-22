(ns lupapalvelu.pate.pdf-layouts
  "Verdict PDF layout definitions. Note: keyword
  sources (e.g., :application-id) are defined in
  `lupapalvelu.pate.pdf/verdict-properties`."
  (:require [clojure.string :as s]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]))

(def cell-widths (range 10 101 5))
(def row-styles [:pad-after :pad-before
                 :border-top :border-bottom
                 :page-break :bold :spaced])
(def cell-styles [:bold :center :right :nowrap])

(defschema Source
  "Value of PdfEntry :source property aka data source for the row."
  (sc/conditional
   ;; Keyword corresponds to a key in the data context.
   keyword? sc/Keyword
   :else (shared-schemas/only-one-of [:doc :dict]
                                     ;; Vector is a path to application
                                     ;; document data. The first item is the
                                     ;; document name and rest are path within
                                     ;; the data.
                                     {(sc/optional-key :doc)     [sc/Keyword]
                                      ;; Kw-path into published verdict data.
                                      (sc/optional-key :dict)    sc/Keyword})))

(defn styles
  "Definition that only allows either individual kw or subset of kws."
  [kws]
  (let [enum (apply sc/enum kws)]
    (sc/conditional
     keyword? enum
     :else    [enum])))

(defschema PdfEntry
  "An entry in the layout consists of left- and right-hand sides. The
  former contains the entry title and the latter actual data. In the
  schema, an entry is modeled as a vector, where the first element
  defines both the title and the data source for the whole entry.

  On styles: the :styles definition of the first item applies to the
  whole row. Border styles (:border-top and :border-bottom) include
  padding and margin so the adjacent rows should not add any
  padding. Cell items' :styles only apply to the corresponding cell.

  In addition to the schema definition, styles can be added in
  'runtime': if source value has ::styles property, it should be map
  with the following possible keys:

  :row    Row styles

  :cell   Cell styles (applied to every cell)

  path    Path is the value of the :path property. For example, if
  the :path has a value of :foo, then the cell could be emphasized
  with ::styles map {:foo :bold}. "
  [(sc/one {;; Localisation key for the row (left-hand) title.
            :loc                        sc/Keyword
            ;; If :loc-many is given it is used as the title key if
            ;; the source value denotes multiple values.
            (sc/optional-key :loc-many) sc/Keyword
            (sc/optional-key :source)   Source
            ;; Post-processing function for source value.
            (sc/optional-key :post-fn) (sc/conditional
                                        keyword? sc/Keyword
                                        :else   (sc/pred fn?))
            (sc/optional-key :styles)   (styles row-styles)}
           {})
   ;; Note that the right-hand side can consist of multiple
   ;; cells/columns. As every property is optional, the cells can be
   ;; omitted. In that case, the value of the right-hand side is the
   ;; source value.
   ;; Path within the source value. Useful, when the value is a map.
   {(sc/optional-key :path)       shared-schemas/path-type
    ;; Textual representation that is static and
    ;; independent from any source value.
    (sc/optional-key :text)       shared-schemas/keyword-or-string
    (sc/optional-key :width)      (apply sc/enum cell-widths)
    (sc/optional-key :unit)       (sc/enum :ha :m2 :m3 :kpl)
    ;; Additional localisation key prefix. Is
    ;; applied both to path and text values.
    (sc/optional-key :loc-prefix) shared-schemas/path-type
    (sc/optional-key :styles)     (styles cell-styles)}])

(defschema PdfLayout
  "PDF contents layout."
  {;; Width of the left-hand side.
   :left-width (apply sc/enum cell-widths)
   :entries    [PdfEntry]})

;; ------------------------------
;; Entries
;; ------------------------------

(defn entry--simple
  ([dict styles]
   [{:loc    (case dict
               :address       :pate.address
               :buyout        :pate.buyout
               :collateral    :pate-collateral
               :deviations    :pate-deviations
               :extra-info    :pate-extra-info
               :fyi           :pate.fyi
               :giving        :pate.verdict-giving
               :legalese      :pate.legalese
               :next-steps    :pate.next-steps
               :purpose       :pate-purpose
               :rationale     :pate.verdict-rationale
               :rights        :pate-rights
               :start-info    :pate-start-info
               :neighbors     :phrase.category.naapurit
               :inform-others :pate-inform-others)
     :source {:dict dict}
     :styles styles}])
  ([dict]
   (entry--simple dict :pad-before)))

(def entry--application-id [{:loc    :pate-verdict.application-id
                             :source :application-id
                             :styles [:bold :pad-after]}])

(def entry--rakennuspaikka
  (list [{:loc    :rakennuspaikka._group_label
          :styles :bold}]
        [{:loc    :rakennuspaikka.kiinteisto.kiinteistotunnus
          :source :property-id}]
        (entry--simple :address [])
        [{:loc    :rakennuspaikka.kiinteisto.tilanNimi
          :source {:doc [:rakennuspaikka :kiinteisto.tilanNimi]}}]
        [{:loc    :pdf.pinta-ala
          :source {:doc [:rakennuspaikka :kiinteisto.maapintaala]}}
         {:unit :ha}]
        [{:loc    :rakennuspaikka.kaavatilanne._group_label
          :source {:doc [:rakennuspaikka :kaavatilanne]}
          :styles :pad-after}
         {:loc-prefix :rakennuspaikka.kaavatilanne}]))

(defn entry--applicant [loc loc-many]
  [{:loc      loc
    :loc-many loc-many
    :source   :applicants
    :styles   [:pad-before :border-bottom]}])

(def entry--operation [{:loc      :applications.operation
                        :loc-many :operations
                        :source   :operations
                        :styles   :bold}
                       {:path     :text}])

(def entry--complexity [{:loc    :pate.complexity
                         :source :complexity
                         :styles [:spaced :pad-after :pad-before]}])

(def entry--designers '([{:loc    :pdf.design-complexity
                          :source :designers
                          :styles :pad-before}
                         {:path   :role
                          :styles :nowrap}
                         {:path       :difficulty
                          :width      100
                          :loc-prefix :osapuoli.suunnittelutehtavanVaativuusluokka}]
                        [{:loc      :pdf.designer
                          :loc-many :pdf.designers
                          :source   :designers
                          :styles   :pad-before}
                         {:path   :role
                          :styles :nowrap}
                         {:path  :person
                          :width 100}]))

(def entry--dimensions '([{:loc    :verdict.kerrosala
                           :source :primary
                           :styles :pad-before}
                          {:path :mitat.kerrosala
                           :unit :m2}]
                         [{:loc    :verdict.kokonaisala
                           :source :primary}
                          {:path :mitat.kokonaisala
                           :unit :m2}]
                         [{:loc    :pdf.volume
                           :source :primary}
                          {:path :mitat.tilavuus
                           :unit :m3}]
                         [{:loc    :purku.mitat.kerrosluku
                           :source :primary}
                          {:path :mitat.kerrosluku}]))

(def entry--buildings '([{:loc    :pate-buildings.info.paloluokka
                          :source :paloluokka}]
                        [{:loc    :pdf.parking
                          :source :parking
                          :styles :pad-before}
                         {:path   :text
                          :styles :nowrap}
                         {:path   :amount
                          :styles :right}
                         {:text  ""
                          :width 100}]))

(def entry--statements '([{:loc      :statement.lausunto
                           :loc-many :pate-statements
                           :source   :statements
                           :styles   [:bold :border-top]}]))

(def entry--neighbors (entry--simple :neighbors [:bold :pad-before]))

(def entry--attachments [{:loc      :verdict.attachments
                          :source   :attachments
                          :styles   [:bold :pad-before]}
                         {:path   :text
                          :styles :nowrap}
                         {:path   :amount
                          :styles [:right :nowrap]
                          :unit   :kpl}
                         {:text  ""
                          :width 100}])

(def entry--verdict '([{:loc    :pate-verdict
                        :source {:dict :verdict-code}
                        :styles [:bold :border-top]}
                       {:loc-prefix :pate-r.verdict-code}]
                      [{:loc    :empty
                        :source {:dict :verdict-text}
                        :styles :pad-before}]))

(def entry--foremen [{:loc      :pdf.required-foreman
                      :loc-many :verdict.vaaditutTyonjohtajat
                      :source   {:dict :foremen}
                      :styles   :pad-before}
                     {:loc-prefix :pate-r.foremen}])

(def entry--reviews [{:loc      :pdf.required-review
                      :loc-many :verdict.vaaditutKatselmukset
                      :source   :reviews
                      :styles   :pad-before}])

(def entry--review-info [{:loc    :empty
                          :source :review-info}])

(def entry--plans [{:loc      :pdf.required-plan
                    :loc-many :verdict.vaaditutErityissuunnitelmat
                    :source   :plans
                    :styles   :pad-before}])

(def entry--conditions [{:loc      :pdf.condition
                         :loc-many :pdf.conditions
                         :source   :conditions
                         :styles   [:pad-before]}])

(def entry--collateral [{:loc    :pate-collateral
                         :source :collateral
                         :styles :pad-before}])

(defn entry--verdict-giver [handler-loc]
  (list [{:loc    :empty
          :source {:dict :verdict-date}
          :styles :pad-before}]
        [{:loc handler-loc
          :source :handler
          :styles :pad-before}]
        [{:loc    :empty
          :source :organization
          :styles :pad-after}]))

(def entry--dates '([{:loc    :pdf.julkipano
                      :source {:dict :julkipano}}]
                    [{:loc    :pdf.anto
                      :source {:dict :anto}}]
                    [{:loc    :pdf.muutoksenhaku
                      :source :muutoksenhaku}]
                    [{:loc    :pdf.voimassa
                      :source :voimassaolo}]))

(def entry--dates-ya '([{:loc    :pdf.julkipano
                         :source {:dict :julkipano}}]
                       [{:loc    :pdf.anto
                         :source {:dict :anto}}]
                       [{:loc    :pdf.muutoksenhaku
                         :source :muutoksenhaku}]
                       [{:loc    :pdf.voimassa
                         :source :voimassaolo-ya}]))

(def entry--appeal ;; Page break
  [{:loc    :pate-verdict.muutoksenhaku
    :source {:dict :appeal}
    :styles [:bold :page-break]}])

(def entry--link-permits '([{:loc      :linkPermit.dialog.header
                             :loc-many :application.linkPermits
                             :source :link-permits
                             :styles :pad-before}
                            {:path :id
                             :styles :nowrap}
                            {:path :operation
                             :loc-prefix :operations}]))


(defn combine-entries
  "Entries that are lists (not vectors!) are interpreted as multiple
  entries."
  [& entries]
  (reduce (fn [acc entry]
            (concat acc (cond-> entry
                          (not (list? entry)) vector)))
          []
          entries))

(defn build-layout [& entries]
  (sc/validate PdfLayout
               {:left-width 30
                :entries (apply combine-entries entries)}))

(def r-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--simple :purpose)
                (entry--applicant :pdf.achiever :pdf.achievers)
                entry--operation
                (entry--simple :extra-info)
                entry--complexity
                (entry--simple :rights)
                entry--designers
                entry--dimensions
                entry--buildings
                (entry--simple :deviations)
                entry--statements
                entry--neighbors
                entry--attachments
                entry--verdict
                entry--foremen
                entry--reviews
                entry--plans
                entry--conditions
                entry--collateral
                (entry--verdict-giver :applications.authority)
                entry--dates
                entry--appeal))

(def p-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--simple :purpose)
                (entry--applicant :applicant :pdf.applicants)
                entry--operation
                (entry--simple :deviations)
                entry--statements
                entry--neighbors
                (entry--simple :start-info)
                entry--conditions
                (entry--verdict-giver :pate.prepper)
                entry--verdict
                (entry--simple :rationale)
                (entry--simple :legalese)
                (entry--simple :giving)
                entry--dates
                (entry--simple :next-steps)
                (entry--simple :buyout)
                entry--attachments
                (entry--simple :fyi)
                entry--collateral
                entry--appeal))

(def ya-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--applicant :pdf.achiever :pdf.achievers)
                entry--operation
                entry--statements
                (entry--simple :inform-others)
                entry--attachments
                entry--verdict
                entry--reviews
                entry--review-info
                entry--plans
                entry--conditions
                (entry--verdict-giver :applications.authority)
                entry--dates-ya
                entry--link-permits
                entry--appeal))

;; ----------------------------------
;; Contract layout
;; ----------------------------------

(def entry--contract-text [{:loc    :empty
                            :source {:dict :contract-text}
                            :styles :pad-before}])

(def entry--case [{:loc       :pdf.contract.case
                   :loc-many :pdf.contract.cases
                   :source   :operations
                   :styles   :bold}
                  {:path :text}])

(def entry--contract-conditions
  [{:loc      :pate.contract.condition
    :loc-many :pate.contract.conditions
    :source   :conditions
    :styles   :pad-before}])

(def entry--contract-giver
  '([{:loc    :verdict.name.sijoitussopimus
      :source {:dict :handler}
      :styles :pad-before}]
    [{:loc    :empty
      :source :organization
      :styles :pad-after}]))

(def entry--contract-date
  '([{:loc    :verdict.contract.date
      :source {:dict :verdict-date}}]))

(def entry--contract-signatures
  '([{:loc      :pdf.signature
      :loc-many :verdict.signatures
      :source   :signatures
      :styles   [:bold :pad-before]}
     {:path :name}
     {:path :date}]))

(def contract-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--applicant :applicant :pdf.applicants)
                entry--case
                entry--contract-text
                entry--reviews
                entry--contract-conditions
                entry--contract-giver
                entry--contract-date
                entry--attachments
                entry--contract-signatures))


;; --------------------------------
;; Layouts for legacy verdicts
;; --------------------------------

(defn repeating-texts-post-fn [text-key]
  (util/fn->> vals
              (map (comp s/trim text-key))
              (remove s/blank?)))

(def legacy--application-id [{:loc    :applications.id.longtitle
                              :source :application-id
                              :styles [:bold]}])

(def legacy--kuntalupatunnus [{:loc    :verdict.id
                               :source {:dict :kuntalupatunnus}
                               :styles [:bold :pad-after]}])

(def legacy--verdict-code [{:loc    :pate-verdict
                            :source {:dict :verdict-code}
                            :styles [:bold :border-top]}
                           {:loc-prefix :verdict.status}])

(def legacy--verdict-text [{:loc    :empty
                            :source {:dict :verdict-text}
                            :styles :pad-before}])

(def legacy--foremen [{:loc      :pdf.required-foreman
                       :loc-many :verdict.vaaditutTyonjohtajat
                       :source   {:dict :foremen}
                       :post-fn  (repeating-texts-post-fn :role)
                       :styles   :pad-before}])

(def legacy--reviews [{:loc      :pdf.required-review
                       :loc-many :verdict.vaaditutKatselmukset
                       :source   {:dict :reviews}
                       :post-fn  (repeating-texts-post-fn :name)
                       :styles   :pad-before}])

(def legacy--conditions [{:loc      :verdict.muuMaarays
                          :loc-many :verdict.muutMaaraykset
                          :source   {:dict :conditions}
                          :post-fn (repeating-texts-post-fn :name)
                          :styles   :pad-before}])

(def legacy--verdict-giver '([{:loc    :verdict.name
                               :source {:dict :handler}
                               :styles :pad-before}]
                             [{:loc    :empty
                               :source :organization
                               :styles :pad-after}]))
(def legacy--dates '([{:loc    :pdf.anto
                       :source {:dict :anto}}]
                     [{:loc    :pdf.lainvoimainen
                       :source {:dict :lainvoimainen}}]))

(def entry--dates-tj '([{:loc    :pdf.anto
                       :source {:dict :anto}}]
                      [{:loc    :pdf.lainvoimainen
                        :source {:dict :lainvoimainen}}]
                      [{:loc    :pdf.muutoksenhaku
                        :source {:dict :muutoksenhaku}}]))

(def entry--tj (list [{:loc    :pdf.tj
                       :source {:doc [:tyonjohtaja-v2 :kuntaRoolikoodi]}
                       :styles [:bold :border-top]}]
                     [{:loc      :empty
                      :source   {:doc [:tyonjohtaja-v2 :henkilotiedot]}}
                      {:path   :etunimi
                       :styles [:nowrap :right]}
                      {:path   :sukunimi
                       :styles :nowrap}
                      {:text  ""
                       :width 100}]
                     [{:loc    :empty
                       :source {:doc [:tyonjohtaja-v2 :patevyys-tyonjohtaja.koulutusvalinta]}}]
                     [{:loc    :empty
                       :source {:doc [:tyonjohtaja-v2 :yhteystiedot.puhelin]}}]))

(def entry--tj-vastattavat-tyot [{:loc    :pdf.tj.vastattavat
                                  :source :tj-vastattavat-tyot
                                  :styles :pad-before}])

(def r-legacy-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :pdf.achiever :pdf.achievers)
                entry--operation
                entry--designers
                entry--dimensions
                entry--statements
                entry--neighbors
                entry--attachments
                legacy--verdict-code
                legacy--verdict-text
                legacy--foremen
                legacy--reviews
                legacy--conditions
                legacy--verdict-giver
                legacy--dates))

(def tj-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                entry--tj
                entry--link-permits
                entry--attachments
                entry--tj-vastattavat-tyot
                entry--verdict
                (entry--verdict-giver :applications.authority)
                entry--dates-tj
                entry--appeal))

(def ya-legacy-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :pdf.achiever :pdf.achievers)
                entry--operation
                entry--statements
                entry--attachments
                legacy--verdict-code
                legacy--verdict-text
                legacy--reviews
                legacy--conditions
                legacy--verdict-giver
                legacy--dates))

(def p-legacy-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :applicant :pdf.applicants)
                entry--operation
                entry--statements
                entry--neighbors
                entry--attachments
                legacy--verdict-code
                legacy--verdict-text
                legacy--conditions
                legacy--verdict-giver
                legacy--dates))

(def kt-legacy-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :applicant :pdf.applicants)
                entry--operation
                entry--statements
                entry--neighbors
                entry--attachments
                (butlast legacy--verdict-code)
                legacy--verdict-text
                legacy--conditions
                legacy--verdict-giver
                legacy--dates))

(def tj-legacy-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                entry--tj
                entry--link-permits
                entry--attachments
                entry--tj-vastattavat-tyot
                entry--verdict
                (entry--verdict-giver :applications.authority)
                entry--dates-tj
                entry--appeal))

(def ymp-legacy-layout kt-legacy-layout)

;; ----------------------------------
;; Legacy contracts
;; ----------------------------------

#_(def legacy--contract-text [{:loc    :empty
                            :source {:dict :contract-text}
                            :styles :pad-before}])

#_(def entry--case [{:loc       :pdf.contract.case
                   :loc-many :pdf.contract.cases
                   :source   :operations
                   :styles   :bold}
                  {:path :text}])

(def legacy--contract-conditions
  [{:loc      :pate.contract.condition
    :loc-many :pate.contract.conditions
    :source   {:dict :conditions}
    :post-fn (repeating-texts-post-fn :name)
    :styles   :pad-before}])

#_(def legacy--contract-giver
  '([{:loc    :verdict.name.sijoitussopimus
      :source {:dict :handler}
      :styles :pad-before}]
    [{:loc    :empty
      :source :organization
      :styles :pad-after}]))

#_(def legacy--contract-date
  '([{:loc    :verdict.contract.date
      :source {:dict :verdict-date}}]))

#_(def legacy--contract-signatures
  '([{:loc      :pdf.signature
      :loc-many :verdict.signatures
      :source   :signatures
      :styles   [:bold :pad-before]}
     {:path :name}
     {:path :date}]))

(def contract-legacy-layout
  (build-layout entry--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :applicant :pdf.applicants)
                entry--case
                entry--contract-text
                legacy--reviews
                legacy--contract-conditions
                entry--contract-giver
                entry--contract-date
                entry--attachments
                entry--contract-signatures))

(defn pdf-layout [{:keys [category legacy?]}]
  (case (util/kw-path (when legacy? :legacy) category)
    :r               r-pdf-layout
    :p               p-pdf-layout
    :ya              ya-pdf-layout
    :tj              tj-pdf-layout
    :contract        contract-pdf-layout
    :legacy.r        r-legacy-layout
    :legacy.ya       ya-legacy-layout
    :legacy.p        p-legacy-layout
    :legacy.kt       kt-legacy-layout
    :legacy.ymp      ymp-legacy-layout
    :legacy.contract contract-legacy-layout
    :legacy.tj       tj-legacy-layout))
