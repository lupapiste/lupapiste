(ns lupapalvelu.guest-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as sade]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.user :as usr]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.guest :as guest]))

(def local-db-name (str "test_guest_itest_" (sade/now)))

(mongo/connect!)
(mongo/with-db local-db-name
  (fixture/apply-fixture "minimal")

  (facts "Organization guest authorities"
         (let [admin  (usr/with-org-auth (usr/get-user {:username "sipoo"}))
               veikko "veikko.viranomainen@tampere.fi"
               foo    "foo@bar.com"]
           (fact "Resolve Veikko"
                 (guest/resolve-guest-authority-candidate admin veikko)
                 => {:firstName "Veikko" :lastName "Viranomainen" :hasAccess false})
           (fact "Resolve Luukas"
                 (guest/resolve-guest-authority-candidate admin "luukas.lukija@sipoo.fi")
                 => {:firstName "Luukas" :lastName "Lukija" :hasAccess true})
           (fact "Resolve unknown"
                 (guest/resolve-guest-authority-candidate admin "unknown@example.com")
                 => {:hasAccess false})
           (fact "No guest authorities"
                 (guest/organization-guest-authorities "753-R") => nil)
           (fact "Veikko as a new guest authority"
                 (guest/update-guest-authority-organization admin veikko "Veikko" "Viranomainen" "Dude")
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko Viranomainen"
                                                                     :email veikko
                                                                     :description "Dude"}])
           (fact "Organization known guest authorities"
                 (guest/known-guest-authority {:data {:email veikko
                                                      :role "guestAuthority"}
                                               :application {:organization "753-R"}})=> nil
                 (guest/known-guest-authority {:data {:email "not.known@foo.bar"
                                                      :role "guestAuthority"}
                                               :application {:organization "753-R"}})=> itest/fail?)

           (fact "Update Veikko"
                 (guest/update-guest-authority-organization admin veikko "Veikko" "The Man" "Boss")
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko The Man"
                                                                     :email veikko
                                                                     :description "Boss"}])
           (fact "Guest authority invite creates new user if needed"
                 (usr/get-user-by-email foo) => falsey
                 (guest/update-guest-authority-organization admin foo "Foo" "Bar" "Foo")
                 (count (guest/organization-guest-authorities "753-R")) => 2
                 (usr/get-user-by-email foo) => (contains {:enabled false,
                                                           :firstName "Foo"
                                                           :lastName "Bar"
                                                           :email foo
                                                           :username foo
                                                           :role "authority"}))
           (fact "Remove guest authority"
                 (guest/remove-guest-authority-organization admin foo)
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko The Man"
                                                                     :email veikko
                                                                     :description "Boss"}])))
  (fact "Valid guest role"
        (guest/valid-guest-role {:data {:role "guest"}}) => nil
        (guest/valid-guest-role {:data {:role "guestAuthority"}}) => nil
        (guest/valid-guest-role {:data {:role "bad"}}) => itest/fail?)
  (facts "Application guests and guest authorities"
         (let [app (itest/create-and-submit-local-application itest/pena :property itest/sipoo-property-id :address "Gongti beilu 88")
               cmd (fn [username & [data]] {:user (usr/get-user {:username username})
                                            :application (domain/get-application-no-access-checking (:id app))
                                            :created (sade/now)
                                            :data (assoc data :id (:id app))})
               auth-cmd (fn [username & [data]]
                          (let [{:keys [user] :as cmd} (cmd username data)]
                            (assoc cmd :user (usr/with-org-auth user))))
               mikko "mikko@example.com"
               veikko "veikko.viranomainen@tampere.fi"]
           (fact "Application guests empty"
                 (guest/application-guests (cmd "pena")) => empty?)
           (fact "Invite non-existing user"
                 (guest/invite-guest (cmd "pena" {:email "hii@hoo.net"
                                                  :role "guest"
                                                  :text "Mail contents"})) => itest/ok?)
           (fact "Application guests: 1 entry"
                 (guest/application-guests (cmd "pena")) => [{:description nil
                                                              :name ""
                                                              :username "hii@hoo.net"
                                                              :email "hii@hoo.net"
                                                              :role :guest
                                                              :unsubscribed nil
                                                              :inviter "Pena Panaani"}])
           (fact "Invite Mikko"
                 (guest/invite-guest (cmd "pena" {:email mikko
                                                  :role "guest"
                                                  :text "Hello"}))=> itest/ok?)
           (fact "Two guests"
                 (count (guest/application-guests (cmd "sonja"))) => 2)
           (fact "Delete hii@hoo.net"
                 (guest/delete-guest-application (cmd "pena" {:username "hii@hoo.net"})) => itest/ok?)
           (fact "Mikko's guest details"
                 (guest/application-guests (cmd mikko)) => [{:description nil
                                                             :name "Mikko Intonen"
                                                             :username mikko
                                                             :email mikko
                                                             :role :guest
                                                             :unsubscribed nil
                                                             :inviter "Pena Panaani"}])
           (fact "Mikko unsubscribes Mikko"
                 (guest/toggle-guest-subscription (cmd mikko {:username mikko
                                                              :unsubscribe true})) => itest/ok?
                 (-> mikko cmd guest/application-guests first :unsubscribed) => true?)
           (fact "Pena subscribes Mikko"
                 (guest/toggle-guest-subscription (cmd "pena" {:username mikko
                                                               :unsubscribe false})) => itest/ok?
                 (-> mikko cmd guest/application-guests first :unsubscribed) => false?)
           (fact "Sonja unsubscribes Mikko"
                 (guest/toggle-guest-subscription (auth-cmd "sonja" {:username mikko
                                                                     :unsubscribe true})) => itest/ok?
                 (-> "sonja" auth-cmd guest/application-guests first :unsubscribed) => true?)
           (fact "Sonja invites Veikko"
                 (guest/invite-guest (auth-cmd "sonja" {:email veikko
                                                        :role "guestAuthority"
                                                        :text "hi"}))=> itest/ok?)
           (fact "Duplicate guests"
                 (guest/invite-guest (auth-cmd "sonja" {:email veikko
                                                        :role "guestAuthority"
                                                        :text "hi"})) => itest/fail?
                 (guest/invite-guest (cmd "pena" {:email mikko
                                                  :role "guest"
                                                  :text "Hello"})) => itest/fail?)
           (fact "Sonja unsubscribes Veikko"
                 (guest/toggle-guest-subscription (auth-cmd "sonja" {:username "veikko"
                                                                     :unsubscribe true})) => itest/ok?
                 (-> "pena" cmd guest/application-guests last :unsubscribed) => true?)
           (fact "Veikko subscribes Veikko"
                 (guest/toggle-guest-subscription (cmd "veikko" {:username "veikko"
                                                                    :unsubscribe false})) => itest/ok?
                 (-> "veikko" cmd guest/application-guests last :unsubscribed) => false?)
           (fact "Auth modification check"
             (guest/auth-modification-check (cmd "pena" {:username mikko})) => nil?
             (guest/auth-modification-check (cmd "pena" {:username "veikko"})) => itest/fail?
             (guest/auth-modification-check (auth-cmd "sonja" {:username mikko})) => nil?
             (guest/auth-modification-check (auth-cmd "sonja" {:username "veikko"})) => nil?)
           (fact "Pena deletes Mikko"
                 (guest/delete-guest-application (cmd "pena" {:username mikko})) => itest/ok?)
           (fact "Sonja deletes Veikko"
                 (guest/delete-guest-application (auth-cmd "sonja" {:username "veikko"})) => itest/ok?)
           (fact "No more guests"
                 (guest/application-guests (cmd mikko)) => itest/fail?
                 (guest/application-guests (cmd "pena")) => empty?))))
