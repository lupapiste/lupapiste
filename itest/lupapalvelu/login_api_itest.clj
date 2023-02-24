(ns lupapalvelu.login-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [sade.http :as http]))

(apply-remote-minimal)

(def cookie-store (atom {}))

(defn request [method endpoint kvs]
  (http method
        (str (server-address) endpoint)
        (merge {:throw-exceptions false
                :follow-redirects false
                :cookie-store     (->cookie-store cookie-store)}
               (when (and (seq kvs) (= method http/post))
                 {:form-params (apply hash-map kvs)}))))

(defn GET [endpoint & kvs]
  (request http/get endpoint kvs))

(defn POST [endpoint & kvs]
  (request http/post endpoint kvs))

(defn redirects [{:keys [status headers]} location]
  (fact {:midje/description (str "Redirects to " location)}
    status => 302
    (get headers "Location") => location))

(defn in-html [response & xs]
  (let [{:keys [status headers body]} response]
    (fact "Status OK"
      status => 200)
    (fact "HTML"
      (get headers "Content-Type") => (contains "text/html"))
    (apply in-text body xs)))

(defn logout []
  (fact "Logout"
    (GET "/app/sv/logout") => http302?
    (reset! cookie-store {})))

(facts "Login"
  (fact "Root page redirects to /login/fi"
    (redirects (GET "/")  (str (server-address) "/login/fi")))
  (fact "/login/fi: Finnish contents, password not visible"
    (in-html (GET "/login/fi")
             "Kirjaudu" "Käyttäjätunnus (sähköpostiosoite)" "login-username"
             "Seuraava" "button-next" "Ostoksille" "SVENSKA"
             ["#login-password" "#button-login" "error-note"]))
  (fact "/login: Finnish is the default language"
    (in-html (GET "/login")
             "Kirjaudu" "Käyttäjätunnus (sähköpostiosoite)" "login-username"
             "Seuraava" "button-next" "Ostoksille" "SVENSKA"
             ["#login-password" "#button-login" "error-note"])
    (in-html (GET "/login/foobar")
             "Kirjaudu" "Käyttäjätunnus (sähköpostiosoite)" "login-username"
             "Seuraava" "button-next" "Ostoksille" "SVENSKA"
             ["#login-password" "#button-login" "error-note"]))
  (fact "/login: Username without password shows password field"
    (in-html (POST "/login" :username "hello.world@example.com")
             "hello.world@example.com" "login-username"
             "Salasana" "login-password" "button-login"
             ["button-next" "error-note"]))
  (fact "/login: Password without username"
    (in-html (POST "/login" :password "topsecret")
             "topsecret" "login-username"
             "Salasana" "login-password" "button-login"
             ["button-next" "error-note"]))
  (fact "/login: Username and password do not match"
    (in-html (POST "/login" :username "pena" :password "bad")
             "pena" "bad" "login-username"
             "Salasana" "login-password" "button-login"
             "error-note" "Tunnus tai salasana on väärin."
             ["button-next"]))
  (fact "/login: Unknown user"
    (in-html (POST "/login" :username "unknown@example.com" :password "whatever")
             "unknown@example.com" "whatever" "login-username"
             "Salasana" "login-password" "button-login"
             "error-note" "Tunnus tai salasana on väärin."
             ["button-next"]))
  (fact "/login: Pena logs in successfully"
    (redirects (POST "/login" :username "pena" :password "pena")
               "/app/fi/applicant"))
  (fact "Now Pena cannot re-login or reset password"
    (redirects (GET "/") "/app/fi/applicant")
    (redirects (GET "/login") "/")
    (redirects (GET "/login/fi") "/")
    (redirects (GET "/login/sv") "/")
    (redirects (GET "/login/foo") "/")
    (redirects (POST "/login" :usernname "foo") "/")
    (redirects (GET "/reset-password") "/")
    (redirects (GET "/reset-password/fi") "/")
    (redirects (GET "/reset-password/sv") "/")
    (redirects (GET "/reset-password/foo") "/")
    (redirects (POST "/reset-password" :username "hii") "/"))

  (fact "Pena logs out"
    (redirects (GET "/app/sv/logout") (str (server-address) "/login/sv")))

  (reset! cookie-store {})
  (fact "/login/sv: Swedish contents"
    (in-html (GET "/login/sv")
             "Logga in" "SUOMI" "Användarnamn (e-postadress)"
             "Nästa" "button-next"
             ["#login-password" "#button-login" "error-note"]))
  (fact "/login: lang can be passed as a parameter"
    (in-html (POST "/login" :username "unknown@example.com" :password "topsecret" :lang "sv")
             "error-note" "Fel användarnamn eller lösenord."
             "button-login" "unknown@example.com" "topsecret"
             ["button-next"]))
  (fact "/login: Log in Sven in Swedish"
    (redirects (POST "/login" :username "sven@example.com" :password "sven" :lang "sv")
               "/app/sv/applicant"))
  (fact "Set Sven's default language to Swedish"
    (command sven :update-user :firstName "Sven" :lastName "Svensson"
             :language "sv") => ok?)
  (fact "Log Sven out"
    (GET "/app/sv/logout") => http302?
    (reset! cookie-store {}))
  (fact "From now Sven is always redirected to Swedish Lupapiste after login"
    (redirects (POST "/login" :username "sven@example.com" :password "sven")
               "/app/sv/applicant"))
  (fact "Logout Sven"
    (logout))
  (fact "Login Sonja"
    (redirects (POST "/login" :username "  SONJA  " :password "sonja")
               "/app/fi/authority"))
  (fact "Logout Sonja"
    (logout))
  (facts "/login: AD login"
    (let [pori-url (str (server-address) "/api/saml/ad-login/pori.fi")]
      (fact "Password is not required"
        (redirects (POST "/login" :username "foo@pori.fi")
                   pori-url))
      (fact "Password does not matter if given"
        (redirects (POST "/login" :username "  BIG@PORI.FI  ")
                   pori-url)))))

