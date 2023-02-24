(ns lupapalvelu.building-attributes-test
  (:require [lupapalvelu.building-attributes :refer [get-duplicates latest-entry
                                                     ->onkalo-update valid-onkalo-update?] :as attr]
            [midje.sweet :refer :all]))

(facts "get-duplicates"
  (let [buildings [{:id "id-1" :ratu "ratu-1" :publicity "julkinen"}
                   {:id "id-2" :ratu "ratu-2" :visibilty "asiakas-ja-viranomainen"}]]
    (fact "returns empty when "
      (fact "field not identifier"
        (get-duplicates {:id "some-id" :field :publicity :value "julkinen"} buildings) => [])
      (fact "field is field identifier but has no value"
        (get-duplicates {:id "some-id" :field :vtjprt :value nil} buildings) => [])

      (fact "setting the same identifier value for an existing building"
        (get-duplicates {:id "id-1" :field :ratu :value "ratu-1"} buildings) => []))

    (fact "returns duplicate value(s) for an identifier field"
      (get-duplicates {:id "id-2" :field :ratu :value "ratu-1"} buildings) => [{:id "id-1" :ratu "ratu-1" :publicity "julkinen"}])))

(facts "latest-entry"

  (fact "returns nil when no entry found"
    (latest-entry []) => nil
    (latest-entry [{:type "foo" :time 12345}] [(attr/has? {:type "sent-to-archive"})]) => nil)

  (fact "returns the latest timestamp matching type"
    (latest-entry [{:type "sent-to-archive" :time 123}] [(attr/has? {:type "sent-to-archive"})])
    => {:type "sent-to-archive" :time 123}

    (latest-entry [{:type "sent-to-archive" :time 123}
                   {:type "sent-to-archive" :time 555}
                   {:type "sent-to-archive" :time 222}]
                  [(attr/has? {:type "sent-to-archive"})])
    => {:type "sent-to-archive" :time 555}))

(facts "valid-onkalo-update?"
  (fact "returns false when data is not valid onkalo update"
    (valid-onkalo-update? nil) => false
    (valid-onkalo-update? {}) => false)
  (fact "returns true for valid onkalo update"
    (fact "with only myyntipalvelu"
      (valid-onkalo-update? {:id       "building-id-foo"
                             :search   {:national-building-id "1030462707"}
                             :metadata {:myyntipalvelu true}})) => true
    (fact "with publicity"
      (valid-onkalo-update? {:id       "building-id-foo"
                             :search   {:national-building-id "1030462707"}
                             :metadata {:myyntipalvelu   true
                                        :julkisuusluokka "julkinen"}}) => true)
    (fact "with visibility"
      (valid-onkalo-update? {:id       "building-id-foo"
                             :search   {:national-building-id "1030462707"}
                             :metadata {:myyntipalvelu true
                                        :nakyvyys      "viranomainen"}}) => true)))

(facts "with-secrecy-defaults"
  (fact "does not add secrecy defaults if publicity is not limited"
    (attr/with-secrecy-defaults {:julkisuusluokka "julkinen"}) => {:julkisuusluokka "julkinen"}
    (attr/with-secrecy-defaults {}) => {})
  (fact "adds secrecy defaults if publicity is salainen"
    (attr/with-secrecy-defaults {:julkisuusluokka "salainen"})
    => {:salassapitoperuste  "-"
        :salassapitoaika     1
        :suojaustaso         "ei-luokiteltu"
        :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
        :kayttajaryhma       "viranomaisryhma"
        :kayttajaryhmakuvaus "muokkausoikeus"
        :julkisuusluokka     "salainen"})
  (fact "adds secrecy defaults if publicity is osittain-salassapidettava"
    (attr/with-secrecy-defaults {:julkisuusluokka "osittain-salassapidettava"})
    => {:salassapitoperuste  "-"
        :salassapitoaika     1
        :suojaustaso         "ei-luokiteltu"
        :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
        :kayttajaryhma       "viranomaisryhma"
        :kayttajaryhmakuvaus "muokkausoikeus"
        :julkisuusluokka     "osittain-salassapidettava"}))

