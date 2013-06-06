(ns lupapalvelu.i18n-test
  (:use [lupapalvelu.i18n]
        [midje.sweet])
  (:require [sade.env :as env]
            [clojure.tools.logging :as log]))

(facts
  (fact "in dev-mode messy placeholder is returned"
    (unknown-term "kikka") => "???kikka???"
    (provided
      (env/dev-mode?) => true))
  (fact "in non-dev-mode empty string is returned and stuff is logged"
    (unknown-term "kikka") => ""
    (provided
      (env/dev-mode?) => false
      (log/log* anything :error anything "unknown localization term 'kikka'") => irrelevant)))

(facts "regression test for line parsing"
  (read-lines ["error.vrk:BR319:lammitustapa: this: should: work!"
               "kukka: kakka"]) => {"error.vrk:BR319:lammitustapa" "this: should: work!"
                                    "kukka" "kakka"})