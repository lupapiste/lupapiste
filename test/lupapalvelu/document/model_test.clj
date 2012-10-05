(ns lupapalvelu.document.model-test
  (:use [lupapalvelu.document.model]
        [midje.sweet]))

;; DSL tests

(facts "creating groups"
  (fact (make-group "foo") => (contains {:type :group :name "foo" :body []}))
  (fact (make-group "foo" 1 2 3) => (contains {:type :group :name "foo" :body [1 2 3]})))

(facts "adding meta-data"
  (fact (make-string "foo") => (contains {:type :string :name "foo" :min-len 0 :max-len 32}))
  (fact (make-string "foo" :min-len 5) => (contains {:type :string :name "foo" :min-len 5 :max-len 32}))
  (fact (make-string "foo" :min-len 5 :foo "bar") => (contains {:type :string :name "foo" :min-len 5 :max-len 32 :foo "bar"})))

;; Validation tests:

(def m
  (make-model "test-model" 1
    (make-group "1"
      (make-string "1-1")
      (make-string "1-2" :min-len 2 :max-len 3)
      (make-group "2"
        (make-string "2-1" :min-len 2)
        (make-boolean "2-2")))))

(facts
  (fact (apply-updates {} m {"1" {"1-1" "foo"}})
        => [{"1" {"1-1" "foo"}} [["1.1-1" "foo" true]]])
  (fact (apply-updates {} m {"1" {"xxx" "foo"}})
        => [{"1" {}} [["1.xxx" "foo" false "illegal-key"]]])
  (fact (apply-updates {} m {"1" "foo"})
        => [{} [["1" "foo" false "illegal-value:not-a-map"]]]))

(comment
  (def m
    (make-model "test-model" 1
      (make-string "1-1")
      (make-string "1-2" :min-len 2 :max-len 3)
      (make-group "2"
        (make-string "2-1" :min-len 4 :max-len 4)
        (make-string "2-2"))))

  (let [[doc updates] (apply-updates {} m {"1-1" "Foo" "1-2" "xxx" "1-3" "yyy" "2" {"2-1" "qwert" "2-2" "xxx"}})]
    (println "DOC:")
    (clojure.pprint/pprint doc)
    (println "UPDATES:")
    (clojure.pprint/pprint updates)))
