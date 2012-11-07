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

(facts "with separate doc and schema"
  (fact (apply-updates {} m {"1" {"1-1" "foo"}})
        => [{"1" {"1-1" "foo"}} [["1.1-1" "foo" true]]])
  (fact (apply-updates {} m {"1" {"xxx" "foo"}})
        => [{"1" {}} [["1.xxx" "foo" false "illegal-key"]]])
  (fact (apply-updates {} m {"1" "foo"})
        => [{} [["1" "foo" false "illegal-value:not-a-map"]]]))

(facts "with full document"
  (let [document {:body {} :schema m}]
    (fact (apply-updates document {"1" {"1-1" "foo"}})
          => [{"1" {"1-1" "foo"}} [["1.1-1" "foo" true]]])))

