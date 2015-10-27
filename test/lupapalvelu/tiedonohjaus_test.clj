(ns lupapalvelu.tiedonohjaus-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.tiedonohjaus :refer :all]
            [lupapalvelu.mongo :as mongo]))

(when (env/feature? :tiedonohjaus)

  (facts "about tiedonohjaus utils"
    (fact "case file report data is generated from application"
      (let [application {:organization "753-R"
                         :tosFunction "10 03 00 01"
                         :created 100
                         :attachments [{:type {:foo :bar}
                                        :versions [{:version 1
                                                    :created 200}
                                                   {:version 2
                                                    :created 500}]}
                                       {:type {:foo :qaz}
                                        :versions [{:version 1
                                                    :created 300}]}]
                         :history [{:state "draft" :ts 100}
                                   {:state "open" :ts 250}]}]
        (generate-case-file-data application) => [{:action "Valmisteilla"
                                                   :start 100
                                                   :documents [{:type :hakemus
                                                                :category :document
                                                                :ts 100}
                                                               {:type {:foo :bar}
                                                                :category :attachment
                                                                :version 1
                                                                :ts 200}]}
                                                  {:action "K\u00e4sittelyss\u00e4"
                                                   :start 250
                                                   :documents [{:type {:foo :qaz}
                                                                :category :attachment
                                                                :version 1
                                                                :ts 300}
                                                               {:type {:foo :bar}
                                                                :category :attachment
                                                                :version 2
                                                                :ts 500}]}]
        (provided
          (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
          (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"}))))

  )  ;; /env/feature? :tiedonohjaus

