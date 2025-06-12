(ns lupapalvelu.document.vahahiilisyys-schemas
  (:require [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]))

(defn ^:ryhti tuote-category [name]
  {:name       name
   :type       :table
   :repeating  true
   :css        [:form-table]
   :approvable false
   :body       [{:name "tuote" :type :string :i18nkey "tuote"}
                {:name    "maara"
                 :type    :string
                 :subtype :number
                 :min     0
                 :i18nkey "maara"}
                {:name "yksikko" :type :select, :sortBy :displayname, :default "kpl"
                 :i18nkey "unit"
                 :body [{:name "kpl"}
                        {:name "kg"}
                        {:name "tonnia"}
                        {:name "m2"}
                        {:name "m3"}]}]})

(def ^:ryhti rakennustuoteluettelo-rakennuspaikka
  [{:name "laatimisPvm" :type :date :i18nkey "rakennustuoteluettelo.laatimisPvm"}
   {:name "laatija" :type :string :i18nkey "rakennustuoteluettelo.laatija"}
   {:name        "sisaltyvat-tuotteet"
    :i18nkey     "rakennustuoteluettelo"
    :type        :group
    :uicomponent :docgenGroup
    :approvable  false
    :rows        [{:h3 "rakennustuoteluettelo.sisaltyvat-tuotteet"}
                  ["alueen-rakenteet::4"]
                  ["perustukset::4"]
                  ["paallysteet::4"]
                  ["tuennat::4"]]
    :template    "form-grid-docgen-group-template"
    :body        [(tuote-category "alueen-rakenteet")
                  (tuote-category "perustukset")
                  (tuote-category "paallysteet")
                  (tuote-category "tuennat")]}])

(def ^:ryhti rakennustuoteluettelo-rakennuspaikka-hyodynnetyt
  [{:name "laatimisPvm" :type :date :i18nkey "rakennustuoteluettelo.laatimisPvm"}
   {:name "laatija" :type :string :i18nkey "rakennustuoteluettelo.laatija"}
   {:name        "hyodynnetyt-tuotteet"
    :i18nkey     "rakennustuoteluettelo"
    :type        :group
    :uicomponent :docgenGroup
    :approvable  false
    :rows        [{:h3 "rakennustuoteluettelo.hyodynnetyt-tuotteet"}
                  ["alueen-rakenteet::4"]
                  ["perustukset::4"]
                  ["paallysteet::4"]
                  ["tuennat::4"]]
    :template    "form-grid-docgen-group-template"
    :body        [(tuote-category "alueen-rakenteet")
                  (tuote-category "perustukset")
                  (tuote-category "paallysteet")
                  (tuote-category "tuennat")]}])

(def ^:ryhti rakennustuoteluettelo-hyodynnetyt
  [{:name "laatimisPvm" :type :date :i18nkey "rakennustuoteluettelo.laatimisPvm"}
   {:name "laatija" :type :string :i18nkey "rakennustuoteluettelo.laatija"}
   {:name        "hyodynnetyt-tuotteet"
    :i18nkey     "rakennustuoteluettelo"
    :type        :group
    :uicomponent :docgenGroup
    :approvable  false
    :rows        [{:h3 "rakennustuoteluettelo.hyodynnetyt-tuotteet"}
                  ["alapohja::4"]
                  ["hormit::4"]
                  ["jako-osat::4"]
                  ["julkisivut::4"]
                  ["kattorakenteet::4"]
                  ["ovet-ja-ikkunat::4"]
                  ["runko::4"]
                  ["tilapinnat::4"]
                  ["tilavarusteet::4"]
                  ["ulkotasot-ja-parvekkeet::4"]]
    :template    "form-grid-docgen-group-template"
    :body        [(tuote-category "alapohja")
                  (tuote-category "hormit")
                  (tuote-category "jako-osat")
                  (tuote-category "julkisivut")
                  (tuote-category "kattorakenteet")
                  (tuote-category "ovet-ja-ikkunat")
                  (tuote-category "runko")
                  (tuote-category "tilapinnat")
                  (tuote-category "tilavarusteet")
                  (tuote-category "ulkotasot-ja-parvekkeet")]}])

