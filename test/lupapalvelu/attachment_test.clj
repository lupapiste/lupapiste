(ns lupapalvelu.attachment-test
    (:use lupapalvelu.attachment
        clojure.test
        midje.sweet)
  )

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(facts "Test file name encoding"
  (fact "Nil-safe"
    (encode-filename nil)     => nil)
  (fact "Short US-ASCII string is returned as is"
    (encode-filename "foo.txt")     => "foo.txt")
  (fact "Over 255 chars are truncated (Windows file limit)"
    (encode-filename (clojure.string/join (repeat 256 "x"))) => (clojure.string/join (repeat 255 "x")))
  (fact "255 chars are not truncated (Windows file limit)"
    (encode-filename (clojure.string/join (repeat 255 "y"))) => (clojure.string/join (repeat 255 "y")))
  (fact "File extension is not truncated"
    (encode-filename (str (clojure.string/join (repeat 256 "y")) ".txt")) => (contains ".txt"))
  (fact "Non-ascii letters are removed"
     (encode-filename "Ääkkösiä") => (just ascii-pattern))
  (fact "Unix path separators are removed"
     (encode-filename "/root/secret") => (just ascii-pattern))
  (fact "Windows path separators are removed"
     (encode-filename "\\Windows\\cmd.exe") => (just ascii-pattern))
  (fact "Tabs are removed"
     (encode-filename "12345\t678\t90") => (just ascii-pattern))
  (fact "Newlines are removed"
     (encode-filename "12345\n678\r\n90") => (just ascii-pattern))
  )

