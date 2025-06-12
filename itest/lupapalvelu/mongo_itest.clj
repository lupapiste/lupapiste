(ns lupapalvelu.mongo-itest
  (:require [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [sade.util :as util]))

(testable-privates lupapalvelu.mongo
                   define-index define-indexes)

(def test-index "heatseeker")

(defn index [index-name]
  (util/find-by-key :name index-name
                    (mc/indexes-on (#'lupapalvelu.mongo/get-db)
                                   "applications")))

(with-test-system {:fixture "minimal"}
  (facts "Indexes"
    (fact "No test index"
      (index test-index) => nil)
    (fact "define-index"
      (define-index test-index :applications {:fields {:foo 1}})
      (index test-index)
      => (contains {:name test-index
                    :key  {:foo 1}}))
    (fact "Index cannot be changed"
      (define-index test-index :applications {:fields  {:bar 1}
                                              :options {:sparse true}})
      => (throws))
    (fact "New index version overrides the old one"
      (define-index test-index :applications {:version 2
                                              :fields  {:bar 1}
                                              :options {:sparse true}})
      (index test-index) => nil
      (index (str test-index "_v2"))
      => (contains {:key    {:bar 1}
                    :sparse true}))
    (fact "Index can be dropped"
      (define-index test-index :applications {:version  2
                                              :dropped? true
                                              :fields   {:bar 1}
                                              :options  {:sparse true}})
      (index (str test-index "_v2")) => nil)
    (facts "define-indexes"
      (define-indexes :applications {:one {:fields  {:hii 1 :hoo -1}
                                           :options {:unique true}}
                                     :two {:fields            {:hello 1 :world 1}
                                           :filter-expression {:world {:$exists true}}}})
      (index "one") => (contains {:key    {:hii 1 :hoo -1}
                                  :unique true})
      (index "two") => (contains {:key                     {:hello 1 :world 1}
                                  :partialFilterExpression {:world {:$exists true}}})
      (define-indexes :applications {:one {:fields   {:hii 1 :hoo -1}
                                           :dropped? true
                                           :options  {:unique true}}
                                     :two {:fields  {:hello 1 :world -1}
                                           :version 10}})
      (index "one") => nil
      (index "two") => nil
      (select-keys (index "two_v10") [:name :key :partialFilterExpression])
      => {:name "two_v10" :key {:hello 1 :world -1}})))
