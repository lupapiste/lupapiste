(ns lupapalvelu.digitizer-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.digitizer :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.common-reader :as common-reader]
            [sade.xml :as xml]
            [sade.util :as util]))

(testable-privates lupapalvelu.digitizer
                   good-buildings resolve-verdict-updates)

(facts "about digitizer tools"

  (let [v1 {:id 1 :kuntalupatunnus "foo" :timestamp 11 :paatokset []}
        v2 {:id 2 :kuntalupatunnus "bar" :timestamp 22}
        v3 {:id 3 :kuntalupatunnus "three" :timestamp 33
            :paatokset [{:poytakirjat [{:paatospvm 98765}]}]}
        v4 {:id 4 :kuntalupatunnus "four" :timestamp 44
            :paatokset [{:poytakirjat [{:muupvm 543} {:foo "bar"}]}]}]

    (facts "No verdicts"
      (resolve-verdict-updates nil nil) => []
      (resolve-verdict-updates [] []) => []
      (resolve-verdict-updates [] [{:id 1 :kuntalupatunnus "ignored"}]) => []
      (resolve-verdict-updates nil [{:id 1 :kuntalupatunnus "ignored"}]) => [])

    (fact )

    (fact "new verdict ids can be added"
      (resolve-verdict-updates [v1 v2] [v1 v2 {:kuntalupatunnus "baz"}])
      => [v1 v2 {:id "id1234" :kuntalupatunnus "baz" :timestamp nil :paatokset []}]
      (provided (mongo/create-id) => "id1234"))

    (fact "new verdict with id is ignored"
      (resolve-verdict-updates [v1 v2] [v1 v2 {:id 5 :kuntalupatunnus "baz"}])
      => [v1 v2])

    (fact "new verdict with existing kuntalupatunnus is not created"
      (resolve-verdict-updates [v1] [v1 {:kuntalupatunnus "foo"}])
      => [v1]
      (resolve-verdict-updates [v1] [v1 {:kuntalupatunnus "one"} {:kuntalupatunnus "one"
                                                               :msg "not created"}])
      => [v1 {:id "hello" :kuntalupatunnus "one" :timestamp nil :paatokset []}]
      (provided (mongo/create-id) => "hello"))

    (fact "modify kuntalupatunnus in an existing verdict"
      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :kuntalupatunnus "baz"}])
      => [v1 {:id 2 :kuntalupatunnus "baz" :timestamp 22}])

    (fact "Missing kuntalupatunnus is different than nil"
      (resolve-verdict-updates [v1 v2] [v1 {:id 2}])
      => [v1 v2]
      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :kuntalupatunnus nil}])
      => [v1 {:id 2 :kuntalupatunnus nil :timestamp 22}])

    (facts "modify verdict date in an existing verdict"
      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate 12345} v2])
      => [{:id 1 :kuntalupatunnus "foo" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [{:id 1} v2])
      => [v1 v2]

      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate nil} v2])
      => [{:id 1 :kuntalupatunnus "foo" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm nil}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :verdictDate 12345}])
      => [v1 {:id 2 :kuntalupatunnus "bar" :timestamp 22
              :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v3 v1 v2] [v1 v2 {:id 3 :verdictDate 12345}])
      => [v1 v2
          {:id 3 :kuntalupatunnus "three" :timestamp 33
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v1 v4 v2] [v2 {:id 4 :verdictDate 12345} v1])
      => [v2 {:id 4 :kuntalupatunnus "four" :timestamp 44
              :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                         {:foo "bar"}]}]}
          v1])
    (facts "modify both"
      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate 12345
                                         :kuntalupatunnus "one"} v2])
      => [{:id 1 :kuntalupatunnus "one" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :verdictDate 12345
                                            :kuntalupatunnus "one"}])
      => [v1 {:id 2 :kuntalupatunnus "one" :timestamp 22
              :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v3 v1 v2] [v1 v2 {:id 3 :verdictDate 12345
                                                  :kuntalupatunnus "one"}])
      => [v1 v2
          {:id 3 :kuntalupatunnus "one" :timestamp 33
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v1 v4 v2] [v2 {:id 4 :verdictDate 12345
                                               :kuntalupatunnus "one"} v1])
      => [v2 {:id 4 :kuntalupatunnus "one" :timestamp 44
              :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                         {:foo "bar"}]}]}
          v1])

    (facts "Cumulative updates"
      (resolve-verdict-updates [v1 v4 v2]
                               [v2
                                {:id 4 :kuntalupatunnus "one"}
                                {:kuntalupatunnus "one"} ; Not created
                                {:id 4 :verdictDate 12345}
                                {:id 4 :kuntalupatunnus "two"}
                                v1])
      [v2 {:id 4 :kuntalupatunnus "two" :timestamp 44
           :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                      {:foo "bar"}]}]}
       v1])

    (fact "Verdict is removed if not present in the updates"
      (resolve-verdict-updates [v1 v2] [{:id 1}])
      => [v1]

      (resolve-verdict-updates [v1 v2] [{:id 1}
                                        {:kuntalupatunnus "bar"}])
      => [v1 {:id "new" :kuntalupatunnus "bar" :timestamp nil
              :paatokset []}]
      (provided (mongo/create-id) => "new"))))

