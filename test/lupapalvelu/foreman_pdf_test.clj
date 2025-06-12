(ns lupapalvelu.foreman-pdf-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [lupapalvelu.foreman-pdf :as foreman-pdf]
            [lupapalvelu.pdf.html-template :as html]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fn [test-fn] (sc/with-fn-validation
                                    (test-fn))))

(deftest foreman-pdf-test
  (let [*result (atom nil)]
    (with-redefs [html/html->pdf (fn [_system data _margins]
                                   (reset! *result data)
                                   {:ok true})]
      (let [args {:lang               :fi
                  :now                (.getTime #inst "2023-12-08T14:00")
                  :organization       {:name {:fi "Testiorganisaatio"}}
                  :foreman-app-or-doc {:id                 "LP-2023-TESTI"
                                       :documents          [{:schema-info {:name "tyonjohtaja-v2"}
                                                             :data        {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}
                                                                           :henkilotiedot   {:etunimi  {:value "Jouko"}
                                                                                             :sukunimi {:value "Johtaja"}}}}]
                                       :foremanTermination {:confirmer {:firstName "Petri"
                                                                        :lastName  "Päättäjä"}
                                                            :reason    "Perustelut.."
                                                            :started   (.getTime #inst "2023-12-01T14:00")
                                                            :ended     (.getTime #inst "2023-12-24T14:00")}}
                  :system             {}}]
        (foreman-pdf/generate-termination-document args)
        (let [{:keys [title h-header h-body]} (deref *result)]
          (is (= "Työnjohtajan vastuiden päättäminen" title))
          (is (= [:div.header
                  [:div.section.header
                   [:div.row.pad-after
                    [:div.cell.cell--30 "Testiorganisaatio"]
                    [:div.cell.cell--40.center "Työnjohtajan vastuiden päättäminen"]
                    [:div.cell.cell--30.right [:div.permit "Päätös"]]]
                   [:div.row
                    [:div.cell.cell--30]
                    [:div.cell.cell--40.center "8.12.2023"]
                    [:div.cell.cell--30.right.page-number "Sivu" " " [:span.pageNumber]]]]]
                 h-header))
          (is (= (list [:div.section
                        [:div.row {:class [:bold :pad-after]}
                         [:div.cell {:class [:cell--30 :bold]} "Lupatunnus"] [:div.cell "LP-2023-TESTI"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Työnjohtaja"] [:div.cell "Johtaja Jouko"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Työnjohtajarooli"] [:div.cell "Vastaava työnjohtaja"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Vastuuaika"] [:div.cell "1.12.2023 - 24.12.2023"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Päätöspäivämäärä"] [:div.cell "8.12.2023"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Lainvoimainen"] [:div.cell "22.12.2023"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Päättäjä"] [:div.cell "Petri Päättäjä"]]]
                       [:div.section
                        [:div.row
                         [:div.cell {:class [:cell--30]} "Perustelut"] [:div.cell "Perustelut.."]]])
                 h-body)))))))
