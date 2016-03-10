(ns lupapalvelu.pdf.libre-template-test
  (:require
    [clojure.java.io :refer [writer reader resource]]
    [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.util :as util]
    [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
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
                   :statements [{:person {:text "Pelastusviranomainen"
                                          :name "Pia Nyman"}
                                 :requested date-02012016
                                 :given date-01022016
                                 :status "ehdoilla"
                                 :text "Lausunto liitteenä"
                                 :state "given"}
                                {:person {:text "Rakennussuunnittelu"
                                          :name "Sampo Sälevaara"}
                                 :requested date-03012016
                                 :given nil
                                 :status nil
                                 :state "requested"}]
                   :neighbors  [{:propertyId "111"
                                 :owner {:type "luonnollinen"
                                         :name "Joku naapurin nimi"}
                                 :id "112"
                                 :status [{:state "open"
                                           :created date-02012016}
                                          {:state "mark-done"
                                           :user  {:firstName "Etu" :lastName "Suku"}
                                           :created 1453372412991}]}]
                   :tasks      [{:data {}
                                 :state "requires_user_action"
                                 :taskname "rakennuksen paikan tarkastaminen"
                                 :schema-info {:name "task-katselmus"
                                               :version 1}
                                 :closed nil
                                 :created date-02012016
                                 :duedate nil
                                 :assignee  {:lastName  "Suku"
                                             :firstName  "Etu"
                                             :id 1111}
                                 :source nil
                                 :id  "2222"}
                                ]
                   :attachments  [{:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                   :versions [{:version 1
                                               :created date-02012016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}
                                              {:version 2
                                               :created date-01022016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}]
                                   :user     {:firstName "Testi"
                                              :lastName  "Testaaja"}
                                   :contents "Great attachment"}
                                  {:type     {:type-group "muut" :type-id "paatosote"}
                                   :versions [{:version 1
                                               :created date-03012016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}]}]
                   :history      [{:state "draft"
                                   :ts    date-01012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}
                                  {:state "open"
                                   :ts    date-30012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}]})


(facts "History export"
       (fact "Single row"
             (xml-table-row "a" "b" "c" "d") => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\na\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nb\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nc\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nd\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

       (background
         (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
         (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"})

       (fact {:midje/description (str "common-field-map ")}
             (common-field-map application1 :fi) => {"FIELD001" "Käsittelyprosessin tiedot", "FIELD002" "Korpikuusen kannon alla 6", "FIELD003A" "Asiointikunta", "FIELD003B" "Järvenpää", "FIELD004A" "Hakemuksen vaihe", "FIELD004B" "", "FIELD005A" "Kiinteistötunnus", "FIELD005B" "(Tyhjä)", "FIELD006A" "Hakemus jätetty", "FIELD006B" "-", "FIELD007A" "Asiointitunnus", "FIELD007B" "", "FIELD008A" "Käsittelijä", "FIELD008B" "(Tyhjä)", "FIELD009A" "Hankkeen osoite", "FIELD009B" "Korpikuusen kannon alla 6", "FIELD010A" "Hakija", "FIELD010B" "", "FIELD011A" "Toimenpiteet", "FIELD011B" "", "FOOTER_PAGE" "Sivu", "FOOTER_DATE" (util/to-local-date (System/currentTimeMillis))})

       (fact {:midje/description (str "history rows ")}
             (build-xml-history application1 :fi) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLiite:\n</text:p>\n<text:p>\n  Asemapiirros Great attachment 1\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyyntö:\n</text:p>\n<text:p>\n  Pelastusviranomainen  \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nNaapurinkuulemispyyntö:\n</text:p>\n<text:p>\n  Joku naapurin nimi  \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nKatselmointivaatimus:\n</text:p>\n<text:p>\n  rakennuksen paikan tarkastaminen  \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nSuku Etu\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nKäsittelyssä\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n30.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLiite:\n</text:p>\n<text:p>\n  Asemapiirros Great attachment 2\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.02.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLiite:\n</text:p>\n<text:p>\n  Päätösote  1\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyyntö:\n</text:p>\n<text:p>\n  Rakennussuunnittelu  \n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

       (doseq [lang i18n/languages]
         (fact {:midje/description (str "history libre document - TODO: " (name lang))}
               (let [tmp-file (File/createTempFile (str "history-" (name lang) "-") ".fodt")]
                 (write-history-libre-doc application1 lang tmp-file)
                 (nth (clojure.string/split (slurp tmp-file) #"\n")105)
                 ;;TODO: read the doc
                 ) => (str "Katselmointivaatimus:"))))
