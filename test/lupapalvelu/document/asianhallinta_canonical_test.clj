(ns lupapalvelu.document.asianhallinta_canonical_test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]))

(fl/facts* "UusiAsia canonical"
  (let [canonical (ah/application-to-asianhallinta-canonical poikkeus-test/poikkari-hakemus "fi") => truthy]
    (facts "Has (at least) required elements"
      (fact "UusiAsia" (keys (get-in canonical [:UusiAsia])) => (contains [:Tyyppi
                                                                           :Kuvaus
                                                                           :Kuntanumero
                                                                           :Hakijat] :in-any-order))
      (fact "First Hakija of Hakijat has Henkilo" (keys (:Hakija (first (get-in canonical [:UusiAsia :Hakijat])))) => (contains [:Henkilo]))
      (fact "Maksaja is yritys, and has Laskuviite and Verkkolaskutustieto"
        (get-in canonical [:UusiAsia :Maksaja]) => (contains [:Yritys :Laskuviite :Verkkolaskutustieto] :in-any-order)))))

