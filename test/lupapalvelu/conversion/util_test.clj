(ns lupapalvelu.conversion.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.conversion.util :as util]
            [lupapalvelu.backing-system.krysp.application-from-krysp :refer [get-local-application-xml-by-filename]]
            [lupapalvelu.backing-system.krysp.reader :as reader]))

(fact "Conversion of permit ids from 'database-format' works as expected"
  (facts "ids in 'database-format' are converted into 'ui-format'"
    (util/normalize-permit-id "12-0089-D 17") => "17-0089-12-D"
    (util/normalize-permit-id "10-0088-A 98") => "98-0088-10-A"
    (util/normalize-permit-id "16-0549-DJ 75") => "75-0549-16-DJ")
  (facts "ids already in 'ui-format' are not altered"
    (util/normalize-permit-id "17-0089-12-D") => "17-0089-12-D"
    (util/normalize-permit-id "98-0088-10-A") => "98-0088-10-A"
    (util/normalize-permit-id "75-0549-16-DJ") => "75-0549-16-DJ"))

(fact "Permit ids can be destructured into their constituent parts"
  (facts "Destructuring gives same results despite the input format "
    (util/destructure-permit-id "12-0089-D 17") => (util/destructure-permit-id "17-0089-12-D")
    (util/destructure-permit-id "98-0088-10-A") => (util/destructure-permit-id "10-0088-A 98"))
  (facts "The results are a map with four keys"
    (util/destructure-permit-id "75-0549-16-DJ") => {:kauposa "75" :no "0549" :tyyppi "DJ" :vuosi "16"}
    (-> (util/destructure-permit-id "16-0549-DJ 75") keys count) => 4)
  (facts "Destructuring invalid ids results in nil"
    (util/destructure-permit-id "Hei aijat :D Mita aijat :D Siistii naha teit :D") => nil
    (util/destructure-permit-id "75-0549-4242-A") => nil
    (util/destructure-permit-id "75 0549-4242-A") => nil
    (util/destructure-permit-id "751-0549-42-A") => nil))

(fact "The reader function rakennelmatieto->kaupunkikuvatoimenpide produces documents as expected"
  (let [xml (get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-structure.xml" "R")
        rakennelmatiedot (reader/->rakennelmatiedot xml)
        doc (->> rakennelmatiedot (map util/rakennelmatieto->kaupunkikuvatoimenpide) first)]
    (facts "The generated document looks sane"
      (get-in doc [:data :kuvaus :value]) => "Katos"
      (get-in doc [:schema-info :name]) => "kaupunkikuvatoimenpide")))

(fact "A large number of unique application ids can be generated for a given year"
  (let [counter (atom 0)
        ids (take 10000 (repeatedly #(util/make-converted-application-id "71-0004-13-C")))]
    (-> ids set count) => 10000
    (provided
      (lupapalvelu.mongo/get-next-sequence-value "applications-092-2013") => (swap! counter inc))))
