(ns lupapalvelu.rss.bulletin-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.rss.bulletin :as rss-bulletin]
            [lupapalvelu.rss.common :as rss]
            [sade.date :as date]
            [sade.xml :as xml]))

(deftest build-org-configs-test
  (is (=  {;; No texts -> use defaults
           "1-R" {:julkipano {:fi {:title "Julkipanot"}
                              :sv {:title "Anslag"}},
                  :kuulutus  {:fi {:title "Kuulutukset"}
                              :sv {:title "Kungörelser"}}},
           ;; Only julkipano headers -> use same for both
           "2-R" {:julkipano {:fi {:title "J-FI-H1 - J-FI-H2"}
                              :sv {:title "J-SV-H1 - J-SV-H2"}}
                  :kuulutus  {:fi {:title "J-FI-H1 - J-FI-H2"},
                              :sv {:title "J-SV-H1 - J-SV-H2"}}}
           ;; Only kuulutus headers -> use defaults for julkipano
           "3-R" {:julkipano {:fi {:title "Julkipanot"}
                              :sv {:title "Anslag"}}
                  :kuulutus  {:fi {:title "K-FI-H1 - K-FI-H2"},
                              :sv {:title "K-SV-H1 - K-SV-H2"}}},
           ;; Both julkipano & kuulutus headers
           "4-R" {:julkipano {:fi {:title "J-FI-H1 - J-FI-H2"}
                              :sv {:title "J-SV-H1 - J-SV-H2"}},
                  :kuulutus  {:fi {:title "K-FI-H1 - K-FI-H2"}
                              :sv {:title "K-SV-H1 - K-SV-H2"}}}}
          (rss-bulletin/build-org-configs
            [ {:id "1-R"}
             {:id                            "2-R"
              :local-bulletins-page-settings {:texts {:fi {:heading1 "J-FI-H1"
                                                           :heading2 "J-FI-H2"}
                                                      :sv {:heading1 "J-SV-H1"
                                                           :heading2 "J-SV-H2"}}}}
             {:id                           "3-R"
              :local-kuulutus-page-settings {:texts {:fi {:heading1 "K-FI-H1"
                                                          :heading2 "K-FI-H2"}
                                                     :sv {:heading1 "K-SV-H1"
                                                          :heading2 "K-SV-H2"}}}}
             {:id                            "4-R"
              :local-bulletins-page-settings {:texts {:fi {:heading1 "J-FI-H1"
                                                           :heading2 "J-FI-H2"}
                                                      :sv {:heading1 "J-SV-H1"
                                                           :heading2 "J-SV-H2"}}}
              :local-kuulutus-page-settings  {:texts {:fi {:heading1 "K-FI-H1"
                                                           :heading2 "K-FI-H2"}
                                                      :sv {:heading1 "K-SV-H1"
                                                           :heading2 "K-SV-H2"}}}}]))))

(defn ctx->xml->edn
  [ctx]
  (-> ctx
      rss-bulletin/build-feed-content
      rss/make-rss
      (rss/->xml {:schema-prefixes {"https://www.lupapiste.fi/rss/extensions" "lupapiste"}
                  :schema          rss-bulletin/schemas})
      xml/parse
      xml/xml->edn))

