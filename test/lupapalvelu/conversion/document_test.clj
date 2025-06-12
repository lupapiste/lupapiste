(ns lupapalvelu.conversion.document-test
  (:require [lupapalvelu.conversion.document :as doc]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer [now]]))

(testable-privates lupapalvelu.conversion.document
                   find-options->map)

(def mongo-doc {:id           "my-id"       :backend-id     "foobar-id"
                :organization "123-R"       :history        [{:op "store" :ts 12345}]
                :tags         ["one" "two"] :n              1
                :year         2020          :permit-type    "R"
                :suffix       "ugh"         :_sheriff-notes {:note    "Hello!"
                                                             :created 12345678}})

(def coerced-doc {:id           "my-id"       :backend-id  "foobar-id"
                  :organization "123-R"       :history     [{:op :store :ts 12345}]
                  :tags         ["one" "two"] :n           1
                  :year         2020          :permit-type "R"
                  :suffix       :ugh})

(facts "document"
  (doc/document "123-R" "foobar-id") => nil
  (provided
    (mongo/select-one :conversion anything) => nil)
  (doc/document "123-R" "foobar-id") => coerced-doc
  (provided
    (mongo/select-one :conversion {:organization "123-R"
                                   :backend-id   "foobar-id"})
    => mongo-doc)
  (doc/document "my-id") => nil
  (provided
    (mongo/select-one :conversion {:_id "my-id"}) => nil)
  (doc/document "my-id") => coerced-doc
  (provided
    (mongo/select-one :conversion {:_id "my-id"}) => mongo-doc)
  (doc/document "my-id") => (dissoc coerced-doc :suffix)
  (provided
    (mongo/select-one :conversion {:_id "my-id"}) => (dissoc mongo-doc :suffix)))

(facts "find-options->map"
  (find-options->map nil) => {}
  (find-options->map []) => {}
  (find-options->map [nil]) => (throws)
  (find-options->map [:bad]) => (throws)
  (find-options->map [:exclude-errors]) => {:exclude-errors true}
  (find-options->map [:exclude-errors :bad]) => (throws)
  (find-options->map [:exclude-errors :target-order])
  => {:exclude-errors true :target-order true}
  (find-options->map [:target-order :exclude-errors])
  => {:exclude-errors true :target-order true}
  (find-options->map [:target-order]) => {:target-order true}
  (find-options->map [:target-order :exclude-errors
                      :target-order :exclude-errors
                      :target-order :exclude-errors])
  => {:exclude-errors true :target-order true})

