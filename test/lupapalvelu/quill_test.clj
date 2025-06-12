(ns lupapalvelu.quill-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.docstore :as docstore]
            [lupapalvelu.quill :as q]
            [lupapalvelu.shared-quill :as sq]
            [sade.core :refer [fail]]
            [schema.utils :as scu]))

(deftest coerse-operations
  (testing "no links, no colors"
    (is (= [] (q/coerce-operations nil)))
    (is (= [] (q/coerce-operations [])))
    (is (= [{:insert ""}] (q/coerce-operations [{:insert ""}])))
    (is (= [{:attributes {:bold true}
             :insert     "hello"}
            {:attributes {:align "right"}
             :insert     "world"}]
           (q/coerce-operations [{:attributes {:bold true}
                                  :insert     "hello"}
                                 {:attributes {:align "right"}
                                  :insert     "world"}]))))
  (testing "links and colors"
    (is (= [{:attributes {:color "#aabbcc"
                          :link  "http://example.com"}
             :insert     "hello"}
            {:attributes {:color "rgba(100,150,200,0.5)"
                          :link  "https://www.lupapiste.fi"}
             :insert     "world"}
            {:attributes {:link "http://kauppa.lupapiste.fi"}
             :insert     " "}
            {:attributes {:link "http://file://user/foo/passwords.txt"}
             :insert     ""}]
           (q/coerce-operations [{:attributes {:color "#aabbcc"
                                               :link  "  example.com  "}
                                  :insert     "hello"}
                                 {:attributes {:color "rgba(100,150,200,0.5)"
                                               :link  "https://www.lupapiste.fi"}
                                  :insert     "world"}
                                 {:attributes {:link  "HTTP://KAUPPA.LUPAPISTE.FI"
                                               :color "bad"}
                                  :insert     " "}
                                 {:attributes {:link "file://user/foo/passwords.txt"}
                                  :insert     ""}]))))
  (testing "fail"
    (is (scu/error? (q/coerce-operations [:kaboom true])))
    (is (thrown? Exception
                 (q/coerce-operations! [:kaboom true])))
    (is (scu/error? (q/coerce-operations [{:insert     "hello"
                                           :attributes {:link ""}}])))
    (is (thrown? Exception
                 (q/coerce-operations! [{:insert     "hello"
                                         :attributes {:link ""}}])))
    (is (scu/error? (q/coerce-operations [{:insert     "hello"
                                           :attributes {:link ""}}])))))

(deftest check-images
  (testing "no images"
    (is (nil? (sq/check-images [])))
    (is (nil? (sq/check-images [{:attributes {:bold true}
                                 :insert     "hello"}]))))
  (testing "more than one image"
    (is (= :error.quill.image.count
           (sq/check-images [{:insert "hello"}
                             {:insert {:image "data:image/bar;base64,aabbcc"}}
                             {:attributes {:indent 1}
                              :insert {:image "data:image/foo;base64,ddeeff"}}]))))
  (testing "unsupported image format"
    (is (= :error.quill.image.format
           (sq/check-images [{:insert "hello"}
                             {:insert {:image "data:unknown/png;base64,aabbcc"}}])))
    (is (= :error.quill.image.format
           (sq/check-images [{:insert {:image "data:image/png;base65,aabbcc"}}])))
    (is (= :error.quill.image.format
           (sq/check-images [{:insert {:image "data:image/png;base64;aabbcc"}}]))))
  (testing "image too large"
    (is (= :error.quill.image.size
           (sq/check-images [{:insert {:image (apply str "data:image/png;base64,"
                                                     (take 100000 (cycle ["abc"])))}}])))))

(deftest check-operations
  (testing "No operations"
    (is (nil? (q/check-operations nil)))
    (is (nil? (q/check-operations []))))
  (testing "Bad operations"
    (is (= :error.quill.data (q/check-operations [{:insert :bad}])))
    (is (= :error.quill.image.count
           (q/check-operations [{:insert {:image "data:image/png;base64,aabbcc"}}
                                {:insert "Second image"}
                                {:insert {:image "data:image/png;base64,aabbcc"}}]))))
  (testing "Good operations"
    (is (nil? (q/check-operations [{:insert {:image "data:image/png;base64,aabbcc"}}
                                   {:insert "Caption text"}])))))

