(ns lupapalvelu.pate.markup-test
  (:require [lupapalvelu.pate.markup :refer [markup->tags]]
            [midje.sweet :refer :all]))

(fact "Remove empty lines before and after markup"
  (markup->tags "\nhello\n")
  => '([:span {} "hello" [:br {}]]))

(facts "text tags"
  (markup->tags nil) => nil
  (markup->tags "") => nil

  (markup->tags "hello world")
  => '([:span {} "hello world" [:br {}]])
  (markup->tags "hello \n world")
  => '([:span {} "hello" [:br {}]]
       [:span {}  "world" [:br {}]])
  (fact "bold (:strong tag)"
    (markup->tags "hello *bold text*")
    => '([:span {} "hello " [:strong {} "bold text"] [:br {}]])
    (markup->tags "hello *bold \ntext*")
    => '([:span {} "hello " [:strong {} "bold"] [:br {}]]
         [:span {} "text" [:strong {}] [:br {}]])
    (markup->tags "hello *bold text")
    => '([:span {} "hello " [:strong {} "bold text"] [:br {}]])
    (markup->tags "hello *bold  \\* text")
    => '([:span {} "hello " [:strong {} "bold  * text"] [:br {}]]))
  (fact "escapes"
    (markup->tags "\\* \\_ \\/ \\\\ \\[ \\] \\|")
    => '([:span {} "* _ / \\ [ ] |" [:br {}]])
    (markup->tags "\\- \\+ \\> \\. \\# \\^")
    => '([:span {} "- + > . # ^" [:br {}]])
    (markup->tags
     "
      * Asterisk
      *bold*
      \\* quoted
      - Dash
      \\- quoted
      + Plus
      \\+ quoted
      > Blockquote
      \\> quoted
      1. Numbered
      1\\. quoted
      # Header
      \\## # quoted
      ")
    => '([:ul {}
          [:li {} "Asterisk"]]
         [:span {} [:strong {} "bold"] [:br {}]]
         [:span {} "* quoted" [:br {}]]
         [:ul {}
          [:li {} "Dash"]]
         [:span {} "- quoted" [:br {}]]
         [:ul {}
          [:li {} "Plus"]]
         [:span {} "+ quoted" [:br {}]]
         [:blockquote {} "Blockquote"]
         [:span {} "> quoted" [:br {}]]
         [:ol {}
          [:li {} "Numbered"]]
         [:span {}  "1. quoted" [:br {}]]
         [:h1 {} "Header"]
         [:span {}  "## # quoted" [:br {}]]))
  (fact "italics (:em tag)"
    (markup->tags "hello _italics text_")
    => '([:span {} "hello " [:em {} "italics text"] [:br {}]])
    (markup->tags "hello _italics \ntext_ more")
    => '([:span {} "hello " [:em {} "italics"] [:br {}]]
         [:span {}  "text" [:em {} " more"] [:br {}]])
    (markup->tags "hello _italics text")
    => '([:span {} "hello " [:em {} "italics text"] [:br {}]])
    (markup->tags "hello _italics \\_ text")
    => '([:span {} "hello " [:em {} "italics _ text"] [:br {}]]))
  (fact "underline (:span.underline tag)"
    (markup->tags "hello ~underline text~")
    => '([:span {} "hello " [:span.underline {} "underline text"] [:br {}]])
    (markup->tags "hello ~underline \ntext~")
    => '([:span {} "hello " [:span.underline {} "underline"] [:br {}]]
         [:span {} "text" [:span.underline {}] [:br {}]])
    (markup->tags "hello ~underline text")
    => '([:span {} "hello " [:span.underline {} "underline text"] [:br {}]])
    (markup->tags "hello ~underline \\~    text")
    => '([:span {} "hello " [:span.underline {} "underline ~    text"] [:br {}]]))
  (fact "superscript"
    (markup->tags "area is 200 m^2^")
    => '([:span {} "area is 200 m" [:sup {} "2"] [:br {}]])
    (markup->tags "volume is 200 m^3\n and *then* some...")
    => '([:span {} "volume is 200 m"
          [:sup {} "3"] [:br {}]]
         [:span {} "and "
          [:strong {} "then"]
          " some..." [:br {}]]))
  (fact "enclosing formats"
    (markup->tags "hello ~underlined *bold _italics_*~ world")
    => '([:span {} "hello " [:span.underline {} "underlined "
                       [:strong {} "bold " [:em {} "italics"]]] " world" [:br {}]])
    (markup->tags "hello ~underlined *bold _italics_~ world")
    => '([:span {} "hello " [:span.underline {} "underlined "
                             [:strong {} "bold " [:em {} "italics"] [:span.underline {} " world"]]] [:br {}]])
    (markup->tags "hello ~underlined *bold _italics world")
    => '([:span {} "hello " [:span.underline {} "underlined "
                             [:strong {} "bold " [:em {} "italics world"]]] [:br {}]]))
  (fact "format mismash"
    (markup->tags "*hello ~underlined * foo _italics ~ _ ~ hei")
    => '([:span {} [:strong {} "hello "
                    [:span.underline {} "underlined "
                     [:strong {} " foo "
                      [:em {} "italics "
                       [:span.underline {} " "
                        [:em {} " "
                         [:span.underline {} " hei"]]]]]]] [:br {}]]))
  (facts "links"
    (fact "protocol part is mandatory"
      (markup->tags "[javascript:alert() | bad]")
      => '([:span {} "[javascript:alert() | bad]" [:br {}]]))
    (fact "Link parts do not support formatting"
      (markup->tags "[/link text/ [yes] | https://example.org/hello_world_ ]")
      => '([:span {} [:a {:href "https://example.org/hello_world_" :target :_blank}
                "/link text/ [yes]"] [:br {}]]))
    (fact "formatting encompass link"
      (markup->tags "*_ [ Example | http://example.org:8000/index.html]_*")
      => '([:span {} [:strong {}
                [:em {} " "
                 [:a {:href "http://example.org:8000/index.html"
                      :target :_blank} "Example"]]] [:br {}]]))))

