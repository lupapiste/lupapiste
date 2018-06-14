(ns lupapalvelu.pate.pate-test
  (:require [clj-time.core :as time]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas  :as shared-schemas]
            [midje.sweet :refer :all]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]
            [schema.utils :refer [validation-error-explain]]))

(fact "only-one-of constraint"
  (let [err-msg (util/fn-> validation-error-explain second first)
        s "Only one of the keys is allowed: [:add :remove :click]"]
    (err-msg (sc/check shared-schemas/PateButton
                       {:click :foo :add :bar :remove :baz})) => s
    (err-msg (sc/check shared-schemas/PateButton
                       {:add :one :remove :two :click :three})) => s
    (err-msg (sc/check shared-schemas/PateButton
                       {:click :one :add :two})) => s
    (err-msg (sc/check shared-schemas/PateButton
                       {:add :one :remove :hii})) => s
    (err-msg (sc/check shared-schemas/PateButton
                       {:remove :foo :click :bar})) => s)
  (sc/check shared-schemas/PateButton {:click :foo}) => nil
  (sc/check shared-schemas/PateButton {:add :bar}) => nil
  (sc/check shared-schemas/PateButton {:remove :baz}) => nil
  (sc/check shared-schemas/PateButton {}) => nil)


(def test-template
  {:dictionary {:check       {:toggle {}}
                :delta       {:date-delta {:unit :years}}
                :phrase      {:phrase-text {:category :paatosteksti}}
                :multi       {:multi-select {:items [:foo :bar {:text  "Hello"
                                                                :value :world}]}}
                :string      {:text {}}
                :delta2      {:date-delta {:unit :days}}
                :ref-select  {:reference-list {:type :select
                                               :path [:path :to :somewhere]}}
                :ref-multi   {:reference-list {:type :multi-select
                                               :path [:path :to :somewhere]}}
                :ref-key     {:reference-list {:type     :select
                                               :path     [:my :path]
                                               :item-key :value}}
                :text        {:text {}}
                :giver       {:select {:items [:viranhaltija :lautakunta]}}
                :date        {:date {}}
                :complexity  helper/complexity-select
                :keymap      {:keymap {:one   "hello"
                                       :two   :world
                                       :three 88}}
                :placeholder {:placeholder {:type :neighbors}}
                :loop        {:repeating {:delta3     {:date-delta {:unit :years}}
                                          :date2      {:date {}}
                                          :inner-loop {:repeating {:date {:toggle {}}}}}}
                :dynamic     {:repeating {:text        {:text {}}
                                          :flag        {:toggle {}}
                                          :remove-item {:button {:remove :dynamic}}
                                          :sublevel    {:repeating {:tick       {:toggle {}}
                                                                    :remove-sub {:button {:remove :sublevel}}
                                                                    :remove-top {:button {:remove :dynamic}}}}
                                          :add-sub     {:button {:add :sublevel}}
                                          :bad-add     {:button {:add :dynamic}}}}
                :add-item    {:button {:add :dynamic}}
                :attachments {:application-attachments {}}
                :toggle      {:toggle {}}
                :select      {:select {:items [:one :two :three]}}
                :readonly    {:text {:read-only? true}}}
   :name       "test"
   :sections   [{:id   :one
                 :grid {:columns 4
                        :rows    [[{:col  2
                                    :dict :check}]
                                  {:id  "row"
                                   :row [{:col  2
                                          :dict :delta}
                                         {:dict :phrase}]}]}}
                {:id   :two
                 :grid {:columns 2
                        :rows    [[{}
                                   {:dict :multi}]
                                  {:id  "list-row"
                                   :row [{:list
                                          {:items [{:dict :string}
                                                   {:dict :delta2}
                                                   {:dict :ref}]}}]}]}}
                {:id   :three
                 :grid {:columns 5
                        :rows    [{:id  "docgen"
                                   :row [{:dict :text}
                                         {:dict :giver}
                                         {:dict :radio}
                                         {:dict :date}
                                         {:dict :complexity}]}]}}
                {:id   :repeat
                 :grid {:columns 1
                        :rows    [[{:grid {:columns   3
                                           :repeating :loop
                                           :rows      [[{:dict :delta3}
                                                        {:dict :date2}
                                                        {:grid {:columns   1
                                                                :repeating :inner-loop
                                                                :rows      [[{:dict :date}]]}}]]}}]]}}
                {:id   :dynamic
                 :grid {:columns 1
                        :rows    [[{:grid {:columns   2
                                           :repeating :dynamic
                                           :rows      [[{:dict :text}
                                                        {:dict :remove-item}]]}}]
                                  [{:dict :add-item}]]}}
                {:id   :attachments
                 :grid {:columns 1
                        :rows    [[{:dict :attachments}]]}}]})

