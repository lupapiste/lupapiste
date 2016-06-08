(ns lupapalvelu.application-search-api-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             ))

(testable-privates lupapalvelu.application-search-api localize-application)

(def empty-op {:id nil :created nil :name nil :description nil :displayNameFi "Ei tiedossa" :displayNameSv "Ok\u00e4nd"})

(facts "localize-application"
  ; Presence of state is checked with a smoke test.
  ; Primary operation might be missing in legacy data.
  (fact "Only state set"
    (localize-application {:state :open}) => {:state :open, :stateNameFi "N\u00e4kyy viranomaiselle", :stateNameSv "Under beredning"
                                              :primaryOperation empty-op, :secondaryOperations []})

  (fact "Primary operation name is localized"
    (localize-application {:state :open
                           :primaryOperation {:name "ya-jatkoaika"}})
    => {:state :open, :stateNameFi "N\u00e4kyy viranomaiselle", :stateNameSv "Under beredning"
        :primaryOperation {:id nil :created nil :name "ya-jatkoaika" :description nil
                           :displayNameFi "Jatkoajan hakeminen" :displayNameSv "Ans\u00f6kan om till\u00e4ggstid"}
        :secondaryOperations []})

  (fact "Secondary operation name is localized, empty operation info is removed"
    (localize-application {:state :open
                           :secondaryOperations [{:name "ya-jatkoaika"}, {}]})
    => {:state :open, :stateNameFi "N\u00e4kyy viranomaiselle", :stateNameSv "Under beredning"
        :primaryOperation empty-op
        :secondaryOperations [{:id nil :created nil :name "ya-jatkoaika" :description nil
                               :displayNameFi "Jatkoajan hakeminen" :displayNameSv "Ans\u00f6kan om till\u00e4ggstid"}]}))
