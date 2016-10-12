(ns lupapalvelu.xml.krysp.application-from-krysp-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.application-from-krysp :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [slingshot.slingshot :refer [try+]]
            [sade.common-reader :as scr]
            [sade.xml :as sxml]))

(testable-privates lupapalvelu.xml.krysp.application-from-krysp
                   get-lp-tunnus
                   get-kuntalupatunnus
                   not-empty-content
                   group-content-by
                   get-application-xmls-in-chunks
                   get-application-xmls-by-backend-id)

(facts get-lp-tunnus
  (fact "get tunnus from xml"
    (->> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001"}]])
         scr/strip-xml-namespaces
         (get-lp-tunnus "R")) => "LP-123-2016-00001")

  (fact "get tunnus from empty xml"
    (->> (build-multi-app-xml [])
         scr/strip-xml-namespaces
         (get-lp-tunnus "R")) => nil)

  (fact "get tunnus from nil"
    (get-lp-tunnus "R" nil) => nil)

  (fact "get tunnus from xml with multiple apps"
    (->> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001"}] [{:lp-tunnus "LP-123-2016-00002"}]])
         scr/strip-xml-namespaces
         (get-lp-tunnus "R")) => "LP-123-2016-00001"))

(facts get-kuntalupatunnus
  (fact "get tunnus from xml"
    (->> (build-multi-app-xml [[{:kuntalupatunnus "XYZ-123-G"}]])
         scr/strip-xml-namespaces
         (get-kuntalupatunnus "R")) => "XYZ-123-G")

  (fact "get tunnus from empty xml"
    (->> (build-multi-app-xml [])
         scr/strip-xml-namespaces
         (get-kuntalupatunnus "R")) => nil)

  (fact "get tunnus from nil"
    (get-kuntalupatunnus "R" nil) => nil)

  (fact "get tunnus from xml with multiple apps"
    (->> (build-multi-app-xml [[{:kuntalupatunnus "XYZ-123-G"}] [{:kuntalupatunnus "XYZ-123-F"}]])
         scr/strip-xml-namespaces
         (get-kuntalupatunnus "R")) => "XYZ-123-G"))

(facts not-empty-content
  (fact "content with lp-tunnus"
    (let [xml (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00002"}]])
                  scr/strip-xml-namespaces)]
      (not-empty-content "R" xml) => xml))

  (fact "content with kuntalupatunnus"
    (let [xml (-> (build-multi-app-xml [[{:kuntalupatunnus "XYZ-123-G"}]])
                  scr/strip-xml-namespaces)]
      (not-empty-content "R" xml) => xml))

  (fact "content is empty"
    (let [xml (-> (build-multi-app-xml [])
                  scr/strip-xml-namespaces)]
      (not-empty-content "R" xml) => nil))

  (fact "content is nil"
    (not-empty-content "R" nil) => nil))


(facts group-content-by
  (fact "one application xml"
    (let [xml (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                  scr/strip-xml-namespaces)]
      (group-content-by get-lp-tunnus "R" xml) => {"LP-123-2016-00001" xml}))

  (fact "one application xml - group by kuntalupatunnus"
    (let [xml (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                  scr/strip-xml-namespaces)]
      (group-content-by get-kuntalupatunnus "R" xml) => { "XYZ-123-G" xml}))

  (fact "one application with many xmls contents"
    (let [xml (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]
                                        [{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-F"}]])
                  scr/strip-xml-namespaces)]
      (group-content-by get-lp-tunnus "R" xml) => {"LP-123-2016-00001" xml}))

  (fact "many applications"
    (let [xml (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]
                                        [{:lp-tunnus "LP-123-2016-00002" :kuntalupatunnus "XYZ-123-F"}]])
                  scr/strip-xml-namespaces)
          toimituksen-tiedot (sxml/select1 xml [:toimituksenTiedot])
          result (group-content-by get-lp-tunnus "R" xml)]

      result => map?

      (keys result) => (just #{"LP-123-2016-00001" "LP-123-2016-00002"} :in-any-order :gaps-ok)

      (sxml/select1 (result "LP-123-2016-00001") [:toimituksenTiedot]) = toimituksen-tiedot

      (sxml/select1 (result "LP-123-2016-00002") [:toimituksenTiedot]) = toimituksen-tiedot

      (get-lp-tunnus "R" (result "LP-123-2016-00001")) => "LP-123-2016-00001"

      (get-lp-tunnus "R" (result "LP-123-2016-00002")) => "LP-123-2016-00002"

      (get-kuntalupatunnus "R" (result "LP-123-2016-00001")) => "XYZ-123-G"

      (get-kuntalupatunnus "R" (result "LP-123-2016-00002")) => "XYZ-123-F"

      result => {"LP-123-2016-00001" (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                                         scr/strip-xml-namespaces)
                 "LP-123-2016-00002" (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00002" :kuntalupatunnus "XYZ-123-F"}]])
                                         scr/strip-xml-namespaces)}))

  (fact "nil content"
    (group-content-by get-lp-tunnus "R" nil) => {})

  (fact "empty content"
    (group-content-by get-lp-tunnus "R" {}) => {})

  (fact "empty xml content"
    (->> (build-multi-app-xml [])
         scr/strip-xml-namespaces
         (group-content-by get-lp-tunnus "R")) => {}))

