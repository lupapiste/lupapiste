(ns lupapalvelu.conversion.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.conversion.util :as util]
            [sade.strings :as ss]))

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
  (let [rakennelmatieto {:Rakennelma {:alkuHetki "2013-01-10T00:00:00Z"
                                      :kuvaus {:kuvaus "Katos"}
                                      :sijaintitieto {:Sijainti {:piste {:Point {:pos "2.449317E7 6445909.0"}}}}
                                      :tunnus {:aanestysalue nil
                                               :jarjestysnumero "1"
                                               :kiinttun "07201800869996"
                                               :kunnanSisainenPysyvaRakennusnumero "05643"
                                               :rakennuksenSelite "akt"
                                               :rakennusnro "001"}
                                      :yksilointitieto "0543154"}}]
    (fact "A rakennelmatieto with tunnus results in a document of type 'kaupunkikuvatoimenpide'"
      (util/rakennelmatieto->kaupunkikuvatoimenpide rakennelmatieto) => {:kayttotarkoitus {:value ""}
                                                                    :kokonaisala {:value ""}
                                                                    :kuvaus {:value "Katos"}
                                                                    :tunnus {:value "05643"}
                                                                    :valtakunnallinenNumero {:value "07201800869996"}})
    (fact "A rakennelmatieto without tunnus results in a document of type 'kaupunkikuvatoimenpide-ei-tunnusta'"
      (util/rakennelmatieto->kaupunkikuvatoimenpide (update-in rakennelmatieto [:Rakennelma] dissoc :tunnus)) => {:kayttotarkoitus {:value ""}
                                                                                                             :kokonaisala {:value ""}
                                                                                                             :kuvaus {:value "Katos"}})))
