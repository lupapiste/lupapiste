(ns lupapalvelu.conversion.link-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.link :refer :all]
            [lupapalvelu.foreman-application-util :refer [get-linked-foreman-applications]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.config :as midje-config]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.util :as util]))

(testable-privates lupapalvelu.conversion.link
                   get-conversion-documents
                   resolve-app-link
                   get-proper-links
                   reference-ts
                   rotate-backend-id
                   good-set good-set?
                   make-link
                   remove-links
                   clean-up-links)

;; The following applications are used in `resolve-app-link` tests.
(def regular-app {:id           "LP-186-2023-00001"
                  :organization "186-R"})
(def backend-id-app1 {:id           "LP-186-2023-00002"
                      :organization "186-R"
                      :verdicts     [{:kuntalupatunnus "86-2001-R"}]})
(def backend-id-app2 {:id            "LP-186-2023-00003"
                      :organization  "186-R"
                      :pate-verdicts [{:data {:kuntalupatunnus "86-2002-R"}}]})
(def backend-id-app3 {:id            "LP-186-2023-00004"
                      :organization  "186-R"
                      :pate-verdicts [{:data {:kuntalupatunnus {:_value "86-2003-R"}}}]})
(def lx-app {:id           "LX-186-2023-00005"
             :permitType   "ARK"
             :organization "186-R"})
(def lx-backend-id-app1 {:id           "LX-186-2023-00006"
                         :permitType   "ARK"
                         :organization "186-R"
                         ;; The same as backend-id1
                         :verdicts     [{:kuntalupatunnus "86-2001-R"}]})
(def lx-backend-id-app2 {:id           "LX-186-2023-00007"
                         :permitType   "ARK"
                         :organization "186-R"
                         :verdicts     [{:kuntalupatunnus "86-2006-R"}]})

(def pientalo-app {:id               "LP-404-2000-00003"
                   :propertyId       "40441300040107"
                   :permitType       "R"
                   :organization     "404-R"
                   :facta-imported   true
                   :primaryOperation {:name "pientalo"}})

(def mock-applications
  [{:id           "LP-404-2000-00001"
    :organization "404-R"
    :permitType   "R"}
   {:id               "LP-404-2000-00002"
    :organization     "404-R"
    :facta-imported   true
    :propertyId       "40442600010304"
    :primaryOperation {:name "aita"}
    :permitType       "R"}
   pientalo-app
   {:id               "LP-404-2000-00005"
    :permitType       "R"
    :organization     "404-R"
    :propertyId       "40456900010008"
    :pate-verdicts    [{:data {:kuntalupatunnus "09-1234-TJO"}}]
    :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}
   ;; A non-converted foreman app to be linked
   {:id               "LP-404-2000-00006"
    :organization     "404-R"
    :permitType       "R"
    :propertyId       "40456900030025"
    :verdicts         [{:kuntalupatunnus "12-4567-TJO"}]
    :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}
   ;; The app that the incorrectly linked conversion didn't find
   {:id               "LP-404-2000-00007"
    :organization     "404-R"
    :permitType       "R"
    :propertyId       "40448600010017"
    :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
    :verdicts         [{:kuntalupatunnus "9999-01-TJO"}]}
   ;; Digitized application that could share the same backend-id as the conversion.
   {:id               "LX-123-2023-01234"
    :organization     "404-R"
    :permitType       "ARK"
    :primaryOperation {:name "archiving-project"}}
   ;; Application in the different organization
   {:id           "LX-321-2023-50005"
    :permitType   "ARK"
    :organization "505-R"}
   {:id               "LP-404-2000-00099"
    :facta-imported   true
    :permitType       "R"
    :propertyId       "40442200030273"
    :primaryOperation {:name "konversio"}
    :organization     "404-R"}
   {:id           "LP-500-2000-00001"
    :permitType   "R"
    :organization "500-R"
    :verdicts     [{:kuntalupatunnus "4321-02-BOO"}]}
   {:id           "LP-500-2000-00003"
    :permitType   "R"
    :organization "500-R"}
   regular-app
   backend-id-app1
   backend-id-app2
   backend-id-app3
   lx-app
   lx-backend-id-app1
   lx-backend-id-app2
   {:id           "LP-186-2010-01010"
    :organization "186-R"}])

