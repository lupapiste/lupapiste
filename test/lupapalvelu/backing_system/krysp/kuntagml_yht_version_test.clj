(ns lupapalvelu.backing-system.krysp.kuntagml-yht-version-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht]
            [sade.date :as date]
            [sade.env :as env]))

(def permit-type "R")
(def org-223 {:krysp {(keyword permit-type) {:version "2.2.3"}}})
(def org-226 {:krysp {(keyword permit-type) {:version "2.2.6"}}})

(def ts-2024 (date/timestamp "2024-11-28T13:07:02.071"))

(def ts-2025 (date/timestamp "2025-01-05T13:07:02.071"))

(def application-not-submitted {:permitType permit-type
                                :submitted  nil})
(def application-submitted-2024 {:permitType permit-type
                                 :submitted  ts-2024})
(def application-submitted-2025 {:permitType permit-type
                                 :submitted  ts-2025})

(def now-2024 (date/timestamp "2024-12-28T13:07:02.071"))

(def now-2025 (date/timestamp "2025-01-10T13:07:02.071"))

(deftest application-krysp-version-test
  (testing "In 2024 -"
    (testing "application not submitted"
      (is (= (yht/application-krysp-version org-223 application-not-submitted now-2024) "2.2.3")))
    (testing "application submitted (obviously) before 2025 "
      (is (= (yht/application-krysp-version org-223 application-submitted-2024 now-2024) "2.2.3"))))

  (testing "In 2025 - org does NOT use 2.2.6 -"
    (testing "application not submitted"
      (is (= (yht/application-krysp-version org-223 application-not-submitted now-2025) "2.2.3")))
    (testing "application submitted before 2025"
      (is (= (yht/application-krysp-version org-223 application-submitted-2024 now-2025) "2.2.3")))
    (testing "application submitted after the beginning of 2025"
      (is (= (yht/application-krysp-version org-223 application-submitted-2025 now-2025) "2.2.3"))))

  (testing "In 2025 - org does use 2.2.6 -"
    (testing "application not submitted"
      (is (= (yht/application-krysp-version org-226 application-not-submitted now-2025) "2.2.6")))

    (env/with-feature-value :ignore-kuntagml-publish-date false
      (testing "application submitted before 2025 - :ignore-kuntagml-publish-date feature false"
        (is (= (yht/application-krysp-version org-226 application-submitted-2024 now-2025) "2.2.4"))))
    (env/with-feature-value :ignore-kuntagml-publish-date true
      (testing "application submitted before 2025  - :ignore-kuntagml-publish-date feature true"
        (is (= (yht/application-krysp-version org-226 application-submitted-2024 now-2025) "2.2.6"))))

    (testing "application submitted after the beginning of 2025"
      (is (= (yht/application-krysp-version org-226 application-submitted-2025 now-2025) "2.2.6")))

    ;; Timestamp for organization moving to 2.2.6 is not actually known so this is the same as the previous one
    (testing "application submitted after organization started using 2.2.6"
      (is (= (yht/application-krysp-version org-226 application-submitted-2025 now-2025) "2.2.6")))))


