(ns lupapalvelu.conversion.util-test
  (:require [lupapalvelu.backing-system.krysp.application-from-krysp :refer [get-local-application-xml-by-filename]]
            [lupapalvelu.backing-system.krysp.reader :as reader]
            [lupapalvelu.conversion.util :as util]
            [midje.sweet :refer :all]
            [sade.core :refer :all]))

(def dummy-application
  {:primaryOperation {:id "5c890a531d8bffa82469437f"
                      :name "kerrostalo-rivitalo"
                      :description "Luhtitalo"
                      :created (now)}
   :history [{:state :submitted
              :ts 1378080000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :submitted
              :ts 1378080000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :sent
              :ts 1379635200000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :verdictGiven
              :ts 1379980800000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :canceled
              :ts 1454976000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}]})

(def another-dummy-application
  {:primaryOperation {:id "5c890a531d8bffa82469437f"
                      :name "kerrostalo-rivitalo"
                      :description "Luhtitalo"
                      :created (now)}
   :history [{:state :submitted
              :ts 1378080000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :submitted
              :ts 1378080000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :sent
              :ts 1379635200000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :verdictGiven
              :ts 1379980800000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :closed
              :ts 1454976000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}
             {:state :canceled
              :ts 1454976000000
              :user {:enabled true
                     :firstName "Lupapiste"
                     :id "batchrun-user"
                     :lastName "Er\u00e4ajo"
                     :role "authority"
                     :username "eraajo@lupapiste.fi"}}]})

(facts "Permit id variations are generated correctly"
  (util/get-permit-id-variations "12-0089-D 17")
  => ["12-0089-D 17" ; (Original) database format
      "17-0089-12-D" ; General format
      "17 12-0089-D" ; Database format with kaupunginosa at front
      "0089-12-D"    ; General format without kaupunginosa
      "12-0089-D"]   ; Database format without kaupunginosa
  (util/get-permit-id-variations "164 10-3001-R")
  => ["164 10-3001-R" ; (Original) database format with kaupunginosa at front
      "164-3001-10-R" ; General format
      "10-3001-R 164" ; Database format
      "3001-10-R"     ; General format without kaupunginosa
      "10-3001-R"]    ; Database format without kaupunginosa
  (util/get-permit-id-variations "0010-00-TJO")
  => ["0010-00-TJO"   ; (Original) general format without kaupunginosa
      "00-0010-TJO"]) ; Database format without kaupunginosa

(fact "Permit ids can be destructured into their constituent parts"
  (facts "Destructuring gives same results despite the input format "
    (util/destructure-permit-id "12-0089-D 17") => (util/destructure-permit-id "17-0089-12-D")
    (util/destructure-permit-id "98-0088-10-A") => (util/destructure-permit-id "10-0088-A 98")
    (util/destructure-permit-id "164 10-3001-R")= {:vuosi   2010
                                                   :kauposa "164"
                                                   :no      "3001"
                                                   :tyyppi  "R"})
  (facts "The results are a map with four keys"
    (util/destructure-permit-id "75-0549-16-DJ") => {:kauposa "75" :no "0549" :tyyppi "DJ" :vuosi 2016}
    (-> (util/destructure-permit-id "16-0549-DJ 75") keys count) => 4)
  (facts "Legacy ids without kauposa work as well"
    (util/destructure-permit-id "0010-00-TJO") => {:no "0010" :tyyppi "TJO" :vuosi 2000})
  (facts "Destructuring invalid ids results in exception"
    (util/destructure-permit-id nil) => (throws)
    (util/destructure-permit-id "") => (throws)
    (util/destructure-permit-id "  ") => (throws)
    (util/destructure-permit-id "Hei \u00e4ij\u00e4t :D Mit\u00e4 \u00e4ij\u00e4t :D Siistii n\u00e4h\u00e4 teit :D") => (throws)
    (util/destructure-permit-id "75-0549-4242-A") => (throws)
    (util/destructure-permit-id "75 0549-4242-A") => (throws)))