(facts "->onkalo-update"
  (fact "turns empty input results in update with empty values"
    (->onkalo-update {}) => {:id       nil
                             :search   {:national-building-id nil}
                             :metadata {:myyntipalvelu false}})
  (fact "returns minimal onkalo-update for minimal building data"
    (->onkalo-update {:id "building-id-foo" :vtjprt "1030462707"})
    => {:id       "building-id-foo"
        :search   {:national-building-id "1030462707"}
        :metadata {:myyntipalvelu false}})
  (fact "returns onkalo-update with supported metadata"
    (fact "given myyntipalvelu only"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu true}})
    (fact "given publicity"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "julkinen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   true
                     :julkisuusluokka "julkinen"}})
    (fact "given visibility"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :visibility       "julkinen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu true
                     :nakyvyys      "julkinen"}})
    (fact "given all supported metadata"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "julkinen"
                        :visibility       "julkinen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   true
                     :julkisuusluokka "julkinen"
                     :nakyvyys        "julkinen"}}))
  (fact "Sends myyntipalvelussa as false when"
    (fact "publicity salainen"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "salainen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   false
                     :julkisuusluokka "salainen"}})
    (fact "publicity osittain-salassapidettava"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "osittain-salassapidettava"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   false
                     :julkisuusluokka "osittain-salassapidettava"}})
    (fact "visibility viranomainen"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :visibility       "viranomainen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu false
                     :nakyvyys      "viranomainen"}})
    (fact "visibility asiakas-ja-viranomainen"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :visibility       "asiakas-ja-viranomainen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu false
                     :nakyvyys      "asiakas-ja-viranomainen"}})
    (fact "visibility public but publicity not"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "salainen"
                        :visibility       "julkinen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   false
                     :julkisuusluokka "salainen"
                     :nakyvyys        "julkinen"}})
    (fact "publicity public but visibility not"
      (->onkalo-update {:id               "building-id-foo"
                        :vtjprt           "1030462707"
                        :myyntipalvelussa true
                        :publicity        "julkinen"
                        :visibility       "viranomainen"})
      => {:id       "building-id-foo"
          :search   {:national-building-id "1030462707"}
          :metadata {:myyntipalvelu   false
                     :julkisuusluokka "julkinen"
                     :nakyvyys        "viranomainen"}}))
  (facts "limited-building?"
    (fact "returns false when"
      (fact "building is nil"
        (attr/limited-building? nil) => false)
      (fact "building has no publicity or visibility setting"
        (attr/limited-building? {}) => false)
      (fact "building has only visibility but it is not secret"
        (attr/limited-building? {:visibility nil}) => false
        (attr/limited-building? {:visibility "julkinen"}) => false
        (attr/limited-building? {:visibility "FOOBAR"}) => false)
      (fact "building has only publicity but it is not secret"
        (attr/limited-building? {:publicity nil}) => false
        (attr/limited-building? {:publicity "julkinen"}) => false
        (attr/limited-building? {:publicity "FOOBAR"}) => false))
    (fact "return true when "
      (fact "visibility and publicity present and either one has a non-public value"
        (attr/limited-building? {:visibility "viranomainen" :publicity "julkinen"}) => true
        (attr/limited-building? {:visibility "julkinen" :publicity "salainen"}) => true)
      (fact "visibility is viranomainen"
        (attr/limited-building? {:visibility "viranomainen"}) => true)
      (fact "visibility is asiakas-ja-viranomainen"
        (attr/limited-building? {:visibility "asiakas-ja-viranomainen"}) => true)
      (fact "publicity is salainen"
        (attr/limited-building? {:publicity "salainen"}) => true)
      (fact "publicity is osittain-salassapidettava"
        (attr/limited-building? {:publicity "osittain-salassapidettava"}) => true))))