(deftest blank-operations?
  (testing "Blank"
    (is (true? (q/blank-operations? nil)))
    (is (true? (q/blank-operations? [])))
    (is (true? (q/blank-operations? [{:insert "  "}])))
    (is (true? (q/blank-operations? [{:insert ""} {} {:insert "  "}
                                     {:image "foobar"}]))))
  (testing "Not blank"
    (is (false? (q/blank-operations? [{:insert "hello"}])))
    (is (false? (q/blank-operations? [{:insert ""} {} {:insert " world "}
                                      {:image "foobar"}])))))

(deftest input-validator
  (testing "Good"
    (is (nil? ((q/input-validator :txt)
               {:data {:txt [{:insert "yeah"}]}})))
    (is (nil? ((q/input-validator "txt" true)
               {:data {:txt [{:insert "yeah"}]}})))
    (is (nil? ((q/input-validator :txt true) {}))))
  (testing "Bad"
    (is (= (fail :error.missing-parameters)
           ((q/input-validator :bad)
            {:data {:txt [{:insert "yeah"}]}})))
    (is (= (fail :error.quill.data)
           ((q/input-validator :txt)
            {:data {:txt [{:what "yeah"}]}})))
    (is (= (fail :error.missing-parameters)
           ((q/input-validator :txt)
            [{:insert " "}])))
    (is (= (fail :error.missing-parameters)
           ((q/input-validator :txt)
            [{:insert {:image "data:image/png;base64,aabbcc"}}
             {:insert "  "}])))))

(defn tagify
  "Deflate -> inflate -> tags"
  ([ops div]
   (let [dops (q/deflate ops)]
     (testing "deflate/inflate"
       (is (not= dops ops))
       (is (= ops (q/inflate dops))))
     (sq/->tags ops div)))
  ([ops] (tagify ops nil)))

