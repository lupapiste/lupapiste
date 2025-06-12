(ns lupapalvelu.conversion.schemas-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [schema.core :as sc]))

(deftest supported-backend-id-test
  (testing "supported"
    (is (true? (conv-sc/supported-backend-id? "12-12345-YUP")))
    (is (true? (conv-sc/supported-backend-id? "500-2026-0012"))))
  (testing "unsupported"
    (is (false? (conv-sc/supported-backend-id? "12-12345-VAK")))
    (is (false? (conv-sc/supported-backend-id? "can't parse this")))
    (is (false? (conv-sc/supported-backend-id? "")))
    (is (false? (conv-sc/supported-backend-id? " ")))
    (is (false? (conv-sc/supported-backend-id? nil)))
    (is (false? (conv-sc/supported-backend-id? "500-1899-0001")))))

(def parse-permit-id #'conv-sc/parse-permit-id)

(deftest parse-permit-id-test
  (testing "Permit ids can be split into their constituent parts"
    (is (= {:kauposa "17" :no "0089" :tyyppi "D" :vuosi "12"}
           (parse-permit-id "12-0089-D 17")))
    (is (= {:kauposa "17" :no "0089" :tyyppi "D" :vuosi "12"}
           (parse-permit-id "17-0089-12-D")))
    (is (= {:kauposa "98" :no "0088" :vuosi "10" :tyyppi "A"}
           (parse-permit-id "98-0088-10-A")))
    (is (= {:vuosi "10" :no "0088" :tyyppi "A" :kauposa "98"}
           (parse-permit-id "10-0088-A 98")))
    (is (= {:kauposa "164" :vuosi "10" :no "3001" :tyyppi "R"}
           (parse-permit-id "164 10-3001-R")))
    (is (= {:kauposa "75" :no "0549" :vuosi "16" :tyyppi "DJ"}
           (parse-permit-id "75-0549-16-DJ")))
    (is (= {:no "0010" :vuosi "00" :tyyppi "TJO"}
           (parse-permit-id "0010-00-TJO")))
    (is (= {:kauposa "123" :vuosi "12" :no "5678" :tyyppi "STR"}
           (parse-permit-id "123 12-5678-STR")))
    (is (= {:vuosi "12" :no "5678" :tyyppi "ABC"}
           (parse-permit-id "12-5678-ABC")))
    (is (= {:vuosi "12" :no "56789" :tyyppi "POP"}
           (parse-permit-id "12-56789-POP")))
    (is (= {:vuosi "00" :no "456789" :tyyppi "TJO"}
           (parse-permit-id "00-456789-TJO")))
    (is (= {:kunta "500" :vuosi "2026" :no "0012"}
           (parse-permit-id "500-2026-0012")))

    (testing "Parsing invalid ids results in nil"
      (is (nil? (parse-permit-id nil)))
      (is (nil? (parse-permit-id "")))
      (is (nil? (parse-permit-id "  ")))
      (is (nil? (parse-permit-id "Hei äijät :D Mitä äijät :D Siistii nähä teit :D")))
      (is (nil? (parse-permit-id "75-0549-4242-A")))
      (is (nil? (parse-permit-id "75 0549-4242-A"))))))

(deftest destructure-permit-id-test
  (testing "Permit ids can be destructured into their constituent parts"
    (testing "Destructuring gives same results despite the input format "
      (is (= (conv-sc/destructure-permit-id "12-0089-D 17")
             (conv-sc/destructure-permit-id "17-0089-12-D")))
      (is (= (conv-sc/destructure-permit-id "98-0088-10-A")
             (conv-sc/destructure-permit-id "10-0088-A 98")))
      (is (= (conv-sc/destructure-permit-id "164 10-3001-R")
             {:vuosi  2010 :kauposa     "164" :no "3001"
              :tyyppi "R"  :permit-type "R"})))

    (testing "The results are a map with four keys"
      (is (= (conv-sc/destructure-permit-id "75-0549-16-DJ")
             {:kauposa "75" :no          "0549" :tyyppi "DJ"
              :vuosi   2016 :permit-type "R"}))
      (is (= 5 (count (conv-sc/destructure-permit-id "16-0549-DJ 75")))))

    (testing "Legacy ids without kauposa work as well"
      (is (= (conv-sc/destructure-permit-id "0010-00-TJO")
             {:no    "0010" :tyyppi      "TJO"
              :vuosi 2000   :permit-type "R"}))
      (is (= (conv-sc/destructure-permit-id "12345-99-PSR")
             {:no    "12345" :tyyppi      "PSR"
              :vuosi 1999    :permit-type "P"})))

    (testing "Database format with city block at front"
      (is (= (conv-sc/destructure-permit-id "123 12-5678-STR")
             {:kauposa "123" :no          "5678" :tyyppi "STR"
              :vuosi   2012  :permit-type "P"})))

    (testing "Database format without city block"
      (is (= (conv-sc/destructure-permit-id "12-5678-ABC")
             {:no    "5678" :tyyppi      "ABC"
              :vuosi 2012   :permit-type "R"}))
      (is (= (conv-sc/destructure-permit-id "12-56789-POP")
             {:no    "56789" :tyyppi      "POP"
              :vuosi 2012    :permit-type "P"}))
      (is (= (conv-sc/destructure-permit-id "00-456789-TJO")
             {:no    "456789" :tyyppi      "TJO"
              :vuosi 2000     :permit-type "R"})))

    (testing "Full-year format without type"
      (is (= (conv-sc/destructure-permit-id "500-2026-0012")
             {:kunta "500" :no "0012" :vuosi 2026 :permit-type "R"})))

    (testing "Full-year format with year <= 1900 throws"
      (is (thrown? Exception (conv-sc/destructure-permit-id "500-1899-0001")))
      (is (thrown? Exception (conv-sc/destructure-permit-id "500-1900-0001"))))

    (testing "Destructuring invalid ids results in exception"
      (is (thrown? Exception (conv-sc/destructure-permit-id nil)))
      (is (thrown? Exception (conv-sc/destructure-permit-id "")))
      (is (thrown? Exception (conv-sc/destructure-permit-id "  ")))
      (is (thrown? Exception (conv-sc/destructure-permit-id "Hei äijät :D Mitä äijät :D Siistii nähä teit :D")))
      (is (thrown? Exception (conv-sc/destructure-permit-id "75-0549-4242-A")))
      (is (thrown? Exception (conv-sc/destructure-permit-id "75 0549-4242-A"))))))

(def lowkey? #'conv-sc/lowerkeyword?)

(deftest lowerkeyword-test
  (testing "->lowerkeyword"
    (doseq [[s k] [[nil nil] [" HeLlO " :hello] ["  " nil]
                   ["1" :1] ["R" :r] ["VAK" :vak]]]
      (is (= k (conv-sc/->lowerkeyword s)))))
  (testing "lowerkeyword?"
    (is (true? (lowkey? :hello-world)))
    (is (true? (lowkey? :1)))
    (is (false? (lowkey? :What?)))
    (is (false? (lowkey? (keyword ""))))
    (is (false? (lowkey? "hello")))
    (is (false? (lowkey? nil)))))

(deftest backend-id->suffix
  (doseq [[bid suffix] [[nil nil] ["Älömölö" nil] ["" nil] ["  " nil]
                        ["12-5678-ABC" :abc] ["12-56789-POP" :pop]
                        ["12-5678-Ö" nil] ["00-456789-tjo" nil]
                        ["00-456789-TJO" :tjo] ["22-1234-TJO 3" :tjo]]]
    (is (= suffix (conv-sc/backend-id->suffix bid)))))

(def item {:backend-id "hello"
           :taskname   "world"
           :received   123456789
           :processed  "6.5.2024"
           :text       "Done."})

(deftest valid-task-item!-test
  (testing "good"
    (doseq [item [item
                  (dissoc item :received :processed)
                  (dissoc item :processed :text)
                  (dissoc item :text :received)]]
      (is (= item (conv-sc/valid-task-item! item)))))

  (testing "bad"
    (doseq [item [(assoc item :backend-id "  ")
                  (dissoc item :received :processed :text)
                  (-> item (dissoc :received :processed)
                      (assoc :text "   "))
                  (-> item (dissoc :processed :text)
                      (assoc :received nil))]]
      (is (thrown-with-msg? Exception #"Bad task item"
                            (conv-sc/valid-task-item! item))))))

(def si-check (sc/checker conv-sc/StatementItem))

(deftest statement-item-test
  (testing "StatementItem constraints"
    (is (nil? (si-check {:text "hello" :backend-id "foo"})))
    (is (some? (si-check {:backend-id "foo"})))
    (is (nil? (si-check {:backend-id "foo" :given "5.2.2024"})))
    ;; Date is not parsed during schema validation
    (is (nil? (si-check {:backend-id "foo" :given "foobar"})))
    (is (some? (si-check {:backend-id "foo" :status "oh-no"})))))