(facts "find-documents"
  (facts "organization-id"
    (doc/find-documents {:organization-id "123-R"}) => [coerced-doc]
    (provided
      (mongo/select-options :conversion {:organization "123-R"} {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :permit-type     "R"
                         :targets         [{:id "foobar-id"}
                                           {:filename "file.xml"}]})
    => [coerced-doc (assoc coerced-doc :backend-id "other-id")]
    (provided
      (lupapalvelu.conversion.util/find-xml-ids "R" "file.xml")
      => {:backend-id "other-id"}
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :permit-type  "R"
                             :backend-id   {$in ["foobar-id" "other-id"]}}
                            {})
      => [mongo-doc (assoc mongo-doc :backend-id "other-id")]))

  (facts "targets"
    (doc/find-documents {:organization-id "123-R"
                         :permit-type     "R"
                         :targets         [{:id "foobar-id"}
                                           {:filename "file.xml"}]})
    => [coerced-doc]
    (provided
      (lupapalvelu.conversion.util/find-xml-ids "R" "file.xml") => {}
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :permit-type  "R"
                             :backend-id   "foobar-id"}
                            {})
      => [mongo-doc]))

  (facts "tags"
    (doc/find-documents {:organization-id "123-R"
                         :tags            ["one"]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :tags         "one"}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :tags            ["one" "two"]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             $and          [{:tags "one"} {:tags "two"}]}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :tags            []})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion {:organization "123-R"}
                            {})
      => [mongo-doc]))

  (facts "range"
    (doc/find-documents {:organization-id "123-R"
                         :range           [1 10]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :n            {$gte 1 $lte 10}}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :range           [1 1]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :n            {$gte 1 $lte 1}}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :range           [2 3]})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :n            {$gte 2 $lte 3}}
                            {})
      => []))

  (facts "years"
    (doc/find-documents {:organization-id "123-R"
                         :years           [2012]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :year         2012}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :years           [2011 2012 2013]})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :year         {$in [2011 2012 2013]}}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"
                         :years           [2013]})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :year         2013}
                            {})
      => [])
    (doc/find-documents {:organization-id "123-R"
                         :years           [2011 2013]})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :year         {$in [2011 2013]}}
                            {})
      => []))

  (facts "limit: no target-order ids, use mongo options"
    (doc/find-documents {:organization-id "123-R"
                         :limit           10})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"}
                            {:limit 10})
      => [])
    (doc/find-documents {:organization-id "123-R"
                         :limit           nil})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"}
                            {})
      => [])
    (doc/find-documents {:organization-id "123-R"
                         :limit           10
                         :targets         [{:id "one"} {:id "two"}]})
    => []
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :backend-id   {$in ["one" "two"]}}
                            {:limit 10})
      => []))

  (facts "limit: target-order with ids, no mongo limit"
    (doc/find-documents {:organization-id "123-R"
                         :limit           1
                         :targets         [{:id (:backend-id mongo-doc)}
                                           {:id "two"} {:id "three"}]}
                        nil
                        :target-order)
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :backend-id   {$in [(:backend-id mongo-doc) "two" "three"]}}
                            {:projection [:backend-id]})
      => [{:id "id3" :backend-id "three"}
          mongo-doc
          {:id "id2" :backend-id "two"}]
      (mongo/select-one :conversion {:_id "my-id"}) => mongo-doc))

  (facts "extras"
    (doc/find-documents {:organization-id "123-R"}
                        {:converted {$ne true}})
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization "123-R"
                             :converted    {$ne true}}
                            {})
      => [mongo-doc]))

  (facts "exclude-errors"
    (doc/find-documents {:organization-id "123-R"}
                        {:converted {$ne true}}
                        :exclude-errors)
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization     "123-R"
                             :converted        {$ne true}
                             :conversion-error {$exists false}
                             :kuntagml.error   {$exists false}}
                            {})
      => [mongo-doc])
    (doc/find-documents {:organization-id "123-R"} nil :exclude-errors)
    => [coerced-doc]
    (provided
      (mongo/select-options :conversion
                            {:organization     "123-R"
                             :conversion-error {$exists false}
                             :kuntagml.error   {$exists false}}
                            {})
      => [mongo-doc]))


  (let [condition-check (fn [check value]
                          (fact "condition-check"
                            value => check))]
    (facts "exclude-foremen"
      (doc/find-documents {:organization-id "123-R"}
                          {:converted true}
                          :exclude-foremen)
      => [coerced-doc]
      (provided
        (mongo/select-options :conversion
                              (as-checker
                                (partial condition-check
                                         {:organization "123-R"
                                          :suffix       {$ne :tjo}
                                          :converted    true}))
                              {})
        => [mongo-doc])


      (doc/find-documents {:organization-id "123-R"
                           :targets         [{:id "one"}]} nil :exclude-foremen)
      => [coerced-doc]
      (provided
        (mongo/select-options :conversion
                              (as-checker
                                (partial condition-check
                                         (just {:organization "123-R"
                                                :backend-id   "one"
                                                :suffix       {$ne :tjo}})))
                              {})
        => [mongo-doc])
      (doc/find-documents {:organization-id "123-R"
                           :targets         [{:id "one"} {:id "two"}]}
                          {:converted {$ne true}}
                          :exclude-errors :exclude-foremen)
      => [coerced-doc]
      (provided
        (mongo/select-options :conversion
                              (just {:organization     "123-R"
                                     :converted        {$ne true}
                                     :conversion-error {$exists false}
                                     :kuntagml.error   {$exists false}
                                     :backend-id       {$in ["one" "two"]}
                                     :suffix           {$ne :tjo}})
                              {})
        => [mongo-doc])))

  (let [make-doc       #(assoc mongo-doc :id (str % "-id") :backend-id %
                               :LP-id (format "LP-123-2024-%05d"
                                              (case (keyword %)
                                                :one  1 :two  2 :three 3
                                                :four 4 :five 5)))
        default-order  (map make-doc ["one" "two" "three" "four" "five"])
        default-result (map #'doc/->document default-order)]

    (facts "target-order"
      (against-background
        [(mongo/select-options :conversion anything {})
         => default-order]
        (fact "Default ordering by LP-id"
          (doc/find-documents {:organization-id "123-R"})
          => default-result
          (doc/find-documents {:organization-id "123-R"
                               :targets         [{:id "five"} {:id "four"}
                                                 {:id "one"} {:id "three"}
                                                 {:id "two"}]})
          => default-result)

        (fact "Ordering without ids does nothing"
          (doc/find-documents {:organization-id "123-R"} nil :target-order)
          => default-result))

      (against-background
        [(mongo/select-options :conversion anything {:projection [:backend-id] })
         => default-order
         (mongo/select-one :conversion {:_id "one-id"}) => (make-doc "one")
         (mongo/select-one :conversion {:_id "two-id"}) => (make-doc "two")
         (mongo/select-one :conversion {:_id "three-id"}) => (make-doc "three")
         (mongo/select-one :conversion {:_id "four-id"}) => (make-doc "four")
         (mongo/select-one :conversion {:_id "five-id"}) => (make-doc "five")]
        (fact "Ordering by target-order"
          (map :id (doc/find-documents {:organization-id "123-R"
                                        :targets         [{:id "five"} {:id "four"}
                                                          {:id "one"} {:id "three"}
                                                          {:id "two"}]}
                                       nil :target-order))
          => ["five-id" "four-id" "one-id" "three-id" "two-id"]
          (map :id (doc/find-documents {:organization-id "123-R"
                                        :targets         [{:id "two"} {:id "three"}
                                                          {:id "one"} {:id "four"}
                                                          {:id "five"}]}
                                       nil :target-order))
          => ["two-id" "three-id" "one-id" "four-id" "five-id"]
          (map :id (doc/find-documents {:organization-id "123-R"
                                        :targets         [{:id "five"} {:id "foo"}
                                                          {:id "four"} {:bar "bar"}
                                                          {:id "one"} {:id "three"}
                                                          {:id "two"} {:id "where"}]}
                                       nil :target-order :lp-id-order))
          => ["five-id" "four-id" "one-id" "three-id" "two-id"]
          (map :id (doc/find-documents {:organization-id "123-R"
                                        :limit           2
                                        :targets         [{:id "five"} {:id "foo"}
                                                          {:id "four"} {:bar "bar"}
                                                          {:id "one"} {:id "three"}
                                                          {:id "two"} {:id "where"}]}
                                       nil :target-order))
          => ["five-id" "four-id"]))
      (against-background
        [(mongo/select-options :conversion anything {:projection [:LP-id] })
         => default-order
         (mongo/select-one :conversion {:_id "one-id"}) => (make-doc "one")
         (mongo/select-one :conversion {:_id "two-id"}) => (make-doc "two")
         (mongo/select-one :conversion {:_id "three-id"}) => (make-doc "three")
         (mongo/select-one :conversion {:_id "four-id"}) => (make-doc "four")
         (mongo/select-one :conversion {:_id "five-id"}) => (make-doc "five")]
        (fact "Ordering by lp-id-order"
          (map :id (doc/find-documents {:organization-id "123-R"}
                                       nil :lp-id-order))
          => ["one-id" "two-id" "three-id" "four-id" "five-id"]
          (map :id (doc/find-documents {:organization-id "123-R"}
                                       nil :target-order :lp-id-order))
          => ["one-id" "two-id" "three-id" "four-id" "five-id"]
          (map :id (doc/find-documents {:organization-id "123-R"
                                        :limit           2}
                                       nil :lp-id-order))
          => ["one-id" "two-id"])))))

