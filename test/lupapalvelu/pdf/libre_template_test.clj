(ns lupapalvelu.pdf.libre-template-test
  (:require
    [clojure.java.io :refer [writer reader resource]]
    [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
    [lupapalvelu.pdf.libreoffice-template :refer :all]
    [lupapalvelu.tiedonohjaus :refer :all])
  (:import (java.io File StringWriter)))

(def date-01012016 1451606400000)
(def date-02012016 1451692800000)
(def date-30012016 1454112000000)
(def date-01022016 1454284800000)

(def application1 {:organization "753-R"
                   :tosFunction  "10 03 00 01"
                   :created      100
                   :applicant    "Testaaja Testi"
                   :address      "Korpikuusen kannon alla 6"
                   :municipality "186"
                   :attachments  [{:type     {:foo :bar}
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
                                  {:type     {:type-group :foo :type-id :qaz}
                                   :versions [{:version 1
                                               :created 300
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

       (doseq [lang i18n/languages]

         (fact {:midje/description (str "common-field-map " (name lang))}
               (common-field-map application1 :fi) => {"FIELD001" "Käsittelyprosessin tiedot", "FIELD002" "Korpikuusen kannon alla 6", "FIELD003A" "Asiointikunta", "FIELD003B" "Järvenpää", "FIELD004A" "Hakemuksen vaihe", "FIELD004B" "", "FIELD005A" "Kiinteistötunnus", "FIELD005B" "(Tyhjä)", "FIELD006A" "Hakemus jätetty", "FIELD006B" "-", "FIELD007A" "Asiointitunnus:", "FIELD007B" "", "FIELD008A" "Käsittelijä", "FIELD008B" "(Tyhjä)", "FIELD009A" "Hankkeen osoite", "FIELD009B" "Korpikuusen kannon alla 6", "FIELD010A" "Hakija", "FIELD010B" "", "FIELD011A" "Toimenpiteet", "FIELD011B" ""})

         (fact {:midje/description (str "history rows " (name lang))}
               (build-xml-history application1) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n28.03.2013\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n{:foo :bar} :attachment Great attachment 1\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n28.03.2013\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nKäsittelyssä\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n09.04.2013\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nKäsittelyssä\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n{:foo :bar} :attachment Great attachment 2\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n09.04.2013\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

         (fact {:midje/description (str "history libre document " (name lang))}
                 (let [tmp-file (File/createTempFile (str "history-" (name lang) "-") ".fodt")]
                   (write-history-libre-doc application1 lang tmp-file) => [{:action    "Valmisteilla"
                                                                             :start     100
                                                                             :user      "Testaaja Testi"
                                                                             :documents [{:type     :hakemus
                                                                                          :category :document
                                                                                          :ts       100
                                                                                          :user     "Testaaja Testi"}
                                                                                         {:type     {:foo :bar}
                                                                                          :category :attachment
                                                                                          :version  1
                                                                                          :ts       200
                                                                                          :user     "Testaaja Testi"
                                                                                          :contents "Great attachment"}]}
                                                                            {:action    "K\u00e4sittelyss\u00e4"
                                                                             :start     250
                                                                             :user      "Testaaja Testi"
                                                                             :documents [{:type     {:foo :qaz}
                                                                                          :category :attachment
                                                                                          :version  1
                                                                                          :ts       300
                                                                                          :user     "Testaaja Testi"
                                                                                          :contents nil}
                                                                                         {:type     {:foo :bar}
                                                                                          :category :attachment
                                                                                          :version  2
                                                                                          :ts       500
                                                                                          :user     "Testaaja Testi"
                                                                                          :contents "Great attachment"}]}]
                   )))

       )