(facts get-application-xml-by-application-id
  (fact "application found"
    (let [result (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})]
      (fact "result is map"
        result => map?)
      (fact "result is xml"
        (keys result) => (just #{:tag :attrs :content}))

      (fact "contains lp-tunnus"
        (get-lp-tunnus "R" result) => "LP-123-2016-00001")

      (fact "content"
        result =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                       scr/strip-xml-namespaces))) => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])))

  (fact "application not found"
    (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})  => nil

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [])))

  (fact "raw enabled"
    (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"} true)  => ..some-content..

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id true) => ..some-content..))

  (fact "no endpoint defined"
    (try+
     (get-application-xml-by-application-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"})
     (catch [:sade.core/type :sade.core/fail :text "error.no-legacy-available"] e
       true))  => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => nil)))

(facts get-application-xml-by-backend-id
  (fact "application found"
    (let [result (get-application-xml-by-backend-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"} "XYZ-123-G")]
      (fact "result is map"
        result => map?)
      (fact "result is xml"
        (keys result) => (just #{:tag :attrs :content}))

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus "R" result) => "XYZ-123-G")

      (fact "content"
        result =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                       scr/strip-xml-namespaces))) => truthy     ; truthy for getting midje understand provided

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])))

  (fact "application not found"
    (get-application-xml-by-backend-id {:id "LP-123-2016-00001" :organization "123-R" :permitType "R"}  "XYZ-123-G")  => nil

    (provided (organization/get-krysp-wfs {:organization "123-R" :permitType "R"}) => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml []))))