(facts "find-converted-documents"
  (doc/find-converted-documents {:organization-id "123-R"})
  => [coerced-doc]
  (provided
    (mongo/select-options :conversion
                          {:organization     "123-R"
                           :converted        true
                           :conversion-error {$exists false}
                           :kuntagml.error   {$exists false}}
                          {})
    => [mongo-doc])
  (doc/find-converted-documents {:organization-id "123-R"
                                 :permit-type     "R"}
                                {:converted false
                                 :year      2024}
                                :exclude-errors)
  => [coerced-doc]
  (provided
    (mongo/select-options :conversion
                          {:organization     "123-R"
                           :permit-type      "R"
                           :year             2024
                           :converted        true
                           :conversion-error {$exists false}
                           :kuntagml.error   {$exists false}}
                          {})
    => [mongo-doc])
  (doc/find-converted-documents {:organization-id "123-R"})
  => [coerced-doc]
  (provided
    (mongo/select-options :conversion
                          {:organization     "123-R"
                           :converted        true
                           :conversion-error {$exists false}
                           :kuntagml.error   {$exists false}}
                          {})
    => [mongo-doc])
  (doc/find-converted-documents {:organization-id "123-R"})
  => [coerced-doc]
  (provided
    (mongo/select-options :conversion
                          {:organization     "123-R"
                           :converted        true
                           :conversion-error {$exists false}
                           :kuntagml.error   {$exists false}}
                          {})
    => [mongo-doc]))

