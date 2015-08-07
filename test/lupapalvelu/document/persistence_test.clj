(ns lupapalvelu.document.persistence-test
  (:require [lupapalvelu.document.persistence :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.model :as model]))

(def some-time 123456789)

(model/with-timestamp some-time
  (facts "Facts about ->update"
    (fact (->mongo-updates "foo" (->model-updates []) nil) => {})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) nil) => {"foo.a.value" "A"
                                                                    "foo.a.modified" some-time})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) {:source "krysp"}) => {"foo.a.value" "A"
                                                                                      "foo.a.sourceValue" "A"
                                                                                      "foo.a.modified" some-time
                                                                                      "foo.a.source" "krysp"})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) {:source "krysp" :modifier "Me"}) => {"foo.a.value" "A"
                                                                                                     "foo.a.sourceValue" "A"
                                                                                                     "foo.a.modified" some-time
                                                                                                     "foo.a.source" "krysp"
                                                                                                     "foo.a.modifier" "Me"})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"] ["b.b" "B"]]) nil) => {"foo.a.value"      "A"
                                                                                    "foo.a.modified"   some-time
                                                                                    "foo.b.b.value"    "B"
                                                                                    "foo.b.b.modified" some-time}))

  (fact "create model updates from ui-protocol"
    (->model-updates [["mitat.kerrosluku" "44"]]) => [[[:mitat :kerrosluku] "44"]])

  (fact "create mongo updates from model-updates"
    (->mongo-updates "documents.$.data"
      [[[:mitat :kerrosluku] ..kerrosluku..]
       [[:henkilo :nimi] ..nimi..]] nil)
    => {"documents.$.data.mitat.kerrosluku.value"    ..kerrosluku..
        "documents.$.data.mitat.kerrosluku.modified" some-time
        "documents.$.data.henkilo.nimi.value"        ..nimi..
        "documents.$.data.henkilo.nimi.modified"     some-time}))

