(ns lupapalvelu.pdf.html-templates.document-data-converter-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.pdf.html-templates.document-data-converter :refer :all]))

(testable-privates lupapalvelu.pdf.html-templates.document-data-converter
                   parse-i18nkey
                   get-in-schema-with-i18n-path
                   kw)

(facts kw
  (kw :foo :#bar) => :foo#bar
  (kw :foo) => :foo
  (kw :foo "." "quu") => :foo.quu
  (kw :foo 1) => :foo1
  (kw 1) => :1)

(facts parse-i18nkey
  (parse-i18nkey {:i18nkey "foo"}) => [:foo]
  (parse-i18nkey {:i18nkey "foo.bar"}) => [:foo :bar]
  (fact "nil key"
    (parse-i18nkey {:i18nkey nil}) => nil)
  (fact "no i18nkey"
    (parse-i18nkey {}) => nil))

(facts get-in-schema-with-i18n-path
  (fact "root element"
    (get-in-schema-with-i18n-path {:info {:name "docu"} :body [{:name "foo" :body [{:name "bar"}]}]} [])
    => {:info {:name "docu"}, :body [{:name "foo", :body [{:name "bar"}]}], :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:docu]})

  (fact "select first level element"
    (get-in-schema-with-i18n-path {:info {:name "docu"} :body [{:name "foo" :body [{:name "bar"}]}]} [:foo])
    => {:name "foo", :body [{:name "bar"}], :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:docu :foo]})

  (fact "select leaf element"
    (get-in-schema-with-i18n-path {:info {:name "docu"} :body [{:name "foo" :body [{:name "bar"} {:name "baz"}]} {:name "quu"}]} [:foo :bar])
    => {:name "bar", :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:docu :foo :bar]})

  (fact "select leaf element with i18nkey"
    (get-in-schema-with-i18n-path {:info {:name "docu"} :body [{:name "foo" :body [{:name "bar" :i18nkey "bizz.buzz"}]}]} [:foo :bar])
    => {:name "bar", :i18nkey "bizz.buzz", :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:bizz :buzz]})

  (fact "i18nkey in first level element"
    (get-in-schema-with-i18n-path {:info {:name "docu"} :body [{:name "foo" :i18nkey "bizz.buzz" :body [{:name "bar"}]}]} [:foo :bar])
    => {:name "bar", :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:bizz :buzz :bar]})

  (fact "doc i18name set"
    (get-in-schema-with-i18n-path {:info {:name "docu" :i18name "fuz"} :body [{:name "foo" :body [{:name "bar"}]}]} [:foo :bar])
    => {:name "bar", :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:fuz :foo :bar]})

  (fact "leaf i18nkey overrides doc i18name"
    (get-in-schema-with-i18n-path {:info {:name "docu" :i18name "fuz"} :body [{:name "foo" :i18nkey "bizz.buzz"  :body [{:name "bar"}]}]} [:foo :bar])
    => {:name "bar", :lupapalvelu.pdf.html-templates.document-data-converter/i18n-path [:bizz :buzz :bar]}))

(facts element-value
  (fact "string"
    (element-value {:foo {:value "asdfad"}, :bar {:value nil}} [:foo] [:docu :foo] {:name "foo", :type :string}) => "asdfad")

  (fact "string inside group"
    (element-value {:quu {:foo {:value "asdfad"}, :bar {:value nil}}} [:quu :foo] [:docu :quu :foo] {:name "foo", :type :string}) => "asdfad")

  (fact "no value from path"
    (element-value {:foo {:value "asdfad"}, :bar {:value nil}} [:mysterious :path] [:docu :quu :foo] {:name "foo", :type :string}) => nil)

  (fact "select inside group"
    (element-value {:quu {:foo {:value "hii"}, :bar {:value nil}}} [:quu :foo] [:docu :quu :foo] {:name "foo", :type :select}) => "asdfad"
    (provided (lupapalvelu.i18n/loc :docu :quu :foo "hii") => "asdfad"))

  (fact "select value with i18nkey"
    (element-value {:foo {:value "hii"}, :bar {:value nil}} [:foo] [:docu :foo] {:name "foo", :type :select :body [{:name "hii" :i18nkey "hiikey"}]}) => "asdfad"
    (provided (lupapalvelu.i18n/loc :hiikey) => "asdfad")))

(facts get-value-in
  (fact "text element - 3 arity"
    (get-value-in {:schema-info {:name "docu"}, :data {:foo {:value "hii"}}}
                  "fi"
                  [:foo]) => "hii"
    (provided (lupapalvelu.document.schemas/get-schema {:name "docu"})
              => {:info {:name "docu"}, :body [{:name "foo", :type :text}]}))


  (fact "text element - 4 arity"
    (get-value-in {:info {:name "docu", :i18name "docuname"}, :body [{:name "foo", :type :text} {:name "bar", :type :select, :body [{:name "A"} {:name "B"} {:name "C"}]}]}
                  {:foo {:value "hii"}, :bar {:value nil}}
                  "fi"
                  [:foo]) => "hii")

  (fact "text element inside group"
    (get-value-in {:info {:name "docu"}, :body [{:name "quu" :body [{:name "foo", :type :text}]}]}
                  {:quu {:foo {:value "hii"}}}
                  "fi"
                  [:quu :foo]) => "hii")

  (fact "select element"
    (get-value-in {:info {:name "docu"}, :body [{:name "foo", :type :text} {:name "bar", :type :select, :body [{:name "A"} {:name "B"} {:name "C"}]}]}
                  {:foo {:value "hii"}, :bar {:value "A"}}
                  "fi"
                  [:bar]) => "a-luokka"
    (provided (lupapalvelu.i18n/localize :fi :docu :bar "A") => "a-luokka"))

  (fact "select element with i18nkey"
    (get-value-in {:info {:name "docu"}, :body [{:name "bar", :i18nkey "bim.bom" :type :select, :body [{:name "A"} {:name "B"} {:name "C"}]}]}
                  {:bar {:value "A"}}
                  "fi"
                  [:bar]) => "a-luokka"
    (provided (lupapalvelu.i18n/localize :fi :bim :bom "A") => "a-luokka")))