(facts "update-document"
  (doc/update-document "my-id" {$unset {:converted true}})
  => coerced-doc
  (provided
    (mongo/update-one-and-return :conversion {:_id "my-id"}
                                 {$unset {:converted true}})
    => mongo-doc)
  (doc/update-document {:id "my-id" :foo "bar"}
                       {$unset {:converted true}})
  => coerced-doc
  (provided
    (mongo/update-one-and-return :conversion {:_id "my-id"}
                                 {$unset {:converted true}})
    => mongo-doc)
  (doc/update-document {} {$set {:linked true}}) => throws)

(facts "set-conversion-error"
  (doc/set-conversion-error "my-id" {:code :error-code}) => nil
  (provided
    (mongo/update-by-id :conversion "my-id"
                        {$set {:conversion-error {:code :error-code}}})
    => nil)
  (doc/clear-conversion-error {:id "my-id"}) => nil
  (provided
    (mongo/update-by-id :conversion "my-id"
                        {$unset {:conversion-error true}})
    => nil)
  (doc/set-conversion-error "my-id"
                            {:exception (ex-info "Test error"
                                                 {:conversion-error :from-exception})})
  => nil
  (provided
    (mongo/update-by-id :conversion "my-id"
                        {$set {:conversion-error {:code :from-exception
                                                  :text "Test error"}}})
    => nil)
  (doc/set-conversion-error {:id "my-id"}
                            {:exception (ex-info "Test error" {})})
  => nil
  (provided
    (mongo/update-by-id :conversion "my-id"
                        {$set {:conversion-error {:code :unknown
                                                  :text "Test error"}}})
    => nil)
  (doc/set-conversion-error {:id "my-id"}
                            {:code      :no-luck
                             :text      "Oh well"
                             :xml-error "no idea"
                             :exception (ex-info "Test error" {})})
  => nil
  (provided
    (mongo/update-by-id :conversion "my-id"
                        {$set {:conversion-error {:code :no-luck
                                                  :text "Oh well"}
                               :kuntagml.error   "no idea"}})
    => nil))

(def ts (now))
(def mongo-id (mongo/create-id))