(facts get-application-xmls
  (facts "single application - by application-id"
    (let [result (get-application-xmls {:id "123-R"} "R" :application-id ["LP-123-2016-00001"])]
      (fact "result is map"
        result => map?)

      (fact "result keys"
        (keys result) => ["LP-123-2016-00001"])

      (fact "contains lp-tunnus"
        (get-lp-tunnus "R" (result "LP-123-2016-00001")) => "LP-123-2016-00001")

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus "R" (result "LP-123-2016-00001")) => "XYZ-123-G")

      (fact "content"
        (result "LP-123-2016-00001") =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                                             scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/resolve-krysp-wfs {:id "123-R"} "R") => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001"] :application-id anything) => (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])))

  (facts "single application - by kuntalupatunnus"
    (let [result (get-application-xmls {:id "123-R"} "R" :kuntalupatunnus ["XYZ-123-G"])]
      (fact "result is map"
        result => map?)

      (fact "result keys"
        (keys result) => ["XYZ-123-G"])

      (fact "contains lp-tunnus"
        (get-lp-tunnus "R" (result "XYZ-123-G")) => "LP-123-2016-00001")

      (fact "contains kuntalupatunnus"
        (get-kuntalupatunnus "R" (result "XYZ-123-G")) => "XYZ-123-G")

      (fact "content"
        (result "XYZ-123-G") =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])
                                     scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/resolve-krysp-wfs {:id "123-R"} "R") => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["XYZ-123-G"] :kuntalupatunnus anything) => (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001" :kuntalupatunnus "XYZ-123-G"}]])))

  (facts "multiple application - application-id"
    (let [result (get-application-xmls {:id "123-R"} "R" :application-id ["LP-123-2016-00001" "LP-123-2016-00002" "LP-123-2016-00003"])]
      (fact "result is map"
        result => map?)

      (fact "result keys - two applications found"
        (keys result) => (just #{"LP-123-2016-00001" "LP-123-2016-00003"} :in-any-order :gaps-ok))

      (fact "first xml contains lp-tunnus"
        (get-lp-tunnus "R" (result "LP-123-2016-00001")) => "LP-123-2016-00001")

      (fact "second xml contains lp-tunnus"
        (get-lp-tunnus "R" (result "LP-123-2016-00003")) => "LP-123-2016-00003")

      (fact "first xml content"
        (result "LP-123-2016-00001") =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001"}]])
                                             scr/strip-xml-namespaces))

      (fact "second xml content"
        (result "LP-123-2016-00003") =>  (-> (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00003"}]])
                                             scr/strip-xml-namespaces))) => truthy ; truthy for getting midje understand provided


    (provided (organization/resolve-krysp-wfs {:id "123-R"} "R") => {:url ..some-url.. :credentials ..some-credentials..})
    (provided (permit/fetch-xml-from-krysp "R" ..some-url.. ..some-credentials.. ["LP-123-2016-00001" "LP-123-2016-00002" "LP-123-2016-00003"] :application-id anything) => (build-multi-app-xml [[{:lp-tunnus "LP-123-2016-00001"}] [{:lp-tunnus "LP-123-2016-00003"}]]))))


(facts get-application-xmls-in-chunks
  (fact "one chunk"
    (get-application-xmls-in-chunks ..org.. ..permit-type.. ..search-type.. ["id1" "id2"] 2)  => {"id1" ..xml-1.. "id2" ..xml-2..}

    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id1" "id2"]) => {"id1" ..xml-1.. "id2" ..xml-2..}))

  (fact "multiple chunks"
    (get-application-xmls-in-chunks ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3" "id4" "id5" "id6"] 2)  => {"id1" ..xml-1.. "id2" ..xml-2..
                                                                                                                          "id3" ..xml-3.. "id4" ..xml-4..
                                                                                                                          "id5" ..xml-5.. "id6" ..xml-6..}

    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id1" "id2"]) => {"id1" ..xml-1.. "id2" ..xml-2..})
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id3" "id4"]) => {"id3" ..xml-3.. "id4" ..xml-4..})
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id5" "id6"]) => {"id5" ..xml-5.. "id6" ..xml-6..}))

  (fact "multiple unevenly separated chunks"
    (get-application-xmls-in-chunks ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3" "id4" "id5" "id6"] 5)  => {"id1" ..xml-1.. "id2" ..xml-2..
                                                                                                                          "id3" ..xml-3.. "id4" ..xml-4..
                                                                                                                          "id5" ..xml-5.. "id6" ..xml-6..}

    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3" "id4" "id5"]) => {"id1" ..xml-1.. "id2" ..xml-2.. "id3" ..xml-3.. "id4" ..xml-4.. "id5" ..xml-5..})
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id6"]) => {"id6" ..xml-6..}))

  (fact "empty result"
    (get-application-xmls-in-chunks ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3" "id4" "id5" "id6"] 3)  => nil

    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3"]) => nil)
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id4" "id5" "id6"]) => nil))

  (fact "partially empty result"
    (get-application-xmls-in-chunks ..org.. ..permit-type.. ..search-type.. ["id1" "id2" "id3" "id4" "id5" "id6"] 2)  => {"id3" ..xml-3.. "id4" ..xml-4..}

    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id1" "id2"]) => nil)
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id3" "id4"]) => {"id3" ..xml-3.. "id4" ..xml-4..})
    (provided (get-application-xmls ..org.. ..permit-type.. ..search-type.. ["id5" "id6"]) => nil)))



(facts get-application-xmls-by-backend-id
  (fact "two applications with backend id"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => {"id1" ..xml-1.. "id2" ..xml-2..}

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus ["klid1" "klid2"] ..chunk-size..)
              => {"klid1" ..xml-1.. "klid2" ..xml-2..}))

  (fact "empty result"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => nil

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus ["klid1" "klid2"] ..chunk-size..)
              => nil))

  (fact "empty applications input"
    (let [applications []]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => nil

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus nil ..chunk-size..)
              => nil))

  (fact "nil applications input"
    (let [applications nil]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => nil

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus nil ..chunk-size..)
              => nil))

  (fact "two applications with one backend id"
    (let [applications [{:id "id1"}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => {"id2" ..xml-2..}

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus ["klid2"] ..chunk-size..)
              => {"klid2" ..xml-2..}))

  (fact "multiple applications"
    (let [applications [{:id "id1"}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}
                        {:id "id3" :verdicts [{:kuntalupatunnus "klid3"}]}
                        {:id "id4" :verdicts [{:kuntalupatunnus "klid4"}]}]]
      (get-application-xmls-by-backend-id ..org.. ..permit-type.. applications ..chunk-size..))  => {"id2" ..xml-2.. "id3" ..xml-3..}

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus ["klid2" "klid3" "klid4"] ..chunk-size..)
              => {"klid2" ..xml-2.. "klid3" ..xml-3..} )))

