(ns lupapalvelu.matti-test
  (:require [clj-time.core :as time]
            [lupapalvelu.matti.date :as date]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared  :as shared]
            [midje.sweet :refer :all]
            [schema.core :refer [defschema] :as sc]))

(def test-template
  {:dictionary {:check      {:docgen "matti-verdict-check"}
                :delta      {:date-delta {:unit :years}}
                :phrase     {:phrase-text {:category :paatosteksti}}
                :multi      {:multi-select {:items [:foo :bar {:text  "Hello"
                                                               :value :world}]}}
                :string     {:docgen "matti-string"}
                :delta2     {:date-delta {:unit :days}}
                :ref-select {:reference-list {:type :select
                                              :path [:path :to :somewhere]}}
                :ref-multi  {:reference-list {:type :multi-select
                                              :path [:path :to :somewhere]}}
                :ref-key     {:reference-list {:type     :select
                                               :path     [:my :path]
                                               :item-key :value}}
                :text       {:docgen "matti-verdict-text"}
                :giver      {:docgen "matti-verdict-giver"}
                :radio      {:docgen "automatic-vs-manual"}
                :date       {:docgen "matti-date"}
                :complexity {:docgen {:name "matti-complexity"}}
                :keymap     {:keymap {:one   "hello"
                                      :two   :world
                                      :three 88}}
                :placeholder {:placeholder {:type :neighbors}}}
   :name       "test"
   :sections   [{:id   "one"
                 :grid {:columns 4
                        :rows    [[{:col  2
                                    :dict :check}]
                                  {:id  "row"
                                   :row [{:col  2
                                          :dict :delta}
                                         {:dict :phrase}]}]}}
                {:id   "two"
                 :grid {:columns 2
                        :rows    [[{}
                                   {:dict :multi}]
                                  {:id  "list-row"
                                   :row [{:list
                                          {:items [{:dict :string}
                                                   {:dict :delta2}
                                                   {:dict :ref}]}}]}]}}
                {:id   "three"
                 :grid {:columns 5
                        :rows    [{:id  "docgen"
                                   :row [{:dict :text}
                                         {:dict :giver}
                                         {:dict :radio}
                                         {:dict :date}
                                         {:dict :complexity}]}]}}]})

(facts "Test template is valid"
  (sc/validate shared/MattiVerdict test-template)
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
  (facts "Docgen: checkbox"
    (validate-path-value [:check] true) => nil
    (validate-path-value [:check] false) => nil
    (validate-path-value [:check] "bad") => :error.invalid-value
    (validate-path-value [:check :bad] true) => :error.invalid-value-path)
  (facts "Date delta"
    (validate-path-value [:delta] {:enabled true})
    => :error.invalid-value-path
    (validate-path-value [:delta :enabled] true) => nil
    (validate-path-value [:delta :enabled] "bad") => :error.invalid-value
    (validate-path-value [:delta :enabled] nil) => :error.invalid-value
    (validate-path-value [:delta :delta] 0) => nil
    (validate-path-value [:delta :delta] 2) => nil
    (validate-path-value [:delta :delta] -2) => :error.invalid-value)
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
  (facts "Docgen: string"
    (validate-path-value [:string] "hello") => nil
    (validate-path-value ["string"] "hello") => nil
    (validate-path-value [:string :hii] "hello") => :error.invalid-value-path
    (validate-path-value [:string] nil) => :error.invalid-value
    (validate-path-value [:string] "") => nil
    (validate-path-value [:string] 88) => :error.invalid-value)
  (facts "Reference list"
    (let [refs {:path {:to {:somewhere [:one :two :three]}}}]
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
      (validate-path-value [:ref-multi] nil refs) => nil))
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
  (facts "Docgen: select"
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
  (facts "Docgen: radioGroup"
    (validate-path-value [:radio] :automatic) => nil
    (validate-path-value [:radio] "manual") => nil
    (validate-path-value [:radio] :bad) => :error.invalid-value
    (validate-path-value [:radio :bad] :automatic) => :error.invalid-value-path
    (validate-path-value [:radio] nil) => :error.invalid-value)
  (facts "Date"
    (validate-path-value [:date] "13.9.2017") => nil
    (validate-path-value [:date] "13.09.2017") => nil
    (validate-path-value [:date] "03.9.2017") => nil
    (validate-path-value [:date] "03.09.2017") => nil
    (validate-path-value [:date] "31.9.2017") => :error.invalid-value
    (validate-path-value [:date] "bad") => :error.invalid-value
    (validate-path-value [:date] "") => nil
    (validate-path-value [:date] nil) => nil)
  (facts "Docgen: select defined with map"
    (validate-path-value [:complexity] :medium) => nil
    (validate-path-value [:complexity] "large") => nil
    (validate-path-value [:complexity] nil) => nil
    (validate-path-value [:complexity] "") => nil
    (validate-path-value [:complexity] "bad") => :error.invalid-value
    (validate-path-value [:complexity :bad] :extra-large)
    => :error.invalid-value-path)
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
    => :error.invalid-value-path))