(fact "The reader function rakennelmatieto->kaupunkikuvatoimenpide produces documents as expected"
  (let [xml (get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-structure.xml" "R")
        rakennelmatiedot (reader/->rakennelmatiedot xml)
        doc (->> rakennelmatiedot (map util/rakennelmatieto->kaupunkikuvatoimenpide) first)]
    (facts "The generated document looks sane"
      (get-in doc [:data :kuvaus :value]) => "Katos"
      (get-in doc [:schema-info :name]) => "kaupunkikuvatoimenpide")))

(fact "A large number of unique application ids can be generated for a given year"
  (let [counter (atom 0)
        ids (take 10000 (repeatedly #(util/make-converted-application-id "092" "71-0004-13-C")))]
    (-> ids set count) => 10000
    (provided
      (lupapalvelu.mongo/get-next-sequence-value "applications-092-2013") => (swap! counter inc)
      (lupapalvelu.mongo/any? :applications anything) => false)))

(fact "Generated application id vs. feature"
  (util/make-converted-application-id "123" "71-0004-99-D")
  => "LP-123-1999-00012"
  (provided
    (lupapalvelu.mongo/get-next-sequence-value "applications-123-1999") => 12
    (lupapalvelu.mongo/any? :applications anything) => false
    (sade.env/feature? :prefixed-id) => false)
  (util/make-converted-application-id "123" "71-0004-99-D")
  => "LP-123-1999-90012"
  (provided
    (lupapalvelu.mongo/get-next-sequence-value "applications-123-1999") => 12
    (lupapalvelu.mongo/any? :applications anything) => false
    (sade.env/feature? :prefixed-id) => true))

(fact "Kiinteistötunnus-ids can be hyphenated"
  (facts "Ids without error work as expected"
    (util/normalize-ktunnus "09201202030015") => "092-012-0203-0015"
    (util/normalize-ktunnus "09208000480005") => "092-080-0048-0005")
  (facts "Määräalatunnus is included if it's provided"
    (util/normalize-ktunnus "09201202030015" "M0014") => "092-012-0203-0015-M-0014"
    (util/normalize-ktunnus "09208000480005" "M0109") => "092-080-0048-0005-M-0109"
    (facts "Unless it's invalid"
      (util/normalize-ktunnus "09201202030015" "M00014") => "092-012-0203-0015"
      (util/normalize-ktunnus "09201202030015" nil) => "092-012-0203-0015"
      (util/normalize-ktunnus "09208000480005" "") => "092-080-0048-0005"))
  (facts "Ids in unexpected format are left untouched"
    (util/normalize-ktunnus "092080004700FF") => "092080004700FF"
    (util/normalize-ktunnus "INGENTING") => "INGENTING"))

(fact "State determination heuristics work as expected"
  (util/determine-app-state dummy-application) => :canceled
  (util/determine-app-state another-dummy-application) => :closed)

(facts "Generate history array"
  (fact "Regular case"
    (util/generate-history-array ..anything.. {:permitType "R"})
    => (just (just {:state :submitted :ts 2 :user truthy})
             (just {:state :constructionStarted :ts 3 :user truthy}))
    (provided
      (lupapalvelu.backing-system.krysp.reader/get-sorted-tilamuutos-entries ..anything..)
      => [{:tila "ei tiedossa" :pvm 1}
          {:tila "vireillä" :pvm 2}
          {:tila "rakennustyöt aloitettu" :pvm 3}]))
  (fact "Bad or missing data"
    (util/generate-history-array ..anything.. {:permitType "R"})
    => (just (just {:state :submitted :ts 0 :user truthy})
             (just {:state :constructionStarted :ts 0 :user truthy})
             :in-any-order)
    (provided
      (lupapalvelu.backing-system.krysp.reader/get-sorted-tilamuutos-entries ..anything..)
      => [{:tila "ei tiedossa" :pvm 1}
          {:tila "kumma humma" :pvm 11234}
          {:tila "vireillä" :pvm -2}
          {:tila "rakennustyöt aloitettu"}])))