(deftest build-feed-content-test
  (let [org-config {:kuulutus  {:fi {:title "FI Testikunta - kuulutukset"}
                                :sv {:title "SV Testikunta - kuulutukset"}}
                    :julkipano {:fi {:title "FI Testikunta - julkipanot"}
                                :sv {:title "SV Testikunta - julkipanot"}}}
        ctx        {:org-config org-config
                    :org-id     "753-R"
                    :now        (date/zoned-date-time "12.6.2025 13:23")
                    :system     {:config {:host "http://localhost:8000"}}}]
    (testing "no bulletins"
      (let [ctx (assoc ctx :bulletins [])]
        (testing "fi"
          (let [ctx (assoc ctx :lang :fi)]
            (testing "kuulutukset"
              (let [ctx (assoc ctx :bulletin-process :kuulutus)]
                (is (= {:rss {:channel {:copyright     "Cloudpermit Oy"
                                        :language      "fi"
                                        :lastBuildDate "Thu, 12 Jun 2025 13:23:00 +0300"
                                        :link          "http://localhost:8000/rss/kuulutus?organization=753-R&lang=fi"
                                        :title         "FI Testikunta - kuulutukset"
                                        :description   "FI Testikunta - kuulutukset"
                                        :item          {:guid  "no-data"
                                                        :title "Ei kuulutuksia"}}}}
                       (ctx->xml->edn ctx)))))
            (testing "julkipano"
              (let [ctx (assoc ctx :bulletin-process :julkipano)]
                (is (= {:rss {:channel {:copyright     "Cloudpermit Oy"
                                        :language      "fi"
                                        :lastBuildDate "Thu, 12 Jun 2025 13:23:00 +0300"
                                        :link          "http://localhost:8000/rss/julkipano?organization=753-R&lang=fi"
                                        :title         "FI Testikunta - julkipanot"
                                        :description   "FI Testikunta - julkipanot"
                                        :item          {:guid  "no-data"
                                                        :title "Ei julkipanoja"}}}}
                       (ctx->xml->edn ctx)))))))))
    (testing "bulletins"
      (let [ctx (assoc ctx :bulletins [{:address               "osoite"
                                        :appealPeriodEndsAt    (date/timestamp "20.7.2025")
                                        :appealPeriodStartsAt  (date/timestamp "1.7.2025")
                                        :application-id        "LP-753-2025-90001"
                                        :attachment-id         "456"
                                        :bulletinOpDescription "kuvaus"
                                        :id                    "123"
                                        :modified              (date/timestamp "2.6.2025")
                                        :primaryOperation      {:name "kerrostalo-rivitalo"}
                                        :propertyId            "75300000000000"
                                        :verdictData           {:section "1"
                                                                :contact "Ahto Simakuutio"}
                                        :verdictDate           (date/timestamp "1.6.2025")}])]
        (testing "fi"
          (let [ctx (assoc ctx :lang :fi)]
            (testing "kuulutukset"
              (let [ctx (assoc ctx :bulletin-process :kuulutus)]
                (is (= {:rss {:channel {:copyright     "Cloudpermit Oy"
                                        :description   "FI Testikunta - kuulutukset"
                                        :item          {:description                "kuvaus"
                                                        :guid                       "123"
                                                        :link                       "http://localhost:8000/app/fi/local-bulletins?organization=753-R#!/r-bulletin/123"
                                                        :lupapiste:asiakirjaLink    "http://localhost:8000/api/raw/download-bulletin-doc?bulletinId=123"
                                                        :lupapiste:julkaisuAlkaa    "Tue, 1 Jul 2025 00:00:00 +0300"
                                                        :lupapiste:julkaisuPaattyy  "Sun, 20 Jul 2025 23:59:59 +0300"
                                                        :lupapiste:kiinteistotunnus "75300000000000"
                                                        :lupapiste:lupatunnus       "LP-753-2025-90001"
                                                        :lupapiste:paatoksenPykala  "1"
                                                        :lupapiste:paatosPvm        "Sun, 1 Jun 2025 00:00:00 +0300"
                                                        :lupapiste:paattaja         "Ahto Simakuutio"
                                                        :lupapiste:rakennuspaikka   "osoite"
                                                        :lupapiste:toimenpideteksti "Asuinkerrostalon tai rivitalon rakentaminen"
                                                        :pubDate                    "Mon, 2 Jun 2025 00:00:00 +0300"
                                                        :title                      "Asuinkerrostalon tai rivitalon rakentaminen"}
                                        :language      "fi"
                                        :lastBuildDate "Thu, 12 Jun 2025 13:23:00 +0300"
                                        :link          "http://localhost:8000/rss/kuulutus?organization=753-R&lang=fi"
                                        :pubDate       "Mon, 2 Jun 2025 00:00:00 +0300"
                                        :title         "FI Testikunta - kuulutukset"}}}
                       (ctx->xml->edn ctx)))))
            (testing "julkipano"
              (let [ctx (assoc ctx :bulletin-process :julkipano)]
                (is (=  {:rss {:channel {:copyright     "Cloudpermit Oy"
                                         :description   "FI Testikunta - julkipanot"
                                         :item          {:description                "kuvaus"
                                                         :guid                       "123"
                                                         :link                       "http://localhost:8000/app/fi/local-bulletins?organization=753-R#!/r-bulletin/123"
                                                         :lupapiste:asiakirjaLink    "http://localhost:8000/api/raw/download-bulletin-doc?bulletinId=123"
                                                         :lupapiste:julkaisuAlkaa    "Tue, 1 Jul 2025 00:00:00 +0300"
                                                         :lupapiste:julkaisuPaattyy  "Sun, 20 Jul 2025 23:59:59 +0300"
                                                         :lupapiste:kiinteistotunnus "75300000000000"
                                                         :lupapiste:lupatunnus       "LP-753-2025-90001"
                                                         :lupapiste:paatoksenPykala  "1"
                                                         :lupapiste:paatosPvm        "Sun, 1 Jun 2025 00:00:00 +0300"
                                                         :lupapiste:paattaja         "Ahto Simakuutio"
                                                         :lupapiste:rakennuspaikka   "osoite"
                                                         :lupapiste:toimenpideteksti "Asuinkerrostalon tai rivitalon rakentaminen"
                                                         :pubDate                    "Mon, 2 Jun 2025 00:00:00 +0300"
                                                         :title                      "Asuinkerrostalon tai rivitalon rakentaminen"}
                                         :language      "fi"
                                         :lastBuildDate "Thu, 12 Jun 2025 13:23:00 +0300"
                                         :link          "http://localhost:8000/rss/julkipano?organization=753-R&lang=fi"
                                         :pubDate       "Mon, 2 Jun 2025 00:00:00 +0300"
                                         :title         "FI Testikunta - julkipanot"}}}
                        (ctx->xml->edn ctx)))))))))))
