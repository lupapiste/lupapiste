(ns lupapalvelu.xml.validator-test
  (:require [clojure.data.xml :as data-xml]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as mapping]
            [lupapalvelu.document.rakennuslupa-canonical :as canonical]
            [lupapalvelu.organization :as org]
            [lupapalvelu.publish-dates :as pd]
            [lupapalvelu.rakennuslupa-canonical-util :as test-canonical]
            [lupapalvelu.xml.validator :as validator]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]))

(def day-before-ryhti-publish-date (date/minus pd/ryhti-2025-publish-date :day))
(def day-after-ryhti-publish-date (date/plus pd/ryhti-2025-publish-date :day))

(deftest krysp-226-published-test
  (let [published? (fn [m] (contains? (set (:R m)) "2.2.6"))]
    (testing "in dev, local, qa.."
      (env/with-feature-value :ignore-kuntagml-publish-date true
        (testing "before publish date"
          (let [now   day-before-ryhti-publish-date
                krysp (validator/supported-krysp-versions-by-permit-type now)]
            (is (published? krysp))))
        (testing "after publish date"
          (let [now   day-after-ryhti-publish-date
                krysp (validator/supported-krysp-versions-by-permit-type now)]
            (is (published? krysp))))))
    (testing "in prod"
      (env/with-feature-value :ignore-kuntagml-publish-date false
        (testing "before publish date"
          (let [now   day-before-ryhti-publish-date
                krysp (validator/supported-krysp-versions-by-permit-type now)]
            (is (not (published? krysp)))))
        (testing "after publish date"
          (let [now   day-after-ryhti-publish-date
                krysp (validator/supported-krysp-versions-by-permit-type now)]
            (is (published? krysp))))))))

(deftest thread-safe-validators-test
  (with-redefs [org/pate-scope?                  (constantly false)
                org/get-application-organization (constantly {})]
    ;; Generate a sample XML document for validation
    (let [application test-canonical/application-rakennuslupa
          permit-type (:permitType application)
          canonical   (canonical/rakval-application-to-canonical application "fi")
          xml         (mapping/rakennuslupa-element-to-xml canonical "2.2.6")
          xml-string  (data-xml/indent-str xml)]

      (is (->> (range 50)
               ;; If a validator is accessed concurrently, the result seems to be a wide
               ;; range of different exceptions; NPEs, SAXParseExceptions wrt. missing declarations,
               ;; "FWK005 parse may not be called while parsing" errors, "Content not allowed in prologue", ...
               (pmap (fn [_]
                       (validator/validate xml-string permit-type "2.2.6")
                       true))
               doall
               seq))
      (testing "validate-integration-message-test"
        (letfn [(set-title [title]
                  (ss/replace xml-string
                              #"<yht:aineistonnimi>s</yht:aineistonnimi>"
                              (format "<yht:aineistonnimi>%s</yht:aineistonnimi>"
                                      title)))]
          (is (= xml-string
                 (validator/validate-integration-message! xml-string permit-type "2.2.6")))
          (is (= (set-title " hello ")
                 (validator/validate-integration-message! (set-title "\u0000hello\u00A0")
                                                          permit-type "2.2.6")))
          ;; No purging
          (is (thrown-with-msg? Exception
                                #"An invalid XML character \(Unicode: 0x0\) was found"
                                (validator/validate (set-title "\u0000hello\u00A0")
                                                    permit-type "2.2.6")))
          ;; Unsupported bad character
          (is (thrown-with-msg? Exception
                                #"An invalid XML character \(Unicode: 0xd800\) was found"
                                (validator/validate-integration-message! (set-title "\uD800yikes")
                                                          permit-type "2.2.6"))))))))
