(ns lupapalvelu.i18n-test
  (:use [lupapalvelu.i18n]
        [midje.sweet])
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
            [sade.env :as env]))

(facts
  (fact "in dev-mode messy placeholder is returned"
    (unknown-term "kikka") => "???kikka???"
    (provided
      (env/dev-mode?) => true))

(fact "in non-dev-mode empty string is returned"
  (unknown-term "kikka") => ""
  (provided
    (env/dev-mode?) => false)))

(facts "regression test for line parsing"
  (read-lines ["error.vrk:BR319:lammitustapa: this: should: work!"
               "kukka: kakka"]) => {"error.vrk:BR319:lammitustapa" "this: should: work!"
                                    "kukka" "kakka"})
