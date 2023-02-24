(ns lupapalvelu.conversion.link-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.link :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]))

(testable-privates lupapalvelu.conversion.link
                   get-converted-apps
                   get-proper-link-ids
                   linkable-type?)

(def db-name (str "test_link-converted-itest_" (now)))

(def mock-applications
  [{:id "LP-404-2000-00001" :organization "404-R"}
   {:id "LP-404-2000-00002" :organization "404-R"}
   {:id "LP-404-2000-00003" :organization "404-R"}
   {:id "LP-404-2000-00005" :organization "404-R"}
   ;; A non-converted foreman app to be linked
   {:id           "LP-404-2000-00006"
    :organization "404-R"
    :verdicts     [{:kuntalupatunnus "12-4567-TJO"}]}
   ;; The app that the incorrectly linked conversion didn't find
   {:id           "LP-404-2000-00007"
    :organization "404-R"
    :verdicts     [{:kuntalupatunnus "9999-01-TJO"}]}])

(def mock-app-links
  [;; Already incorrectly linked app that will be fixed by re-linking
   {:id   "9999-01-TJO|LP-404-2000-00002"
    :link ["9999-01-TJO" "LP-404-2000-00002"]}
   ;; Handmade link that is not to be affected even by re-linking
   {:id   "LP-404-2000-00004|LP-404-2000-00001"
    :link ["LP-404-2000-00004" "LP-404-2000-00004"]}])

(def mock-conversions
  [;; Conversion failed
   {:id           "failed-conversion-id"
    :organization "404-R"
    :backend-id   "00-0000-A"
    :converted    false
    :app-links    []}
   ;; Simple conversion with no app-links
   {:id           "simple-conversion-id"
    :organization "404-R"
    :backend-id   "00-0000-A"
    :LP-id        "LP-404-2000-00001"
    :converted    true
    :app-links    []}
   ;; App which has been linked incorrectly and will be fixed by re-linking
   {:id           "re-linked-conversion-id"
    :organization "404-R"
    :backend-id   "09-0100-R"
    :LP-id        "LP-404-2000-00002"
    :converted    true
    :linked       true
    :app-links    ["9999-01-TJO"]}
   ;; App which will be linked to LP apps right away
   {:id           "linked-conversion-id"
    :organization "404-R"
    :backend-id   "09-0100-R"
    :LP-id        "LP-404-2000-00003"
    :converted    true
    :app-links    ["1234-09-TJO"   ; A converted foreman app
                   "12-4567-TJO"]} ; Pre-existing foreman app
   ;; Conversion from a different organization but same backend-ids (not affected)
   {:id           "different-org-conversion-id"
    :organization "500-R"
    :backend-id   "09-0100-R"
    :LP-id        "LP-500-2000-00001"
    :converted    true
    :app-links    ["1234-09-TJO"]}
   ;; A converted foreman app that will be linked to by the above
   {:id           "converted-foreman-conversion-id"
    :organization "404-R"
    :backend-id   "09-1234-TJO" ; Note different permit id format
    :LP-id        "LP-404-2000-00005"
    :converted    true
    :linked       false ; Test explicitly non-linked as well
    :app-links    []}])

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (mongo/clear!)
  (mongo/insert-batch :applications mock-applications)
  (mongo/insert-batch :app-links mock-app-links)
  (mongo/insert-batch :conversion mock-conversions))

