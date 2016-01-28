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
                       :applicant "Testaaja Testi"
                       :attachments  [{:type     {:foo :bar}
                                       :versions [{:version 1
                                                   :created 200
                                                   :user {:firstName "Testi"
                                                          :lastName "Testaaja"}}
                                                  {:version 2
                                                   :created 500
                                                   :user {:firstName "Testi"
                                                          :lastName "Testaaja"}}]
                                       :user {:firstName "Testi"
                                              :lastName "Testaaja"}
                                       :contents "Great attachment"}
                                      {:type     {:foo :qaz}
                                       :versions [{:version 1
                                                   :created 300
                                                   :user {:firstName "Testi"
                                                          :lastName "Testaaja"}}]}]
                       :history      [{:state "draft"
                                       :ts 100
                                       :user {:firstName "Testi"
                                              :lastName "Testaaja"}}
                                      {:state "open"
                                       :ts 250
                                       :user {:firstName "Testi"
                                              :lastName "Testaaja"}}]}]
      (generate-case-file-data application) => [{:action    "Valmisteilla"
                                                 :start     100
                                                 :user "Testaaja Testi"
                                                 :documents [{:type     :hakemus
                                                              :category :document
                                                              :ts       100
                                                              :user "Testaaja Testi"}
                                                             {:type     {:foo :bar}
                                                              :category :attachment
                                                              :version  1
                                                              :ts       200
                                                              :user "Testaaja Testi"
                                                              :contents "Great attachment"}]}
                                                {:action    "K\u00e4sittelyss\u00e4"
                                                 :start     250
                                                 :user "Testaaja Testi"
                                                 :documents [{:type     {:foo :qaz}
                                                              :category :attachment
                                                              :version  1
                                                              :ts       300
                                                              :user "Testaaja Testi"
                                                              :contents nil}
                                                             {:type     {:foo :bar}
                                                              :category :attachment
                                                              :version  2
                                                              :ts       500
                                                              :user "Testaaja Testi"
                                                              :contents "Great attachment"}]}]
      (provided
        (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
        (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"})))

  (fact "application and attachment state (tila) is changed correctly"
    (let [data-and-app {:data        {:id 1000}
                        :application {:id           1000
                                      :organization "753-R"
                                      :metadata     {:tila "luonnos"}
                                      :attachments  [{:id 1 :metadata {:tila "luonnos"}}
                                                     {:id 2 :metadata {:tila "luonnos"}}]}}
          command (merge data-and-app {:created 12345678})]
      (change-app-and-attachments-metadata-state! command :luonnos :valmis) => nil
      (provided
        (action/update-application command {$set {:modified      12345678
                                                  :metadata.tila :valmis}}) => nil
        (action/update-application data-and-app
                                   {:attachments.id 1}
                                   {$set {:modified                    12345678
                                          :attachments.$.metadata.tila :valmis}}) => nil
        (action/update-application data-and-app
                                   {:attachments.id 2}
                                   {$set {:modified                    12345678
                                          :attachments.$.metadata.tila :valmis}}) => nil)))

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
