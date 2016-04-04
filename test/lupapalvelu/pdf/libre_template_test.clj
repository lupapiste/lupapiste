(ns lupapalvelu.pdf.libre-template-test
  (:require
    [clojure.java.io :refer [writer reader resource]]
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.util :as util]
    [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
    [lupapalvelu.pdf.libreoffice-template :refer :all]
    [lupapalvelu.tiedonohjaus :refer :all])
  (:import (java.io File StringWriter)))

(def date-01012016 1451606400000)
(def date-02012016 1451692800000)
(def date-03012016 1456790400000)
(def date-30012016 1454112000000)
(def date-01022016 1454284800000)

(def application1 {:organization "753-R"
                   :tosFunction  "10 03 00 01"
                   :created      100
                   :applicant    "Testaaja Testi"
                   :address      "Korpikuusen kannon alla 6"
                   :municipality "186"
                   :verdicts     [{:id              "a1"
                                   :timestamp       1454562242169
                                   :kuntalupatunnus "20160043"
                                   :sopimus false
                                   :paatokset       [{:id "a2"
                                                      :paivamaarat    {:anto          1454544000000
                                                                       :lainvoimainen 1454544000000
                                                                       :voimassaHetki 1613520000000
                                                                       :aloitettava   1550448000000}

                                                      :lupamaaraykset {:maaraykset               [{:sisalto "Vaaditut erityissuunnitelmat: Vesijohto- ja viemärisuunnitelma"}]
                                                                       :vaaditutTyonjohtajat     "Vastaava työnjohtaja"
                                                                       :vaadittuTyonjohtajatieto ["Vastaava työnjohtaja"]
                                                                       :vaaditutKatselmukset     [{:tarkastuksenTaiKatselmuksenNimi "* KVV-tarkastus" :katselmuksenLaji " muu katselmus "}
                                                                                                  {:tarkastuksenTaiKatselmuksenNimi " * Sähkötarkastus " :katselmuksenLaji " muu katselmus "}
                                                                                                  {:tarkastuksenTaiKatselmuksenNimi " * Rakennetarkastus " :katselmuksenLaji " muu katselmus "}
                                                                                                  {:katselmuksenLaji " loppukatselmus "}
                                                                                                  {:tarkastuksenTaiKatselmuksenNimi " Aloitusilmoitus " :katselmuksenLaji " muu katselmus "}]}

                                                      :poytakirjat    [{:urlHash         "4196f10a7fef9bec325dc567f1b87fbcd10163ce"
                                                                        :status          "1"
                                                                        :paatoksentekija "Tytti Mäntyoja"
                                                                        :pykala          31
                                                                        :paatospvm       1454284800000
                                                                        :paatoskoodi     "myönnetty"}]}]}]
                   :statements   [{:person    {:text "Pelastusviranomainen"
                                               :name "Pia Nyman"}
                                   :requested date-02012016
                                   :given     date-01022016
                                   :status    "ehdoilla"
                                   :text      "Lausunto liitteen\u00e4"
                                   :state     "given"}
                                  {:person    {:text "Rakennussuunnittelu"
                                               :name "Sampo S\u00e4levaara"}
                                   :requested date-03012016
                                   :given     nil
                                   :status    nil
                                   :state     "requested"}]
                   :neighbors    [{:propertyId "111"
                                   :owner      {:type "luonnollinen"
                                                :name "Joku naapurin nimi"}
                                   :id         "112"
                                   :status     [{:state   "open"
                                                 :created date-02012016}
                                                {:state   "mark-done"
                                                 :user    {:firstName "Etu" :lastName "Suku"}
                                                 :created 1453372412991}]}]
                   :tasks        [{:data        {}
                                   :state       "requires_user_action"
                                   :taskname    "rakennuksen paikan tarkastaminen"
                                   :schema-info {:name    "task-katselmus"
                                                 :version 1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      nil
                                   :id          "2222"}
                                  ]
                   :attachments  [{:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                   :versions [{:version {:major 1 :minor 0}
                                               :created date-02012016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}
                                              {:version {:major 2 :minor 0}
                                               :created date-01022016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}]
                                   :user     {:firstName "Testi"
                                              :lastName  "Testaaja"}
                                   :contents "Great attachment"}
                                  {:type     {:type-group "muut" :type-id "paatosote"}
                                   :versions [{::version {:major 1 :minor 0}
                                               :created  date-03012016
                                               :user     {:firstName "Testi"
                                                          :lastName  "Testaaja"}}]}]
                   :history      [{:state "draft"
                                   :ts    date-01012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}
                                  {:state "open"
                                   :ts    date-30012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}]})

(fact {:midje/description (str "Libre Template common-field-map ")}
      (common-field-map application1 :fi) => {"FIELD002" "Korpikuusen kannon alla 6", "FIELD003A" "Asiointikunta", "FIELD003B" "J\u00e4rvenp\u00e4\u00e4", "FIELD004A" "Hakemuksen vaihe", "FIELD004B" "", "FIELD005A" "Kiinteist\u00f6tunnus", "FIELD005B" "(Tyhj\u00e4)", "FIELD006A" "Hakemus j\u00e4tetty", "FIELD006B" "-", "FIELD007A" "Asiointitunnus", "FIELD007B" "", "FIELD008A" "K\u00e4sittelij\u00e4", "FIELD008B" "(Tyhj\u00e4)", "FIELD009A" "Hankkeen osoite", "FIELD009B" "Korpikuusen kannon alla 6", "FIELD010A" "Hakija", "FIELD010B" "", "FIELD011A" "Toimenpiteet", "FIELD011B" "", "FOOTER_PAGE" "Sivu", "FOOTER_DATE" (util/to-local-date (System/currentTimeMillis))})

(facts "History export"
       (fact "Single row"
             (-> (xml-table-row "a" "b" "c" "d")
                 (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\na\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nb\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nc\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nd\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

  (background
    (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
    (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"})

       (fact {:midje/description (str "history rows ")}
             (-> (build-xml-history application1 :fi)
                 (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lisätty: Asemapiirros, Great attachment, v. 1.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyyntö tehty: Pelastusviranomainen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nNaapurin kuuleminen tehty: Joku naapurin nimi\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nVaatimus lisätty: rakennuksen paikan tarkastaminen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nSuku Etu\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nKäsittelyssä\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n30.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lisätty: Asemapiirros, Great attachment, v. 2.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.02.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lisätty: Päätösote\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyyntö tehty: Rakennussuunnittelu\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

       (doseq [lang i18n/languages]
         (fact {:midje/description (str "history libre document: " (name lang))}
               (let [tmp-file (File/createTempFile (str "history-" (name lang) "-") ".fodt")]
                 (debug "writing file: " (.getAbsolutePath tmp-file))
                 (write-history-libre-doc application1 lang tmp-file)
                 (let [res (s/split (slurp tmp-file) #"\r?\n")]
                   #_(.delete tmp-file)
                   (nth res 945))) => (str (localize lang "caseFile.operation.review.request") ": rakennuksen paikan tarkastaminen"))))

(facts "Verdict export "
       (doseq [lang i18n/languages]
               (let [tmp-file (File/createTempFile (str "verdict-" (name lang) "-") ".fodt")]
                 (debug "writing file: " (.getAbsolutePath tmp-file))
                 (write-verdict-libre-doc application1 "a1" 0 lang tmp-file)
                 (let [pos 1060
                       res (s/split (slurp tmp-file) #"\r?\n")]
                   #_(.delete tmp-file)
                   (fact {:midje/description (str " verdict libre document title (" (name lang) ")")} (nth res pos) => #(s/includes? % (localize lang "application.verdict.title")))
                   (fact {:midje/description (str " verdict libre document kuntalupatunnus (" (name lang) ")")} (nth res (+ pos 55)) => #(s/includes? % (str (localize lang "verdict.id") ": " "20160043" ) ))
                   ))))
