(ns lupapalvelu.pate.pdf-layouts
  "Verdict PDF layout definitions. Note: keyword
  sources (e.g., :application-id) are defined in
  `lupapalvelu.pate.pdf/verdict-properties`."
  (:require [sade.strings :as ss]
            #?(:clj  [lupapalvelu.i18n :as i18n]
               :cljs [lupapalvelu.ui.common :as common])
            #?(:clj  [sade.date :as date])
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]))

#?(:clj (def finnish-date date/finnish-date)
   :cljs (def finnish-date common/format-timestamp))

#?(:clj (def localize i18n/localize)
   :cljs (defn localize [_ & terms]
           (->> (flatten terms)
                (map name)
                (ss/join "." )
                common/loc)))

#?(:clj (def localize-and-fill i18n/localize-and-fill)
   :cljs (defn localize-and-fill [lang term & values]
           (let [s (localize lang term)]
             (reduce (fn [acc i]
                       (ss/replace acc
                                  (js/sprintf "{%s}" i)
                                  (nth values i)))
                     s
                     (range (count values))))))

#?(:clj (def has-term? i18n/has-term?)
   :cljs (def has-term? js/loc.hasTerm))

(def cell-widths (range 5 101 5))
(def row-styles [:pad-after :pad-before
                 :border-top :border-bottom
                 :page-break :bold :spaced
                 :indent])
(def cell-styles [:bold :center :right :tight-right :nowrap :border-top])

(defn add-unit
  "Result is nil for blank value."
  [lang unit v]
  (when-not (ss/blank? (str v))
    (case unit
      :ha      (str v " " (localize lang :unit.hehtaaria))
      :cm      [:span {} v " cm"]
      :m       [:span {} v " m"]
      :m2      [:span {} v " m" [:sup 2]]
      :m3      [:span {} v " m" [:sup 3]]
      :kpl     (str v " " (localize lang :unit.kpl))
      :section (str "\u00a7" v)
      :eur     (str v "\u20ac"))))

(defschema Source
  "Value of PdfEntry :source property aka data source for the row."
  (sc/conditional
   ;; Keyword corresponds to a key in the data context.
   keyword? sc/Keyword
   :else (shared-schemas/only-one-of [:doc :dict]
                                     ;; Vector is a path to application document data. The
                                     ;; first item is the document name or document id and
                                     ;; rest are path within the data.
                                     {(sc/optional-key :doc)     [(sc/conditional keyword? sc/Keyword string? sc/Str)]
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
            (sc/optional-key :loc)      sc/Keyword
            ;; Title as a text. Shown as such. Mutually exclusive with `:loc`
            (sc/optional-key :text)     sc/Str
            ;; If :loc-many is given it is used as the title key if
            ;; the source value denotes multiple values.
            (sc/optional-key :loc-many) sc/Keyword
            ;; Localization rule which can be used for different localization key
            ;; values based on application details.
            ;; For example: {:rule [:application :operation-name] :key :applications.operation}
            ;; adds operation name from application at end of given key, like:
            ;; applications.operation.ya-jatkoaika. If the localization key is not found, only
            ;; given :key value is used as localization key.
            (sc/optional-key :loc-rule) {:rule sc/Keyword :key sc/Keyword}
            (sc/optional-key :source)   Source
            ;; Post-processing function for source value.
            (sc/optional-key :post-fn)  (sc/conditional
                                          keyword? sc/Keyword
                                          :else   (sc/pred fn?))
            (sc/optional-key :styles)   (styles row-styles)
            (sc/optional-key :id)       sc/Keyword}
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
    (sc/optional-key :unit)       (sc/enum :ha :cm :m :m2 :m3 :kpl :section :eur)
    ;; Additional localisation key prefix. Is
    ;; applied both to path and text values.
    (sc/optional-key :loc-prefix) shared-schemas/path-type
    (sc/optional-key :styles)     (styles cell-styles)}])

(defschema RawEntry
  "Raw markup that is converted to html without further checks."
  {:raw sc/Any})


(defschema PdfLayout
  "PDF contents layout."
  {;; Width of the left-hand side.
   :left-width (apply sc/enum cell-widths)
   :entries    [(sc/conditional
                  :raw  RawEntry
                  :else PdfEntry)]})

