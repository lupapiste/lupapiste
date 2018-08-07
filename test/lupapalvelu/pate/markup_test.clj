(ns lupapalvelu.pate.markup-test
  (:require [lupapalvelu.pate.markup :refer [markup->tags]]
            [midje.sweet :refer :all]))

(facts "text tags"
  (markup->tags nil) => '()
  (markup->tags "") => '()

  (markup->tags "hello world")
  => '([:p {} "hello world" [:br]])
  (markup->tags "hello \n world")
=> '([:p {} "hello" [:br] "world" [:br]])
  (fact "bold (:strong tag)"
    (markup->tags "hello *bold text*")
    => '([:p {} "hello " [:strong {} "bold text"] [:br]])
    (markup->tags "hello *bold \ntext*")
    => '([:p {} "hello " [:strong {} "bold" [:br] "text"] [:br]])
    (markup->tags "hello *bold text")
    => '([:p {} "hello " [:strong {} "bold text" [:br]]])
    (markup->tags "hello *bold  \\* text")
    => '([:p {} "hello " [:strong {} "bold * text" [:br]]]))
  (fact "escapes"
    (markup->tags "\\* \\_ \\/ \\\\ \\[ \\] \\|")
    => '([:p {} "* _ / \\ [ ] |" [:br]])
    (markup->tags "\\- \\+ \\> \\. \\# \\^")
    => '([:p {} "- + > . # ^" [:br]])
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
          [:li {} "Asterisk "  [:strong {} "bold" ] " * quoted"]
          [:li {} "Dash - quoted"]
          [:li {} "Plus + quoted"]]
         [:blockquote {} "Blockquote > quoted"]
         [:ol {}
          [:li {} "Numbered 1. quoted"]]
         [:h1 {} "Header"]
         [:p {}  "## # quoted" [:br]]))
  (fact "italics (:em tag)"
    (markup->tags "hello /italics text/")
    => '([:p {} "hello " [:em {} "italics text"] [:br]])
    (markup->tags "hello /italics \ntext/ more")
    => '([:p {} "hello " [:em {} "italics" [:br] "text"] " more" [:br]])
    (markup->tags "hello /italics text")
    => '([:p {} "hello " [:em {} "italics text" [:br]]])
    (markup->tags "hello /italics \\/ text")
    => '([:p {} "hello " [:em {} "italics / text" [:br]]]))
  (fact "underline (:span.underline tag)"
    (markup->tags "hello _underline text_")
    => '([:p {} "hello " [:span.underline {} "underline text"] [:br]])
    (markup->tags "hello _underline \ntext_")
    => '([:p {} "hello " [:span.underline {} "underline" [:br] "text"] [:br]])
    (markup->tags "hello _underline text")
    => '([:p {} "hello " [:span.underline {} "underline text" [:br]]])
    (markup->tags "hello _underline \\_    text")
    => '([:p {} "hello " [:span.underline {} "underline _ text" [:br]]]))
  (fact "superscript"
    (markup->tags "area is 200 m^2^")
    => '([:p {} "area is 200 m" [:sup {} "2"] [:br]])
    (markup->tags "volume is 200 m^3\n and *then* some...")
    => '([:p {} "volume is 200 m"
          [:sup {} "3" [:br] "and "
           [:strong {} "then"]
           " some..." [:br]]]))
  (fact "enclosing formats"
    (markup->tags "hello _underlined *bold /italics/*_ world")
    => '([:p {} "hello " [:span.underline {} "underlined "
                       [:strong {} "bold " [:em {} "italics"]]] " world" [:br]])
    (markup->tags "hello _underlined *bold /italics/_ world")
    => '([:p {} "hello " [:span.underline {} "underlined "
                       [:strong {} "bold " [:em {} "italics"] [:span.underline {} " world" [:br]]]]])
    (markup->tags "hello _underlined *bold /italics world")
    => '([:p {} "hello " [:span.underline {} "underlined "
                       [:strong {} "bold " [:em {} "italics world" [:br]]]]]))
  (fact "format mismash"
    (markup->tags "*hello _underlined * foo /italics _ / _ hei")
    => '([:p {} [:strong {} "hello "
              [:span.underline {} "underlined "
               [:strong {} " foo "
                [:em {} "italics "
                 [:span.underline {} " "
                  [:em {} " "
                   [:span.underline {} " hei" [:br]]]]]]]]]))
  (facts "links"
    (fact "protocol part is mandatory"
      (markup->tags "[javascript:alert() | bad]")
      => '([:p {} "[javascript:alert() | bad]" [:br]]))
    (fact "Link parts do not support formatting, but text can contain escapes."
      (markup->tags "[https://example.org/hello_world_ | /link text/ \\[yes\\] ]")
      => '([:p {} [:a {:href "https://example.org/hello_world_" :target :_blank}
                "/link text/ [yes]"] [:br]]))
    (fact "formatting encompass link"
      (markup->tags "*_ [http://example.org:8000/index.html | Example]_*")
      => '([:p {} [:strong {}
                [:span.underline {} " "
                 [:a {:href "http://example.org:8000/index.html"
                      :target :_blank} "Example"]]] [:br]]))))

(facts "headings"
  (fact "H1 - H6"
    (markup->tags
     "# h1 *bold
## h2 hello
   ### h3 /italics
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
    => '([:h2 {} "hello"] [:p {} "world" [:br]])
    (markup->tags "##   hello  \n  world  ")
    => '([:h2 {} "hello"] [:p {} "world" [:br]]))
  (fact "Significant whitespace"
    (markup->tags "### hello")
    => '([:h3 {} "hello"])
    (markup->tags "###hello")
    => '([:p {} "###hello" [:br]])
    (markup->tags "## # hello")
    => '([:h2 {} "# hello"])))

(facts "lists"
  (fact "simple unordered"
    (markup->tags
     "Unordered list below:
* First item
* Second item
text continues

New paragraph")
    => '([:p {} "Unordered list below:" [:br]]
         [:ul {}
          [:li {} "First item"]
          [:li {} "Second item text continues"]]
[:p {} "New paragraph" [:br]]))
  (fact "multiple unordered levels"
    (markup->tags
     "Unordered list below:
* First item
* Second item
text continues
  - Inner one
more
  - Inner two
* Third item

New paragraph")
    => '([:p {} "Unordered list below:" [:br]]
         [:ul {}
          [:li {} "First item"]
          [:li {} "Second item text continues"]
          [:ul {}
           [:li {} "Inner one more"]
           [:li {} "Inner two"]]
          [:li {} "Third item"]]
         [:p {} "New paragraph" [:br]]))
  (fact "Scopes"
    (markup->tags
     "  + Item one
          text
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
          [:ol {} [:li {} "Five"] [:li {} "Six"]]]
         [:ol {} [:li {} "Seven"]]))
  (fact "Ordered list"
    (markup->tags "1. First
more text
1. Second
12345. Third
1. Fourth
> Blockquote
1. Fifth")
    => '([:ol {}
          [:li {} "First more text"]
          [:li {} "Second"]
          [:li {} "Third"]
          [:li {} "Fourth"]]
         [:blockquote {} "Blockquote"]
         [:ol {} [:li {} "Fifth"]])))

(fact "Tabs"
  (markup->tags "hello\tworld")
  => '([:p {} "hello world" [:br]])
  (markup->tags "+\titem 1\n+ item 2")
  => '([:ul {}
        [:li {} "item 1"]
        [:li {} "item 2"]]))