(facts convert-element
  (fact "string"
    (convert-element {:foo {:value "fiz"}, :bar {:value "buz"}} [:foo] [:docu :foo] {:name "foo", :type :string})
    => [:span#foo.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz"]]
    (provided (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "select"
    (convert-element {:foo {:value "fiz"}}
                     [:foo]
                     [:docu :foo]
                     {:name "foo" :type :select
                      :body [{:name "buz"}
                             {:name "fiz"}
                             {:name "quz"}]}) => [:span#foo.leaf-select {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz-value"]]
    (provided (lupapalvelu.i18n/loc :docu :foo "fiz") => "fiz-value"
              (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "group"
    (convert-element {:foo {:baz {:value "bazibaa"} :fiz {:value "fizizizizii"} :quz {:value "quziquziquu"}}}
                     [:foo]
                     [:docu :foo]
                     {:name "foo" :type :group
                      :body [{:name "baz", :type :string}
                             {:name "fiz", :type :string}
                             {:name "quz", :type :string}]})
    => [:div#foo.group [:h3.group-title "foo-title"]
        [:span#baz.leaf-string {} [:div.element-title [:b "baz-title"]] [:div.element-value "bazibaa"]]
        [:span#fiz.leaf-string {} [:div.element-title [:b "fiz-title"]] [:div.element-value "fizizizizii"]]
        [:span#quz.leaf-string {} [:div.element-title [:b "quz-title"]] [:div.element-value "quziquziquu"]]]
    (provided (lupapalvelu.i18n/loc :docu :foo "_group_label") => "foo-title"
              (lupapalvelu.i18n/loc :docu :foo :baz) => "baz-title"
              (lupapalvelu.i18n/loc :docu :foo :fiz) => "fiz-title"
              (lupapalvelu.i18n/loc :docu :foo :quz) => "quz-title"))

  (fact "::hide-when - hide"
    (convert-element {:foo {:value "fiz"}, :bar {:value "buz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :hide-when {:path "bar" :values #{"buz"}}}) => nil)

  (fact "::hide-when - show"
    (convert-element {:foo {:value "fiz"}, :bar {:value "buz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :hide-when {:path "bar" :values #{"hii"}}})
    => [:span#foo.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz"]]
    (provided (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "::show-when - hide"
    (convert-element {:foo {:value "fiz"}, :bar {:value "buz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :show-when {:path "bar" :values #{"hii"}}}) => nil)

  (fact "::show-when - show"
    (convert-element {:foo {:value "fiz"}, :bar {:value "buz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :show-when {:path "bar" :values #{"buz"}}})
    => [:span#foo.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz"]]
    (provided (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "::i18nkey"
    (convert-element {:foo {:value "fiz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :i18nkey "some.loc.key"})
    => [:span#foo.leaf-string {} [:div.element-title [:b "some-loc-value"]] [:div.element-value "fiz"]]
    (provided (lupapalvelu.i18n/loc :some :loc :key) => "some-loc-value"))

  (fact "::repeating"
    (convert-element {:foo {:0 {:value "fiz"} :1 {:value "buz"}}} [:foo] [:docu :foo] {:name "foo", :type :string, :repeating true})
    => [:div.repeating
        [:span#foo-0.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz"]]
        [:span#foo-1.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "buz"]]]
    (provided (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "::layout"
    (convert-element {:foo {:value "fiz"}} [:foo] [:docu :foo] {:name "foo", :type :string, :layout :special})
    => [:span#foo.leaf-string.layout-special {} [:div.element-title [:b "foo-title"]] [:div.element-value "fiz"]]
    (provided (lupapalvelu.i18n/loc :docu :foo) => "foo-title"))

  (fact "::select-one-of"
    (convert-element {:foo {:_selected {:value "fiz"} :fiz {:value "fizizizizii"} :quz {:value "quziquziquu"}}}
                     [:foo]
                     [:docu :foo]
                     {:name "foo" :type :group
                      :body [{:name "_selected", :type :radioGroup}
                             {:name "fiz", :type :string}
                             {:name "quz", :type :string}]})
    => [:span#fiz.leaf-string {} [:div.element-title [:b "foo-title"]] [:div.element-value "fizizizizii"]]
    (provided (lupapalvelu.i18n/loc :docu :foo :fiz) => "foo-title"))

  (fact "::hidden"
    (convert-element {:foo {:0 {:value "fiz"} :1 {:value "buz"}}} [:foo] [:docu :foo] {:name "foo", :type :string, :hidden true}) => nil))
