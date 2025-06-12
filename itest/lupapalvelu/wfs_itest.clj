(ns lupapalvelu.wfs-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [server-address with-test-system]]
            [lupapalvelu.wfs :as wfs]
            [midje.sweet :refer [fact falsey]]))

(with-test-system (-> {}
                      test-system/with-remote-components)
  (let [local-wfs         (str (server-address) "/dev/krysp")
        local-private-wfs (str (server-address) "/dev/private-krysp")]
    (fact "nil url is not alive"
      (wfs/wfs-is-alive? nil nil nil) => falsey)
    (fact "blank url is not alive"
      (wfs/wfs-is-alive? "" nil nil) => falsey)
    (fact "invalid krysp wfs url is not alive"
      (wfs/wfs-is-alive? "invalid_url" nil nil) => nil)
    (fact "local test krysp wfs url is alive"
      (wfs/wfs-is-alive? local-wfs nil nil) => true)
    (fact "local private test krysp wfs url without credentials is not alive"
      (wfs/wfs-is-alive? local-private-wfs nil nil) => falsey)
    (fact "local private test krysp wfs url without password is not alive"
      (wfs/wfs-is-alive? local-private-wfs "pena" nil) => falsey)
    (fact "local private test krysp wfs url with credentials is alive"
      (wfs/wfs-is-alive? local-private-wfs "pena" "pena") => true)))