(mongo/with-db db-name
  (with-local-actions
    (fact "Conversion link and re-link"

      (fact "get-converted-apps without re-link"
        (get-converted-apps "404-R" false)
        => [{:id           "simple-conversion-id"
             :organization "404-R"
             :backend-id   "00-0000-A"
             :LP-id        "LP-404-2000-00001"
             :converted    true
             :app-links    []}
            {:LP-id        "LP-404-2000-00003"
             :app-links    ["1234-09-TJO" "12-4567-TJO"]
             :backend-id   "09-0100-R"
             :converted    true
             :id           "linked-conversion-id"
             :organization "404-R"}
            {:LP-id        "LP-404-2000-00005"
             :app-links    []
             :backend-id   "09-1234-TJO"
             :converted    true
             :linked       false
             :id           "converted-foreman-conversion-id"
             :organization "404-R"}])

      (fact "get-converted-apps with re-link"
        (get-converted-apps "404-R" true)
        => [{:LP-id        "LP-404-2000-00001"
             :app-links    []
             :backend-id   "00-0000-A"
             :converted    true
             :id           "simple-conversion-id"
             :organization "404-R"}
            {:LP-id        "LP-404-2000-00002"
             :app-links    ["9999-01-TJO"]
             :backend-id   "09-0100-R"
             :converted    true
             :linked       true
             :id           "re-linked-conversion-id"
             :organization "404-R"}
            {:LP-id        "LP-404-2000-00003"
             :app-links    ["1234-09-TJO" "12-4567-TJO"]
             :backend-id   "09-0100-R"
             :converted    true
             :id           "linked-conversion-id"
             :organization "404-R"}
            {:LP-id        "LP-404-2000-00005"
             :app-links    []
             :backend-id   "09-1234-TJO"
             :converted    true
             :linked       false
             :id           "converted-foreman-conversion-id"
             :organization "404-R"}])

      (fact "get-proper-link-ids"
        (get-proper-link-ids "404-R" ["1234-09-TJO"
                                      "560 00-0000-A"
                                      "09-0100-R 789"
                                      "1234-99-R"])
        => ["LP-404-2000-00005"
            "LP-404-2000-00001"
            "LP-404-2000-00002"
            ;; Last one not found so falls back to original permit id
            "1234-99-R"])

      (facts "linkable-type?"
        (linkable-type? "1234-99-R") => true
        (linkable-type? "1234-99-DJ") => false
        (linkable-type? "456 00-0000-A") => true
        (linkable-type? "456 00-0000-TJO") => false
        (linkable-type? "00-1234-A 456") => true
        (linkable-type? "00-1234-TJO 456") => false)

      (fact "link-converted-files"
        (facts "no re-link"
          (link-converted-files "404-R" false) => nil
          (mongo/select :app-links {})
          => [;; The pre-existing incorrect link not fixed (not re-linked)
              {:id   "9999-01-TJO|LP-404-2000-00002"
               :link ["9999-01-TJO" "LP-404-2000-00002"]}
              ;; The pre-existing correct LP-to-LP link not affected
              {:id   "LP-404-2000-00004|LP-404-2000-00001"
               :link ["LP-404-2000-00004" "LP-404-2000-00004"]}
              ;; The link to a converted foreman app
              {:id                "LP-404-2000-00003|LP-404-2000-00005"
               :link              ["LP-404-2000-00005" "LP-404-2000-00003"]
               :LP-404-2000-00003 {:apptype        nil
                                   :linkpermittype "lupapistetunnus"
                                   :type           "linkpermit"}
               :LP-404-2000-00005 {:apptype    nil
                                   :propertyId nil
                                   :type       "application"}}
              ;; The link to a non-converted foreman app
              {:id                "LP-404-2000-00003|LP-404-2000-00006"
               :link              ["LP-404-2000-00006" "LP-404-2000-00003"]
               :LP-404-2000-00003 {:apptype        nil
                                   :linkpermittype "lupapistetunnus"
                                   :type           "linkpermit"}
               :LP-404-2000-00006 {:apptype    nil
                                   :propertyId nil
                                   :type       "application"}}])

        (facts "re-link"
          (link-converted-files "404-R" true) => nil
          (mongo/select :app-links {})
          => [;; The pre-existing correct LP-to-LP link not affected
              {:id   "LP-404-2000-00004|LP-404-2000-00001"
               :link ["LP-404-2000-00004" "LP-404-2000-00004"]}
              ;; The link to a converted foreman app
              {:id                "LP-404-2000-00003|LP-404-2000-00005"
               :link              ["LP-404-2000-00005" "LP-404-2000-00003"]
               :LP-404-2000-00003 {:apptype        nil
                                   :linkpermittype "lupapistetunnus"
                                   :type           "linkpermit"}
               :LP-404-2000-00005 {:apptype    nil
                                   :propertyId nil
                                   :type       "application"}}
              ;; The link to a non-converted foreman app
              {:id                "LP-404-2000-00003|LP-404-2000-00006"
               :link              ["LP-404-2000-00006" "LP-404-2000-00003"]
               :LP-404-2000-00003 {:apptype        nil
                                   :linkpermittype "lupapistetunnus"
                                   :type           "linkpermit"}
               :LP-404-2000-00006 {:apptype    nil
                                   :propertyId nil
                                   :type       "application"}}
              ;; The link fixed by re-linking
              {:LP-404-2000-00007 {:apptype    nil
                                   :propertyId nil
                                   :type       "application"}
               :LP-404-2000-00002 {:apptype        nil
                                   :linkpermittype "lupapistetunnus"
                                   :type           "linkpermit"}
               :id                "LP-404-2000-00002|LP-404-2000-00007"
               :link              ["LP-404-2000-00007" "LP-404-2000-00002"]}])))))
