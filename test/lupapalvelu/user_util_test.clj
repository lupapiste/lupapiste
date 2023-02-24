(ns lupapalvelu.user-util-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.user-api :refer :all]
            [lupapalvelu.user-utils :refer :all]))

(facts "Authority admin can edit authority info"
       (let [auth-admin-1 {:email    "paakayttaja@sipoo.fi"
                           :orgAuthz {:753-R #{"authorityAdmin"}}}
             ;; use strings for some auths, so that we ensure they are sanitized
             auth-admin-2 {:email    "paakayttaja@naantali.fi"
                           :orgAuthz {:564-R #{"authorityAdmin"}}}
             auth-admin-3 {:email    "paakayttaja@example.com"
                           :orgAuthz {:564-R #{"authorityAdmin"}
                                      :753-R #{:authorityAdmin}}}
             auth-admin-4 {:email    "paakayttaja-b@example.com"
                           :orgAuthz {:564-R #{:authorityAdmin}
                                      :753-R #{:commenter}}}
             authority-1  {:email    "viranomainen@sipoo.fi"
                           :orgAuthz {:753-R #{:authority :approver}}}
             authority-2  {:email    "kommentoija@sipoo.fi"
                           :orgAuthz {:753-R #{:commenter}
                                      :564-R #{:commenter}}}
             authority-3  {:email    "viranomainen@sipoo.fi"
                           :orgAuthz {:564-R #{:authority :approver}}}]
    (fact "Auth admin can't edit authority if email domain is different"
      (admin-and-user-have-same-email-domain? auth-admin-1 authority-1) => true
      (admin-and-user-have-same-email-domain? auth-admin-2 authority-1) => false)

    (fact "Auth admin can't view authority in different org"
      (auth-admin-can-view-authority? authority-1 auth-admin-1) => true
      (auth-admin-can-view-authority? authority-2 auth-admin-1) => true
      (auth-admin-can-view-authority? authority-3 auth-admin-1) => false
      (auth-admin-can-view-authority? authority-1 auth-admin-2) => false
      (auth-admin-can-view-authority? authority-2 auth-admin-2) => true
      (auth-admin-can-view-authority? authority-3 auth-admin-2) => true
      (auth-admin-can-view-authority? authority-1 auth-admin-3) => true
      (auth-admin-can-view-authority? authority-2 auth-admin-3) => true
      (auth-admin-can-view-authority? authority-3 auth-admin-3) => true
      (auth-admin-can-view-authority? authority-1 auth-admin-4) => false
      (auth-admin-can-view-authority? authority-2 auth-admin-4) => true
      (auth-admin-can-view-authority? authority-3 auth-admin-4) => true)

    (fact "Authority is not authority in multiple organizations"
      (authority-has-only-one-org? authority-1) => true
      (authority-has-only-one-org? authority-2) => false)))
