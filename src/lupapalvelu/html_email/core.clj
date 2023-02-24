(ns lupapalvelu.html-email.core
  "Successor and to be replacement for `lupapalvelu.email`. In the future, only HTML emails
  are supported."
  (:require [lupapalvelu.html-email.template :as template]
            [lupapalvelu.i18n :as i18n]
            [sade.email :as email]
            [sade.strings :as ss]
            [sade.validators :refer [valid-email?]]
            [taoensso.timbre :as timbre]))

(defn- make-to-field
  "Returns either fully-formed recipient (Randy Random <randy@example.net) or just the email
  address. If email is not valid or blacklisted, returns nil."
  [to]
  (let [clean     #(some->> % (remove (set "<>,")) (apply str))
        email     (some-> (:email to to) clean ss/trim ss/lower-case)
        full-name (some->> [(:firstName to) (:lastName to)]
                           (map (comp ss/trim clean))
                           (ss/join-non-blanks " ")
                           ss/blank-as-nil)]
    (cond
      (not (valid-email? email))
      (timbre/errorf "Bad email address %s." email)

      (email/blacklisted? email)
      (timbre/errorf "Blacklisted email address %s" email)

      full-name (format "%s <%s>" full-name email)
      :else email)))

(defn send-template-email
  "Sends email using HTML templates. Options map keys:

  `to`: Either an email address or user.

  `template-id`: Template to be used

  `lang` (optional): Either an individual lang or list of langs. If nil then the email has
  every language version. Note: if `lang` is nil and `to` is user, the user's language is
  used as fallback.

  `base-template` (optional) :id, :context map that defines the used base template."
  [options context]
  (if-let [to (some-> options :to make-to-field)]
    (let [{:keys [subject body]
           } (template/render-email (update options :lang
                                            #(or %
                                                 (some-> options :to :language)
                                                 i18n/supported-langs))
                                    context)]
      (email/send-mail to subject :html body))
    (timbre/errorf "Email sending for template %s failed." (:template-id options))))