;; ------------------------------
;; Entries
;; ------------------------------

(defn entry--simple
  ([dict styles]
   [{:loc    (case dict
               :collateral    :pate-collateral
               :deviations    :pate-deviations
               :extra-info    :pate-extra-info
               :giving        :pate.verdict-giving
               :purpose       :pate-purpose
               :rationale     :pate.verdict-rationale
               :rights        :pate-rights
               :start-info    :pate-start-info
               :neighbors     :phrase.category.naapurit
               :inform-others :pate-inform-others
               :appellants    :pate.appellant
               (util/kw-path :pate dict))
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

(def entry--rakennuspaikka-ya
  (list [{:loc    :rakennuspaikka._group_label
          :styles :bold}]
        [{:loc    :rakennuspaikka.kiinteisto.kiinteistotunnus
          :source :property-id-ya}]
        [{:loc    :pate.location
          :source {:dict :address}}]
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

(def entry--construction-site-responsible [{:loc      :pdf.construction-site.responsible
                                            :loc-many :pdf.construction-site.responsibles
                                            :source   :site-responsibles
                                            :styles   [:pad-before :border-bottom]}])

(def entry--operation [{:loc      :applications.operation
                        :loc-many :operations
                        :loc-rule {:rule :application.primaryOperation.name :key :applications.operation}
                        :source   :operations
                        :styles   :bold}
                       {:path     :text}])

(def entry--complexity [{:loc    :pate.complexity
                         :source :complexity
                         :styles [:spaced :pad-after :pad-before]}])

(def entry--designers '([{:loc    :pdf.design-complexity
                          :source :designers
                          :styles :pad-before}
                         {:path  :role
                          :width 50}
                         {:path       :difficulty
                          :width      50
                          :loc-prefix :osapuoli.suunnittelutehtavanVaativuusluokka}]
                        [{:loc      :pdf.designer
                          :loc-many :pdf.designers
                          :source   :designers
                          :styles   :pad-before}
                         {:path  :role
                          :width 50}
                         {:path  :person
                          :width 50}]))

(def entry--dimensions '([{:loc    :verdict.kerrosala
                           :source :dimensions
                           :styles :pad-before}
                          {:path :kerrosala
                           :unit :m2}]
                         [{:loc    :pdf.rakennusoikeudellinen-kerrosala
                           :source :dimensions}
                          {:path :rakennusoikeudellinenKerrosala
                           :unit :m2}]
                         [{:loc    :pdf.rakennusoikeudellinen-tilavuus
                           :source :dimensions}
                          {:path :rakennusoikeusKuutiometreina
                           :unit :m3}]
                         [{:loc    :pdf.volume
                           :source :dimensions}
                          {:path :tilavuus
                           :unit :m3}]
                         [{:loc    :pdf.rakennettavaKokonaisala
                           :source :dimensions}
                          {:path :rakennettavaKokonaisala
                           :unit :m2}]
                         [{:loc    :pdf.laajennettavaKokonaisala
                           :source :dimensions}
                          {:path :laajennettavaKokonaisala
                           :unit :m2}]
                         [{:loc    :pdf.uusiKokonaisala
                           :source :dimensions}
                          {:path :uusiKokonaisala
                           :unit :m2}]))

(def entry--buildings '([{:loc    :pate-buildings.info.paloluokka
                          :source :paloluokka}]
                        [{:loc    :pate-buildings.info.vss-luokka
                          :source :vss-luokka}]
                        [{:loc    :pate-buildings.info.kokoontumistilanHenkilomaara
                          :source :kokoontumistilanHenkilomaara}]
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

(defn entry--verdict [proposal-reasoning?]
  (->> [[{:loc    :pate-verdict-proposal
          :source {:dict :proposal-text}
          :styles [:bold :border-top]}]
        (when proposal-reasoning?
          [{:loc    :pate-verdict-proposal-reasoning
            :source {:dict :proposal-reasoning-text}
            :styles [:bold :border-bottom]}])
        [{:loc    :pate-verdict
          :source {:dict :verdict-code}
          :styles [:bold :pad-before]}
         {:loc-prefix :pate.verdict-code}]
        [{:loc    :empty
          :source {:dict :verdict-text}
          :styles :pad-before}]]
       (remove nil?)
       (apply list))) ; needs to be a list for the layout macro