(facts "xls-rows->header-and-rows"
  (fact "turns rows data into header and rows"
    (attr/rows->header-and-rows [["Ohjerivi-1"]
                                 ["Ohjerivi-2"]
                                 ["VTJPRT" "MYYNTIPALVELUSSA" "NÄKYVYYS" "JULKISUUS" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                 ["100012345N"]
                                 ["182736459F" "0" "2" "2" "" "" "Jokukatu 1" "Lorem ipsum"]
                                 ["182736459F" "1" "1" "1" "9999" "123-123-1234-1234" "Jokukatu 9" "Dolor sit"]])
    => {:header ["VTJPRT" "MYYNTIPALVELUSSA" "NÄKYVYYS" "JULKISUUS" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
        :rows   [["100012345N"]
                 ["182736459F" "0" "2" "2" "" "" "Jokukatu 1" "Lorem ipsum"]
                 ["182736459F" "1" "1" "1" "9999" "123-123-1234-1234" "Jokukatu 9" "Dolor sit"]]})
  (fact "returns nil if data is nil"
    (attr/rows->header-and-rows nil) => {:header nil :rows nil}))

(facts "header+rows->building-attributes"
  (fact "returns empty updates when"
    (fact "headers are empty"
      (attr/header+rows->building-attributes nil []) => []
      (attr/header+rows->building-attributes [] []) => []
      (attr/header+rows->building-attributes [] [:some :row :data]) => [])
    (fact "no rows"
      (attr/header+rows->building-attributes ["VTJPRT"] nil) => []
      (attr/header+rows->building-attributes ["VTJPRT"] []) => []))

  (fact "returns updates "

    (fact "with NÄKYVYYS mapped"

      (fact "1 => viranomainen"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                               [["182736459F" "1"]])
        => [{:vtjprt "182736459F" :visibility "viranomainen"}])

      (fact "2 => asiakas-ja-viranomainen"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                               [["182736459F" "2"]])
        => [{:vtjprt "182736459F" :visibility "asiakas-ja-viranomainen"}])

      (fact "3 => julkinen"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                               [["182736459F" "3"]])
        => [{:vtjprt "182736459F" :visibility "julkinen"}])

      (fact "empty string => omit key"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                               [["182736459F" ""]])
        => [{:vtjprt "182736459F"}])

      (fact "random string => take value as is"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS"]
                                               [["182736459F" "EN-OLE-NAKYVYYS"]])
        => [{:vtjprt "182736459F" :visibility "EN-OLE-NAKYVYYS"}]))

    (fact "with JULKISUUS mapped"
      (fact "1 => salainen"
        (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                               [["182736459F" "1"]])
        => [{:vtjprt "182736459F" :publicity "salainen"}])

      (fact "2 => osittain-salassapidettava"
        (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                               [["182736459F" "2"]])
        => [{:vtjprt "182736459F" :publicity "osittain-salassapidettava"}])

      (fact "3 => julkinen"
        (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                               [["182736459F" "3"]])
        => [{:vtjprt "182736459F" :publicity "julkinen"}])

      (fact "empty string => omit key"
        (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                               [["182736459F" ""]])
        => [{:vtjprt "182736459F"}])

      (fact "random string => take value as is"
        (attr/header+rows->building-attributes ["VTJPRT" "JULKISUUS"]
                                               [["182736459F" "EN-OLE-JULKISUUSLUOKKA"]])
        => [{:vtjprt "182736459F" :publicity "EN-OLE-JULKISUUSLUOKKA"}]))

    (fact "with MYYNTIPALVELUSSA mapped"
      (fact "0 => false"
        (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                               [["182736459F" "0"]])
        => [{:vtjprt "182736459F" :myyntipalvelussa false}])

      (fact "1 => true"
        (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                               [["182736459F" "1"]])
        => [{:vtjprt "182736459F" :myyntipalvelussa true}])

      (fact "nil => omit :myyntipalvelussa from result"
        (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                               [["182736459F"]])
        => [{:vtjprt "182736459F"}])

      (fact "empty string => omit key"
        (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                               [["182736459F" ""]])
        => [{:vtjprt "182736459F"}])

      (fact "random string => take value as is"
        (attr/header+rows->building-attributes ["VTJPRT" "MYYNTIPALVELUSSA"]
                                               [["182736459F" "EN-OLE-MYYNTIPALVELUSSA"]])
        => [{:vtjprt "182736459F" :myyntipalvelussa "EN-OLE-MYYNTIPALVELUSSA"}]))

    (fact "with RATU"
      (attr/header+rows->building-attributes ["VTJPRT" "RATU"]
                                             [["182736459F" "OLEN-RATU"]])
      => [{:vtjprt "182736459F" :ratu "OLEN-RATU"}])

    (fact "with KIINTEISTÖTUNNUS"
      (fact "removes dashes"
        (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                               [["182736459F" "123-123-1234-1234"]])
        => [{:vtjprt "182736459F" :kiinteistotunnus "12312312341234"}])
      (fact "adds leading zeroes where necessary"
        (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                               [["182736459F" "1-1-1-1"]])
        => [{:vtjprt "182736459F" :kiinteistotunnus "00100100010001"}])
      (fact "keeps value untouched if it is not a valid kiinteistotunnus"
        (attr/header+rows->building-attributes ["VTJPRT" "KIINTEISTÖTUNNUS"]
                                               [["182736459F" "EN-OLE-KIINTEISTOTUNNUS"]])
        => [{:vtjprt "182736459F" :kiinteistotunnus "EN-OLE-KIINTEISTOTUNNUS"}]))

    (fact "with OSOITE"
      (attr/header+rows->building-attributes ["VTJPRT" "OSOITE"]
                                             [["182736459F" "Jokukatu 7 Vantaa"]])
      => [{:vtjprt "182736459F" :address "Jokukatu 7 Vantaa"}])

    (fact "with LISÄTIETO"
      (attr/header+rows->building-attributes ["VTJPRT" "LISÄTIETO"]
                                             [["182736459F" "Olen kommentti"]])
      => [{:vtjprt "182736459F" :comment "Olen kommentti"}])

    (fact "when header in varying case"
      (attr/header+rows->building-attributes ["VTJPRT" "Näkyvyys" "myyntipalvelussa"]
                                             [["182736459F" "1" "1"]])
      => [{:vtjprt "182736459F" :visibility "viranomainen" :myyntipalvelussa true}])

    (fact "ignoring leading and trailing whitespace in values"
      (attr/header+rows->building-attributes ["VTJPRT" "Näkyvyys" "myyntipalvelussa"]
                                             [["182736459F" " 1 " "    1"]])
      => [{:vtjprt "182736459F" :visibility "viranomainen" :myyntipalvelussa true}])

    (fact "with duplicate vtjprt rows removed"
      (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS" "JULKISUUS" "MYYNTIPALVELUSSA" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                             [["1030462707"]
                                      ["100012345N" "3"]
                                      ["1030462707" "" "1"]
                                      ["100012345N" "" "" "1"]])
      => [{:vtjprt "1030462707" :publicity "salainen"}
          {:vtjprt "100012345N" :myyntipalvelussa true}])

    (fact "Fields that dont have value in data row do not end up in the updates when"
      (fact "only VTJPRT in excell"
        (attr/header+rows->building-attributes ["VTJPRT"]
                                               [["182736459F"]])
        => [{:vtjprt "182736459F"}])

      (fact "multiple fields in header but no corresponding values on all rows"
        (attr/header+rows->building-attributes ["VTJPRT" "NÄKYVYYS" "JULKISUUS" "MYYNTIPALVELUSSA" "RATU" "KIINTEISTÖTUNNUS" "OSOITE" "LISÄTIETO"]
                                               [["182736459F" "" "" "" "" "" "" ""]
                                        ["1030462707"]
                                        ["100012345N" "3" nil nil]
                                        ["1234567892" "" "1"]
                                        ["102260741K" "" "" "1"]
                                        ["1030368157" "" "" "" "" "" "" "Kommentti"]])
        => [{:vtjprt "182736459F"}
            {:vtjprt "1030462707"}
            {:vtjprt "100012345N" :visibility "julkinen"}
            {:vtjprt "1234567892" :publicity "salainen"}
            {:vtjprt "102260741K" :myyntipalvelussa true}
            {:vtjprt "1030368157" :comment "Kommentti"}]))))