(def timestamp util/to-millis-from-local-date-string)

(facts "Test template is valid"
  (sc/validate shared-schemas/PateVerdictTemplate test-template)
  => test-template)

(defn validate-path-value [path value & [references]]
  (schemas/validate-path-value test-template
                               path
                               value
                               references))

(facts "Dictionary validation"
  (fact "Bad path"
    (validate-path-value [:foo :bar] 88)
    => :error.invalid-value-path)
  (facts "Toggle (was docgen checkbox)"
    (validate-path-value [:check] true) => nil
    (validate-path-value [:check] false) => nil
    (validate-path-value [:check] "bad") => :error.invalid-value
    (validate-path-value [:check :bad] true) => :error.invalid-value-path)
  (facts "Date delta"
    (validate-path-value [:delta] 0) => nil
    (validate-path-value [:delta] 2) => nil
    (validate-path-value [:delta] -2) => :error.invalid-value
    (validate-path-value [:delta :delta] 2) => :error.invalid-value-path)
  (facts "Phrase text"
    (validate-path-value [:phrase] "hello") => nil
    (validate-path-value [:phrase :bad] "hello") => :error.invalid-value-path
    (validate-path-value [:phrase] 888) => :error.invalid-value
    (validate-path-value [:phrase] nil) => :error.invalid-value)
  (facts "Multi-select"
    (validate-path-value [:multi] [:foo]) => nil
    (validate-path-value [:multi] ["foo"]) => nil
    (validate-path-value [:multi] []) => nil
    (validate-path-value [:multi] [:bar :foo]) => nil
    (validate-path-value [:multi :bad] [:foo]) => :error.invalid-value-path
    (validate-path-value [:multi :items] [:foo]) => :error.invalid-value-path
    (validate-path-value [:multi] [:foo :bad]) => :error.invalid-value
    (validate-path-value [:multi] [88]) => :error.invalid-value
    (validate-path-value [:multi] [:world]) => nil
    (validate-path-value [:multi] [:world "bar" :foo]) => nil)
  (facts "Text (was docgen string)"
    (validate-path-value [:string] "hello") => nil
    (validate-path-value ["string"] "hello") => nil
    (validate-path-value [:string :hii] "hello") => :error.invalid-value-path
    (validate-path-value [:string] nil) => :error.invalid-value
    (validate-path-value [:string] "") => nil
    (validate-path-value [:string] 88) => :error.invalid-value)
  (facts "Reference list"
    (let [refs {:path {:to {:somewhere [:one :two :three]}}}]
      (fact "Empty selection"
        (validate-path-value [:ref-select] [""] refs) => nil)
      (validate-path-value [:ref-select] [:one] refs) => nil
      (validate-path-value [:ref-select] :one refs) => nil
      (validate-path-value [:ref-select] ["one"] refs) => nil
      (validate-path-value [:ref-select] [:bad] refs) => :error.invalid-value
      (validate-path-value [:ref-select] :bad refs) => :error.invalid-value
      (validate-path-value [:ref-select :bad] [] refs)
      => :error.invalid-value-path
      (validate-path-value [:ref-select] [:two :three] refs)
      => :error.invalid-value
      (validate-path-value [:ref-select] [] refs) => nil
      (validate-path-value [:ref-select] nil refs) => nil
      (validate-path-value [:ref-multi] [:two :three] refs) => nil
      (validate-path-value [:ref-multi] [:two "three"] refs) => nil
      (validate-path-value [:ref-multi] [:two :three :bad] refs)
      => :error.invalid-value
      (validate-path-value [:ref-multi] [] refs) => nil
      (validate-path-value [:ref-multi] nil refs) => nil
      (validate-path-value [:ref-multi] "one" refs)
      => :error.invalid-value))
  (facts "Reference list with item-key"
    (let [refs {:my {:path [{:name "One" :value :one}
                            {:name "Two" :value :two}
                            {:name "Three" :value :three}
                            {:name "Four" :value :four}]}}]
      (validate-path-value [:ref-key] [:one] refs) => nil
      (validate-path-value [:ref-key] [:bad] refs) => :error.invalid-value
      (validate-path-value [:ref-key] [:two :three] refs)
      => :error.invalid-value
      (validate-path-value [:ref-key] [] refs) => nil
      (validate-path-value [:ref-key] nil refs) => nil))
  (facts "Select (was docgen select)"
    (validate-path-value [:giver :bad] "viranhaltija")
    => :error.invalid-value-path
    (validate-path-value [:giver] "viranhaltija") => nil
    (validate-path-value [:giver] :viranhaltija) => nil
    (validate-path-value [:giver] :lautakunta) => nil
    (validate-path-value [:giver] :bad) => :error.invalid-value
    (validate-path-value [:giver] 88) => :error.invalid-value
    ;; Empty selection
    (validate-path-value [:giver] nil) => nil
    (validate-path-value [:giver] "") => nil)
  (facts "Date"
    (validate-path-value [:date] "13.9.2017") => :error.invalid-value
    (validate-path-value [:date] (timestamp "13.9.2017")) => nil
    (validate-path-value [:date] "bad") => :error.invalid-value
    (validate-path-value [:date] "") => nil
    (validate-path-value [:date] nil) => nil)
  (facts "KeyMap"
    (validate-path-value [:keymap] :hii) => :error.invalid-value-path
    (validate-path-value [:keymap :one] :hii) => nil
    (validate-path-value [:keymap :one :extra] :hii)
    => :error.invalid-value-path
    (validate-path-value [:keymap :bad] 33 ) => :error.invalid-value-path
    (validate-path-value [:keymap :two] 33 ) => nil)
  (facts "Placeholder: always fails"
    (validate-path-value [:placeholder] :hii) => :error.invalid-value-path
    (validate-path-value [:placeholder :type] :neighbors)
    => :error.invalid-value-path)
  (facts "Repeating"
    (validate-path-value [:loop] :hii) => :error.invalid-value-path
    (validate-path-value [:loop :some-index :delta3] 8)
    => nil
    (validate-path-value [:loop :date2] (timestamp "25.9.2017"))
    => :error.invalid-value-path
    (validate-path-value [:loop :i :date2] (timestamp "25.9.2017"))
    => nil
    (validate-path-value [:date2] (timestamp "25.9.2017"))
    => :error.invalid-value-path
    (validate-path-value [:loop :innerloop] :foo)
    => :error.invalid-value-path
    (validate-path-value [:loop :i :innerloop] :foo)
    => :error.invalid-value-path
    (validate-path-value [:loop :i :innerloop :j] :foo)
    => :error.invalid-value-path
    ;; Dict :date refers to docgen checkbox within inner-loop.
    (validate-path-value [:loop :i :inner-loop :j :date] (timestamp "25.9.2017"))
    => :error.invalid-value
    (validate-path-value [:loop :i :inner-loop :j :date] false)
    => nil
    ;; Top-level date is still docgen date.
    (validate-path-value [:date] false)
    => :error.invalid-value
    (validate-path-value [:date] (timestamp "25.9.2017"))
    => nil)
  (facts "Dynamic repeating"
    (validate-path-value [:add-item] true)
    => nil
    (validate-path-value [:add-item :bad] true)
    => :error.invalid-value-path)
  (facts "Application attachments"
    (validate-path-value [:attachments] nil) => nil
    (validate-path-value [:attachments] []) => nil
    (validate-path-value [:attachments] 8)
    => :error.invalid-value
    (validate-path-value [:attachments] [8])
    => :error.invalid-value
    (validate-path-value [:attachments] "hello")
    => :error.invalid-value
    (validate-path-value [:attachments] ["hello"]) => nil
    (validate-path-value [:attachments] ["" "foo" "bar"]) => nil)
  (facts "Toggle"
    (validate-path-value [:toggle] nil) => :error.invalid-value
    (validate-path-value [:toggle] "foo") => :error.invalid-value
    (validate-path-value [:toggle] true) => nil
    (validate-path-value [:toggle] false) => nil)
  (facts "Text"
    (validate-path-value [:text] nil) => :error.invalid-value
    (validate-path-value [:text] true) => :error.invalid-value
    (validate-path-value [:text] "") => nil
    (validate-path-value [:text] "hello") => nil)
  (fact "Readonly"
    (validate-path-value [:readonly] "hi") => :error.read-only))

