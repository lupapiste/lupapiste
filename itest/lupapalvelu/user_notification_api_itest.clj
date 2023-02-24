(ns lupapalvelu.user-notification-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(fact "Admin adds notification for all applicants"
  (command admin :notifications-update :applicants true :authorities false :title-fi "otsake" :message-fi "Ostakaa makkaraa") => ok?
  (let [users (:users (query admin :users))
        applicants (filter #(= "applicant" (:role %)) users)
        authorities (filter #(= "authority" (:role %)) users)
        notifications (remove nil? (map :notification applicants))]
    (count applicants) => (count notifications)
    (first notifications) => {:message "Ostakaa makkaraa" :title "otsake"}
    (count (remove nil? (map :notification authorities))) => 0))

(fact "Admin adds notification for all applicants and authorities"
  (command admin :notifications-update :applicants true :authorities true :title-fi "otsake2" :message-fi "Unohtakaa askeinen!") => ok?
  (let [users (:users (query admin :users))
        applicants (filter #(= "applicant" (:role %)) users)
        authorities (filter #(= "authority" (:role %)) users)
        applicant-notifications (remove nil? (map :notification applicants))
        authority-notifications (remove nil? (map :notification authorities))]
    (count applicants) => (count applicant-notifications)
    (count authorities) => (count authority-notifications)
    (first applicant-notifications) => {:message "Unohtakaa askeinen!" :title "otsake2"}
    (first authority-notifications) => {:message "Unohtakaa askeinen!" :title "otsake2"}))

(fact "Admin set empty notification for all"
  (command admin :notifications-update :applicants true :authorities true :title-fi "" :message-fi "") => ok?
  (let [users (:users (query admin :users))
        applicants (filter #(= "applicant" (:role %)) users)
        authorities (filter #(= "authority" (:role %)) users)
        applicant-notifications (remove nil? (map :notification applicants))
        authority-notifications (remove nil? (map :notification authorities))]
    (count applicants) => (count applicant-notifications)
    (count authorities) => (count authority-notifications)
    (first applicant-notifications) => {:message "" :title ""}
    (first authority-notifications) => {:message "" :title ""}))