(def dummy-id "99")
(def id-fn (constantly dummy-id))

(facts "->db-buildings"

  (fact "returns empty db-buildings when attributes-list empty"
    (attr/->db-buildings [] "073-R" 123 id-fn) => {:valid-buildings []})

  (fact "turns attributes into proper db-buildings when there are no invalid entries"
    (attr/->db-buildings [{:vtjprt           "182736459F"
                           :myyntipalvelussa true
                           :publicity        "julkinen"}] "073-R" 123 id-fn)
    => {:valid-buildings [{:id           dummy-id
                           :organization "073-R"
                           :meta         {:modified 123}
                           :attributes   {:vtjprt           "182736459F"
                                          :myyntipalvelussa true
                                          :publicity        "julkinen"}}]})
  (fact "Returns valid and invalid buildings under different keys"
    (attr/->db-buildings [{:vtjprt "182736459F"}
                          {:vtjprt "VIALLINEN-VTJPRT"}
                          {:vtjprt "1030462707" :publicity "VIALLINEN-JULKISUUSLUOKKA"}
                          {:vtjprt "100012345N" :kiinteistotunnus "VIALLINEN-KIINTEISTOTUNNUS"}] "073-R" 123 id-fn)
    => {:valid-buildings   [{:id           dummy-id
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
                                            :kiinteistotunnus "VIALLINEN-KIINTEISTOTUNNUS"}}]}))