(def entry--foremen [{:loc      :pdf.required-foreman
                      :loc-many :verdict.vaaditutTyonjohtajat
                      :source   :foremen
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
  (list [{:loc    handler-loc
          :source :handler
          :styles :pad-before}]
        [{:loc    :pate-verdict.giver
          :source :giver}]
        [{:loc    :empty
          :source :organization
          :styles :pad-after}]))

(defn entry--handler [handler-loc]
  (list [{:loc    handler-loc
          :source :handler
          :styles [:pad-before :pad-after]}]))

(def entry--dates '([{:loc    :info.verdictDate
                      :source {:dict :verdict-date}}]
                    [{:loc    :pdf.julkipano
                      :source {:dict :julkipano}}]
                    [{:loc    :pdf.kuulutus
                      :source {:dict :k-kuulutus}}]
                    [{:loc    :pdf.anto
                      :source {:dict :anto}}]
                    [{:loc    :pdf.muutoksenhaku
                      :source :muutoksenhaku}]
                    [{:loc    :pdf.lainvoimainen
                      :source {:dict :lainvoimainen}}]
                    [{:loc    :pdf.lainvoimainen
                      :source {:dict :k-lainvoimainen}}]
                    [{:loc    :pdf.voimassa
                      :source :voimassaolo}]))

(def entry--dates-p '([{:loc    :info.verdictDate
                        :source {:dict :verdict-date}}]
                      [{:loc    :pdf.julkipano
                        :source {:dict :julkipano}}]
                      [{:loc    :pdf.kuulutus
                      :source {:dict :k-kuulutus}}]
                      [{:loc    :pdf.anto
                        :source {:dict :anto}}]
                      [{:loc    :pdf.muutoksenhaku
                        :source :muutoksenhaku}]
                      [{:loc    :pdf.lainvoimainen
                        :source {:dict :lainvoimainen}}]
                      [{:loc    :pdf.lainvoimainen
                        :source {:dict :k-lainvoimainen}}]
                      [{:loc    :pdf.voimassa
                        :source :voimassaolo-p}]))

(def entry--dates-ya '([{:loc    :info.verdictDate
                         :source {:dict :verdict-date}}]
                       [{:loc    :pdf.julkipano
                         :source {:dict :julkipano}}]
                       [{:loc    :pdf.anto
                         :source {:dict :anto}}]
                       [{:loc    :pdf.muutoksenhaku
                         :source :muutoksenhaku}]
                       [{:loc    :pdf.lainvoimainen
                         :source {:dict :lainvoimainen}}]
                       [{:loc    :pdf.voimassa
                         :source :voimassaolo-ya}]))

(def entry--appeal ;; Page break
  [{:loc    :pdf.muutoksenhakuohje
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

(def entry--processing '([{:styles :pad-before
                           :loc    :pdf.processing.months
                           :source :processing}
                          {:path :months}]
                         [{:loc    :pdf.processing.used
                           :source :processing}
                          {:path :used}]
                         [{:styles :pad-before
                           :loc :empty
                           :source :processing}
                          {:path :text}]))

(defn combine-entries
  "Entries that are lists (not vectors!) are interpreted as multiple
  entries."
  [& entries]
  (reduce (fn [acc entry]
            (concat acc (cond-> entry
                          (not (list? entry)) vector)))
          []
          entries))

(def ^:private valid-pdf-layout! (sc/validator PdfLayout))

(defn build-layout [& entries]
  (valid-pdf-layout!
    {:left-width 30
     :entries    (apply combine-entries entries)}))

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
                (entry--verdict false)
                (entry--simple :rationale)
                (entry--simple :legalese)
                entry--foremen
                entry--reviews
                entry--plans
                entry--conditions
                entry--collateral
                entry--processing
                (entry--verdict-giver :applications.authority)
                entry--dates
                entry--link-permits
                entry--appeal))