(def ^:ryhti rakennustuoteluettelo
  [{:name "laatimisPvm" :type :date :i18nkey "rakennustuoteluettelo.laatimisPvm"}
   {:name "laatija" :type :string :i18nkey "rakennustuoteluettelo.laatija"}
   {:name        "sisaltyvat-tuotteet"
    :i18nkey     "rakennustuoteluettelo"
    :type        :group
    :uicomponent :docgenGroup
    :approvable  false
    :rows        [{:h3 "rakennustuoteluettelo.sisaltyvat-tuotteet"}
                  ["alapohja::4"]
                  ["hormit::4"]
                  ["jako-osat::4"]
                  ["julkisivut::4"]
                  ["kattorakenteet::4"]
                  ["ovet-ja-ikkunat::4"]
                  ["runko::4"]
                  ["tilapinnat::4"]
                  ["tilavarusteet::4"]
                  ["ulkotasot-ja-parvekkeet::4"]]
    :template    "form-grid-docgen-group-template"
    :body        [(tuote-category "alapohja")
                  (tuote-category "hormit")
                  (tuote-category "jako-osat")
                  (tuote-category "julkisivut")
                  (tuote-category "kattorakenteet")
                  (tuote-category "ovet-ja-ikkunat")
                  (tuote-category "runko")
                  (tuote-category "tilapinnat")
                  (tuote-category "tilavarusteet")
                  (tuote-category "ulkotasot-ja-parvekkeet")]}])

(def ^:ryhti ilmastoselvityslaatimistiedot
  [{:name   "laatimistiedot"
    :i18nkey "ilmastoselvitys.tiedot"
    :css     [:hide-header "mx-2" "border-solid" "border-0" "border-b-2" "border-ui-dark-gray"]
    :type    :group
    :body    [(doc-schemas/ryhti-id :climateReportKey)
              {:name    "pvm"
               :type    :date
               :required true
               :i18nkey "ilmastoselvitys.pvm"}
              {:name    "laatija"
               :required true
               :type    :string
               :i18nkey "ilmastoselvitys.laatija"}
              {:name    "arvioinnissa-kaytetty-ohjelmisto"
               :required true
               :type    :string
               :i18nkey "ilmastoselvitys.arvioinnissa-kaytetty-ohjelmisto"}]}])

(def ^:ryhti hiilijalanjalkeen-raja-arvoista-poikkeaminen
  [{:name        "poikkeaminen"
    :i18nkey     "ilmastoselvitys.poikkeaminen"
    :type        :group
    :uicomponent :exceeding-carbon-threshold
    :css         ["mx-2" "border-solid" "border-0" "border-b-2" "border-ui-dark-gray"]
    :body        [(doc-schemas/ryhti-id :reasonForExceedingThresholdLimitKey)
                  {:name    "raja-arvoista-poikkeamisen-perusteen-laji"
                   :i18nkey "ilmastoselvitys.poikkeaminen.raja-arvoista-poikkeamisen-perusteen-laji"
                   :type    :select
                   :body    (doc-schemas/mapping->options ryhti-mappings/ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji-grouped
                                                          :i18nkey :suomi.fi.ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji)}
                  {:name   "perustelu"
                   :i18nkey "ilmastoselvitys.poikkeaminen.perustelu"
                   :type   :text
                   :layout :full-width}]}])

(defn ^:ryhti hiilikadenjalki [& {:keys [rakennuspaikka?]}]
  [{:name           "hiilikadenjalki"
    :i18nkey        (if rakennuspaikka?
                      "rakennuspaikan-hiilikadenjalki"
                      "hiilikadenjalki")
    :group-help     "help"
    :group-help-css ["p-4" "mt-2" "mb-6" "bg-ui-blue-10" "rounded-sm"]
    :type           :group
    :uicomponent    :docgenGroup
    :body           (doc-tools/body (doc-schemas/ryhti-id (if rakennuspaikka?
                                                            :buildingSiteCarbonHandprintKey
                                                            :buildingCarbonHandprintKey))
                                    [{:name    "uudelleenkaytto"
                                      :i18nkey "hiilikadenjalki.uudelleenkaytto"
                                      :type    :string
                                      :subtype :decimal
                                      :min     0}
                                     {:name    "kierratys"
                                      :i18nkey "hiilikadenjalki.kierratys"
                                      :type    :string
                                      :subtype :decimal
                                      :min     0}
                                     {:name    "ylimaarainen-uusiutuva-energia"
                                      :i18nkey "hiilikadenjalki.ylimaarainen-uusiutuva-energia"
                                      :type    :string
                                      :subtype :decimal
                                      :min     0}
                                     {:name    "tuotteiden-hiilivarastovaikutus"
                                      :i18nkey "hiilikadenjalki.tuotteiden-hiilivarastovaikutus"
                                      :type    :string
                                      :subtype :decimal
                                      :min     0}
                                     {:name    "karbonatisoituminen"
                                      :i18nkey "hiilikadenjalki.karbonatisoituminen"
                                      :type    :string
                                      :subtype :decimal
                                      :min     0}])}])