(def fixed-link
  "Handmade link that is not to be affected even by re-linking"
  {:id    "LP-404-2000-00001|LP-404-2000-00004"
   :hello "world"
   :link  ["LP-404-2000-00004" "LP-404-2000-00001"]})

(def mock-app-links
  [;; Already incorrectly linked app that will be fixed by re-linking
   {:id                     "9999-01-TJO|LP-404-2000-00002"
    :conversion-document-id "re-linked-conversion-id"
    :link                   ["9999-01-TJO" "LP-404-2000-00002"]}
   ;; "Legacy" link that will be removed when a permit corresponding to the backend-id is
   ;; converted and linked.
   {:id   "09-1234-TJO|LP-404-2000-00003"
    :link ["09-1234-TJO" "LP-404-2000-00003"]}
   ;; Existing foreman application linking to backen-id of the converted application.
   ;; This will be replaced with full-fledged app - app link.
   {:id   "09-0100-R|LP-404-2000-00006"
    :link ["LP-404-2000-00006" "09-0100-R"]}
   fixed-link
   ;; For `resolve-app-link` test
   {:id   "86-1998-R|LP-186-2022-00001"
    :tag  "resolve-link" ;; Testing bookkeeping
    :link ["86-1998-R" "LP-186-2022-00001"]}
   {:id   "LP-186-2022-00001|LP-186-2010-01010"
    :tag  "resolve-link"
    :link ["LP-186-2010-01010" "LP-186-2022-00001"]}])

(def failed-doc "Conversion failed"
  {:id           "failed-conversion-id"
   :organization "404-R"
   :backend-id   "00-0000-A"
   :year         2000
   :permit-type  "R"
   :converted    false
   :app-links    []})

(def simple-doc "Simple conversion with no app-links"
  {:id           "simple-conversion-id"
   :organization "404-R"
   :backend-id   "00-0000-A"
   :year         2000
   :permit-type  "R"
   :LP-id        "LP-404-2000-00001"
   :converted    true
   :app-links    []})

(def re-linked-doc
  "App which has been linked incorrectly and will be fixed by re-linking"
  {:id           "re-linked-conversion-id"
   :organization "404-R"
   :backend-id   "09-0100-R"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-404-2000-00002"
   :converted    true
   :linked       true
   :app-links    [;; Outdated, existing link
                  "9999-01-TJO"
                  ;; Not yet linked
                  "77-2022-YES"]})

(def linked-doc
  "App which will be linked to LP apps right away"
  {:id           "linked-conversion-id"
   :organization "404-R"
   :backend-id   "09-0100-R"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-404-2000-00003"
   :converted    true
   :app-links    [;; A converted foreman app
                  "09-1234-TJO"
                  ;; Pre-existing foreman app
                  "12-4567-TJO"
                  ;; No such application
                  "LP-753-2023-90011"
                  ;; Links to self
                  "LP-404-2000-00003"
                  "09-0100-R"]})

(def different-org-doc
  "Conversion from a different organization but same backend-ids (not affected)"
  {:id           "different-org-conversion-id"
   :organization "500-R"
   :backend-id   "4321-02-BOO"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-500-2000-00001"
   :converted    true
   :app-links    ["LP-500-2000-00003"
                  "4321-02-BOO"
                  "LP-500-2000-00001"]})

(def foreman-doc
  "A converted foreman app that will be linked to by the above"
  {:id           "converted-foreman-conversion-id"
   :organization "404-R"
   :backend-id   "09-1234-TJO"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-404-2000-00005"
   :converted    true
   :linked       false ; Test explicitly non-linked as well
   :app-links    []})

(def lx-sibling-doc
  "Converted application that has the same backend-id as a previously digitized ARK
  application"
  {:id           "lx-sibling-id"
   :organization "404-R"
   :backend-id   "22-1234-FOO"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-404-2000-00099"
   :converted    true
   :app-links    ["LX-123-2023-01234"]})