(def p-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--simple :purpose)
                (entry--applicant :applicant :pdf.applicants)
                entry--operation
                entry--designers
                (entry--simple :deviations)
                entry--statements
                entry--neighbors
                (entry--simple :start-info)
                entry--conditions
                (entry--verdict-giver :pate.prepper)
                (entry--verdict false)
                (entry--simple :rationale)
                (entry--simple :legalese)
                (entry--simple :giving)
                entry--dates-p
                (entry--simple :next-steps)
                (entry--simple :buyout)
                entry--attachments
                (entry--simple :fyi)
                entry--collateral
                entry--link-permits
                entry--appeal))

(def ya-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka-ya
                (entry--applicant :pdf.applicant :pdf.applicants)
                entry--construction-site-responsible
                entry--operation
                entry--statements
                (entry--simple :inform-others)
                entry--attachments
                (entry--verdict false)
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

(def entry--case [{:loc      :pdf.contract.case
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
    [{:loc    :pate-contract.signer
      :source :giver}]
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
      :styles   [:bold :pad-before]
      :id       :signatures}
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
              (map (comp ss/trim text-key))
              (remove ss/blank?)))

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
                     [{:loc    :info.verdictDate
                       :source {:dict :k-verdict-date}}]
                     ;; Note that neither julkipano or k-kuulutus is included
                     [{:loc    :pdf.lainvoimainen
                       :source {:dict :lainvoimainen}}]
                     [{:loc    :pdf.lainvoimainen
                       :source {:dict :k-lainvoimainen}}]))

(def entry--dates-tj '([{:loc    :info.verdictDate
                         :source {:dict :verdict-date}}]
                       [{:loc    :pdf.anto
                       :source {:dict :anto}}]
                      [{:loc    :pdf.lainvoimainen
                        :source {:dict :lainvoimainen}}]
                      [{:loc    :pdf.muutoksenhaku
                        :source {:dict :muutoksenhaku}}]
                      [{:loc    :pdf.responsibilities-start-date
                        :source {:dict :responsibilities-start-date}}]))

(defn entry--tj
  [tj-doc-name]
  (list [{:loc    :pdf.tj
          :source {:doc [tj-doc-name :kuntaRoolikoodi]}
          :styles [:bold :border-top]}
         {:loc-prefix :osapuoli.tyonjohtaja.kuntaRoolikoodi}]
        [{:loc    :empty
          :source {:doc [tj-doc-name :henkilotiedot]}}
         {:path   :etunimi
          :styles [:nowrap :right]}
         {:path   :sukunimi
          :styles :nowrap}
         {:text  ""
          :width 100}]
        [{:loc    :empty
          :source {:doc [tj-doc-name :patevyys-tyonjohtaja.koulutusvalinta]}}
         {:loc-prefix :koulutus}]
        [{:loc    :empty
          :source {:doc [tj-doc-name :yhteystiedot.puhelin]}}]))

(def entry--tj-vastattavat-tyot [{:loc    :pdf.tj.vastattavat
                                  :source :tj-vastattavat-tyot
                                  :styles :pad-before}])

(def entry--rectifications [{:loc     :pate.verdict-rectifications
                             :source  :verdict-rectifications
                             :post-fn (partial map #(update % :datestamp finnish-date))
                             :styles  [:bold :pad-before :pad-after]}
                            {:path   :rectification-id
                             :styles :nowrap}
                            {:path   :appellant
                             :styles :nowrap}
                            {:path   :datestamp
                             :styles :nowrap}
                            ;; Make the table more compact
                            {:text  ""
                             :width 100}])

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
                (entry--verdict-giver :applications.authority)
                legacy--dates))

(def tj-pdf-layout
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--tj :tyonjohtaja-v2)
                entry--link-permits
                entry--attachments
                entry--tj-vastattavat-tyot
                (entry--verdict false)
                (entry--verdict-giver :applications.authority)
                entry--dates-tj
                entry--appeal))

(defn tj-v3-pdf-layout [doc-id]
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--tj doc-id)
                entry--link-permits
                entry--attachments
                entry--tj-vastattavat-tyot
                (entry--verdict false)
                (entry--verdict-giver :applications.authority)
                entry--dates-tj
                entry--appeal))

