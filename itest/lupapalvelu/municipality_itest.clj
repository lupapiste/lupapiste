(ns lupapalvelu.municipality-itest
  (:require [lupapalvelu.itest-util :refer [admin command query apply-remote-minimal pena ok?]]
            [midje.sweet :refer :all]
            [sade.date :as date]))

(apply-remote-minimal)

(def future-date (-> (date/now) (date/plus :week) (date/timestamp)))
(def past-date   (-> (date/now) (date/minus :week) (date/timestamp)))

(fact "nothing is found successfully"
  (let [resp (query pena :municipality-borders)]
    resp => ok?
    (:data resp) => empty?))

(facts "municipality-active"
  (fact "only info requests enabled"
    (let [m (query pena :municipality-active :municipality "980")]
      (:applications m) => empty?
      (:infoRequests m) => ["R"]
      (:opening m) => empty?)
    (let [all-active-m (:municipalities (query pena :active-municipalities))
          m (first (filter #(= (:id %) "980") all-active-m))]
      (:applications m) => empty?
      (:infoRequests m) => ["R"]
      (:opening m) => empty?))
  (fact "only applications enabled"
    (let [m (query pena :municipality-active :municipality "981")]
      (:applications m) => ["R"]
      (:infoRequests m) => empty?
      (:opening m) => empty?)
    (let [all-active-m (:municipalities (query pena :active-municipalities))
          m (first (filter #(= (:id %) "981") all-active-m))]
      (:applications m) => ["R"]
      (:infoRequests m) => empty?
      (:opening m) => empty?))

  (fact "nothing enabled, but coming"
    (command admin :update-organization
      :permitType "R"
      :municipality "989"
      :inforequestEnabled false
      :applicationEnabled false
      :openInforequestEnabled false
      :openInforequestEmail "someone@localhost"
      :opening future-date
      :pateEnabled false) => ok?
    (let [m (query pena :municipality-active :municipality "989")]
      (:applications m) => empty?
      (:infoRequests m) => empty?
      (:opening m) => [{:permitType "R", :opening future-date}])
    (let [all-active-m (:municipalities (query pena :active-municipalities))]
      (:opening  (first (filter #(= (:id %) "989") all-active-m))) => [{:opening future-date, :permitType "R"}]))
  (fact "nothing enabled, and the opening date has passed"
    (command admin :update-organization
      :permitType "R"
      :municipality "989"
      :inforequestEnabled false
      :applicationEnabled false
      :openInforequestEnabled false
      :openInforequestEmail "someone@localhost"
      :opening past-date
      :pateEnabled false) => ok?
    (let [m (query pena :municipality-active :municipality "989")]
      (:applications m) => empty?
      (:infoRequests m) => empty?
      (:opening m) => empty?)
    (let [all-active-m (:municipalities (query pena :active-municipalities))]
      (:opening  (first (filter #(= (:id %) "989") all-active-m))) => empty?))
  (fact "opening is not included, if applications enabled"
    (command admin :update-organization
             :permitType "R"
             :municipality "989"
             :inforequestEnabled false
             :applicationEnabled true
             :openInforequestEnabled false
             :openInforequestEmail "someone@localhost"
             :opening future-date
             :pateEnabled false) => ok?
    (let [resp (query pena :municipality-active :municipality "989")]
      (:applications resp) => (just "R")
      (:opening resp) => empty?))

  (fact "open-inforequest is taken into account"
    (command admin :update-organization
             :permitType "R"
             :municipality "989"
             :inforequestEnabled false
             :applicationEnabled false
             :openInforequestEnabled true
             :openInforequestEmail "someone@localhost"
             :opening future-date
             :pateEnabled false) => ok?
    (let [resp (query pena :municipality-active :municipality "989")]
      (:applications resp) => empty?
      (:infoRequests resp) => (just "R")
      (:opening resp) => (just #{(just {:opening future-date :permitType "R"})})))

  (fact "municipality in two organizations"
    (let [oulu (query pena :municipality-active :municipality "564")
          porvoo (query pena :municipality-active :municipality "638")]
      (fact "Oulu initial" (:applications oulu) => (just #{"YM" "YI" "YL" "MAL" "VVVL" "R" "YA"}))
      (fact "Porvoo initial" (:applications porvoo) => (just ["R" "YI" "YL"])))
    (command admin :add-scope
             :organization "564-YMP"                        ; Oulu YMP
             :permitType "YM"
             :municipality "638" ; Naantali in minimal
             :inforequestEnabled true
             :applicationEnabled true
             :openInforequestEnabled false
             :openInforequestEmail ""
             :opening nil) => ok?
    (fact "Porvoo now has also YMP"
      (:applications (query pena :municipality-active :municipality "638")) => (just ["YM" "R" "YI" "YL"] :in-any-order))))
