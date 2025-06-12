(ns sade.xml-test2-test
  (:require [clojure.test :refer :all]
            [sade.xml :as xml]))

(deftest purge-xml-string-test
  (testing "Valid XML characters are preserved"
    (is (= "Hello World!" (xml/purge-xml-string "Hello World!")))
    (is (= "Valid XML Content ÄlämölÖ"
           (xml/purge-xml-string "Valid XML Content ÄlämölÖ")))
    (is (= "ä\u0009\u000A\u000Dö"
           (xml/purge-xml-string "ä\u0009\u000A\u000Dö"))) ;; Tab, LF, CR
    (is (= "a\t\n\r b" (xml/purge-xml-string "a\t\n\r b")))
    (is (= "\uD800\uDC00\uD83D\uDE00"
           (xml/purge-xml-string "\uD800\uDC00\uD83D\uDE00")))) ;; Surrogate pairs, not whitespace

  (testing "Invalid XML whitespace is removed"
    (is (= "Valid Content" (xml/purge-xml-string "Valid\u0000Content")))  ;; Removes invalid control character
    (is (= "Valid Text" (xml/purge-xml-string "Valid\u00A0Text"))) ;; Removes non-breaking space
    (is (= "Valid Text" (xml/purge-xml-string "Valid\u2028\u2029Text")))) ;; Removes line and paragraph separators

  (testing "Mixed valid and invalid characters"
    (is (= "Valid Text\uD83D\uDE00 "
           (xml/purge-xml-string "Valid\u0001Text\uD83D\uDE00\u0000")))) ;; Mixed case

  (testing "Nil or blank input"
    (is (nil? (xml/purge-xml-string nil))) ;; Input is nil
    (is (nil? (xml/purge-xml-string ""))) ;; Input is an empty string
    (is (nil? (xml/purge-xml-string "   "))) ;; Input is a blank string
    (is (nil? (xml/purge-xml-string "\t\n\r ")))
    (is (nil? (xml/purge-xml-string "\u0000\u0001")))
    (is (nil? (xml/purge-xml-string "\u0009\u000A\u000D")))))
