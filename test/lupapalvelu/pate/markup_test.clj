(ns lupapalvelu.pate.markup-test
  (:require [lupapalvelu.pate.markup :as markup]
            [midje.sweet :refer :all]))

(facts "text tags"
  (markup/markup->tags nil) => '()
  (markup/markup->tags "") => '()

  (markup/markup->tags "hello world")
  => '([:p "hello world"])
  (fact "bold (:strong tag)"
    (markup/markup->tags "hello *bold text*")
    => '([:p "hello " [:strong "bold text"]])
    (markup/markup->tags "hello *bold \ntext*")
    => '([:p "hello " [:strong "bold text"]])
    (markup/markup->tags "hello *bold text")
    => '([:p "hello " [:strong "bold text"]]))
  (fact "escapes"
    (markup/markup->tags "\\* \\_ \\/ \\\\ \\[ \\] \\|")
    => '([:p "* _ / \\ [ ] |"])
    (markup/markup->tags "\\- \\+ \\> \\.")
    => '([:p "- + > ."])
    (markup/markup->tags
     "
      * Asterisk
      \\* quoted
      - Dash
      \\- quoted
      + Plus
      \\+ quoted
      > Blockquote
      \\> quoted
      1. Numbered
      1\\. quoted")
    => '([:ul
          [:li "Asterisk * quoted"]
          [:li "Dash - quoted"]
          [:li "Plus + quoted"]]
         [:blockquote "Blockquote > quoted"]
         [:ol
          [:li "Numbered 1. quoted"]]))
  (fact "italics (:em tag)"
    (markup/markup->tags "hello /italics text/")
    => '([:p "hello " [:em "italics text"]])
    (markup/markup->tags "hello /italics \ntext/ more")
    => '([:p "hello " [:em "italics text"] " more"])
    (markup/markup->tags "hello /italics text")
    => '([:p "hello " [:em "italics text"]]))
  (fact "underline (:span.underline tag)"
    (markup/markup->tags "hello _underline text_")
    => '([:p "hello " [:span.underline "underline text"]])
    (markup/markup->tags "hello _underline \ntext_")
    => '([:p "hello " [:span.underline "underline text"]])
    (markup/markup->tags "hello _underline text")
    => '([:p "hello " [:span.underline "underline text"]]))
  (fact "enclosing formats"
    (markup/markup->tags "hello _underlined *bold /italics/*_ world")
    => '([:p "hello " [:span.underline "underlined "
                       [:strong "bold " [:em "italics"]]] " world"])))