(def not-converted-doc
  {:id           "not-converted-id"
   :organization "404-R"
   :backend-id   "22-1234-BAR"
   :year         2009
   :permit-type  "R"
   :LP-id        "LP-404-2000-00066"
   :app-links    ["22-3344-DOH"]})

(def mock-conversions [failed-doc
                       simple-doc
                       re-linked-doc
                       linked-doc
                       different-org-doc
                       foreman-doc
                       lx-sibling-doc
                       not-converted-doc])

(def cfg {:organization-id "404-R"
          :municipality    "404"})

(facts "rotate-backend-id"
  (rotate-backend-id "12-3456-78") => "3456-12-78"
  (rotate-backend-id "3456-12-78") => "12-3456-78"
  (rotate-backend-id "12-3456-78   hello") => "hello-3456-12-78"
  (rotate-backend-id "05-2051-A 98") => "98-2051-05-A"
  (doseq [id [nil "" " " "11-22" "12-hei-30" "A 12-34-56" "98-2051-05-A"]]
    (rotate-backend-id id) => nil))

(facts "good-set"
  (good-set 1) => #{1}
  (good-set 1 1 2 2 nil nil 3 3) => #{1 2 3}
  (good-set) => (throws)
  (good-set nil) => (throws)
  (good-set nil nil) => (throws))

