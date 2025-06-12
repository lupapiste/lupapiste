(ns lupapalvelu.building-attributes-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.building-attributes :refer [->onkalo-update get-duplicates latest-entry valid-onkalo-update?] :as attr]))

(deftest get-duplicates-test
  (let [buildings [{:id "id-1" :ratu "ratu-1" :publicity "julkinen"}
                   {:id "id-2" :ratu "ratu-2" :visibilty "asiakas-ja-viranomainen"}]]
    (testing "returns empty when"
      (testing "field not identifier"
        (is (= [] (get-duplicates {:id "some-id" :field :publicity :value "julkinen"} buildings))))
      (testing "field is field identifier but has no value"
        (is (= [] (get-duplicates {:id "some-id" :field :vtjprt :value nil} buildings))))
      (testing "setting the same identifier value for an existing building"
        (is (= [] (get-duplicates {:id "id-1" :field :ratu :value "ratu-1"} buildings)))))
    (testing "returns duplicate value(s) for an identifier field"
      (is (= [{:id "id-1" :ratu "ratu-1" :publicity "julkinen"}]
             (get-duplicates {:id "id-2" :field :ratu :value "ratu-1"} buildings))))))

(deftest latest-entry-test
  (testing "returns nil when no entry found"
    (is (nil? (latest-entry [])))
    (is (nil? (latest-entry [{:type "foo" :time 12345}] [(attr/has? {:type "sent-to-archive"})]))))

  (testing "returns the latest timestamp matching type"
    (is (= {:type "sent-to-archive" :time 123}
           (latest-entry [{:type "sent-to-archive" :time 123}] [(attr/has? {:type "sent-to-archive"})])))
    (is (= {:type "sent-to-archive" :time 555}
           (latest-entry [{:type "sent-to-archive" :time 123}
                          {:type "sent-to-archive" :time 555}
                          {:type "sent-to-archive" :time 222}]
                         [(attr/has? {:type "sent-to-archive"})])))))

(deftest valid-onkalo-update?-test
  (testing "returns false when data is not valid onkalo update"
    (is (false? (valid-onkalo-update? nil)))
    (is (false? (valid-onkalo-update? {}))))
  (testing "returns true for valid onkalo update"
    (testing "with only myyntipalvelu"
      (is (true? (valid-onkalo-update? {:id       "building-id-foo"
                                        :search   {:national-building-id "1030462707"}
                                        :metadata {:myyntipalvelu true}}))))
    (testing "with publicity"
      (is (true? (valid-onkalo-update? {:id       "building-id-foo"
                                        :search   {:national-building-id "1030462707"}
                                        :metadata {:myyntipalvelu   true
                                                   :julkisuusluokka "julkinen"}}))))
    (testing "with visibility"
      (is (true? (valid-onkalo-update? {:id       "building-id-foo"
                                        :search   {:national-building-id "1030462707"}
                                        :metadata {:myyntipalvelu true
                                                   :nakyvyys      "viranomainen"}}))))))

(deftest with-secrecy-defaults-test
  (testing "does not add secrecy defaults if publicity is not limited"
    (is (= {:julkisuusluokka "julkinen"} (attr/with-secrecy-defaults {:julkisuusluokka "julkinen"})))
    (is (= {} (attr/with-secrecy-defaults {}))))
  (testing "adds secrecy defaults if publicity is salainen"
    (is (= {:salassapitoperuste  "-"
            :salassapitoaika     1
            :suojaustaso         "ei-luokiteltu"
            :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
            :kayttajaryhma       "viranomaisryhma"
            :kayttajaryhmakuvaus "muokkausoikeus"
            :julkisuusluokka     "salainen"}
           (attr/with-secrecy-defaults {:julkisuusluokka "salainen"}))))
  (testing "adds secrecy defaults if publicity is osittain-salassapidettava"
    (is (= {:salassapitoperuste  "-"
            :salassapitoaika     1
            :suojaustaso         "ei-luokiteltu"
            :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
            :kayttajaryhma       "viranomaisryhma"
            :kayttajaryhmakuvaus "muokkausoikeus"
            :julkisuusluokka     "osittain-salassapidettava"}
           (attr/with-secrecy-defaults {:julkisuusluokka "osittain-salassapidettava"})))))

