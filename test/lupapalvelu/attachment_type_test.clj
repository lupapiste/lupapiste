(ns lupapalvelu.attachment-type-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.attachment-type :refer :all]
            [lupapalvelu.attachment-metadata :refer :all]))

(facts "Test parse-attachment-type"
  (fact (parse-attachment-type "foo.bar")  => {:type-group :foo, :type-id :bar})
  (fact (parse-attachment-type "foo.")     => nil)
  (fact (parse-attachment-type "")         => nil)
  (fact (parse-attachment-type nil)        => nil))

(facts "Facts about allowed-attachment-types-contain?"
  (let [allowed-types [["a" ["1" "2"]] [:b [:3 :4]]]]
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :1}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :2}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :3}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :4}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :5}) => falsey)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :c :type-id :1}) => falsey)))

(fact "attachment type IDs are unique"
  (let [known-duplicates (set (conj osapuolet
                                :ote_asunto-osakeyhtion_kokouksen_poytakirjasta
                                :ote_alueen_peruskartasta
                                :ote_asemakaavasta
                                :ote_kauppa_ja_yhdistysrekisterista
                                :asemapiirros
                                :ote_yleiskaavasta
                                :jaljennos_perunkirjasta
                                :valokuva :rasitesopimus
                                :valtakirja
                                :muu
                                :paatos
                                :paatosote))
        all-except-commons (remove known-duplicates all-attachment-type-ids)
        all-unique (set all-except-commons)]

    (count all-except-commons) => (count all-unique)))
