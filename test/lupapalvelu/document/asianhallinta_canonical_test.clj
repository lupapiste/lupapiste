(ns lupapalvelu.document.asianhallinta_canonical_test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]))

(fl/facts* "UusiAsia canonical"
  (let [canonical (ah/application-to-asianhallinta-canonical poikkeus-test/poikkari-hakemus "fi") => truthy]))