(deftest ->onkalo-update-test
  (testing "turns empty input results in update with empty values"
    (is (= {:id       nil
            :search   {:national-building-id nil}
            :metadata {:myyntipalvelu false}}
           (->onkalo-update {}))))
  (testing "returns minimal onkalo-update for minimal building data"
    (is (= {:id       "building-id-foo"
            :search   {:national-building-id "1030462707"}
            :metadata {:myyntipalvelu false}}
           (->onkalo-update {:id "building-id-foo" :vtjprt "1030462707"}))))
  (testing "returns onkalo-update with supported metadata"
    (testing "given myyntipalvelu only"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu true}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true}))))
    (testing "given publicity"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   true
                         :julkisuusluokka "julkinen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "julkinen"}))))
    (testing "given visibility"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu true
                         :nakyvyys      "julkinen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :visibility       "julkinen"}))))
    (testing "given all supported metadata"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   true
                         :julkisuusluokka "julkinen"
                         :nakyvyys        "julkinen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "julkinen"
                               :visibility       "julkinen"})))))
  (testing "Sends myyntipalvelussa as false when"
    (testing "publicity salainen"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   false
                         :julkisuusluokka "salainen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "salainen"}))))
    (testing "publicity osittain-salassapidettava"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   false
                         :julkisuusluokka "osittain-salassapidettava"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "osittain-salassapidettava"}))))
    (testing "visibility viranomainen"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu false
                         :nakyvyys      "viranomainen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :visibility       "viranomainen"}))))
    (testing "visibility asiakas-ja-viranomainen"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu false
                         :nakyvyys      "asiakas-ja-viranomainen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :visibility       "asiakas-ja-viranomainen"}))))
    (testing "visibility public but publicity not"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   false
                         :julkisuusluokka "salainen"
                         :nakyvyys        "julkinen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "salainen"
                               :visibility       "julkinen"}))))
    (testing "publicity public but visibility not"
      (is (= {:id       "building-id-foo"
              :search   {:national-building-id "1030462707"}
              :metadata {:myyntipalvelu   false
                         :julkisuusluokka "julkinen"
                         :nakyvyys        "viranomainen"}}
             (->onkalo-update {:id               "building-id-foo"
                               :vtjprt           "1030462707"
                               :myyntipalvelussa true
                               :publicity        "julkinen"
                               :visibility       "viranomainen"}))))))

(deftest limited-building?-test
  (testing "returns false when"
    (testing "building is nil"
      (is (false? (attr/limited-building? nil))))
    (testing "building has no publicity or visibility setting"
      (is (false? (attr/limited-building? {}))))
    (testing "building has only visibility but it is not secret"
      (is (false? (attr/limited-building? {:visibility nil})))
      (is (false? (attr/limited-building? {:visibility "julkinen"})))
      (is (false? (attr/limited-building? {:visibility "FOOBAR"}))))
    (testing "building has only publicity but it is not secret"
      (is (false? (attr/limited-building? {:publicity nil})))
      (is (false? (attr/limited-building? {:publicity "julkinen"})))
      (is (false? (attr/limited-building? {:publicity "FOOBAR"})))))
  (testing "return true when"
    (testing "visibility and publicity present and either one has a non-public value"
      (is (true? (attr/limited-building? {:visibility "viranomainen" :publicity "julkinen"})))
      (is (true? (attr/limited-building? {:visibility "julkinen" :publicity "salainen"}))))
    (testing "visibility is viranomainen"
      (is (true? (attr/limited-building? {:visibility "viranomainen"}))))
    (testing "visibility is asiakas-ja-viranomainen"
      (is (true? (attr/limited-building? {:visibility "asiakas-ja-viranomainen"}))))
    (testing "publicity is salainen"
      (is (true? (attr/limited-building? {:publicity "salainen"}))))
    (testing "publicity is osittain-salassapidettava"
      (is (true? (attr/limited-building? {:publicity "osittain-salassapidettava"}))))))