(defn validate-and-process-value [path value old-data & [references]]
  (schemas/validate-and-process-value test-template
                                      path
                                      value
                                      old-data
                                      references))
(defn err
  ([path error]
   {:errors [[path error]]})
  ([failure]
   {:failure failure}))

(defn ok [path value & [m]]
  (let [path (map keyword path)]
    {:op   :set
     :value value
     :path  path
     :data  (assoc-in m path value)}))

(defn ok-add [path value & [m]]
  (let [path (map keyword path)]
    {:op    :add
     :value value
     :path  path
     :data  (assoc-in m path value)}))

(defn ok-remove [path m]
  (let [path (map keyword path)]
    {:op    :remove
     :value true
     :path  path
     :data  (util/dissoc-in m path)}))

(facts "Value validation and processing"
  (fact "Bad path"
    (validate-and-process-value [:foo :bar] 88 {})
    => (err :error.invalid-value-path))
  (facts "Toggle (was docgen checkbox)"
    (validate-and-process-value [:check] true {}) => (ok [:check] true)
    (validate-and-process-value ["check"] false {:foo 8})
    => (ok [:check] false {:foo 8})
    (validate-and-process-value ["check"] "bad" {})
    => (err [:check] :error.invalid-value)
    (validate-and-process-value [:check :bad] true {})
    => (err :error.invalid-value-path))
  (facts "Date delta"
    (validate-and-process-value [:delta] 0 {})
    => (ok [:delta] 0)
    (validate-and-process-value [:delta] 2 {})
    => (ok [:delta] 2)
    (validate-and-process-value [:delta] -2 {})
    => (err [:delta] :error.invalid-value)
    (validate-and-process-value [:delta :delta] 2 {})
    => (err :error.invalid-value-path))
  (facts "Phrase text"
    (validate-and-process-value [:phrase] "hello" {:hi "moi"})
    => (ok [:phrase] "hello" {:hi "moi"})
    (validate-and-process-value [:phrase :bad] "hello" {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:phrase] 888 {}) =>
    (err [:phrase] :error.invalid-value)
    (validate-and-process-value [:phrase] nil {})
    => (err [:phrase] :error.invalid-value))
  (facts "Multi-select"
    (validate-and-process-value [:multi] [:foo] {})
    => (ok [:multi] [:foo])
    (validate-and-process-value [:multi] ["foo"] {})
    => (ok [:multi] ["foo"])
    (validate-and-process-value [:multi] [] {})
    => (ok [:multi] [])
    (validate-and-process-value [:multi] [:bar :foo] {})
    => (ok [:multi] [:bar :foo])
    (validate-and-process-value [:multi :bad] [:foo] {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:multi :items] [:foo] {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:multi] [:foo :bad] {})
    => (err [:multi] :error.invalid-value)
    (validate-and-process-value [:multi] [88] {})
    => (err [:multi] :error.invalid-value)
    (validate-and-process-value [:multi] [:world] {})
    => (ok [:multi] [:world])
    (validate-and-process-value [:multi] [:world "bar" :foo] {:dum "dom"})
    => (ok [:multi] [:world "bar" :foo] {:dum "dom"}))
  (facts "Text (was docgen string)"
    (validate-and-process-value [:string] "hello" {})
    => (ok [:string] "hello")
    (validate-and-process-value ["string"] "hello" {})
    => (ok [:string] "hello")
    (validate-and-process-value [:string :hii] "hello" {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:string] nil {})
    => (err [:string] :error.invalid-value)
    (validate-and-process-value [:string] "" {})
    => (ok [:string] "")
    (validate-and-process-value [:string] 88 {})
    => (err [:string ]:error.invalid-value))
  (facts "Reference list"
    (let [refs {:path {:to {:somewhere [:one :two :three]}}}]
      (fact "Empty selection"
        (validate-and-process-value [:ref-select] [""] {} refs)
        => (ok [:ref-select] [""]))
      (validate-and-process-value [:ref-select] [:one] {} refs)
      => (ok [:ref-select] [:one])
      (validate-and-process-value [:ref-select] :one {} refs)
      => (ok [:ref-select] :one)
      (validate-and-process-value [:ref-select] ["one"] {} refs)
      => (ok [:ref-select] ["one"])
      (validate-and-process-value [:ref-select] [:bad] {} refs)
      => (err [:ref-select] :error.invalid-value)
      (validate-and-process-value [:ref-select] :bad {} refs)
      => (err [:ref-select] :error.invalid-value)
      (validate-and-process-value [:ref-select :bad] [] {} refs)
      => (err :error.invalid-value-path)
      (validate-and-process-value [:ref-select] [:two :three] {} refs)
      => (err [:ref-select] :error.invalid-value)
      (validate-and-process-value [:ref-select] [] {} refs)
      => (ok [:ref-select] [])
      (validate-and-process-value [:ref-select] nil {} refs)
      => (ok [:ref-select] nil)
      (validate-and-process-value [:ref-multi] [:two :three] {} refs)
      => (ok [:ref-multi] [:two :three])
      (validate-and-process-value [:ref-multi] [:two "three"] {} refs)
      => (ok [:ref-multi] [:two "three"])
      (validate-and-process-value [:ref-multi] [:two :three :bad] {} refs)
      => (err [:ref-multi] :error.invalid-value)
      (validate-and-process-value [:ref-multi] [] {} refs)
      => (ok [:ref-multi] [])
      (validate-and-process-value [:ref-multi] nil {} refs)
      => (ok [:ref-multi] nil)
      (validate-and-process-value [:ref-multi] "one" {} refs)
      => (err [:ref-multi] :error.invalid-value)))
  (facts "Reference list with item-key"
    (let [refs {:my {:path [{:name "One" :value :one}
                            {:name "Two" :value :two}
                            {:name "Three" :value :three}
                            {:name "Four" :value :four}]}}]
      (validate-and-process-value [:ref-key] [:one] {} refs)
      => (ok [:ref-key] [:one])
      (validate-and-process-value [:ref-key] [:bad] {} refs)
      => (err [:ref-key] :error.invalid-value)
      (validate-and-process-value [:ref-key] [:two :three] {} refs)
      => (err [:ref-key] :error.invalid-value)
      (validate-and-process-value [:ref-key] [] {} refs)
      => (ok [:ref-key] [])
      (validate-and-process-value [:ref-key] nil {} refs)
      => (ok [:ref-key] nil)))
  (facts "Select (was docgen select)"
    (validate-and-process-value [:giver :bad] "viranhaltija" {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:giver] "viranhaltija" {})
    => (ok [:giver] "viranhaltija")
    (validate-and-process-value [:giver] :viranhaltija {})
    => (ok [:giver] :viranhaltija)
    (validate-and-process-value [:giver] :lautakunta {})
    => (ok [:giver] :lautakunta)
    (validate-and-process-value [:giver] :bad {})
    => (err [:giver] :error.invalid-value)
    (validate-and-process-value [:giver] 88 {})
    => (err [:giver] :error.invalid-value)
    ;; Empty selection
    (validate-and-process-value [:giver] nil {:foo "bar"})
    => (ok [:giver] nil {:foo "bar"})
    (validate-and-process-value [:giver] "" {})
    => (ok [:giver] ""))
  (facts "Date"
    (validate-and-process-value [:date] (timestamp "13.9.2017") {})
    => (ok [:date] (timestamp "13.9.2017"))
    (validate-and-process-value [:date] (timestamp "13.09.2017") {})
    => (ok [:date] (timestamp "13.09.2017"))
    (validate-and-process-value [:date] (timestamp "03.9.2017") {})
    => (ok [:date] (timestamp "03.9.2017"))
    (validate-and-process-value [:date] (timestamp "03.09.2017") {})
    => (ok [:date] (timestamp "03.09.2017"))
    (validate-and-process-value [:date] "bad" {})
    => (err [:date] :error.invalid-value)
    (validate-and-process-value [:date] "bad" {})
    => (err [:date] :error.invalid-value)
    (validate-and-process-value [:date] "" {})
    => (ok [:date] "")
    (validate-and-process-value [:date] nil {})
    => (ok [:date] nil))
  (facts "KeyMap"
    (validate-and-process-value [:keymap] :hii {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:keymap :one] :hii {})
    => (ok [:keymap :one] :hii)
    (validate-and-process-value [:keymap :one :extra] :hii {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:keymap :bad] 33 {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:keymap :two] 33 {})
    => (ok [:keymap :two] 33))
  (facts "Placeholder: always fails"
    (validate-and-process-value [:placeholder] :hii {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:placeholder :type] :neighbors {})
    => (err :error.invalid-value-path))
  (facts "Repeating"
    (validate-and-process-value [:loop] :hii {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :some-index :delta3]
                                8 {:loop {:some-index {}}})
    => (ok [:loop :some-index :delta3] 8)
    (validate-and-process-value [:loop :some-index :delta3]
                                8 {:loop {:some-index {:delta3 {}}}})
    => (ok [:loop :some-index :delta3] 8)
    (validate-and-process-value [:loop :some-index :delta3]
                                8 {:loop {:some-index {:delta3 1}}})
    => (ok [:loop :some-index :delta3] 8)
    (validate-and-process-value [:loop :some-index :delta3]
                                8 {:loop {}})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :some-index :delta3]
                                8 {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :date2] (timestamp "25.9.2017") {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :i :date2] (timestamp "25.9.2017") {:loop {:i {}}})
    => (ok [:loop :i :date2] (timestamp "25.9.2017"))
    (validate-and-process-value [:date2] (timestamp "25.9.2017") {})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :inner-loop] :foo {:loop {:inner-loop {}}})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :i :inner-loop] :foo (:loop {:i {:inner-loop {}}}))
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :i :inner-loop :j] :foo
                                {:loop {:i {:inner-loop {:j {}}}}})
    => (err :error.invalid-value-path)
    ;; Dict :date refers to docgen checkbox within inner-loop.
    (validate-and-process-value [:loop :i :inner-loop :j :date] (timestamp "25.9.2017")
                                {:loop {:i {:inner-loop {:j {}}}}})
    => (err [:loop :i :inner-loop :j :date] :error.invalid-value)
    (validate-and-process-value [:loop :i :inner-loop :j :date] true
                                {:loop {:i {:inner-loop {:z {}}}}})
    => (err :error.invalid-value-path)
    (validate-and-process-value [:loop :i :inner-loop :j :date] false
                                {:loop {:i {:inner-loop {:j {}}}}})
    => (ok [:loop :i :inner-loop :j :date] false)
    ;; Top-level date is still docgen date.
    (validate-and-process-value [:date] false {})
    => (err [:date] :error.invalid-value)
    (validate-and-process-value [:date] (timestamp "25.9.2017") {})
    => (ok [:date] (timestamp "25.9.2017")))
  (facts "Dynamic repeating"
    (facts "Subpaths"
      (schema-util/repeating-subpath :dynamic [:dynamic] (:dictionary test-template))
      => [:dynamic]
      (schema-util/repeating-subpath :dynamic-foo [:dynamic] (:dictionary test-template))
      => nil?
      (schema-util/repeating-subpath :dynamic [:dynamic :id :dynamic] (:dictionary test-template))
      => nil?)
    (facts "Top level"
      (validate-and-process-value [:add-item] true {:dynamic {}})
      => (ok-add [:dynamic :new-id] {} {})
      (provided (mongo/create-id) => "new-id")
      (validate-and-process-value [:add-item] true {})
      => (ok-add [:dynamic :new-id] {} {})
      (provided (mongo/create-id) => "new-id")
      (validate-and-process-value [:dynamic :old-id :remove-item]
                                  true
                                  {:dynamic {:id     {:foo 8}
                                             :old-id {:bar 99}}})
      => (ok-remove [:dynamic :old-id] {:dynamic {:id     {:foo 8}
                                                  :old-id {:bar 99}}})
      (validate-and-process-value [:dynamic :id :flag]
                                  true
                                  {:dynamic {:id {}}})
      => (ok [:dynamic :id :flag] true))
    (facts "Sub level"
      (fact "Add button must be sibling"
        (validate-and-process-value [:dynamic :id :bad-add]
                                    true
                                    {:dynamic {:id {}}})
        => (err :error.invalid-value-path)
        (validate-and-process-value [:dynamic :id :add-sub]
                                    true
                                    {:dynamic {:id {}}})
        => (ok-add [:dynamic :id :sublevel :new-id] {} {})
        (provided (mongo/create-id) => "new-id"))
      (fact "Remove sublevel item"
        (validate-and-process-value [:dynamic :id :sublevel :foo :remove-sub]
                                    true
                                    {:dynamic {:id {:sublevel {:foo {:tick false}
                                                               :bar {:tick true}}}}})
        => (ok-remove [:dynamic :id :sublevel :foo]
                      {:dynamic {:id {:sublevel {:foo {:tick false}
                                                 :bar {:tick true}}}}}))
      (fact "Remove toplevel item from sublevel"
        (validate-and-process-value [:dynamic :id :sublevel :foo :remove-top]
                                    true
                                    {:dynamic {:id {:sublevel {:foo {:tick false}
                                                               :bar {:tick true}}}
                                               :doh {:hi "hello"}}})
        => (ok-remove [:dynamic :id]
                      {:dynamic {:id {:sublevel {:foo {:tick false}
                                                 :bar {:tick true}}}
                                 :doh {:hi "hello"}}}))
      (fact "Add sublevel item"
        (validate-and-process-value [:dynamic :id :add-sub] true {:dynamic {:id {}}})
        => (ok-add [:dynamic :id :sublevel :new-id] {})
        (provided (mongo/create-id) => "new-id")
        (validate-and-process-value [:dynamic :id :add-sub] true
                                    {:dynamic {:id {:sublevel {:old {:tick false}}}}})
        => (ok-add [:dynamic :id :sublevel :new-id] {}
               {:dynamic {:id {:sublevel {:old {:tick false}}}}})
        (provided (mongo/create-id) => "new-id"))
      (fact "Cannot skip level"
        (validate-and-process-value [:dynamic :id :add-sub] true {})
        => (err :error.invalid-value-path)
        (validate-and-process-value [:dynamic :id :add-sub] true {:dynamic {}})
        => (err :error.invalid-value-path)))))

