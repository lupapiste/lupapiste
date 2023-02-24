(ns sade.schemas-test
  (:require [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.schema-utils :as ssu]
            [sade.schemas :refer :all]
            [sade.shared-schemas :refer [FileId]]
            [schema.core :as sc])
  (:import [java.util UUID]))

(facts max-length-constraint
  (fact (sc/check (sc/pred (max-length-constraint 1)) []) => nil)
  (fact (sc/check (sc/pred (max-length-constraint 1)) [1]) => nil)
  (fact (sc/check (sc/pred (max-length-constraint 1)) [1 2]) =not=> nil))

(facts max-length-string
  (fact (sc/check (max-length-string 1) "a") => nil)
  (fact (sc/check (max-length-string 1) "ab") =not=> nil)
  (fact (sc/check (max-length-string 1) [1]) =not=> nil))

(facts "schema-utils"
  (let [test-schema {:key                    sc/Str
                     (sc/required-key :rkey) sc/Int
                     (sc/optional-key :okey) sc/Any}]
    (fact "keys"
      (ssu/keys test-schema) => (just [:key :rkey :okey]))
    (fact "select-keys"
      (ssu/select-keys test-schema [:key :okey :fookey]) => (just {:key sc/Str
                                                                   (sc/optional-key :okey) sc/Any}))))


(facts "FileId"
  (fact "Empty and bad ids"
    (sc/check FileId nil) =not=> nil
    (sc/check FileId "") =not=> nil
    (sc/check FileId "bad id") =not=> nil
    (sc/check FileId 313 ) =not=> nil
    (sc/check FileId (str (mongo/create-id) "-foo")) =not=> nil
    (sc/check FileId (str (mongo/create-id) "-pdfa ")) =not=> nil
    (sc/check FileId (str (mongo/create-id) "-preview")) =not=> nil)
  (fact "Object id"
    (sc/check FileId (mongo/create-id)) => nil
    (sc/check FileId (str (mongo/create-id) "-pdfa")) => nil
    (sc/check ObjectIdStr (mongo/create-id)) => nil
    (sc/check ObjectIdStr (str (mongo/create-id) "-pdfa")) => nil
    (sc/check ObjectIdKeyword (keyword (mongo/create-id))) => nil
    (sc/check ObjectIdKeyword (keyword (str (mongo/create-id) "-pdfa"))) => nil)
  (fact "UUID string"
    (sc/check FileId (str (UUID/randomUUID))) => nil
    (sc/check FileId (str (UUID/randomUUID) "-pdfa")) => nil
    (sc/check UUIDStr (str (UUID/randomUUID))) => nil
    (sc/check UUIDStr (str (UUID/randomUUID) "-pdfa")) => nil))

(facts "Email"
  (sc/check Email "hello.world@example.com") => nil
  (sc/check Email "Hello.World@example.com") =not=> nil
  (sc/check Email " hello.world@example.com ") =not=> nil)

(facts "EmailAnyCase"
  (sc/check EmailAnyCase "hello.world@example.com") => nil
  (sc/check EmailAnyCase "Hello.World@example.com") => nil
  (sc/check EmailAnyCase " hello.world@example.com ") =not=> nil)

(facts "EmailSpaced"
  (sc/check EmailSpaced "hello.world@example.com") => nil
  (sc/check EmailSpaced "Hello.World@example.com") =not=> nil
  (sc/check EmailSpaced " hello.world@example.com ") => nil)
