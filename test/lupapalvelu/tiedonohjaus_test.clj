(ns lupapalvelu.tiedonohjaus-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [lupapalvelu.tiedonohjaus :refer :all]
            [lupapalvelu.action :as action]))

(facts "about tiedonohjaus utils"
  (fact "case file report data is generated from application"
    (let [application {:organization "753-R"
                       :tosFunction  "10 03 00 01"
                       :created      100
                       :attachments  [{:type     {:foo :bar}
                                       :versions [{:version 1
                                                   :created 200}
                                                  {:version 2
                                                   :created 500}]}
                                      {:type     {:foo :qaz}
                                       :versions [{:version 1
                                                   :created 300}]}]
                       :history      [{:state "draft" :ts 100}
                                      {:state "open" :ts 250}]}]
      (generate-case-file-data application) => [{:action    "Valmisteilla"
                                                 :start     100
                                                 :documents [{:type     :hakemus
                                                              :category :document
                                                              :ts       100}
                                                             {:type     {:foo :bar}
                                                              :category :attachment
                                                              :version  1
                                                              :ts       200}]}
                                                {:action    "K\u00e4sittelyss\u00e4"
                                                 :start     250
                                                 :documents [{:type     {:foo :qaz}
                                                              :category :attachment
                                                              :version  1
                                                              :ts       300}
                                                             {:type     {:foo :bar}
                                                              :category :attachment
                                                              :version  2
                                                              :ts       500}]}]
      (provided
        (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
        (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"})))

  (fact "application and attachment state (tila) is changed correctly"
    (let [command {:created     12345678
                   :data        {:id 1000}
                   :application {:id           1000
                                 :organization "753-R"
                                 :metadata     {:tila "luonnos"}
                                 :attachments  [{:id 1 :metadata {:tila "luonnos"}}
                                                {:id 2 :metadata {:tila "luonnos"}}]}}]
      (change-app-and-attachments-metadata-state! command :luonnos :valmis) => nil
      (provided
        (action/update-application command {$set {:modified      12345678
                                                  :metadata.tila :valmis
                                                  :attachments   [{:id 1 :metadata {:tila :valmis} :modified 12345678}
                                                                  {:id 2 :metadata {:tila :valmis} :modified 12345678}]}}) => nil)))
  (fact "attachment state (tila) is changed correctly"
    (let [application {:id           1000
                       :organization "753-R"
                       :metadata     {:tila "luonnos"}
                       :attachments  [{:id 1 :metadata {:tila "luonnos"}}
                                      {:id 2 :metadata {:tila "luonnos"}}]}
          now 12345678
          attachment-id 2]
      (change-attachment-metadata-state! application now attachment-id :luonnos :valmis) => nil
      (provided
        (action/update-application (action/application->command application)
                                   {:attachments.id attachment-id}
                                   {$set {:modified                    now
                                          :attachments.$.metadata.tila :valmis}}) => nil))))
