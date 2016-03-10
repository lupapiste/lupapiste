(ns lupapalvelu.municipality-itest
  (:require [lupapalvelu.itest-util :refer [query apply-remote-minimal pena ok?]]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "nothing is found successfully"
  (let [resp (query pena :municipality-borders)]
    resp => ok?
    (:data resp) => empty?))
