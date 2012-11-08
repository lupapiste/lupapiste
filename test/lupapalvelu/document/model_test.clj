(ns lupapalvelu.document.model-test
  (:use [lupapalvelu.document.model]
        [lupapalvelu.document.schemas]
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

(def schema
  (make-model "test-model" 1
    (make-group "1"
      (make-string "1-1")
      (make-string "1-2" :min-len 2 :max-len 3)
      (make-group "2"
        (make-string "2-1" :min-len 2)
        (make-boolean "2-2")))))

(facts "with separate doc and schema"
  (fact (apply-updates {} schema {"1" {"1-1" "foo"}})
        => [{"1" {"1-1" "foo"}} [["1.1-1" "foo" true]]])
  (fact (apply-updates {} schema {"1" {"xxx" "foo"}})
        => [{"1" {}} [["1.xxx" "foo" false "illegal-key"]]])
  (fact (apply-updates {} schema {"1" "foo"})
        => [{} [["1" "foo" false "illegal-value:not-a-map"]]]))

(facts "with full document"
  (let [document {:body {} :schema schema}]
    (fact (apply-updates document {"1" {"1-1" "foo"}})
          => [{"1" {"1-1" "foo"}} [["1.1-1" "foo" true]]])))

(facts "with real schemas - important field for paasuunnittelija"
   (let [document {:body {} :schema (schemas "paasuunnittelija")}]
     (fact (apply-updates document {"etunimiz" "Tauno"}) =>     [{} [["etunimiz" "Tauno" false "illegal-key"]]])
     (fact (apply-updates document {"etunimi" "Tauno"}) =>      [{"etunimi" "Tauno"} [["etunimi" "Tauno" true]]])
     (fact (apply-updates document {"sukunimi" "Palo"}) =>      [{"sukunimi" "Palo"} [["sukunimi" "Palo" true]]])
     (fact (apply-updates document {"email" "tauno@iki.fi"}) => [{"email" "tauno@iki.fi"} [["email" "tauno@iki.fi" true]]])
     (fact (apply-updates document {"puhelin" "050"}) =>        [{"puhelin" "050"} [["puhelin" "050" true]]])))
