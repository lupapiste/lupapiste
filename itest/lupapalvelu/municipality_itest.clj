(ns lupapalvelu.municipality-itest
  (:require [lupapalvelu.itest-util :refer [admin command query apply-remote-minimal pena ok?]]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "nothing is found successfully"
  (let [resp (query pena :municipality-borders)]
    resp => ok?
    (:data resp) => empty?))

(facts "municipality-active"
  (fact "only info requests enabled"
    (let [m (query pena :municipality-active :municipality "997")]
      (:applications m) => empty?
      (:infoRequests m) => ["R"]
      (:opening m) => empty?)
    (let [all-active-m (:municipalities (query pena :active-municipalities))
          m (first (filter #(= (:id %) "997") all-active-m))]
      (:applications m) => empty?
      (:infoRequests m) => ["R"]
      (:opening m) => empty?
      ))
  (fact "only applications enabled"
    (let [m (query pena :municipality-active :municipality "998")]
      (:applications m) => ["R"]
      (:infoRequests m) => empty?
      (:opening m) => empty?)
    (let [all-active-m (:municipalities (query pena :active-municipalities))
          m (first (filter #(= (:id %) "998") all-active-m))]
      (:applications m) => ["R"]
      (:infoRequests m) => empty?
      (:opening m) => empty?))

  (fact "nothing enabled, but coming"
    (command admin :update-organization
      :permitType "R"
      :municipality "999"
      :inforequestEnabled false
      :applicationEnabled false
      :openInforequestEnabled false
      :openInforequestEmail "someone@localhost"
      :opening 123)
    (let [m (query pena :municipality-active :municipality "999")]
      (:applications m) => empty?
      (:infoRequests m) => empty?
      (:opening m) => [{:permitType "R", :opening 123}])
    (let [all-active-m (:municipalities (query pena :active-municipalities))]
      (:opening  (first (filter #(= (:id %) "999") all-active-m))) => [{:opening 123, :permitType "R"}])))
