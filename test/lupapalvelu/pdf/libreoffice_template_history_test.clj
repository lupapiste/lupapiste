(ns lupapalvelu.pdf.libreoffice-template-history-test
  (:require [clojure.string :as s]
            [taoensso.timbre :refer [trace debug]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.files :as files]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.i18n :refer [loc localize]]
            [lupapalvelu.pdf.libreoffice-template :refer :all]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.tiedonohjaus :refer :all]
            [lupapalvelu.pdf.libreoffice-template-base-test :refer :all]
            [lupapalvelu.organization :as o]))

(def build-history-rows #'lupapalvelu.pdf.libreoffice-template-history/build-history-rows)

(def handler-roles [{:id "58933955cbc214d39ae9688c", :name {:fi "K\u00e4sittelij\u00e4", :sv "Handl\u00e4ggare", :en "Handler"}, :general true}
                    {:id "58a1ae0a9f5d940c0647d25a", :name {:fi "Testi-rooli", :sv "Test Role", :en "Test Role"}}
                    {:id "58aab772a20112dee7662c92", :name {:fi "Rooli", :sv "Role", :en "Role"}}
                    {:id "58b581558a73e50b5cd2e2fb", :name {:fi "KVV-K\u00e4sittelij\u00e4", :sv "KVV-Handl\u00e4ggare", :en "KVV-Handler"}}
                    {:id "58b7e9254cee47962aea4df4", :name {:fi "Uusi", :sv "Ny", :en "New"}, :disabled true}])

(defn build-xml-history [application lang]
  (s/join " " (map #(apply xml-table-row %) (build-history-rows application lang))))

(facts "History export"
       (fact "Single row"
             (-> (xml-table-row "a" "b" "c" "d")
                 (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\na\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nb\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nc\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nd\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

       (background
         (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
         (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"}
         (o/get-organization "753-R") => {:handler-roles handler-roles})

       (fact {:midje/description (str "history rows")}
             (build-history-rows application1 :fi) => [["Valmisteilla" "" "01.01.2016" "Testaaja Testi"] ["" "Asiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 1.0" "02.01.2016" "Testaaja Testi"] ["" "Lausuntopyynt\u00f6 tehty: Pelastusviranomainen" "02.01.2016" ""] ["" "Kuultava naapuri lis\u00e4tty: Joku naapurin nimi" "02.01.2016" " "] ["" "Vaatimus lis\u00e4tty: rakennuksen paikan tarkastaminen" "02.01.2016" "Suku Etu"] ["" "Naapuri merkitty kuulluksi: Joku naapurin nimi" "21.01.2016" "Suku Etu"] ["K\u00e4sittelyss\u00e4" "" "30.01.2016" "Testaaja Testi"] ["" "Asiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 2.0" "01.02.2016" "Testaaja Testi"] ["" "Asiakirja lis\u00e4tty: P\u00e4\u00e4t\u00f6sote" "01.03.2016" "Testaaja Testi"] ["" "Lausuntopyynt\u00f6 tehty: Rakennussuunnittelu" "01.03.2016" ""]])

       (fact {:midje/description (str "history rows xml")}
             (-> (build-xml-history application1 :fi)
                 (s/replace "\r\n" "\n")) => "<table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nValmisteilla\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 1.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyynt\u00f6 tehty: Pelastusviranomainen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nKuultava naapuri lis\u00e4tty: Joku naapurin nimi\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n \n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nVaatimus lis\u00e4tty: rakennuksen paikan tarkastaminen\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n02.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nSuku Etu\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nNaapuri merkitty kuulluksi: Joku naapurin nimi\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n21.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nSuku Etu\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\nK\u00e4sittelyss\u00e4\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n30.01.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: Asemapiirros, Great attachment, v. 2.0\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.02.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nAsiakirja lis\u00e4tty: P\u00e4\u00e4t\u00f6sote\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nTestaaja Testi\n</text:p>\n</table:table-cell>\n</table:table-row>\n <table:table-row>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\nLausuntopyynt\u00f6 tehty: Rakennussuunnittelu\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n01.03.2016\n</text:p>\n</table:table-cell>\n<table:table-cell office:value-type='string'>\n<text:p>\n\n</text:p>\n</table:table-cell>\n</table:table-row>\n")

       (doseq [lang test-util/test-languages]
         (fact {:midje/description (str "history libre document: " (name lang))}
           (files/with-temp-file tmp-file
             (history/write-history-libre-doc application1 lang tmp-file)
             (let [res (s/split (slurp tmp-file) #"\r?\n")]
               (nth res 945))) => (str (localize lang "caseFile.operation.review.request") ": rakennuksen paikan tarkastaminen"))))