(def oik-pdf-layout
  (build-layout entry--application-id
                entry--rectifications
                entry--operation
                entry--rakennuspaikka
                (entry--simple :purpose)
                (entry--applicant :pdf.achiever :pdf.achievers)
                (entry--simple :appellants)
                (entry--simple :phases)
                (entry--verdict true)
                entry--attachments
                (entry--handler :pate.prepper)
                entry--dates))

(def ya-legacy-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :pdf.applicant :pdf.applicants)
                entry--construction-site-responsible
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
                (entry--applicant :pdf.applicant :pdf.applicants)
                entry--operation
                entry--statements
                entry--neighbors
                entry--attachments
                legacy--verdict-code
                legacy--verdict-text
                legacy--conditions
                (entry--verdict-giver :applications.authority)
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

(defn tj-legacy-layout [doc-name-or-id]
  (build-layout entry--application-id
                entry--rakennuspaikka
                (entry--tj doc-name-or-id)
                entry--link-permits
                entry--attachments
                entry--tj-vastattavat-tyot
                legacy--verdict-code
                legacy--verdict-text
                (entry--verdict-giver :applications.authority)
                entry--dates-tj
                entry--appeal))

(def ymp-legacy-layout kt-legacy-layout)

;; ----------------------------------
;; Legacy contracts
;; ----------------------------------

(def legacy--contract-conditions
  [{:loc      :pate.contract.condition
    :loc-many :pate.contract.conditions
    :source   {:dict :conditions}
    :post-fn (repeating-texts-post-fn :name)
    :styles   :pad-before}])

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

;; ----------------------------------
;; Backing system.
;; Only used for creating tags for UI. The PDF is never generated as
;; it is received from the backing system. Note that a verdict can
;; consist of multiple sub-verdicts (poytakirja).
;; ----------------------------------

(def backing--kuntalupatunnus [{:loc    :verdict.id
                                :source :kuntalupatunnus
                                :styles [:bold :pad-after]}])

(defn- autopaikka [k]
  [{:loc    (util/kw-path :verdict k)
    :source k}
   {:unit :kpl}])

(def backing--autopaikat (apply list (map autopaikka
                                          [:autopaikkojaKiinteistolla
                                           :autopaikkojaUlkopuolella
                                           :autopaikkojaRakennettu
                                           :autopaikkojaVahintaan
                                           :autopaikkojaEnintaan
                                           :autopaikkojaRakennettava])))

(defn- area [k]
  [{:loc    (util/kw-path :verdict k)
    :source k}
   {:unit   :m2}])

(def backing--areas (apply list (map area [:kerrosala :kokonaisala])))

(def backing--maaraykset [{:loc-many :verdict.lupamaaraykset
                           :loc      :pate-verdict.lupamaarays
                           :source   :maaraykset
                           :styles [:pad-before]}])

(def backing--muut-maaraykset [{:loc-many :verdict.muutMaaraykset
                           :loc      :pate-verdict.muu-maarays
                           :source   :muutMaaraykset
                           :styles [:pad-before]}])

(def backing--katselmukset [{:loc      :pdf.required-review
                             :loc-many :verdict.vaaditutKatselmukset
                             :source   :vaaditutKatselmukset
                             :styles   [:pad-before]}])

(def backing--tyonjohtajat [{:loc    :verdict.vaaditutTyonjohtajat
                             :source :vaaditutTyonjohtajat
                             :styles [:pad-before]}])

(defn- paiva [k]
  [{:loc     (util/kw-path :verdict k)
    :source  k
    :post-fn finnish-date}])

(def backing--paivamaarat (apply list (map paiva [:anto
                                                  :julkipano
                                                  :viimeinenValitus
                                                  :aloitettava
                                                  :voimassaHetki
                                                  :raukeamis
                                                  :lainvoimainen
                                                  :paatosdokumentinPvm])))

(def backing-kuntalupatunnus-layout (build-layout backing--kuntalupatunnus))