(deftest ->tags
  (testing "Data is not operations"
    (is (nil? (sq/->tags nil)))
    (is (= [:div.quill.string ""]
           (sq/->tags "")))
    (is (= [:div.quill.string "hello"]
           (sq/->tags "hello")))
    (is (= [:div.foo "world"]
           (sq/->tags "world" [:div.foo])))
    (is (= [:div.foo {:bar "baz"} "world"]
           (sq/->tags "world" [:div.foo {:bar "baz"}]))))
  (testing "Quill operations"
    (is (= [:div.quill [:p.quill "regular text"]]
           (tagify [{:insert "regular text\n"}])))
    (is (= [:div.yikes [:p.quill "more text"]]
           (tagify [{:insert "more text\n"}] [:div.yikes])))
    (is (= [:div.yikes {:one 1} [:p.quill "more text"]]
           (tagify [{:insert "more text\n"}] [:div.yikes {:one 1}])))
    (is (= [:div.quill
            [:p.quill "First paragraph"]
            [:p.quill [:br]]
            [:p.quill "Second one"]
            [:p.quill [:br]]]
           (tagify [{:insert "First paragraph\n\nSecond one\n\n"}])))
    (is (= [:div.quill [:h3.quill [:span.quill "Formatting"]]
            [:p.quill [:span.quill "Something "] [:strong.quill "important"]
             [:span.quill ", "] [:em.quill "cursive"] [:span.quill " and "]
             [:u.quill "underlined"] "."]
            [:blockquote.quill "Quoted " [:code.quill "code word"]
             [:span.quill ", with "]
             [:span.quill
              {:style {:background-color "#ffff66", :color "#e60000"}}
              "highlight"]]]
           (tagify [{:insert "Formatting"}
                    {:attributes {:header 3} :insert "\n"}
                    {:insert "Something "}
                    {:attributes {:bold true} :insert "important"}
                    {:insert ", "}
                    {:attributes {:italic true} :insert "cursive"}
                    {:insert " and "}
                    {:attributes {:underline true} :insert "underlined"}
                    {:insert ".\nQuoted "}
                    {:attributes {:code true} :insert "code word"}
                    {:insert ", with "}
                    {:attributes {:background "#ffff66" :color "#e60000"},
                     :insert     "highlight"}
                    {:attributes {:blockquote true} :insert "\n"}])))
    (is (= [:div.quill [:h1.quill [:span.quill "H1"]]
            [:p.quill {:class ["ql-align-center"]} [:span.quill "Center "]
             [:s.quill "error"]]
            [:p.quill {:class ["ql-align-right"]} [:span.quill "Right"]
             [:sup.quill "asterisk"]]
            [:p.quill {:class ["ql-align-justify"]} [:span.quill "Justified "]
             [:a.quill
              {:href   "http://www.example.org",
               :rel    "noopener noreferrer",
               :target :_blank} "hyperlink"] [:span.quill "."]]]
           (tagify [{:insert "H1"}
                    {:attributes {:header 1} :insert "\n"}
                    {:insert "Center "}
                    {:attributes {:strike true} :insert "error"}
                    {:attributes {:align "center"} :insert "\n"}
                    {:insert "Right"}
                    {:attributes {:script "super"} :insert "asterisk"}
                    {:attributes {:align "right"} :insert "\n"}
                    {:insert "Justified "}
                    {:attributes {:link "http://www.example.org"} :insert "hyperlink"}
                    {:insert "."}
                    {:attributes {:align "justify"} :insert "\n"}])))
    (is (= [:div.quill
            [:span.quill
             [:p.quill {:class ["ql-indent-1"]}
              [:sup.quill
               [:code.quill
                [:u.quill
                 [:s.quill
                  [:em.quill
                   [:strong.quill
                    {:style {:background-color "#3d1466" :color "#ffffff"}}
                    "Everything"]]]]]]]]]
           (tagify [{:attributes {:background "#3d1466"
                                  :bold       true
                                  :code       true
                                  :color      "#ffffff"
                                  :italic     true
                                  :script     "super"
                                  :strike     true
                                  :underline  true}
                     :insert     "Everything"}
                    {:attributes {:indent 1} :insert "\n"}])))
    (is (= [:div.quill [:h4.quill [:span.quill "Various lists"]]
            [:ul.quill
             [:li.ul.quill.quill [:u.quill "First"] [:span.quill " bullet"]]
             [:li.ul.quill.quill [:span.quill "Second bullet"]]
             [:li.ul.quill.quill {:class ["ql-indent-1"]}
              [:span.quill "Inner bullet"]]]
            [:ol.quill
             [:li.ol.quill.quill {:class ["ql-indent-1"]} [:span.quill "Letter "]
              [:span.quill {:style {:color "#e60000"}} "A"]]
             [:li.ol.quill.quill {:class ["ql-indent-1"]}
              [:span.quill "Letter B"]]
             [:li.ol.quill.quill {:class ["ql-indent-2"]} [:em.quill "Letter"]
              [:span.quill " I"]]]
            [:ul.quill
             [:li.ul.quill.quill {:class ["ql-indent-2"]}
              [:span.quill "Level 3 bullet"]]
             [:li.ul.quill.quill {:class ["ql-indent-2"]}
              [:span.quill "Another 3 bullet"]]]
            [:ol.quill
             [:li.ol.quill.quill {:class ["ql-indent-1"]}
              [:span.quill "Another A"]]]
            [:ul.quill
             [:li.ul.quill.quill {:class ["ql-indent-1"]} [:span.quill "Second "]
              [:strong.quill "inner"] [:span.quill " bullet"]]]
            [:ol.quill [:li.ol.quill.quill [:span.quill "Number 1"]]
             [:li.ol.quill.quill [:span.quill "Number 2"]]]
            [:h2.quill [:span.quill "Done."]]]
           (tagify [{:insert "Various lists"}
                    {:attributes {:header 4} :insert "\n"}
                    {:attributes {:underline true} :insert "First"}
                    {:insert " bullet"}
                    {:attributes {:list "bullet"} :insert "\n"}
                    {:insert "Second bullet"}
                    {:attributes {:list "bullet"} :insert "\n"}
                    {:insert "Inner bullet"}
                    {:attributes {:indent 1 :list "bullet"} :insert "\n"}
                    {:insert "Letter "}
                    {:attributes {:color "#e60000"} :insert "A"}
                    {:attributes {:indent 1 :list "ordered"} :insert "\n"}
                    {:insert "Letter B"}
                    {:attributes {:indent 1 :list "ordered"} :insert "\n"}
                    {:attributes {:italic true} :insert "Letter"}
                    {:insert " I"}
                    {:attributes {:indent 2 :list "ordered"} :insert "\n"}
                    {:insert "Level 3 bullet"}
                    {:attributes {:indent 2 :list "bullet"} :insert "\n"}
                    {:insert "Another 3 bullet"}
                    {:attributes {:indent 2 :list "bullet"} :insert "\n"}
                    {:insert "Another A"}
                    {:attributes {:indent 1 :list "ordered"} :insert "\n"}
                    {:insert "Second "}
                    {:attributes {:bold true} :insert "inner"}
                    {:insert " bullet"}
                    {:attributes {:indent 1 :list "bullet"} :insert "\n"}
                    {:insert "Number 1"}
                    {:attributes {:list "ordered"} :insert "\n"}
                    {:insert "Number 2"}
                    {:attributes {:list "ordered"} :insert "\n"}
                    {:insert "Done."}
                    {:attributes {:header 2} :insert "\n"}])))
    (is (= [:div.quill
            [:p.quill [:span.quill "Here is image:"]
             [:img {:src "data:image/png;base64,iVBORw0KGgoAAAA......"}]
             " Text after."] [:p.quill [:br]]]
           (tagify [{:insert "Here is image:"}
                    {:insert
                     {:image "data:image/png;base64,iVBORw0KGgoAAAA......"}}
                    {:insert " Text after.\n\n"}])))))