(facts "good-buildings"
  (fact "invalid id 2"
    (good-buildings [{:location [395320.093 6697384.603] :nationalId 1}
                     {:location [1 2] :nationalId 2}])
    => [{:location [395320.093 6697384.603] :nationalId 1}])
  (fact "all valid"
    (good-buildings [{:location [395320.093 6697384.603] :nationalId 1}
                     {:location [430109.3125 7210461.375] :nationalId 2}])
    => [{:location [395320.093 6697384.603] :nationalId 1}
        {:location [430109.3125 7210461.375] :nationalId 2}])
  (fact "all bad"
    (good-buildings [{:location [395320.093 6] :nationalId 1}
                     {:location [430109.3125 7210461375] :nationalId 2}])
    => nil)
  (fact "not validated unless there is non-nil :location key"
    (good-buildings [{:nationalId 1}
                     {:nationalId 2 :location nil}])
    => [{:nationalId 1}
        {:nationalId 2 :location nil}]))

(defn read-xml [source]
  (-> (slurp source)
      (xml/parse-string  "utf8")
      common-reader/strip-xml-namespaces))

(def app-xml   (read-xml (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))
(def build-xml (read-xml "dev-resources/krysp/building-2.2.0.xml"))

(def backend-id "14-0241-R 3")
(def app-id     "LX-123-2025-00123")
(def good       {:ok true :id app-id})

(def location [:x 395287.043 :y 6702930.767
               :address "Testitie 10"
               :propertyId "18640200080000"])

(def lox-map (into {} (partitionv 2 location)))

(defn fetch-or-create [& {:as m}]
  (fetch-or-create-archiving-project! {:data (merge {:lang "fi"
                                                     :organizationId "123-R"
                                                     :kuntalupatunnus backend-id}
                                                    m)}))

(against-background
  [(lupapalvelu.digitizer/create-archiving-project-application!
     anything anything anything anything anything anything anything anything)
   => {:id app-id}
   (org/get-organization "123-R")
   => {:default-digitalization-location {:x "388587" :y "6725517"}
       :permanent-archive-enabled       true}
   (org/get-building-wfs anything anything) => (util/mmap :url :credentials)]
  (facts "fetch-or-create-archiving-project!"
    (fact "Application KuntaGML found"
      (fetch-or-create :createWithPreviousPermit true
                       :createWithBuildings true) => good
      (provided
        (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
        => app-xml
        (building-reader/building-xml anything anything anything)
        => :not-called :times 0))
    (fact "Application KuntaGML not found, but required: error"
      (fetch-or-create :createWithPreviousPermit true)
      => {:ok             false
          :permitNotFound true
          :text           "error.no-previous-permit-found-from-backend"}
      (provided
        (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
        => nil
        (building-reader/building-xml anything anything anything)
        => :not-called :times 0)
      (fetch-or-create :createWithPreviousPermit true)
      => {:ok             false
          :permitNotFound true
          :text           "error.no-previous-permit-found-from-backend"}
      (provided
        (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
        =throws=> (Exception. "Kaboom!")
        (building-reader/building-xml anything anything anything)
        => :not-called :times 0))
    (facts "Create without previous permit (no application KuntaGML)"
      (fact "Fetch buildings, but not KuntaGML: createWithBuildings"
        (apply fetch-or-create :createWithBuildings true location)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          => build-xml))
      (fact "Fetch buildings, but not KuntaGML: refreshBuildings"
        (apply fetch-or-create :refreshBuildings true location)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          => build-xml))
      (fact "... location is mandatory"
        (fetch-or-create)
        => {:needMorePrevPermitInfo true
            :ok                     false
            :text                   "error.more-prev-app-info-needed"}
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (building-reader/building-xml anything anything anything)
          => nil :times 0))
      (fact "No buildings found: createWithBuildings"
        (apply fetch-or-create :createWithBuildings true location)
        => {:buildingsNotFound true
            :ok                false
            :text              "error.no-buildings-found-from-backend"}
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          => nil :times 1)
        (apply fetch-or-create :createWithBuildings true location)
        => {:buildingsNotFound true
            :ok                false
            :text              "error.no-buildings-found-from-backend"}
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          =throws=> (Exception. "Kaboom!") :times 1))
      (fact "No buildings found: refreshBuildings"
        (apply fetch-or-create :refreshBuildings true location)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          => nil :times 1)
        (apply fetch-or-create :refreshBuildings true location)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          =throws=> (Exception. "Kaboom!") :times 1))
      (fact "Create without buildings"
        (apply fetch-or-create
               location)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (krysp-reader/resolve-valid-location nil anything)
          => lox-map
          (building-reader/building-xml anything anything anything)
          => nil :times 0))
      (fact "Create into default location"
        (fetch-or-create :createWithDefaultLocation true
                         :createWithBuildings true)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (building-reader/building-xml anything anything anything)
          => build-xml :times 1))
      (fact "Create into default location without buildings"
        (fetch-or-create :createWithDefaultLocation true)
        => good
        (provided
          (krysp-fetch/get-application-xml-by-backend-id anything backend-id)
          => nil :times 0
          (building-reader/building-xml anything anything anything)
          => build-xml :times 0)))))
