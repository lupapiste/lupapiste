(ns lupapalvelu.application-utils-test
  (:require [lupapalvelu.application-utils :refer :all]
            [midje.sweet :refer :all]))

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