(deftest match-version-test
  (testing "match-version?"
    (testing "non-version inputs"
      (is (= (yht/match-version? nil nil) true))
      (is (= (yht/match-version? "" "") true))
      (is (= (yht/match-version? "" nil) false))
      (is (= (yht/match-version? nil "") true)))
    (testing "single digit target"
      (let [target "2"]
        (is (= (yht/match-version? target "0") false))
        (is (= (yht/match-version? target "0.0") false))
        (is (= (yht/match-version? target "1") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.99") false))
        (is (= (yht/match-version? target "2") true))
        (is (= (yht/match-version? target "2.0") true))
        (is (= (yht/match-version? target "2.14") true))
        (is (= (yht/match-version? target "2.1.0") true))
        (is (= (yht/match-version? target "3") true))
        (is (= (yht/match-version? target "3.0.00.000") true))
        (is (= (yht/match-version? target "10") true))))
    (testing "two digit target"
      (let [target "2.2"]
        (is (= (yht/match-version? target "0") false))
        (is (= (yht/match-version? target "0.0") false))
        (is (= (yht/match-version? target "1") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.99") false))
        (is (= (yht/match-version? target "2") false))
        (is (= (yht/match-version? target "2.0") false))
        (is (= (yht/match-version? target "2.1.0") false))
        (is (= (yht/match-version? target "2.1.9") false))
        (is (= (yht/match-version? target "2.2") true))
        (is (= (yht/match-version? target "2.2.0") true))
        (is (= (yht/match-version? target "2.19") true))
        (is (= (yht/match-version? target "3") true))
        (is (= (yht/match-version? target "3.0.00.000") true))
        (is (= (yht/match-version? target "10") true))))
    (testing "three digit target"
      (let [target "2.2.2"]
        (is (= (yht/match-version? target "0") false))
        (is (= (yht/match-version? target "0.0") false))
        (is (= (yht/match-version? target "1") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.0") false))
        (is (= (yht/match-version? target "1.99") false))
        (is (= (yht/match-version? target "2") false))
        (is (= (yht/match-version? target "2.0") false))
        (is (= (yht/match-version? target "2.1.0") false))
        (is (= (yht/match-version? target "2.1.9") false))
        (is (= (yht/match-version? target "2.2.0") false))
        (is (= (yht/match-version? target "2.2.2") true))
        (is (= (yht/match-version? target "2.2.2.2") true))
        (is (= (yht/match-version? target "3") true))
        (is (= (yht/match-version? target "3.0.00.000") true))
        (is (= (yht/match-version? target "10") true))))))

(deftest valid-krysp-version
  (testing "Valid KRYPS version string"
    (testing "Non-string values"
      (is (= (yht/valid-krysp-version-string? true) false))
      (is (= (yht/valid-krysp-version-string? nil) false))
      (is (= (yht/valid-krysp-version-string? 1) false))
      (is (= (yht/valid-krysp-version-string? 2.333) false))
      (is (= (yht/valid-krysp-version-string? 2/4) false))
      (is (= (yht/valid-krysp-version-string? ["2.2.4"]) false)))
    (testing "Valid version strings"
      (is (= (yht/valid-krysp-version-string? "2.1.0") true))
      (is (= (yht/valid-krysp-version-string? "2.1.9") true))
      (is (= (yht/valid-krysp-version-string? "2.2.0") true))
      (is (= (yht/valid-krysp-version-string? "2.2.2") true))
      (is (= (yht/valid-krysp-version-string? "12.2.2") true))
      (is (= (yht/valid-krysp-version-string? "2.12.2") true))
      (is (= (yht/valid-krysp-version-string? "2.2.12") true))
      (is (= (yht/valid-krysp-version-string? "12.12.12") true))
      (is (= (yht/valid-krysp-version-string? "2.1.0-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "2.1.9-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "2.2.0-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "2.2.2-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "12.2.2-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "2.12.2-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "2.2.12-dmcity-lp") true))
      (is (= (yht/valid-krysp-version-string? "12.12.12-dmcity-lp") true)))
    (testing "Invalid version strings"
      (is (= (yht/valid-krysp-version-string? "") false))
      (is (= (yht/valid-krysp-version-string? "2.1") false))
      (is (= (yht/valid-krysp-version-string? "2.1.a") false))
      (is (= (yht/valid-krysp-version-string? "2.b.0") false))
      (is (= (yht/valid-krysp-version-string? "c.2.2") false))
      (is (= (yht/valid-krysp-version-string? "12.2.2.2") false))
      (is (= (yht/valid-krysp-version-string? "2.122.") false))
      (is (= (yht/valid-krysp-version-string? "2") false))
      (is (= (yht/valid-krysp-version-string? "12.12") false))
      (is (= (yht/valid-krysp-version-string? "2,1,2") false))
      (is (= (yht/valid-krysp-version-string? "2.1.9-dmcity-lpp") false)))))
