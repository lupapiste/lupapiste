(ns lupapalvelu.user-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(fact
  (apply-remote-minimal)
  (let [resp (query pena :user)]
    (success resp) => true
    (:user resp) => (contains {:enabled true
                               :firstName "Pena"
                               :lastName "Panaani"
                               :username "pena"
                               :street "Paapankuja 12"
                               :city "Piippola"
                               :zip "010203"
                               :phone "0102030405"
                               :email "pena"
                               :personId "010203-0405"
                               :role "applicant"}))

  (success (command pena :save-user-info :firstName "f" :lastName "l" :street "s" :city "c" :zip "z" :phone "p")) => true

  (:user (query pena :user)) => (contains {:firstName "f"
                                                    :lastName "l"
                                                    :street "s"
                                                    :city "c"
                                                    :zip "z"
                                                    :phone "p"}))