(against-background
  [(now) => ts
   (mongo/create-id) => mongo-id]
  (facts "upsert-document"
    (facts "store: new document"
      (doc/upsert-document :store {:conversion-error {:code :bad-kuntagml}
                                   :kuntagml         {:error "Bad KuntaGML"
                                                      :xml   "<bad>BAD</bad>"}
                                   :organization-id  "123-R"
                                   :n                10
                                   :backend-id       "12-1234-HI"
                                   :suffix           :hi})
      => {:id               mongo-id
          :conversion-error {:code :bad-kuntagml}
          :kuntagml         {:error "Bad KuntaGML"
                             :xml   "<bad>BAD</bad>"}
          :organization     "123-R"
          :backend-id       "12-1234-HI"
          :suffix           :hi
          :permit-type      "R"
          :tags             []
          :n                10
          :year             2012
          :history          [{:op :store :ts ts}]}
      (provided
        (mongo/insert :conversion anything) => nil))
    (facts "store: update document"
      (doc/upsert-document :store {:kuntagml        {:xml "<good>GOOD</good>"}
                                   :organization-id "123-R"
                                   :backend-id      "12-1234-HI"
                                   :tags            ["hello" "world"]
                                   :n               10 ;; Not stored for existing documents
                                   :document        {:id               mongo-id
                                                     :conversion-error {:code :bad-kuntagml}
                                                     :kuntagml         {:error "Bad KuntaGML"
                                                                        :xml   "<bad>BAD</bad>"}
                                                     :organization     "123-R"
                                                     :backend-id       "12-1234-HI"
                                                     :suffix           :hi
                                                     :permit-type      "R"
                                                     :tags             []
                                                     :history          [{:op :store :ts 123456}]}})
      => {:id               mongo-id
          :kuntagml         {:xml "<good>GOOD</good>"}
          :organization     "123-R"
          :conversion-error {:code :bad-kuntagml}
          :backend-id       "12-1234-HI"
          :suffix           :hi
          :permit-type      "R"
          :year             2012
          :tags             ["hello" "world"]
          :history          [{:op :store :ts 12345}
                             {:op :store :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set      {:kuntagml {:xml "<good>GOOD</good>"}}
                                      $push     {:history {:op :store :ts ts}}
                                      $addToSet {:tags {$each ["hello" "world"]}}})
        => {:_id              mongo-id
            :conversion-error {:code :bad-kuntagml}
            :kuntagml         {:xml "<good>GOOD</good>"}
            :organization     "123-R"
            :backend-id       "12-1234-HI"
            :suffix           "hi"
            :permit-type      "R"
            :year             2012
            :tags             ["hello" "world"]
            :history          [{:op :store :ts 12345}
                               {:op :store :ts ts}]}))

    (let [xy       {:x 404376.232 :y 6694419.107}
          location (assoc xy :address "Test Town 10")]
      (facts "store: location"
        (doc/upsert-document :location {:location location
                                        :document {:id mongo-id}})
        => {:id           mongo-id
            :kuntagml     {:xml "<good>GOOD</good>"}
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       :hi
            :permit-type  "R"
            :year         2012
            :tags         ["hello" "world"]
            :location     location
            :history      [{:op :store :ts 12345}
                           {:op :location :ts ts}]}
        (provided
          (mongo/update-one-and-return :conversion {:_id mongo-id}
                                       {$set  {:location location}
                                        $push {:history {:op :location :ts ts}}})
          => {:_id          mongo-id
              :kuntagml     {:xml "<good>GOOD</good>"}
              :organization "123-R"
              :backend-id   "12-1234-HI"
              :suffix       "hi"
              :permit-type  "R"
              :year         2012
              :tags         ["hello" "world"]
              :location     location
              :history      [{:op :store :ts 12345}
                             {:op :location :ts ts}]}))

      (facts "convert"
        (doc/upsert-document :convert {:id             mongo-id
                                       :reset-state    "closed"
                                       :reset-location xy} )
        => {:id             mongo-id
            :converted      true
            :organization   "123-R"
            :backend-id     "12-1234-HI"
            :suffix         :hi
            :reset-state    :closed
            :reset-location xy
            :permit-type    "R"
            :year           2012
            :app-links      []
            :LP-id          "LP-123-2023-00001"
            :history        [{:op :store :ts 12345}
                             {:op :convert :ts ts}]}
        (provided
          (mongo/update-one-and-return :conversion {:_id mongo-id}
                                       {$set   {:converted      true
                                                :app-links      []
                                                :reset-state    "closed"
                                                :reset-location xy}
                                        $unset {:conversion-error true}
                                        $push  {:history {:op :convert :ts ts}}})
          => {:_id            mongo-id
              :converted      true
              :app-links      []
              :organization   "123-R"
              :backend-id     "12-1234-HI"
              :suffix         "hi"
              :reset-state    "closed"
              :reset-location xy
              :permit-type    "R"
              :year           2012
              :LP-id          "LP-123-2023-00001"
              :history        [{:op "store" :ts 12345}
                               {:op "convert" :ts ts}]}))

      (facts "fix-locations"
        (fact "Converted"
          (doc/upsert-document :fix-locations {:document {:id        mongo-id
                                                          :converted true}
                                               :location location} )
          => {:id             mongo-id
              :converted      true
              :organization   "123-R"
              :backend-id     "12-1234-HI"
              :suffix         :hi
              :location       location
              :reset-location xy
              :permit-type    "R"
              :year           2012
              :app-links      []
              :LP-id          "LP-123-2023-00001"
              :history        [{:op :store :ts 12345}
                               {:op :convert :ts 23456}
                               {:op :fix-locations :ts ts}]}
          (provided
            (mongo/update-one-and-return :conversion {:_id mongo-id}
                                         {$set  {:location       location
                                                 :reset-location xy}
                                          $push {:history {:op :fix-locations :ts ts}}})
            => {:_id            mongo-id
                :converted      true
                :app-links      []
                :organization   "123-R"
                :backend-id     "12-1234-HI"
                :suffix         "hi"
                :location       location
                :reset-location xy
                :permit-type    "R"
                :year           2012
                :LP-id          "LP-123-2023-00001"
                :history        [{:op "store" :ts 12345}
                                 {:op "convert" :ts 23456}
                                 {:op "fix-locations" :ts ts}]}))
        (fact "Not converted"
          (doc/upsert-document :fix-locations {:document {:id mongo-id}
                                               :location location} )
          => {:id           mongo-id
              :converted    true
              :organization "123-R"
              :backend-id   "12-1234-HI"
              :suffix       :hi
              :location     location
              :permit-type  "R"
              :year         2012
              :app-links    []
              :LP-id        "LP-123-2023-00001"
              :history      [{:op :store :ts 12345}
                             {:op :fix-locations :ts ts}]}
          (provided
            (mongo/update-one-and-return :conversion {:_id mongo-id}
                                         {$set  {:location location}
                                          $push {:history {:op :fix-locations :ts ts}}})
            => {:_id          mongo-id
                :converted    true
                :app-links    []
                :organization "123-R"
                :backend-id   "12-1234-HI"
                :suffix       "hi"
                :location     location
                :permit-type  "R"
                :year         2012
                :LP-id        "LP-123-2023-00001"
                :history      [{:op "store" :ts 12345}
                               {:op "fix-locations" :ts ts}]}))))

    (facts "reset-state"
      (doc/upsert-document :reset-state {:id          mongo-id
                                         :reset-state "closed"} )
      => {:id           mongo-id
          :converted    true
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :reset-state  :closed
          :permit-type  "R"
          :year         2012
          :app-links    []
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:reset-state "closed"}})
        => {:_id          mongo-id
            :converted    true
            :app-links    []
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :reset-state  "closed"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts ts}]}))

    (facts "app-links"
      (doc/upsert-document :app-links {:id          mongo-id
                                       :reset-state "inUse"
                                       :app-links   ["LP-123-2020-00067"
                                                     "HELLO" "HELLO"]})
      => {:id           mongo-id
          :converted    true
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :reset-state  :inUse
          :permit-type  "R"
          :year         2012
          :app-links    ["LP-123-2020-00067" "HELLO"]
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:app-links   ["LP-123-2020-00067" "HELLO"]
                                            :reset-state "inUse"}})
        => {:_id          mongo-id
            :converted    true
            :app-links    ["LP-123-2020-00067" "HELLO"]
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :reset-state  "inUse"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts ts}]}))

    (facts "link"
      (doc/upsert-document :link {:id mongo-id} )
      => {:id           mongo-id
          :converted    true
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :linked       true
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts 23456}
                         {:op :link :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set  {:linked true}
                                      $push {:history {:op :link :ts ts}}})
        => {:_id          mongo-id
            :converted    true
            :linked       true
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts 23456}
                           {:op "link" :ts ts}]}))

    (facts "send"
      (doc/upsert-document :send {:id mongo-id} )
      => {:id           mongo-id
          :converted    true
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts 23456}
                         {:op :send :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$push {:history {:op :send :ts ts}}})
        => {:_id          mongo-id
            :converted    true
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts 23456}
                           {:op "send" :ts ts}]}))

    (facts "tag: changes"
      (doc/upsert-document :tag {:document {:id   mongo-id
                                            :tags ["going" "old" "gone"]}
                                 :add      ["hello" "world"]
                                 :remove   ["going" "gone"]} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["hello" "old" "world"]
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:tags ["hello" "old" "world"]}})
        => {:_id          mongo-id
            :organization "123-R"
            :tags         ["hello" "old" "world"]
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}]})

      (doc/upsert-document :tag {:document {:id   mongo-id
                                            :tags ["going" "old" "gone"]}
                                 :add      ["hello" "world"]} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["going" "gone" "hello" "old" "world"]
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:tags ["going" "gone" "hello" "old" "world"]}})
        => {:_id          mongo-id
            :organization "123-R"
            :tags         ["going" "gone" "hello" "old" "world"]
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}]})

      (doc/upsert-document :tag {:document {:id   mongo-id
                                            :tags ["going" "old"]}
                                 :remove   ["going" "gone"]} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["old"]
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:tags ["old"]}})
        => {:_id          mongo-id
            :organization "123-R"
            :tags         ["old"]
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}]}))

    (facts "tag: renumber"
      (doc/upsert-document :tag {:document coerced-doc
                                 :n        100})
      => (assoc coerced-doc :n 100)
      (provided
        (mongo/update-one-and-return :conversion {:_id "my-id"}
                                     {$set {:n 100}})
        => (assoc mongo-doc :n 100))
      (doc/upsert-document :tag {:document {:id   mongo-id
                                            :tags ["foo" "old"]}
                                 :add      ["new"]
                                 :remove   ["old"]
                                 :n        99} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["foo" "new"]
          :n            99
          :year         2012
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set {:tags ["foo" "new"]
                                            :n    99}})
        => {:_id          mongo-id
            :organization "123-R"
            :n            99
            :year         2012
            :tags         ["foo" "new"]
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}]}))

    (facts "tag: no changes"
      (doc/upsert-document :tag {:document {:id           mongo-id
                                            :organization "123-R"
                                            :tags         ["going" "gone" "old"]
                                            :backend-id   "12-1234-HI"
                                            :suffix       :hi
                                            :permit-type  "R"
                                            :year         2012
                                            :LP-id        "LP-123-2023-00001"
                                            :history      [{:op :store :ts 12345}]}
                                 :add      []
                                 :remove   []} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["going" "gone" "old"]
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]}
      (doc/upsert-document :tag {:document {:id           mongo-id
                                            :organization "123-R"
                                            :tags         ["going" "gone" "old"]
                                            :backend-id   "12-1234-HI"
                                            :suffix       :hi
                                            :permit-type  "R"
                                            :year         2012
                                            :LP-id        "LP-123-2023-00001"
                                            :history      [{:op :store :ts 12345}]}
                                 :add      ["old" "gone"]
                                 :remove   ["unknown" "whatever"]} )
      => {:id           mongo-id
          :organization "123-R"
          :tags         ["going" "gone" "old"]
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}]})

    (facts "clean"
      (doc/upsert-document :clean {:id mongo-id})
      => {:id           mongo-id
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts 23456}
                         {:op :clean :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$unset {:linked           true
                                              :conversion-error true
                                              :converted        true
                                              :reset-state      true
                                              :reset-location   true
                                              :app-links        true}
                                      $push  {:history {:op :clean :ts ts}}})
        => {:_id          mongo-id
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts 23456}
                           {:op "clean" :ts ts}]}))

    (facts "fix-history"
      (doc/upsert-document :fix-history {:id          mongo-id
                                         :reset-state "hello"})
      => {:id           mongo-id
          :organization "123-R"
          :backend-id   "12-1234-HI"
          :suffix       :hi
          :reset-state  :hello
          :permit-type  "R"
          :year         2012
          :LP-id        "LP-123-2023-00001"
          :history      [{:op :store :ts 12345}
                         {:op :convert :ts 23456}
                         {:op :fix-history :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$set  {:reset-state "hello"}
                                      $push {:history {:op :fix-history :ts ts}}})
        => {:_id          mongo-id
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :reset-state  "hello"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts 23456}
                           {:op "fix-history" :ts ts}]}))

    (facts "History upsert fallback operations"
      (doseq [op [:fix-verdicts :fix-foremen]]
        (fact {:midje/description (name op)}
          (doc/upsert-document op {:id mongo-id})
          => {:id           mongo-id
              :organization "123-R"
              :backend-id   "12-1234-HI"
              :suffix       :hi
              :permit-type  "R"
              :year         2012
              :LP-id        "LP-123-2023-00001"
              :history      [{:op :store :ts 12345}
                             {:op :convert :ts 23456}
                             {:op op :ts ts}]}
      (provided
        (mongo/update-one-and-return :conversion {:_id mongo-id}
                                     {$push {:history {:op op :ts ts}}})
        => {:_id          mongo-id
            :organization "123-R"
            :backend-id   "12-1234-HI"
            :suffix       "hi"
            :permit-type  "R"
            :year         2012
            :LP-id        "LP-123-2023-00001"
            :history      [{:op "store" :ts 12345}
                           {:op "convert" :ts 23456}
                           {:op (name op) :ts ts}]})))

      (fact "Unknown operation"
        (doc/upsert-document :what? {:id mongo-id})
        => (throws #"history-entry does not match schema")))))