(facts fetch-xmls-for-applications
  (fact "two apps found by application id"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (fetch-xmls-for-applications ..org.. ..permit-type.. applications)) => {"id1" ..xml-1.. "id2" ..xml-2..}

    (provided (get-application-xmls ..org.. ..permit-type.. :application-id ["id1" "id2"])  => {"id1" ..xml-1.. "id2" ..xml-2..}))

  (fact "get chunk size is from organization krysp config"
    (let [organization {:krysp {:R {:fetch-chunk-size ..chunk-size..}}}
          applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}]]
      (fetch-xmls-for-applications organization "R" applications)) => {"id1" ..xml-1..}

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks anything "R" :application-id ["id1"] ..chunk-size..)  => {"id1" ..xml-1..})
    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks anything "R" :kuntalupatunnus nil ..chunk-size..)  => nil))

  (fact "default chunk size is 10"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}]]
      (fetch-xmls-for-applications ..org.. ..permit-type.. applications)) => {"id1" ..xml-1..}

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :application-id ["id1"] 10)  => {"id1" ..xml-1..})
    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls-in-chunks ..org.. ..permit-type.. :kuntalupatunnus nil 10)  => nil))

  (fact "two apps found by backend id"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (fetch-xmls-for-applications ..org.. ..permit-type.. applications)) => {"id1" ..xml-1.. "id2" ..xml-2..}

    (provided (get-application-xmls ..org.. ..permit-type.. :application-id ["id1" "id2"])  => {})
    (provided (get-application-xmls ..org.. ..permit-type.. :kuntalupatunnus ["klid1" "klid2"])  => {"klid1" ..xml-1.. "klid2" ..xml-2..}))

  (fact "two apps - another found by application id, another by backen id"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}]]
      (fetch-xmls-for-applications ..org.. ..permit-type.. applications)) => {"id1" ..xml-1.. "id2" ..xml-2..}

    (provided (get-application-xmls ..org.. ..permit-type.. :application-id ["id1" "id2"])  => {"id2" ..xml-2..})
    (provided (get-application-xmls ..org.. ..permit-type.. :kuntalupatunnus ["klid1"])  => {"klid1" ..xml-1..}))

  (fact "multiple applications"
    (let [applications [{:id "id1" :verdicts [{:kuntalupatunnus "klid1"}]}
                        {:id "id2" :verdicts [{:kuntalupatunnus "klid2"}]}
                        {:id "id3"}
                        {:id "id4" :verdicts [{:kuntalupatunnus "klid4"}]}
                        {:id "id5" :verdicts [{:kuntalupatunnus "klid5"}]}]]
      (fetch-xmls-for-applications ..org.. ..permit-type.. applications)) => {"id1" ..xml-1.. "id2" ..xml-2.. "id5" ..xml-5..}

    (provided (get-application-xmls ..org.. ..permit-type.. :application-id ["id1" "id2" "id3" "id4" "id5"])  => {"id2" ..xml-2..})
    (provided (get-application-xmls ..org.. ..permit-type.. :kuntalupatunnus ["klid1" "klid4" "klid5"])  => {"klid1" ..xml-1.. "klid5" ..xml-5..})))