(defn ^:ryhti hiilijalanjalki [& {:keys [rakennuspaikka?]}]
  [{:name           "hiilijalanjalki"
    :i18nkey        (if rakennuspaikka?
                      "rakennuspaikan-hiilijalanjalki"
                      "hiilijalanjalki")
    :group-help     "help"
    :group-help-css ["p-4" "mt-2" "mb-6" "bg-ui-blue-10" "rounded-sm"]
    :type           :group
    :uicomponent    :carbon-footprint
    :body           [(doc-schemas/ryhti-id (if rakennuspaikka?
                                             :buildingSiteCarbonFootprintKey
                                             :buildingCarbonFootprintKey))
                     {:name    "rakennustuotteiden-valmistus"
                      :i18nkey "hiilijalanjalki.rakennustuotteiden-valmistus"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "kuljetukset"
                      :i18nkey "hiilijalanjalki.kuljetukset"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "tyomaatoiminnot"
                      :i18nkey "hiilijalanjalki.tyomaatoiminnot"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "rakennustuotteiden-vaihdot"
                      :i18nkey "hiilijalanjalki.rakennustuotteiden-vaihdot"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "energiankaytto"
                      :i18nkey "hiilijalanjalki.energiankaytto"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "purkaminen"
                      :i18nkey "hiilijalanjalki.purkaminen"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "purkujatteen-kuljetukset"
                      :i18nkey "hiilijalanjalki.purkujatteen-kuljetukset"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "purkujatteen-kasittely"
                      :i18nkey "hiilijalanjalki.purkujatteen-kasittely"
                      :type    :string
                      :subtype :decimal
                      :min     0}
                     {:name    "purkujatteen-loppusijoitus"
                      :i18nkey "hiilijalanjalki.purkujatteen-loppusijoitus"
                      :type    :string
                      :subtype :decimal
                      :min     0}]}])

(def ^:ryhti rakennuskohteen-osan-tiedot
  [{:name    "rakennuskohteen-osan-tiedot"
    :i18nkey "ilmastoselvitys.rakennuskohteen-osan-tiedot"
    :type    :group
    :body    [{:name     "lammitetty-nettoala"
               :i18nkey  "ilmastoselvitys.lammitetty-nettoala"
               :type     :string
               :size     :s
               :unit     :m2
               :subtype  :number
               :min      0
               :required true}]}])

(def ^:ryhti ilmastoselvitys-rakennuspaikka
  (into [] (concat ilmastoselvityslaatimistiedot
                   (hiilijalanjalki :rakennuspaikka? true)
                   hiilijalanjalkeen-raja-arvoista-poikkeaminen
                   (hiilikadenjalki :rakennuspaikka? true))))

(def ^:ryhti ilmastoselvitys-rakennuskohteen-osa
  (into [] (concat ilmastoselvityslaatimistiedot
                   rakennuskohteen-osan-tiedot
                   (hiilijalanjalki)
                   hiilijalanjalkeen-raja-arvoista-poikkeaminen
                   (hiilikadenjalki))))

(doc-schemas/defschemas
  1
  [{:info {:name                  "rakennustuoteluettelo"
           :type                  :building-product-catalog
           :removable-by          :all
           :ref-update-permitted? true
           :approvable            false
           :order                 1}
    :body (doc-tools/body rakennustuoteluettelo)}

   {:info {:name         "rakennustuoteluettelo-rakennuspaikka"
           :type         :building-product-catalog
           :removable-by :all
           :approvable   false
           :order        1}
    :body (doc-tools/body rakennustuoteluettelo-rakennuspaikka)}

   {:info {:name                  "rakennustuoteluettelo-hyodynnetyt"
           :type                  :building-product-catalog
           :removable-by          :all
           :ref-update-permitted? true
           :approvable            false
           :order                 1}
    :body (doc-tools/body rakennustuoteluettelo-hyodynnetyt)}

   {:info {:name         "rakennustuoteluettelo-rakennuspaikka-hyodynnetyt"
           :type         :building-product-catalog
           :removable-by :all
           :approvable   false
           :order        1}
    :body (doc-tools/body rakennustuoteluettelo-rakennuspaikka-hyodynnetyt)}

   {:info {:name         "rakennuspaikan-ilmastoselvitys"
           :type         :climate-report
           :removable-by :all
           :approvable   false
           :order        2}
    :body (doc-tools/body ilmastoselvitys-rakennuspaikka)}

   {:info {:name         "rakennuskohteen-osan-ilmastoselvitys"
           :type         :climate-report
           :removable-by :all
           :approvable   false
           :order        2}
    :body (doc-tools/body ilmastoselvitys-rakennuskohteen-osa)}])