(facts "headings"
  (fact "H1 - H6"
    (markup->tags
     "# h1 *bold
## h2 hello
   ### h3 _italics
 #### h4 # > + -
##### h5
###### h6
####### h6 is the maximum")
    => '([:h1 {} "h1 " [:strong {} "bold"]]
         [:h2 {} "h2 hello"]
         [:h3 {} "h3 " [:em {} "italics"]]
         [:h4 {} "h4 # > + -"]
         [:h5 {} "h5"]
         [:h6 {} "h6"]
         [:h6 {} "h6 is the maximum"]))
  (fact "Ignored whitespace"
    (markup->tags "   ## hello\nworld")
    => '([:h2 {} "hello"] [:span {} "world" [:br {}]])
    (markup->tags "##   hello  \n  world  ")
    => '([:h2 {} "hello"] [:span {} "world" [:br {}]]))
  (fact "Significant whitespace"
    (markup->tags "### hello")
    => '([:h3 {} "hello"])
    (markup->tags "###hello")
    => '([:span {} "###hello" [:br {}]])
    (markup->tags "## # hello")
    => '([:h2 {} "# hello"])))

(facts "lists"
  (fact "simple unordered"
    (markup->tags
     "Unordered list below:
* First item
* Second item
text continues outside list

New paragraph")
    => '([:span {} "Unordered list below:" [:br {}]]
         [:ul {}
          [:li {} "First item"]
          [:li {} "Second item"]]
         [:span {} "text continues outside list" [:br {}]]
         [:br {}]
[:span {} "New paragraph" [:br {}]]))
  (fact "multiple unordered levels"
    (markup->tags
     "Unordered list below:
* First item
* Second item
  - Inner one
  - Inner two
* Third item

New paragraph")
    => '([:span {} "Unordered list below:" [:br {}]]
         [:ul {}
          [:li {} "First item"]
          [:li {} "Second item"]
          [:ul {}
           [:li {} "Inner one"]
           [:li {} "Inner two"]]
          [:li {} "Third item"]]
         [:br {}]
         [:span {} "New paragraph" [:br {}]]))
  (fact "Scopes"
    (markup->tags
     "  + Item one text
* Two
     - Three
  + Four
  1. Five
  2. Six

  3. Seven

")
    => '([:ul {} [:li {} "Item one text"]]
         [:ul {} [:li {} "Two"]
          [:ul {} [:li {} "Three"]]
          [:ul {} [:li {} "Four"]]
          [:ol {} [:li {} "Five"] [:li {:value 2} "Six"]]]
         [:br {}]
         [:ol {} [:li {:value 3} "Seven"]]))
  (fact "Ordered list"
    (markup->tags "1. First more text
1. Second
12345. Third
1. Fourth
> Blockquote
1. Fifth")
    => '([:ol {}
          [:li {} "First more text"]
          [:li {} "Second"]
          [:li {:value 12345} "Third"]
          [:li {} "Fourth"]]
         [:blockquote {} "Blockquote"]
         [:ol {} [:li {} "Fifth"]])))

(fact "Tabs"
  (markup->tags "hello\tworld")
  => '([:span {} "hello\tworld" [:br {}]])
  (markup->tags "+\titem 1\n+ item 2")
  => '([:ul {}
        [:li {} "item 1"]
        [:li {} "item 2"]])
  (markup->tags "\t # \t hello")
  => '([:h1 {} "hello"]))

(facts "Miscellaneous"
  (fact "List item without text"
    (markup->tags "- ")
    => '([:span {} "-" [:br {}]]))
  (fact "Invisible space"
    (markup->tags "\u2063")
    => '([:span {} "\u2063" [:br {}]]))
  (fact "Wrong dashes"
    (markup->tags "Weird list:
- Normal dash
\u2013 En dash is span
\u2014 Em dash is also span
- Again normal dash")
    => '([:span {} "Weird list:" [:br {}]]
         [:ul {}
          [:li {} "Normal dash"]]
         [:span {} "\u2013 En dash is span" [:br {}]]
         [:span {} "\u2014 Em dash is also span" [:br {}]]
         [:ul {}
          [:li {} "Again normal dash"]])))