(fact "parse-int"
  (schemas/parse-int 88) => 88
  (schemas/parse-int "88") => 88
  (schemas/parse-int "  88  ") => 88
  (schemas/parse-int " -88 ") => -88
  (schemas/parse-int "  ") => 0
  (schemas/parse-int 4.5) => nil
  (schemas/parse-int "foo") => nil
  (schemas/parse-int false) => nil
  (schemas/parse-int true) => nil
  (schemas/parse-int :yeah) => nil)

(fact "application->category"
  (schema-util/application->category {:permitType "R"}) => :r
  (schema-util/application->category {:permitType "R"
                                      :permitSubtype "foobar"})=> :r
  (schema-util/application->category {:permitType "R"
                                      :permitSubtype "tyonjohtaja-hakemus"})
  => :tj
  (schema-util/application->category {:permitType "YA"
                                      :permitSubtype "tyonjohtaja-hakemus"})
  => :tj
  (schema-util/application->category {:permitType "YA"}) => :ya
  (schema-util/application->category {:permitType "YA"
                                      :permitSubtype "foobar"})=> :ya
  (schema-util/application->category {:permitType "YA"
                                      :permitSubtype "sijoitussopimus"})
  => :contract
  (schema-util/application->category {:permitType "R"
                                      :permitSubtype "sijoitussopimus"})
  => :contract)
