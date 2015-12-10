(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.schema-generators :as ssg]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement :refer [Statement]]))

(testable-privates lupapalvelu.statement 
                   possible-statement-statuses
                   give-statement
                   update-draft)

(let [test-app-R  {:municipality 753 :permitType "R"}
      test-app-P  {:municipality 753 :permitType "P"}
      test-app-YA {:municipality 753 :permitType "YA"}
      test-app-YM {:municipality 753 :permitType "YM"}]

  ;; permit type R

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-R) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.5"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-R) => (just ["puoltaa" "ei-puolla" "ehdoilla"
                                                       "ei-huomautettavaa" "ehdollinen" "puollettu"
                                                       "ei-puollettu" "ei-lausuntoa" "lausunto"
                                                       "kielteinen" "palautettu" "poydalle"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.6"}}}))

  ;; permit type P

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-P) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.1.5"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-P) => (just ["puoltaa" "ei-puolla" "ehdoilla"
                                                       "ei-huomautettavaa" "ehdollinen" "puollettu"
                                                       "ei-puollettu" "ei-lausuntoa" "lausunto"
                                                       "kielteinen" "palautettu" "poydalle"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.2.0"}}}))

  ;; permit type YA

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-YA) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.1.3"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-YA) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.2.0"}}}))

  ;; permit type YM

  (fact "get-possible-statement-statuses, permit type YM, no krysp versions defined"
    (possible-statement-statuses test-app-YM) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {})))

(facts "update-statement"
  (fact "update-draft"
    (-> (ssg/generate Statement)
        (assoc :modify-id "mod1")
        (dissoc :modified)
        (update-draft "some text" "puoltaa" "mod2" "mod1" "editor1"))
    => (contains #{[:text "some text"] 
                   [:status "puoltaa"] 
                   [:modify-id "mod2"]
                   [:editor-id "editor1"]
                   [:state :draft]
                   [:modified anything]}))

  (fact "update-draft - wrong modify-id"
    (-> (ssg/generate Statement)
        (assoc :modify-id "modx")
        (update-draft "some text" "puoltaa" "mod2" "mod1" "editor1"))
    => (throws Exception))

  (fact "update-draft - updated statement is missing person should produce validation error"
    (-> (ssg/generate Statement)
        (dissoc :person)
        (update-draft "some text" "puoltaa" "mod2" "mod1" "editor1"))
    => (throws Exception))

  (fact "give-statement"
    (-> (ssg/generate Statement)
        (assoc :modify-id "mod1")
        (dissoc :given)
        (give-statement "some text" "puoltaa" "mod2" "mod1" "editor1"))
    => (contains #{[:text "some text"] 
                   [:status "puoltaa"] 
                   [:modify-id "mod2"] 
                   [:editor-id "editor1"]
                   [:state :given] 
                   [:given anything]})))
