(ns lupapalvelu.batchrun.vantaa-statements-test
  (:require [lupapalvelu.batchrun.vantaa-statements :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :refer [timestamp today]])
  (:import [java.util Date]))

(def runeberg "5.2.2021")
(def kalevala (timestamp "28.4.2021"))
(def friday   (Date. (timestamp "2.5.2021")))

(facts resolve-status
  (resolve-status "ehdollinen") => "ehdollinen"
  (resolve-status "ei-puollettu") => "ei-puollettu"
  (resolve-status "ei puollettu") => "ei-puollettu"
  (resolve-status "ei  puollettu") => "ei-puollettu"
  (resolve-status "bad") => (throws Exception))

(facts make-person
  (make-person "Hello") => {:userId "batchrun-user"
                            :email  "eraajo@lupapiste.fi"
                            :name   "Hello"}
  (make-person nil) => (throws Exception))

(facts make-statement
  (fact "All good"
    (make-statement ["munid" "Author" runeberg friday
                     kalevala "lausunto" "Hello world!"])
    => (just {:municipal-id "munid"
              :statement    (just {:id        truthy
                                   :state     :given
                                   :requested (timestamp runeberg)
                                   :status    "lausunto"
                                   :text      "Hello world!"
                                   :dueDate   (timestamp friday)
                                   :given     (timestamp kalevala)
                                   :person    {:userId "batchrun-user"
                                               :email  "eraajo@lupapiste.fi"
                                               :name   "Author"}})}))
  (fact "Optionals missing. Given timestamp set to today"
    (make-statement ["munid" "Author" "" "" "" "puollettu" ""])
    => (just {:municipal-id "munid"
              :statement    (just {:id     truthy
                                   :state  :given
                                   :status "puollettu"
                                   :given  (timestamp (today))
                                   :person {:userId "batchrun-user"
                                            :email  "eraajo@lupapiste.fi"
                                            :name   "Author"}})}))
  (fact "Bad"
    (make-statement ["" "Author" runeberg friday
                     kalevala "lausunto" "Hello world!"])
    => (throws Exception)
    (make-statement ["munid" "Author" "bad date" friday
                     kalevala "lausunto" "Hello world!"])
    => (throws Exception)
    (make-statement ["munid" "Author" friday "bad date"
                     kalevala "lausunto" "Hello world!"])
    => (throws Exception)
    (make-statement ["munid" "Author" friday kalevala
                     "bad date" "lausunto" "Hello world!"])
    => (throws Exception)
    (make-statement ["munid" "Author" runeberg friday
                     kalevala "bad status" "Hello world!"])
    => (throws Exception)
    (make-statement ["munid" "" runeberg friday
                     kalevala "lausunto" "Hello world!"])
    => (throws Exception)))

(def statement (:statement (make-statement ["munid" "Author" runeberg friday
                                            kalevala "lausunto" "Hello world!"])))

(def batch-data (zipmap ["one" "two" "three"] (cycle [[statement]])))

(def query {:organization             ORG-ID
            :facta-imported           true
            :verdicts.kuntalupatunnus {$in ["one" "two" "three"]}})

(defn make-verdicts [& munids]
  {:verdicts (map (partial hash-map :kuntalupatunnus) munids)})

(facts filter-batch
  (fact "All good"
    (filter-batch batch-data) => batch-data
    (provided
      (mongo/select :applications query [:verdicts.kuntalupatunnus])
      => [(make-verdicts "three")
          (make-verdicts "foo" "one")
          (make-verdicts "two" "two" "hoo")]))
  (fact "One missing"
    (filter-batch batch-data) => (dissoc batch-data "one")
    (provided
      (mongo/select :applications query [:verdicts.kuntalupatunnus])
      => [(make-verdicts "three")
          (make-verdicts "foo")
          (make-verdicts "hii" "two" "hoo")]))
  (fact "The same kuntalupatunnus three in two applications"
    (filter-batch batch-data) => (dissoc batch-data "three")
    (provided
      (mongo/select :applications query [:verdicts.kuntalupatunnus])
      => [(make-verdicts "three")
          (make-verdicts "one")
          (make-verdicts "hii" "two" "hoo")
          (make-verdicts "three")]))
  (fact "All bad"
    (filter-batch batch-data) => {}
    (provided
      (mongo/select :applications query [:verdicts.kuntalupatunnus])
      => [(make-verdicts "three")
          (make-verdicts "one")
          (make-verdicts "hii" "hoo")
          (make-verdicts "three" "one")])))