(def backing-paatos-layout (build-layout backing--autopaikat
                                         backing--areas
                                         backing--maaraykset
                                         backing--muut-maaraykset
                                         backing--katselmukset
                                         backing--tyonjohtajat
                                         backing--paivamaarat))

(def kuulutus-mapping
  {:julkipano :kuulutus})

(defn- kuulutus-paiva [k]
  [{:loc     (util/kw-path :verdict (get kuulutus-mapping k k))
    :source  k
    :post-fn finnish-date}])

(def backing--kuulutus-paivamaarat (->> [:julkipano
                                         :viimeinenValitus
                                         :aloitettava
                                         :voimassaHetki
                                         :raukeamis
                                         :lainvoimainen
                                         :paatosdokumentinPvm]
                                        (map kuulutus-paiva)
                                        (apply list)))

(def backing-paatos-layout-kuulutus (build-layout backing--autopaikat
                                                  backing--areas
                                                  backing--maaraykset
                                                  backing--muut-maaraykset
                                                  backing--katselmukset
                                                  backing--tyonjohtajat
                                                  backing--kuulutus-paivamaarat))

;; Poytakirja

(def pk--verdict-code [{:loc    :pate-verdict
                        :source :status
                        :styles [:bold :border-top]}
                       {:loc-prefix :verdict.status}])

(def pk--verdict-text [{:loc         :empty
                        :source :paatos
                        :styles :pad-before}])

(def pk--section [{:loc    :verdict.pykala
                   :source :pykala}
                  {:unit :section}])

(def pk--date [{:loc     :empty
                :source  :paatospvm
                :styles [:pad-before]
                :post-fn finnish-date}])

(def pk--author [{:loc    :empty
                  :source :paatoksentekija
                  :styles [:pad-after]}])

(defn linkify [{:keys [url text]}]
  ;; Extra "layer" needed for proper entry-row layout.
  [[:a {:href url :target "_blank"} text]])

(def pk--attachment [{:loc     :pdf.attachment
                      :source  :attachment
                      :post-fn linkify}])

(def backing-poytakirja-layout (build-layout pk--verdict-code
                                             pk--section
                                             pk--verdict-text
                                             pk--date
                                             pk--author
                                             pk--attachment))


;; Verdict migration layouts
(def migration-verdict-layout
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

(def migration-contract-layout
  (build-layout legacy--application-id
                legacy--kuntalupatunnus
                entry--rakennuspaikka
                (entry--applicant :pdf.applicant :pdf.applicants)
                entry--operation
                entry--designers
                entry--dimensions
                entry--statements
                entry--neighbors
                entry--attachments
                legacy--verdict-code
                entry--contract-text
                legacy--foremen
                legacy--reviews
                legacy--conditions
                legacy--verdict-giver
                legacy--dates
                entry--contract-signatures))


(defn pdf-layout
  "Determines which verdict elements are placed where on the verdict PDF
  (and also on the published verdict page!) and where they get their data from.

  This is one of _four_ definitions needed for a PATE verdict category:
  For the editor schema, see `lupapalvelu.pate.verdict-schemas/verdict-schema`
  For the verdict template, see `lupapalvelu.pate.verdict-template-schemas/verdict-template-schema`
  For the verdict template settings, see `lupapalvelu.pate.settings-schemas/settings-schema`"
  [{:keys [category legacy? document-id]}]
  (case (util/kw-path (when legacy? :legacy) category)
    :r               r-pdf-layout
    :p               p-pdf-layout
    :ya              ya-pdf-layout
    :tj              tj-pdf-layout
    :tj-v3           (tj-v3-pdf-layout document-id)
    :oik             oik-pdf-layout
    :contract        contract-pdf-layout
    :legacy.r        r-legacy-layout
    :legacy.ya       ya-legacy-layout
    :legacy.p        p-legacy-layout
    :legacy.kt       kt-legacy-layout
    :legacy.ymp      ymp-legacy-layout
    :legacy.contract contract-legacy-layout
    :legacy.tj       (tj-legacy-layout :tyonjohtaja-v2)
    :legacy.tj-v3    (tj-legacy-layout document-id)
    :legacy.migration-contract migration-contract-layout
    :legacy.migration-verdict  migration-verdict-layout))