(defn work-day
  ([id from]
   (work-day id from from))
  ([id from to]
   (fact {:midje/description (format "%s next work day: %s -> %s" id from to)}
     (date/forward-to-work-day (date/parse-finnish-date from))
     => (date/parse-finnish-date to))))

(defn holiday [id s result]
  (fact {:midje/description (format "%s %s -> %s" id s result)}
    (date/holiday? (date/parse-finnish-date s)) => result))

(facts "Date parsing"
  (date/parse-finnish-date "1.2.2017")
  => (time/local-date 2017 2 1)
  (date/parse-finnish-date "01.2.2017")
  => (time/local-date 2017 2 1)
  (date/parse-finnish-date "1.02.2017")
  => (time/local-date 2017 2 1)
  (date/parse-finnish-date "01.02.2017")
  => (time/local-date 2017 2 1)
  (date/parse-finnish-date "88.77.2017")
  => nil)

(facts "Date unparsing"
  (date/finnish-date (time/local-date 2017 2 1))
  => "1.2.2017")

(facts "Holidays"
  (holiday "New Year" "1.1.2018" true)
  (holiday "Epiphany" "6.1.2018" true)
  (holiday "Good Friday" "14.4.2017" true)
  (holiday "Good Friday" "30.3.2018" true)
  (holiday "Easter Monday" "17.4.2017" true)
  (holiday "Easter Monday" "2.04.2018" true)
  (holiday "May Day" "01.05.2017" true)
  (holiday "Ascension Day" "25.5.2017" true)
  (holiday "Ascension Day" "10.5.2018" true)
  (holiday "Midsummer eve" "23.6.2017" true)
  (holiday "Midsummer eve" "22.6.2018" true)
  (holiday "Independence Day" "06.12.2018" true)
  (holiday "Christmas eve" "24.12.2017" true)
  (holiday "Christmas" "25.12.2017" true)
  (holiday "Boxing Day" "26.12.2017" true)
  (holiday "Regular Monday" "21.08.2017" false)
  (holiday "Saturday" "26.8.2017" false)
  (holiday "Sunday" "27.8.2017" false))

(facts "Next work days"
  (work-day "New Year" "1.1.2017" "2.1.2017")
  (work-day "Epiphany" "6.1.2017" "9.1.2017")
  (work-day "Good Friday" "14.4.2017" "18.4.2017")
  (work-day "Easter Monday" "17.4.2017" "18.4.2017")
  (work-day "May Day" "1.5.2017" "2.5.2017")
  (work-day "Ascension Day" "25.5.2017" "26.5.2017")
  (work-day "Midsummer eve" "23.6.2017" "26.6.2017")
  (work-day "Independence Day" "6.12.2017" "7.12.2017")
  (work-day "Christmas eve" "24.12.2017" "27.12.2017")
  (work-day "Christmas" "25.12.2017" "27.12.2017")
  (work-day "Boxing Day" "26.12.2017" "27.12.2017")
  (work-day "Regular Monday" "21.8.2017" "21.8.2017")
  (work-day "Saturday" "26.8.2017" "28.8.2017")
  (work-day "Sunday" "27.8.2017" "28.8.2017"))