(deftest document-request-instructions
  (let [write-instructions #'docstore/write-instructions
        ops                [{:insert     "Good info\n"
                             :attributes {:bold true}}]]
    (testing "Write: No instructions"
      (is (= {} (write-instructions {})))
      (is (= {} (write-instructions {:sv "Swedish not supported."})))
      (is (= {:fi []} (write-instructions {:fi nil})))
      (is (= {:fi []} (write-instructions {:fi []}))))
    (testing "Write: Operations"
      (is (= {:fi [{:a {:k [:b]} :i "Good info\n"}]}
             (write-instructions {:en "Not supported"
                                  :fi ops}))))
    (testing "Read: No instructions"
      (is (= {} (docstore/read-instructions {})))
      (is (= {} (docstore/read-instructions {:sv "Swedish not supported."})))
      (is (= {:fi nil} (docstore/read-instructions {:fi nil})))
      (is (= {:fi []} (docstore/read-instructions {:fi []}))))
    (testing "Read: String"
      (is (= {:fi [{:insert "Legacy info"}]}
             (docstore/read-instructions {:fi "Legacy info"}))))
    (testing "Read: Operations"
      (is (= {:fi ops}
             (docstore/read-instructions {:fi [{:a {:k [:b]}
                                                :i "Good info\n"}]}))))))

(deftest trim-operations
  (is (= [] (sq/trim-operations [])))
  (is (= [{:insert "hello"}] (sq/trim-operations [{:insert "hello"}])))
  (is (= [{:insert "hello"}] (sq/trim-operations [{:insert "\n hello \n"}])))
  (is (= [{:insert "hello \n"} {:insert " world"}]
         (sq/trim-operations [{:insert "\n hello \n"}
                              {:insert " world   \n"}])))
  (is (= [{:insert ""}
          {:attributes {:header 1} :insert ""}
          {:insert "Center "}
          {:attributes {:strike true} :insert "error"}
          {:attributes {:align "center"} :insert "\n"}
          {:insert " Right"}
          {:attributes {:script "super"} :insert ""}]
         (sq/trim-operations [{:insert "  \n"}
                              {:attributes {:header 1} :insert "\n"}
                              {:insert "  Center "}
                              {:attributes {:strike true} :insert "error"}
                              {:attributes {:align "center"} :insert "\n"}
                              {:insert " Right   "}
                              {:attributes {:script "super"} :insert "  "}])))
  (is (= [{:insert {:image " data:..... "}}]
         (sq/trim-operations [{:insert {:image " data:..... "}}]))))
