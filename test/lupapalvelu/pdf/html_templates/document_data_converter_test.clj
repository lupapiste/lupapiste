(ns lupapalvelu.pdf.html-templates.document-data-converter-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.pdf.html-templates.document-data-converter :refer :all]))

(testable-privates lupapalvelu.pdf.html-templates.document-data-converter
                   parse-i18nkey
                   get-in-schema-with-i18n-path
                   path-string->absolute-path
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

(facts path-string->absolute-path
  (fact "absolute-path"
    (path-string->absolute-path [:foo :bar] "/quu/quz") => [:quu :quz])

  (fact "relative-path"
    (path-string->absolute-path [:foo :bar] "quu/quz") => [:foo :bar :quu :quz]))

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
