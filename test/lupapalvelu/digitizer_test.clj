(ns lupapalvelu.digitizer-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.digitizer :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(facts "about digitizer tools"

  (let [app-id "LX-999-2017-00001"
        existing-verdicts [{:id 1
                            :kuntalupatunnus "foo"}
                           {:id 2
                            :kuntalupatunnus "bar"}]
        application {:id app-id
                     :verdicts existing-verdicts}
        command {:data {:id app-id}
                 :application application}]

    (against-background
      [(mongo/create-id) => "id1234"
       (mongo/update-by-query :applications
                              {:_id app-id}
                              {$push {:verdicts {$each [{:id "id1234"
                                                         :kuntalupatunnus "baz"
                                                         :timestamp       nil
                                                         :paatokset       []
                                                         :draft           true}]}}}) => 1]

      (fact "new verdict ids can be added"
        (let
          [verdicts (conj existing-verdicts {:id nil :kuntalupatunnus "baz"})]
          (update-verdicts command verdicts) => nil)))

    (against-background
      [(mongo/update-by-query :applications
                              {:_id app-id :verdicts {$elemMatch {:id 2}}}
                              {$set {:verdicts.$.kuntalupatunnus "baz"
                                     :verdicts.$.paatokset.0.poytakirjat.0.paatospvm nil}}) => 1]
      (fact "kuntalupatunnus can be modified"
        (let
          [verdicts [(first existing-verdicts) {:id 2 :kuntalupatunnus "baz"}]]
          (update-verdicts command verdicts) => nil)))


    (fact "verdicts can be removed"
      (against-background
        [(mongo/update-by-query :applications {:_id app-id} {$pull {:verdicts {:id 2}}}) => 1]
        (fact "kuntalupatunnus can be modified"
          (let
            [verdicts [(first existing-verdicts)]]
            (update-verdicts command verdicts) => nil))))))
