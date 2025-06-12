(ns lupapalvelu.filebank-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.filebank :refer :all]))

(def mock-app
  {:organization "753-R"})

(facts "Filebank enabled validator tests"
  (fact "Filebank is enabled"
    (validate-filebank-enabled
      {:application mock-app
       :organization (delay {:filebank-enabled true})})
    => nil)

  (fact "Filebank is disabled"
    (validate-filebank-enabled
      {:application mock-app
       :organization (delay {:filebank-enabled false})})
    => {:ok false :text "error.unauthorized"})

  (fact "Filebank have not been enabled or disabled yet"
    (validate-filebank-enabled
      {:application mock-app
       :organization (delay {})})
    => {:ok false :text "error.unauthorized"})

  (fact "Application has no organization"
    (validate-filebank-enabled
      {:application (dissoc mock-app :organization)
       :organization (delay {:filebank-enabled true})})
    => {:ok false :text "error.unauthorized"}))
