(ns lupapalvelu.guest-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as sade]
            [lupapalvelu.itest-util :as util]
            [lupapalvelu.user :as usr]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.guest :as guest]))

(def local-db-name (str "test_guest_itest_" (sade/now)))

(mongo/connect!)
(mongo/with-db local-db-name
  (fixture/apply-fixture "minimal")

  (facts "Organization guest authorities"
         (let [admin (usr/with-org-auth (usr/get-user {:username "sipoo"}))
               veikko "veikko.viranomainen@tampere.fi"]
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
                 (guest/update-guest-authority-organization admin veikko "Veikko Viranomainen" "Dude")
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko Viranomainen"
                                                                     :email veikko
                                                                     :role "Dude"}])
           (fact "Organization known guest authorities"
                 (guest/known-guest-authority {:data {:email veikko
                                                      :role "guestAuthority"}}
                                              {:organization "753-R"})=> nil
                 (guest/known-guest-authority {:data {:email "not.known@foo.bar"
                                                      :role "guestAuthority"}}
                                              {:organization "753-R"})=> util/fail?)

           (fact "Update Veikko"
                 (guest/update-guest-authority-organization admin veikko "Veikko The Man" "Boss")
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko The Man"
                                                                     :email veikko
                                                                     :role "Boss"}])
           (fact "Remove guest authority"
                 (guest/update-guest-authority-organization admin "foo@bar.com" "Foo Bar" "Foo")
                 (count (guest/organization-guest-authorities "753-R")) => 2
                 (guest/remove-guest-authority-organization admin "foo@bar.com")
                 (guest/organization-guest-authorities "753-R") => [{:name "Veikko The Man"
                                                                     :email veikko
                                                                     :role "Boss"}])))
  (fact "Valid guest role"
        (guest/valid-guest-role {:data {:role "guest"}}) => nil
        (guest/valid-guest-role {:data {:role "guestAuthority"}}) => nil
        (guest/valid-guest-role {:data {:role "bad"}}) => util/fail?)
  (facts "Application guests and guest authorities"
         (let [app (util/create-and-submit-local-application util/pena :property util/sipoo-property-id :address "Gongti beilu 88")
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
                                                  :text "Mail contents"})) => util/ok?)
           (fact "Application guests: 1 entry"
                 (guest/application-guests (cmd "pena")) => [{:authorityRole nil
                                                              :name ""
                                                              :username "hii@hoo.net"
                                                              :email "hii@hoo.net"
                                                              :role :guest
                                                              :unsubscribed nil
                                                              :inviter "Pena Panaani"}])
           (fact "Invite Mikko"
                 (guest/invite-guest (cmd "pena" {:email mikko
                                                  :role "guest"
                                                  :text "Hello"}))=> util/ok?)
           (fact "Two guests"
                 (count (guest/application-guests (cmd "sonja"))) => 2)
           (fact "Delete hii@hoo.net"
                 (guest/delete-guest-application (cmd "pena" {:username "hii@hoo.net"})) => util/ok?)
           (fact "Mikko's guest details"
                 (guest/application-guests (cmd mikko)) => [{:authorityRole nil
                                                             :name "Mikko Intonen"
                                                             :username mikko
                                                             :email mikko
                                                             :role :guest
                                                             :unsubscribed nil
                                                             :inviter "Pena Panaani"}])
           (fact "Mikko unsubscribes Mikko"
                 (guest/toggle-guest-subscription (cmd mikko {:username mikko
                                                              :unsubscribe true})) => util/ok?
                 (-> mikko cmd guest/application-guests first :unsubscribed) => true?)
           (fact "Pena subscribes Mikko"
                 (guest/toggle-guest-subscription (cmd "pena" {:username mikko
                                                               :unsubscribe false})) => util/ok?
                 (-> mikko cmd guest/application-guests first :unsubscribed) => false?)
           (fact "Sonja unsubscribes Mikko"
                 (guest/toggle-guest-subscription (auth-cmd "sonja" {:username mikko
                                                                     :unsubscribe true})) => util/ok?
                 (-> "sonja" auth-cmd guest/application-guests first :unsubscribed) => true?)
           (fact "Sonja invites Veikko"
                 (guest/invite-guest (auth-cmd "sonja" {:email veikko
                                                        :role "guestAuthority"
                                                        :text "hi"}))=> util/ok?)
           (fact "Duplicate guests"
                 (guest/invite-guest (auth-cmd "sonja" {:email veikko
                                                        :role "guestAuthority"
                                                        :text "hi"})) => util/fail?
                 (guest/invite-guest (cmd "pena" {:email mikko
                                                  :role "guest"
                                                  :text "Hello"})) => util/fail?)
           (fact "Sonja unsubscribes Veikko"
                 (guest/toggle-guest-subscription (auth-cmd "sonja" {:username "veikko"
                                                                     :unsubscribe true})) => util/ok?
                 (-> "pena" cmd guest/application-guests last :unsubscribed) => true?)
           (fact "Veikko subscribes Veikko"
                 (guest/toggle-guest-subscription (cmd "veikko" {:username "veikko"
                                                                    :unsubscribe false})) => util/ok?
                 (-> "veikko" cmd guest/application-guests last :unsubscribed) => false?)
           (fact "Auth modification check"
                 (let [{app :application} (cmd "pena")]
                   (guest/auth-modification-check (cmd "pena" {:username mikko}) app) => nil?
                   (guest/auth-modification-check (cmd "pena" {:username "veikko"}) app) => util/fail?
                   (guest/auth-modification-check (auth-cmd "sonja" {:username mikko}) app) => nil?
                   (guest/auth-modification-check (auth-cmd "sonja" {:username "veikko"}) app) => nil?))
           (fact "Pena deletes Mikko"
                 (guest/delete-guest-application (cmd "pena" {:username mikko})) => util/ok?)
           (fact "Sonja deletes Veikko"
                 (guest/delete-guest-application (auth-cmd "sonja" {:username "veikko"})) => util/ok?)
           (fact "No more guests"
                 (guest/application-guests (cmd mikko)) => util/fail?
                 (guest/application-guests (cmd "pena")) => empty?))))
