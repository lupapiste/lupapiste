(ns lupapalvelu.application-utils-test
  (:require [lupapalvelu.application :refer [make-application-id]]
            [lupapalvelu.application-utils :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.env :as env]))

(facts "Operation description"
  (let [app        {:primaryOperation {:name "kerrostalo-rivitalo"}}
        legacy-app {:primaryOperation nil}]
    (fact "Normal application"
      (operation-description app :fi) => "Asuinkerrostalon tai rivitalon rakentaminen"
      (operation-description app :sv) => "Byggande av flerv\u00E5ningshus eller radhus")
    (fact "Legacy application"
      (operation-description legacy-app :fi) => ""
      (operation-description legacy-app :sv) => "")))

(facts person-id-masker-for-user
  (fact "handler authority - no masking"
    ((person-id-masker-for-user {:id ..id.. :role :authority} {:handlers [{:userId ..id..}]}) {:schema-info {:name "maksaja"}
                                                                                               :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})

  (fact "non handler authority"
    ((person-id-masker-for-user {:id ..id.. :role :authority :orgAuthz {:org-id #{:authority}}} {:organization "org-id" :handlers [{:userId ..other-id..}]})
      {:schema-info {:name "maksaja"}
       :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "010101-****"}}}}})

  (fact "authority in different organization"
    ((person-id-masker-for-user {:id ..id.. :role :authority :orgAuthz {:another-org-id #{:authority}}} {:organization "org-id" :handlers [{:userId ..other-id..}]})
      {:schema-info {:name "maksaja"}
       :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}})

  (fact "non authority user"
    ((person-id-masker-for-user {:id ..id.. :role :authority} {:handlers [{:userId ..other-id..}]}) {:schema-info {:name "maksaja"}
                                                                                                     :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}}))

(facts get-sorted-operation-documents
  (fact "one operation - two docs"
    (get-sorted-operation-documents {:documents           [{:schema-info {:name ..doc-name-1.. :op {:id ..op-id-1..}}}
                                                           {:schema-info {:name ..doc-name-2..}}]
                                     :primaryOperation     {:id ..op-id-1.. :name "paatoimenpide" :description "" :created 0}
                                     :secondaryOperations []})
    => [{:schema-info {:name ..doc-name-1.. :op {:id ..op-id-1..}}}])

  (fact "one operation - missing doc"
    (get-sorted-operation-documents {:documents           [{:schema-info {:name ..doc-name-2.. :op {:id ..op-id-2..}}}]
                                     :primaryOperation     {:id ..op-id-1.. :name "paatoimenpide" :description "" :created 0}
                                     :secondaryOperations []})
    => [])

  (fact "multiple operations"
    (get-sorted-operation-documents {:documents           [{:schema-info {:name ..doc-name-1.. :op {:id ..op-id-1..}}}
                                                           {:schema-info {:name ..doc-name-2..}}
                                                           {:schema-info {:name ..doc-name-3.. :op {:id ..op-id-3..}}}
                                                           {:schema-info {:name ..doc-name-4.. :op {:id ..op-id-4..}}}
                                                           {:schema-info {:name ..doc-name-5.. :op {:id ..op-id-5..}}}
                                                           {:schema-info {:name ..doc-name-6.. :op {:id ..op-id-2..}}}
                                                           {:schema-info {:name ..doc-name-7..}}]
                                     :primaryOperation     {:id ..op-id-5.. :name "paatoimenpide" :description "" :created 4}
                                     :secondaryOperations [{:id ..op-id-4.. :name "muutoimenpide" :description "" :created 3}
                                                           {:id ..op-id-2.. :name "eritoimenpide" :description "" :created 1}
                                                           {:id ..op-id-3.. :name "jokutoimenpide" :description "" :created 2}
                                                           {:id ..op-id-1.. :name "sivutoimenpide" :description "" :created 0}]})
    => [{:schema-info {:name ..doc-name-5.. :op {:id ..op-id-5..}}}
        {:schema-info {:name ..doc-name-1.. :op {:id ..op-id-1..}}}
        {:schema-info {:name ..doc-name-6.. :op {:id ..op-id-2..}}}
        {:schema-info {:name ..doc-name-3.. :op {:id ..op-id-3..}}}
        {:schema-info {:name ..doc-name-4.. :op {:id ..op-id-4..}}}]))

(env/with-feature-value :prefixed-id false
  (facts "VRKLupatunnus"
    (vrk-lupatunnus {:municipality "" :created nil}) => nil
    (vrk-lupatunnus {:municipality "123"
                     :created      (date/timestamp "11.10.2016")
                     :id           "LP-123-2016-00321"}) => "12300016-0321"
    (vrk-lupatunnus {:municipality "123"
                     :created      (date/timestamp "11.10.2016")
                     :submitted    (date/timestamp "1.1.2017")
                     :id           "LP-123-2016-00321"}) => "12300017-0321"
    (fact "special '0000'"
      (vrk-lupatunnus {:municipality "123"
                       :created      (date/timestamp "11.10.2016")
                       :submitted    (date/timestamp "1.1.2017")
                       :id           "LP-123-2016-10000"}) => "12300017-1000")

    (fact "with sequence"
      (vrk-lupatunnus {:municipality "123"
                       :created      (date/timestamp "11.10.2016")
                       :submitted    (date/timestamp "1.1.2017")
                       :id           (make-application-id "123")}) => "12300017-0012"
      (provided
        (lupapalvelu.mongo/get-next-sequence-value anything) => 12))
    (fact "with sequence 9999"
      (vrk-lupatunnus {:municipality "123"
                       :created      (date/timestamp "11.10.2016")
                       :submitted    (date/timestamp "1.1.2017")
                       :id           (make-application-id "123")}) => "12300017-9999"
      (provided
        (lupapalvelu.mongo/get-next-sequence-value anything) => 9999))
    (fact "with sequence 10000"
      (vrk-lupatunnus {:municipality "123"
                       :created      (date/timestamp "11.10.2016")
                       :submitted    (date/timestamp "1.1.2017")
                       :id           (make-application-id "123")}) => "12300017-1000"
      (provided
        (lupapalvelu.mongo/get-next-sequence-value anything) => 10000))))