(facts "/reset-password"
  (fact "/reset-password: Finnish is the default language"
    (in-html (GET "/reset-password")
             "SVENSKA" "Saanko linkin?" "Salasanan vaihtaminen"
             "reset-password-email" "button-reset-password"
             ["error-note" "primary-note"])
    (in-html (GET "/reset-password/yup")
             "SVENSKA" "Saanko linkin?" "Salasanan vaihtaminen"
             "reset-password-email" "button-reset-password"
             ["error-note" "primary-note"]))
  (fact "/reset-password: Swedish"
    (in-html (GET "/reset-password/sv")
             "SUOMI" "Ändra lösenord"
             "Uppge din e-postadress så skickar vi dig en länk för att ändra lösenord."
             "reset-password-email" "button-reset-password"
             ["error-note" "primary-note"]))
  (fact "/reset-password: Blank email is no-op"
    (in-html (POST "/reset-password" :username "   ")
             "SVENSKA" "Saanko linkin?" "Salasanan vaihtaminen"
             "reset-password-email" "button-reset-password"
             ["error-note" "primary-note"]))
  (fact "/reset-password: Invalid email"
    (in-html (POST "/reset-password" :username "bad bad bad")
             "error-note" "bad bad bad"
             "Virheellinen sähköpostiosoite"))
  (fact "/reset-password: User information is not leaked, every valid email is accepted."
    (last-email) ;; Empty inbox
    (in-html (POST "/reset-password" :username " UNKNOWN@example.COM ")
             "primary-note"
             "Sähköposti lähetetty osoitteeseen unknown@example.com."
             "Seuraa ohjeita sähköpostistasi."
             ["error-note" "UNKNOWN@example.COM"])
    (last-email) => nil)
  (fact "/reset-password: success"
    (last-email) ;; Empty inbox
    (in-html (POST "/reset-password" :username " MIKKO@example.COM ")
             "primary-note"
             "Sähköposti lähetetty osoitteeseen mikko@example.com."
             "Seuraa ohjeita sähköpostistasi."
             ["error-note" "MIKKO@example.COM"])
    (last-email) => (contains {:subject "Lupapiste: Uusi salasana"
                               :to      "Mikko Intonen <mikko@example.com>"})))