(deftest xls-rows->header-and-rows-test
  (testing "turns rows data into header and rows"
    (is (= {:header ["VTJPRT" "MYYNTIPALVELUSSA" "NÄKYVYYS" "JULKISUUS" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
            :rows   [["100012345N"]
                     ["182736459F" "0" "2" "2" "" "" "Jokukatu 1" "Lorem ipsum"]
                     ["182736459F" "1" "1" "1" "9999" "123-123-1234-1234" "Jokukatu 9" "Dolor sit"]]}
           (attr/rows->header-and-rows [["Ohjerivi-1"]
                                        ["Ohjerivi-2"]
                                        ["VTJPRT" "MYYNTIPALVELUSSA" "NÄKYVYYS" "JULKISUUS" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                        ["100012345N"]
                                        ["182736459F" "0" "2" "2" "" "" "Jokukatu 1" "Lorem ipsum"]
                                        ["182736459F" "1" "1" "1" "9999" "123-123-1234-1234" "Jokukatu 9" "Dolor sit"]]))))
  (testing "returns nil if data is nil"
    (is (= {:header nil :rows nil} (attr/rows->header-and-rows nil)))))

(deftest header+rows->building-attributes-test
  (testing "returns empty updates when"
    (testing "headers are empty"
      (is (= [] (attr/header+rows->building-attributes nil [])))
      (is (= [] (attr/header+rows->building-attributes [] [])))
      (is (= [] (attr/header+rows->building-attributes [] [:some :row :data]))))
    (testing "no rows"
      (is (= [] (attr/header+rows->building-attributes ["VTJPRT"] nil)))
      (is (= [] (attr/header+rows->building-attributes ["VTJPRT"] [])))))

  (testing "returns updates"
    (testing "with NÄKYVYYS mapped"
      (testing "1 => viranomainen"
        (is (= [{:vtjprt "182736459F" :visibility "viranomainen"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                                      [["182736459F" "1"]]))))
      (testing "2 => asiakas-ja-viranomainen"
        (is (= [{:vtjprt "182736459F" :visibility "asiakas-ja-viranomainen"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                                      [["182736459F" "2"]]))))
      (testing "3 => julkinen"
        (is (= [{:vtjprt "182736459F" :visibility "julkinen"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                                      [["182736459F" "3"]]))))
      (testing "empty string => omit key"
        (is (= [{:vtjprt "182736459F"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                                      [["182736459F" ""]]))))
      (testing "random string => take value as is"
        (is (= [{:vtjprt "182736459F" :visibility "EN-OLE-NAKYVYYS"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                                      [["182736459F" "EN-OLE-NAKYVYYS"]])))))

    (testing "with JULKISUUS mapped"
      (testing "1 => salainen"
        (is (= [{:vtjprt "182736459F" :publicity "salainen"}]
               (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                                      [["182736459F" "1"]]))))
      (testing "2 => osittain-salassapidettava"
        (is (= [{:vtjprt "182736459F" :publicity "osittain-salassapidettava"}]
               (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                                      [["182736459F" "2"]]))))
      (testing "3 => julkinen"
        (is (= [{:vtjprt "182736459F" :publicity "julkinen"}]
               (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                                      [["182736459F" "3"]]))))
      (testing "empty string => omit key"
        (is (= [{:vtjprt "182736459F"}]
               (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                                      [["182736459F" ""]]))))
      (testing "random string => take value as is"
        (is (= [{:vtjprt "182736459F" :publicity "EN-OLE-JULKISUUSLUOKKA"}]
               (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                                      [["182736459F" "EN-OLE-JULKISUUSLUOKKA"]])))))

    (testing "with MYYNTIPALVELUSSA mapped"
      (testing "0 => false"
        (is (= [{:vtjprt "182736459F" :myyntipalvelussa false}]
               (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                                      [["182736459F" "0"]]))))
      (testing "1 => true"
        (is (= [{:vtjprt "182736459F" :myyntipalvelussa true}]
               (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                                      [["182736459F" "1"]]))))
      (testing "nil => omit :myyntipalvelussa from result"
        (is (= [{:vtjprt "182736459F"}]
               (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                                      [["182736459F"]]))))
      (testing "empty string => omit key"
        (is (= [{:vtjprt "182736459F"}]
               (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                                      [["182736459F" ""]]))))
      (testing "random string => take value as is"
        (is (= [{:vtjprt "182736459F" :myyntipalvelussa "EN-OLE-MYYNTIPALVELUSSA"}]
               (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                                      [["182736459F" "EN-OLE-MYYNTIPALVELUSSA"]])))))

    (testing "with RATU"
      (is (= [{:vtjprt "182736459F" :ratu "OLEN-RATU"}]
             (attr/header+rows->building-attributes ["VTJPRT" "RATU"]
                                                    [["182736459F" "OLEN-RATU"]]))))

    (testing "with KIINTEISTÖTUNNUS"
      (testing "removes dashes"
        (is (= [{:vtjprt "182736459F" :kiinteistotunnus "12312312341234"}]
               (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                                      [["182736459F" "123-123-1234-1234"]]))))
      (testing "adds leading zeroes where necessary"
        (is (= [{:vtjprt "182736459F" :kiinteistotunnus "00100100010001"}]
               (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                                      [["182736459F" "1-1-1-1"]]))))
      (testing "keeps value untouched if it is not a valid kiinteistotunnus"
        (is (= [{:vtjprt "182736459F" :kiinteistotunnus "EN-OLE-KIINTEISTOTUNNUS"}]
               (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                                      [["182736459F" "EN-OLE-KIINTEISTOTUNNUS"]])))))

    (testing "with OSOITE"
      (is (= [{:vtjprt "182736459F" :address "Jokukatu 7 Vantaa"}]
             (attr/header+rows->building-attributes ["VTJPRT" "OSOITE"]
                                                    [["182736459F" "Jokukatu 7 Vantaa"]]))))

    (testing "with LISÄTIETO"
      (is (= [{:vtjprt "182736459F" :comment "Olen kommentti"}]
             (attr/header+rows->building-attributes ["VTJPRT" "LISÄTIETO"]
                                                    [["182736459F" "Olen kommentti"]]))))

    (testing "when header in varying case"
      (is (= [{:vtjprt "182736459F" :visibility "viranomainen" :myyntipalvelussa true}]
             (attr/header+rows->building-attributes ["VTJPRT" "Näkyvyys" "myyntipalvelussa"]
                                                    [["182736459F" "1" "1"]]))))

    (testing "ignoring leading and trailing whitespace in values"
      (is (= [{:vtjprt "182736459F" :visibility "viranomainen" :myyntipalvelussa true}]
             (attr/header+rows->building-attributes ["VTJPRT" "Näkyvyys" "myyntipalvelussa"]
                                                    [["182736459F" " 1 " "    1"]]))))

    (testing "with duplicate vtjprt rows removed"
      (is (= [{:vtjprt "1030462707" :publicity "salainen"}
              {:vtjprt "100012345N" :myyntipalvelussa true}]
             (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS" "JULKISUUS" "MYYNTIPALVELUSSA" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                                    [["1030462707"]
                                                     ["100012345N" "3"]
                                                     ["1030462707" "" "1"]
                                                     ["100012345N" "" "" "1"]]))))

    (testing "Fields that dont have value in data row do not end up in the updates when"
      (testing "only VTJPRT in excel"
        (is (= [{:vtjprt "182736459F"}]
               (attr/header+rows->building-attributes ["VTJPRT"]
                                                      [["182736459F"]]))))
      (testing "multiple fields in header but no corresponding values on all rows"
        (is (= [{:vtjprt "182736459F"}
                {:vtjprt "1030462707"}
                {:vtjprt "100012345N" :visibility "julkinen"}
                {:vtjprt "1234567892" :publicity "salainen"}
                {:vtjprt "102260741K" :myyntipalvelussa true}
                {:vtjprt "1030368157" :comment "Kommentti"}]
               (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS" "JULKISUUS" "MYYNTIPALVELUSSA" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                                      [["182736459F" "" "" "" "" "" "" ""]
                                                       ["1030462707"]
                                                       ["100012345N" "3" nil nil]
                                                       ["1234567892" "" "1"]
                                                       ["102260741K" "" "" "1"]
                                                       ["1030368157" "" "" "" "" "" "" "Kommentti"]])))))))

(def dummy-id "99")
(def id-fn (constantly dummy-id))

(deftest ->db-buildings-test
  (testing "returns empty db-buildings when attributes-list empty"
    (is (= {:valid-buildings []}
           (attr/->db-buildings [] "073-R" 123 id-fn))))

  (testing "turns attributes into proper db-buildings when there are no invalid entries"
    (is (= {:valid-buildings [{:id           dummy-id
                               :organization "073-R"
                               :meta         {:modified 123}
                               :attributes   {:vtjprt           "182736459F"
                                              :myyntipalvelussa true
                                              :publicity        "julkinen"}}]}
           (attr/->db-buildings [{:vtjprt           "182736459F"
                                  :myyntipalvelussa true
                                  :publicity        "julkinen"}] "073-R" 123 id-fn))))

  (testing "Returns valid and invalid buildings under different keys"
    (is (= {:valid-buildings   [{:id           dummy-id
                                 :organization "073-R"
                                 :meta         {:modified 123}
                                 :attributes   {:vtjprt "182736459F"}}]
            :invalid-buildings [{:id           dummy-id
                                 :organization "073-R"
                                 :meta         {:modified 123}
                                 :attributes   {:vtjprt "VIALLINEN-VTJPRT"}}
                                {:id           dummy-id
                                 :organization "073-R"
                                 :meta         {:modified 123}
                                 :attributes   {:vtjprt    "1030462707"
                                                :publicity "VIALLINEN-JULKISUUSLUOKKA"}}
                                {:id           dummy-id
                                 :organization "073-R"
                                 :meta         {:modified 123}
                                 :attributes   {:vtjprt           "100012345N"
                                                :kiinteistotunnus "VIALLINEN-KIINTEISTOTUNNUS"}}]}
           (attr/->db-buildings [{:vtjprt "182736459F"}
                                 {:vtjprt "VIALLINEN-VTJPRT"}
                                 {:vtjprt "1030462707" :publicity "VIALLINEN-JULKISUUSLUOKKA"}
                                 {:vtjprt "100012345N" :kiinteistotunnus "VIALLINEN-KIINTEISTOTUNNUS"}] "073-R" 123 id-fn)))))

(deftest ->building-result-test
  (let [doc {:id             "house-id" :organization "123-R"
             :attributes     {:address    "Secret Sideway"
                              :vtjprt     "100012345N"
                              :comment    "Hello world."
                              :visibility "viranomainen"}
             :meta           {:modified 12345
                              :history  [{:type           "sent-to-archive"
                                          :time           34567
                                          :docs-by-status {:200 ["file1" "file2"]
                                                           :500 ["file3"]} }
                                         {:type           "sent-to-archive"
                                          :time           98765 ; latest
                                          :docs-by-status {:200 ["file4" "file5"]
                                                           :500 ["file6"]} }
                                         {:type           "sent-to-archive"
                                          :time           67890
                                          :docs-by-status {:200 ["file7"]} }]}
             :_sheriff-notes [{:created 1767357792689
                               :note    "Fixed."}]}]
    (testing "Sheriff notes are accepted by the Building schema."
      (is (nil? (attr/building-checker doc))))
    (testing "->building-result"
      (is (= {:id              "house-id" :modified 12345
              :address         "Secret Sideway"
              :vtjprt          "100012345N"
              :comment         "Hello world."
              :visibility      "viranomainen"
              :sent-to-archive {:type           "sent-to-archive"
                                :time           98765
                                :docs-by-status {:200 ["file4" "file5"]
                                                 :500 ["file6"]} }}
             (attr/->building-result doc))))))
