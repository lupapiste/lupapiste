(ns lupapalvelu.application-search-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [lupapalvelu.test-util :refer :all]
             [lupapalvelu.application-search :refer :all]))

(testable-privates lupapalvelu.application-search make-sort operation-names)

(facts "operation-names"
  (operation-names "bil") => ["auto-katos"]
  (operation-names "grilli") => ["auto-katos"]
  (operation-names "Ty\u00f6njohtaja") => ["tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen"]
  (operation-names "ANNAN") => (just ["muu-uusi-rakentaminen" "muu-tontti-tai-kort-muutos" "ya-kayttolupa-muu-kayttolupa" "muu-laajentaminen" "muu-rakennus-laaj" "talousrakennus-laaj" "masto-tms" "muu-maisema-toimenpide" "varasto-tms" "sisatila-muutos"] :in-any-order)
  (operation-names "S\u00e4hk\u00f6-, data ja muiden kaapelien sijoittaminen") => ["ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen"])

(facts "sorting parameter parsing"
  (make-sort {:sort {:field "unknown" :asc false}})  => {}
  (make-sort {:sort {:field "unknown" :asc true}})  => {}
  (make-sort {:sort {:field "id" :asc false}}) => {}
  (make-sort {:sort {:field "_id" :asc false}}) => {}
  (make-sort {:sort {:field "type" :asc true }})  => {:infoRequest 1}
  (make-sort {:sort {:field "type" :asc false }}) => {:infoRequest -1}
  (make-sort {:sort {:field "location" :asc false }}) => {:address -1}
  (make-sort {:sort {:field "applicant" :asc false }}) => {:applicant -1}
  (make-sort {:sort {:field "submitted" :asc true }})  => {:submitted 1}
  (make-sort {:sort {:field "modified" :asc true }})  => {:modified 1}
  (make-sort {:sort {:field "state" :asc true }})  => {:state 1}
  (make-sort {:sort {:field "handler" :asc true }}) => {"authority.lastName" 1, "authority.firstName" 1}
  (make-sort {:sort {:field {:injection "attempt"}
              :asc "; drop database;"}})   => {}
  (make-sort {})                                 => {}
  (make-sort nil)                                => {})

(fact "make-query (LUPA-519) with filter-user checks both authority and auth.id"
  (-> (make-query {} {:kind  "both"
                     :applicationType "all"
                     :handler  "123"}
                 {:role "authority"}) (get "$and") last) => (contains {"$or" [{"auth.id" "123"} {"authority.id" "123"}]}))

(fact "query contais user query"
  (-> (make-query {:auth.id "123"} {} {}) (get "$and") first) => {:auth.id "123"})

(fact "Tags are present in query"
  (-> (make-query {} {:applicationTags ["test1" "test2"]} {}) (get "$and") last :tags) => {"$in" ["test1" "test2"]})

(fact "Organization are present in query"
  (-> (make-query {} {:applicationOrganizations ["753-R" "753-YA"]} {}) (get "$and") last :organization) => {"$in" ["753-R" "753-YA"]})