(facts "/info"
  (fact "/info: Finnish, no session"
    (in-html (GET "/info")
             "SVENSKA"
             "id=\"registry\""
             "Tässä tietosuojaselosteessa kuvataan"
             "id=\"accessibility\""
             "Saavutettavuudesta säädetään laissa"
             "id=\"terms\""
             "Palvelun käyttö on pääosin maksutonta."
             "id=\"licenses\""
             "Lisenssinsaajan on aineistoa"
             "Kirjaudu / Rekisteröidy"
             "/login/fi")
    (in-html (GET "/info/dum")
             "SVENSKA"
             "id=\"registry\""
             "Tässä tietosuojaselosteessa kuvataan"
             "id=\"accessibility\""
             "Saavutettavuudesta säädetään laissa"
             "id=\"terms\""
             "Palvelun käyttö on pääosin maksutonta."
             "id=\"licenses\""
             "Lisenssinsaajan on aineistoa"
             "Kirjaudu / Rekisteröidy"
             "/login/fi"))
  (fact "/info/sv: Swedish, no session"
    (in-html (GET "/info/sv")
             "SUOMI"
             "id=\"registry\""
             "I denna dataskyddsbeskrivning beskrivs"
             "id=\"accessibility\""
             "Tillgänglighet regleras av lagen"
             "Logga in / Registrera dig"
             "/login/sv"
             ["id=\"terms\"" "id=\"licenses\""]))
  (fact "Pena logs in"
    (POST "/login" :username "pena" :password "pena") => http302?)
  (fact "/info: Finnish with session"
    (in-html (GET "/info")
             "SVENSKA"
             "id=\"registry\""
             "Tässä tietosuojaselosteessa kuvataan"
             "id=\"accessibility\""
             "Saavutettavuudesta säädetään laissa"
             "id=\"terms\""
             "Palvelun käyttö on pääosin maksutonta."
             "id=\"licenses\""
             "Lisenssinsaajan on aineistoa"
             "Hankkeet"
             "\"/\""))
  (fact "Logout Pena"
    (logout))
  (fact "Login Sven"
    (POST "/login" :username "sven@example.com" :password "sven")
    => http302?)
  (fact "/info: Shown in Sven's language"
    (in-html (GET "/info")
             "SUOMI"
             "id=\"registry\""
             "I denna dataskyddsbeskrivning beskrivs"
             "id=\"accessibility\""
             "Tillgänglighet regleras av lagen"
             "Projekt"
             "\"/\""
             ["id=\"terms\"" "id=\"licenses\""]))
  (fact "/info: Override user's language"
    (in-html (GET "/info/fi")
             "SVENSKA"
             "id=\"registry\""
             "Tässä tietosuojaselosteessa kuvataan"
             "id=\"accessibility\""
             "Saavutettavuudesta säädetään laissa"
             "id=\"terms\""
             "Palvelun käyttö on pääosin maksutonta."
             "id=\"licenses\""
             "Lisenssinsaajan on aineistoa"
             "Hankkeet"
             "\"/\""))
  (fact "Logot Sven"
    (logout)))

(facts "/page"
  (fact "/page: No such page"
    (GET "/page/bad/fi") => http404?)
  (fact "/page: Disallowed characters"
    (GET "/page/./fi") => http404?)
  (fact "/page: Registry is in both Finnish and Swedish"
    (in-html (GET "/page/registry")
             "SVENSKA"
             "Rekisteri- ja tietosuojaseloste"
             "Tätä tietosuojaselostetta sovelletaan"
             "Kirjaudu / Rekisteröidy"
             "/login/fi")
    (in-html (GET "/page/registry/sv")
             "SUOMI"
             "Register- och dataskyddsbeskrivning"
             "Denna dataskyddsbeskrivning tillämpas"
             "Logga in / Registrera dig"
             "/login/sv"))
  (fact "/page: Terms is only in Finnish"
    (in-html (GET "/page/terms"
                  "Käyttöehdot"
                  "Sopimus on voimassa toistaiseksi."
                  "Kirjaudu / Rekisteröidy"
                  "/login/fi"
                  ["SVENSKA"]))
    (GET "/page/terms/sv") => http404?)
  (fact "Sven logs in"
    (POST "/login" :username "sven@example.com" :password "sven")
    => http302?)
  (fact "/page: User's language is honored"
    (GET "/page/terms") => http404?
    (in-html (GET "/page/company-contract")
             "SUOMI"
             "Företagskontoavtal"
             "En eller flera administratörer"
             "Projekt"
             "\"/\"")
    (in-html (GET "/page/company-contract/pim")
             "SUOMI"
             "Företagskontoavtal"
             "En eller flera administratörer"
             "Projekt"
             "\"/\""))
  (fact "/page: Explicit language"
    (in-html (GET "/page/company-contract/fi")
             "SVENSKA"
             "Yritystilisopimus"
             "Toimittajalla on oikeus vapaasti"
             "Hankkeet"
             "\"/\""))
  (fact "Logout Sven"
    (logout)))
