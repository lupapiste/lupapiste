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


#_(facts "History export"
       (fact "Single row"
         (-> (xml-table-row "a" "b" "c" "d")
             (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\na\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nb\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nc\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nd\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

  (background
    (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
    (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"})

  (fact {:midje/description (str "common-field-map ")}
    (common-field-map application1 :fi) => {"FIELD001" "K\u00e4sittelyprosessi", "FIELD002" "Korpikuusen kannon alla 6", "FIELD003A" "Asiointikunta", "FIELD003B" "J\u00e4rvenp\u00e4\u00e4", "FIELD004A" "Hakemuksen vaihe", "FIELD004B" "", "FIELD005A" "Kiinteist\u00f6tunnus", "FIELD005B" "(Tyhj\u00e4)", "FIELD006A" "Hakemus j\u00e4tetty", "FIELD006B" "-", "FIELD007A" "Asiointitunnus", "FIELD007B" "", "FIELD008A" "K\u00e4sittelij\u00e4", "FIELD008B" "(Tyhj\u00e4)", "FIELD009A" "Hankkeen osoite", "FIELD009B" "Korpikuusen kannon alla 6", "FIELD010A" "Hakija", "FIELD010B" "", "FIELD011A" "Toimenpiteet", "FIELD011B" "", "FOOTER_PAGE" "Sivu", "FOOTER_DATE" (util/to-local-date (System/currentTimeMillis))})

       (fact {:midje/description (str "history rows ")}
         (-> (build-xml-history application1 :fi)
             (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 1.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyynt\u00f6 tehty: Pelastusviranomainen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nNaapurin kuuleminen tehty: Joku naapurin nimi\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nVaatimus lis\u00e4tty: rakennuksen paikan tarkastaminen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nSuku Etu\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nK\u00e4sittelyss\u00e4\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n30.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 2.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.02.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: P\u00e4\u00e4t\u00f6sote\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyynt\u00f6 tehty: Rakennussuunnittelu\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n"))
