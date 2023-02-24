(ns lupapalvelu.open-inforequest-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.open-inforequest :refer :all]))

(facts inforequest-context
  (fact "writer in open inforequest"
    (-> (inforequest-context {:user        {:id "1"}
                              :application {:infoRequest true
                                            :auth        [{:id "1" :role "writer"} {:id "2" :role "reader"}]}})
        :permissions)
    => #{:application/write}

    (provided (lupapalvelu.permissions/get-permissions-by-role :inforequest "writer") => #{:application/write}))

  (fact "reader in open inforequest"
    (-> (inforequest-context {:user        {:id "2"}
                              :application {:infoRequest true
                                            :auth        [{:id "1" :role "writer"} {:id "2" :role "reader"}]}})
        :permissions)
    => #{:application/read}

    (provided (lupapalvelu.permissions/get-permissions-by-role :inforequest "reader") => #{:application/read}))

  (fact "non-inforequest application"
    (-> (inforequest-context {:user        {:id "2"}
                              :application {:infoRequest false
                                            :auth        [{:id "1" :role "writer"} {:id "2" :role "reader"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "user not in inforequest auth"
    (-> (inforequest-context {:user        {:id "3"}
                              :application {:infoRequest true
                                            :auth        [{:id "1" :role "writer"} {:id "2" :role "reader"}]}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "no application in command"
    (-> (inforequest-context {:user {:id "1"}})
        :permissions)
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "Company auth in inforequest"
    (-> (inforequest-context {:user        {:id      "100"
                                            :company {:id     "id-com"
                                                      :role   "user"
                                                      :submit false}}
                              :application {:infoRequest true
                                            :auth        [{:id "1" :role "reader"}
                                                          {:id   "id-com"
                                                           :role "writer"}]}})
        :permissions)
    => #{:application/cancel}))
