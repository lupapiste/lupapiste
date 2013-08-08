(ns lupapalvelu.organization-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet])
  (:require [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(facts "organization"
  (query pena :organization :organizationId "INVALID") =not=> ok?
  (let [resp (query pena :organization :organizationId "753-R")]
    resp => ok?
    (keys resp) => (just [:ok :attachments :links])))
