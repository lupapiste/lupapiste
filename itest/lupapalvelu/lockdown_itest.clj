(ns lupapalvelu.lockdown-itest
  (:require [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]))

(set! *warn-on-reflection* true)

(with-test-system {}
  (mongo/clear!)

  (against-background [(#'lockdown/sleep-after) => nil]
    (fact "by default lockdown is not enabled"
      (lockdown/lockdown-from-db?) => false)

    (fact "within"
      (fact "not enabled before"
        (lockdown/lockdown-from-db?) => false)
      (lockdown/in-lockdown
        (fact "is enabled during"
          (lockdown/lockdown-from-db?) => true))
      (fact "not enabled afterwards"
        (lockdown/lockdown-from-db?) => false))

    (fact "within nested"
      (fact "not enabled before"
        (lockdown/lockdown-from-db?) => false)
      (lockdown/in-lockdown
        (fact "is enabled during"
          (lockdown/lockdown-from-db?) => true)
        (fact "nested call"
          (lockdown/in-lockdown
            (fact "is still enabled during nested call"
              (lockdown/lockdown-from-db?) => true)))
        (fact "is still enabled after the nested call "
          (lockdown/lockdown-from-db?) => true))
      (fact "not enabled afterwards"
        (lockdown/lockdown-from-db?) => false))

    (fact "body throws"
      (fact "not enabled before"
        (lockdown/lockdown-from-db?) => false)
      (fact "throws"
        (lockdown/in-lockdown
          (throw (ex-info "oh noes" {}))) => (throws #"oh noes"))
      (fact "not enabled afterwards"
        (lockdown/lockdown-from-db?) => false))))