(facts "good-set?"
  (good-set? #{1}) => true
  (good-set? #{1 2 3}) => true
  (good-set? [1 2 3]) => false
  (good-set? nil) => false
  (good-set? #{1 nil 2}) => false)

(against-background
  [(mongo/remove-many :app-links anything) => :yeah]
  (facts "remove-links"
    (remove-links nil nil) => (throws)
    (remove-links "hello" #{}) => (throws)
    (remove-links "hello" #{"hello" "world"}) => (throws)
    (remove-links "hello" #{"world"}) => :yeah))

(with-test-system {:fixture "minimal"}
  (mongo/clear!)
  (mongo/insert-batch :applications mock-applications)
  (mongo/insert-batch :app-links mock-app-links)
  (mongo/insert-batch :conversion mock-conversions)

  (facts "Conversion link and relink"

    (fact "get-conversion-documents without relink"
      (get-conversion-documents cfg nil)
      => (just linked-doc lx-sibling-doc :in-any-order))

    (fact "get-conversion-documents with relink"
      (get-conversion-documents cfg :relink)
      => (just linked-doc lx-sibling-doc
               simple-doc re-linked-doc
               foreman-doc :in-any-order))

    (let [base-doc {:id           "hello"
                    :year         2023
                    :permit-type  "R"
                    :organization "186-R"
                    :LP-id        "LP-186-2022-00001"
                    :backend-id   "86-1999-R"}]
      (facts "resolve-app-link"
        (fact "No match: non-existing Lupapiste application"
          (resolve-app-link base-doc "LP-123-2000-12345") => nil)
        (fact "No match: Lupapiste application in different organization"
          (resolve-app-link base-doc "LP-404-2000-00001") => nil)
        (fact "Application link"
          (resolve-app-link base-doc "LP-186-2023-00001")
          => {:application-id "LP-186-2023-00001"})
        (fact "Application link via backend-id"
          (resolve-app-link base-doc "86-2001-R")
          => {:application-id   "LP-186-2023-00002"
              :old-backend-link "86-2001-R"}
          (resolve-app-link base-doc "86-2002-R")
          => {:application-id   "LP-186-2023-00003"
              :old-backend-link "86-2002-R"})
        (fact "LX application link"
          (resolve-app-link base-doc "LX-186-2023-00005")
          => {:application-id "LX-186-2023-00005"})
        (fact "LX application link via backend-id"
          (resolve-app-link base-doc "86-2006-R")
          => {:application-id   "LX-186-2023-00007"
              :old-backend-link "86-2006-R"}
          (resolve-app-link (assoc base-doc :backend-id "86-2006-R") "86-2006-R")
          => {:application-id   "LX-186-2023-00007"
              :old-backend-link "86-2006-R"})
        (fact "Backend-id link (no application)"
          (resolve-app-link base-doc "86-2009-R")
          => {:backend-id "86-2009-R"})
        (fact "Backend-id link (different organization)"
          (resolve-app-link base-doc "9999-01-TJO")
          => {:backend-id "9999-01-TJO"})
        (fact "No self-referencing link"
          (resolve-app-link (assoc base-doc :LP-id "LP-186-2023-00001")
                            "LP-186-2023-00001")
          => {:linked? true}
          (resolve-app-link (assoc base-doc
                                   :backend-id "86-2001-R"
                                   :LP-id "LP-186-2023-00002")
                            "86-2001-R")
          => {:linked? true}
          (resolve-app-link base-doc (:backend-id base-doc))
          => {:linked? true})
        (fact "Already linked"
          (resolve-app-link base-doc "86-1998-R")
          => {:linked? true :link-type :backend}
          (resolve-app-link base-doc "LP-186-2010-01010")
          => {:linked? true :link-type :application})
        (fact "Bad link param (should not happen)"
          (resolve-app-link base-doc "") => (throws)
          (resolve-app-link base-doc "  ") => (throws))))

    (fact "get-proper-links"
      (get-proper-links linked-doc)
      => (just {:application-id   "LP-404-2000-00005"
                :old-backend-link "09-1234-TJO"}
               {:application-id   "LP-404-2000-00006"
                :old-backend-link "12-4567-TJO"}
               :in-any-order))

    (facts "reference-ts"
      (reference-ts {}) => nil
      (reference-ts {:created 10}) => 10
      (reference-ts {:created        10
                     :facta-imported true}) => nil
      (reference-ts {:history [{:state "foo"
                                :ts    10}]}) => nil
      (reference-ts {:history        [{:state "foo"
                                       :ts    10}]
                     :facta-imported true}) => nil
      (reference-ts {:history        [{:state "foo"
                                       :ts    10}]
                     :created        10
                     :facta-imported true}) => nil
      (reference-ts {:history [{:state "foo"
                                :ts    10}]
                     :created 10}) => 10
      (reference-ts {:history        [{:state "foo"
                                       :ts    10}]
                     :created        100
                     :facta-imported true}) => 10
      (reference-ts {:history [{:state "foo"
                                :ts    10}]
                     :created 100}) => 100
      (reference-ts {:history        [{:ts 1}
                                      {:state "one" :ts 30}
                                      {:state "two" :ts 10}
                                      {}
                                      {:state "three"}
                                      {:state "four"} :ts 20]
                     :created        100
                     :facta-imported true}) => 10)

    (let [application {:id               "LP-123-2023-00001"
                       :primaryOperation {:name "konversio"}
                       :propertyId       "12342100080032"
                       :facta-imported   true
                       :created          99999
                       :history          [{:state "one" :ts 1000}]}
          app-from    {:type       :application
                       :propertyId (:propertyId application)
                       :apptype    "konversio"}
          app-to      {:type           :linkpermit
                       :linkpermittype :lupapistetunnus
                       :apptype        "konversio"}]
      (facts "make-link"
        (fact "Backend id link"
          (make-link "my-doc" application "hello")
          => {:_id                    "LP-123-2023-00001|hello"
              :link                   ["LP-123-2023-00001" "hello"]
              "LP-123-2023-00001"     app-from
              "hello"                 {:type           :linkpermit
                                       :linkpermittype :kuntalupatunnus}
              :conversion-document-id "my-doc"})
        (fact "Target is foreman application"
          (make-link "my-doc" application
                     {:id               "LP-123-2023-00002"
                      :propertyId       "12342100080064"
                      :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}})
          => {:_id                    "LP-123-2023-00001|LP-123-2023-00002"
              :link                   ["LP-123-2023-00002" "LP-123-2023-00001"]
              "LP-123-2023-00001"     app-to
              "LP-123-2023-00002"     {:type       :application
                                       :propertyId "12342100080064"
                                       :apptype    "tyonjohtajan-nimeaminen-v2"}
              :conversion-document-id "my-doc"})
        (fact "Source is foreman application"
          (make-link "my-doc"
                     {:id               "LP-123-2023-00002"
                      :propertyId       "12342100080064"
                      :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}
                     application)
          => {:_id                    "LP-123-2023-00001|LP-123-2023-00002"
              :link                   ["LP-123-2023-00002" "LP-123-2023-00001"]
              "LP-123-2023-00001"     app-to
              "LP-123-2023-00002"     {:type       :application
                                       :propertyId "12342100080064"
                                       :apptype    "tyonjohtajan-nimeaminen-v2"}
              :conversion-document-id "my-doc"})
        (let [target         {:id               "LP-123-2023-00003"
                              :propertyId       "12342100080128"
                              :created          900
                              :primaryOperation {:name "aita"}}
              target-from    {:type       :application
                              :apptype    "aita"
                              :propertyId (:propertyId target)}
              target-to      {:type           :linkpermit
                              :linkpermittype :lupapistetunnus
                              :apptype        "aita"}
              source->target {:_id                    "LP-123-2023-00001|LP-123-2023-00003"
                              :link                   ["LP-123-2023-00001" "LP-123-2023-00003"]
                              "LP-123-2023-00001"     app-from
                              "LP-123-2023-00003"     target-to
                              :conversion-document-id "my-doc"}
              target->source {:_id                    "LP-123-2023-00001|LP-123-2023-00003"
                              :link                   ["LP-123-2023-00003" "LP-123-2023-00001"]
                              "LP-123-2023-00001"     app-to
                              "LP-123-2023-00003"     target-from
                              :conversion-document-id "my-doc"}]
          (fact "Timestamp comparison: source is later"
            (make-link "my-doc" application target) => source->target
            (make-link "my-doc"
                       (dissoc application :facta-imported)
                       (assoc target
                              :facta-imported true
                              :created 12345678
                              :history [{:state "foo" :ts 200}]))
            => source->target)
          (fact "Timestamp comparison: target is later"
            (make-link "my-doc" application (assoc target :created 7000))
            => target->source
            (make-link "my-doc"
                       (dissoc application :facta-imported)
                       (assoc target
                              :facta-imported true
                              :created 12345678
                              :history [{:state "foo" :ts 1000000}]))
            => target->source)
          (fact "Timestamp comparison: no timestamps"
            (make-link "my-doc"
                       (dissoc application :history)
                       (dissoc target :created))
            => source->target))))

    (fact "No link updates yet"
      (mongo/any? :conversion {:history.op :link}) => false)

    (fact "No linked foreman applications for LP-404-2000-00003"
      (get-linked-foreman-applications pientalo-app) => empty?)

    (fact "link-converted-files"
      (facts "no relink"
        (conversion--link cfg nil) => nil
        (let [links (mongo/select :app-links {:tag nil})
              link1 (util/find-by-id "9999-01-TJO|LP-404-2000-00002" links)
              link2 (util/find-by-id "LP-404-2000-00003|LP-404-2000-00005" links)
              link3 (util/find-by-id "LP-404-2000-00003|LP-404-2000-00006" links)
              link4 (util/find-by-id (:id fixed-link) links)
              link5 (util/find-by-id "LP-404-2000-00099|LX-123-2023-01234" links)]
          (facts "No unknown links"
            links => (just link1 link2 link3 link4 link5 :in-any-order))
          (fact "The pre-existing incorrect link not fixed (not re-linked)"
            link1 => (just {:id                     "9999-01-TJO|LP-404-2000-00002"
                            :conversion-document-id "re-linked-conversion-id"
                            :link                   (just "9999-01-TJO" "LP-404-2000-00002"
                                                          :in-any-order)}))
          (fact "Link to converted foreman application"
            link2 => (just {:id                     "LP-404-2000-00003|LP-404-2000-00005"
                            :link                   (just "LP-404-2000-00005" "LP-404-2000-00003"
                                                          :in-any-order)
                            :LP-404-2000-00003      {:apptype        "pientalo"
                                                     :linkpermittype "lupapistetunnus"
                                                     :type           "linkpermit"}
                            :LP-404-2000-00005      {:apptype    "tyonjohtajan-nimeaminen-v2"
                                                     :propertyId "40456900010008"
                                                     :type       "application"}
                            :conversion-document-id "linked-conversion-id"}))

          (fact "Link to non-converted foreman application"
            link3 => (just {:id                     "LP-404-2000-00003|LP-404-2000-00006"
                            :link                   (just "LP-404-2000-00006" "LP-404-2000-00003"
                                                          :in-any-order)
                            :LP-404-2000-00003      {:apptype        "pientalo"
                                                     :type           "linkpermit"
                                                     :linkpermittype "lupapistetunnus"}
                            :LP-404-2000-00006      {:apptype    "tyonjohtajan-nimeaminen-v2"
                                                     :propertyId "40456900030025"

                                                     :type       "application"}
                            :conversion-document-id "linked-conversion-id"}))

          (fact "Fixed link"
            link4 => fixed-link)

          (fact "Link to LX sibling"
            link5 => (just {:id                     "LP-404-2000-00099|LX-123-2023-01234"
                            :link                   (just "LP-404-2000-00099" "LX-123-2023-01234"
                                                          :in-any-order)
                            :LP-404-2000-00099      {:apptype    "konversio"
                                                     :propertyId "40442200030273"
                                                     :type       "application"}
                            :LX-123-2023-01234      {:apptype        "archiving-project"
                                                     :type           "linkpermit"
                                                     :linkpermittype "lupapistetunnus"}
                            :conversion-document-id "lx-sibling-id"}))

          (fact "Two linked foreman applications for LP-404-2000-00003"
            (map :id (get-linked-foreman-applications pientalo-app))
            => (just "LP-404-2000-00005" "LP-404-2000-00006"
                     :in-any-order))

          (fact "Link updates in two conversion documents"
            (map :id (mongo/select :conversion {:history.op :link} [:id]))
            => (just "linked-conversion-id" "lx-sibling-id"
                     :in-any-order))

          (facts "Nothing to link anymore"
            (get-conversion-documents cfg false) => [])

          (letfn [(doc-stat []
                    (let [{:keys [history
                                  linked]} (mongo/by-id :conversion "lx-sibling-id")]
                      [(->> history (filter #(= (:op %) "link")) count)
                       (boolean linked)]))]
            (facts "Document is marked linked also when links already exist"
              (mongo/update-by-id :conversion "lx-sibling-id" {$unset {:linked true}})
              (doc-stat) => [1 false]
              (conversion--link cfg nil) => nil
              (provided
                (#'lupapalvelu.conversion.link/insert-link-permits anything) => nil :times 0)
              (doc-stat) => [2 true]))

          (facts "relink"
            (conversion--link cfg :relink) => nil
            (mongo/select :app-links {:tag nil})
            => (just
                 ;; The link fixed by re-linking
                 (just {:LP-404-2000-00002      {:apptype        "aita"
                                                 :type           "linkpermit"
                                                 :linkpermittype "lupapistetunnus"}
                        :LP-404-2000-00007      {:apptype    "tyonjohtajan-nimeaminen-v2"
                                                 :propertyId "40448600010017"
                                                 :type       "application"}
                        :id                     "LP-404-2000-00002|LP-404-2000-00007"
                        :link                   (just "LP-404-2000-00007" "LP-404-2000-00002"
                                                      :in-any-order)
                        :conversion-document-id "re-linked-conversion-id"})
                 ;; New backend id link
                 (just {:LP-404-2000-00002      {:apptype    "aita"
                                                 :propertyId "40442600010304"
                                                 :type       "application"}
                        :77-2022-YES            { :linkpermittype "kuntalupatunnus"
                                                 :type            "linkpermit"}
                        :id                     "77-2022-YES|LP-404-2000-00002"
                        :link                   (just "77-2022-YES" "LP-404-2000-00002"
                                                      :in-any-order)
                        :conversion-document-id "re-linked-conversion-id"})
                 link2 link3 link4 link5
                 :in-any-order))

          (fact "Link updates in three conversion documents"
            (map :id (mongo/select :conversion {:history.op :link} [:id]))
            => (just "linked-conversion-id" "lx-sibling-id"
                     "re-linked-conversion-id" :in-any-order))))))

  (midje-config/with-augmented-config {:partial-prerequisites true}
    (fact "Linking fails partly for the document"
      (conversion--link cfg :relink) => nil
      (provided
        ( #'lupapalvelu.conversion.link/make-link "re-linked-conversion-id" anything "77-2022-YES")
        => (throw (ex-info "This cannot happen?" {})))
      (->> (mongo/select :app-links
                         {:tag                    nil
                          :conversion-document-id {$exists true}}
                         [:conversion-document-id])
           (keep :conversion-document-id)
           set)
      => #{"linked-conversion-id" "re-linked-conversion-id" "lx-sibling-id"}
      (fact "Conversion document is reset"
        (mongo/any? :conversion {:_id    "re-linked-conversion-id"
                                 :linked nil})
        => true)))

  (fact "Linking fails fully for the document"
    (mongo/remove-by-id :applications "LP-404-2000-00003")
    (conversion--link cfg :relink) => nil
    (->> (mongo/select :app-links
                       {:tag                    nil
                        :conversion-document-id {$exists true}}
                       [:conversion-document-id])
         (keep :conversion-document-id)
         set)
    => #{"re-linked-conversion-id" "lx-sibling-id"}
    (fact "Conversion document is reset"
      (mongo/any? :conversion {:_id    "linked-conversion-id"
                               :linked nil})
      => true))

  (fact "Cleanup safety check"
    (clean-up-links {}) => (throws)
    (clean-up-links (dissoc lx-sibling-doc :id)) => (throws)
    (clean-up-links (assoc lx-sibling-doc :id nil)) => (throws)
    (clean-up-links (assoc lx-sibling-doc :id #".*")) => (throws)
    (mongo/any? :app-links {:conversion-document-id (:id lx-sibling-doc)})
    => true)

  (facts "Repair"
    (let [old-conversion-docs (set (mongo/select :conversion
                                                 {:_id {$nin [:re-linked-conversion-id
                                                              :lx-sibling-id]}}))
          old-target-doc1     (mongo/by-id :conversion :re-linked-conversion-id)
          _                   (mongo/update-by-id :conversion "lx-sibling-id"
                                                  {$push {:app-links "99-2024-OK"}})
          old-target-doc2     (mongo/by-id :conversion :lx-sibling-id)
          old-app-links1      (set (:app-links old-target-doc1))
          old-app-links2      (set (:app-links old-target-doc2))
          old-link            (mongo/select-one :app-links {:link "77-2022-YES"})
          other-links         (set (mongo/select :app-links {:_id {$ne (:id old-link)}}))]
      old-conversion-docs => not-empty
      old-target-doc1 => truthy
      old-target-doc2 => truthy
      (old-app-links1 "77-2022-YES") => truthy
      (old-app-links2 "99-2024-OK") => truthy
      old-link => truthy
      (fact "Insert matching application with a rotated backend-id"
        (mongo/insert :applications {:id               "LP-404-2024-00123"
                                     :primaryOperation {:name :varasto-tms}
                                     :organization     "404-R"
                                     :propertyId       "40442600010321"
                                     :permitType       :R
                                     :verdicts         [{:kuntalupatunnus "2022-77-YES"}]})
        => nil)
      (fact "Insert matching application with a non-rotated backend-id"
        (mongo/insert :applications {:id               "LP-404-2024-00124"
                                     :primaryOperation {:name :teollisuusrakennus}
                                     :organization     "404-R"
                                     :propertyId       "40442600010421"
                                     :permitType       :R
                                     :verdicts         [{:kuntalupatunnus "99-2024-OK"}]})
        => nil)
      (fact "Insert more links to be purged on replace"
        (mongo/insert-batch :app-links [{:id   "bye"
                                         :link ["77-2022-YES" "LP-404-2000-00002"]}
                                        {:id   "adieu"
                                         :link ["99-2024-OK" "LP-404-2000-00099"]}])
        => truthy)
      (facts "Repair and check results"
        (conversion--link cfg :repair) => nil
        (let [target-doc1 (mongo/by-id :conversion :re-linked-conversion-id)
              target-doc2 (mongo/by-id :conversion :lx-sibling-id)
              app-links1  (set (:app-links target-doc1))
              app-links2  (set (:app-links target-doc2))
              new-link1   (mongo/select-one :app-links {:link "LP-404-2024-00123"})
              new-link2   (mongo/select-one :app-links {:link "LP-404-2024-00124"})]
          (app-links1 "77-2022-YES") => nil
          (app-links1 "2022-77-YES") => truthy
          app-links2 => old-app-links2
          (dissoc target-doc1 :app-links :history)
          => (dissoc old-target-doc1 :app-links :history)
          (-> target-doc1 :history last :op) => "link-repair"
          (dissoc target-doc2 :app-links :history)
          => (dissoc old-target-doc2 :app-links :history)
          (-> target-doc2 :history last :op) => "link-repair"
          (mongo/by-id :app-links (:id old-link)) => nil
          new-link1 => (just {:LP-404-2000-00002      {:apptype    "aita"
                                                       :propertyId "40442600010304"
                                                       :type       "application"}
                              :LP-404-2024-00123      {:apptype        "varasto-tms"
                                                       :type           "linkpermit"
                                                       :linkpermittype "lupapistetunnus"}
                              :id                     "LP-404-2000-00002|LP-404-2024-00123"
                              :link                   (just "LP-404-2000-00002" "LP-404-2024-00123"
                                                            :in-any-order)
                              :conversion-document-id "re-linked-conversion-id"})
          new-link2 => (just {:LP-404-2000-00099      {:apptype    "konversio"
                                                       :propertyId "40442200030273"
                                                       :type       "application"}
                              :LP-404-2024-00124      {:apptype        "teollisuusrakennus"
                                                       :type           "linkpermit"
                                                       :linkpermittype "lupapistetunnus"}
                              :id                     "LP-404-2000-00099|LP-404-2024-00124"
                              :link                   (just "LP-404-2000-00099" "LP-404-2024-00124"
                                                            :in-any-order)
                              :conversion-document-id "lx-sibling-id"})

          (fact "Other documents are unchanged"
            (set (mongo/select :conversion {:_id {$nin [:lx-sibling-id
                                                        :re-linked-conversion-id]}}))
            => old-conversion-docs)

          (fact "Other links are unchanged"
            (set (mongo/select :app-links {:_id {$nin [(:id new-link1) (:id new-link2)]}}))
            => other-links
            (mongo/any? :app-links {:_id {$in ["bye" "adieu"]}}) => false))

        (fact "New repair does not add links but purges deprecated links"
          (mongo/insert-batch :app-links [{:id   "bye"
                                           :link ["77-2022-YES" "LP-404-2000-00002"]}
                                          {:id   "adieu"
                                           :link ["99-2024-OK" "LP-404-2000-00099"]}])
          => truthy
          (mongo/count :app-links {:_id {$in ["bye" "adieu"]}}) => 2
          (conversion--link cfg :repair) => nil
          (provided
            (mongo/insert :app-links anything) => nil :times 0)
          (mongo/any? :app-links {:_id {$in ["bye" "adieu"]}}) => false)

        (fact "Since the documents too have been repaired, re-link works (in this case, not necessarily generally if the converted applications refer to each other with bad/repairable links)."
          (let [old-links (mongo/select :app-links {})]
            (conversion--link cfg :relink) => nil
            (mongo/select :app-links {})
            => (just old-links :in-any-order)
            (mongo/remove-many :app-links {:link                   #"^LP-404-"
                                           :conversion-document-id {$exists true}})
            (mongo/update-by-query :conversion {:organization "404-R"}
                                   {$unset {:linked true}}) => pos?
            (conversion--link cfg nil) => nil
            (mongo/select :app-links {})
            => (just old-links :in-any-order)))

        (fact "Now repair does not do anything (in this case)"
          (conversion--link cfg :repair) => nil
          (provided
            (mongo/remove-many :app-links anything) => nil :times 0
            (mongo/insert :app-links anything) => nil :times 0
            (conv-doc/update-document anything anything) => nil :times 0))))))
