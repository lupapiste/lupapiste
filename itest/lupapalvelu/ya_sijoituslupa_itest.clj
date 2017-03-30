(ns lupapalvelu.ya-sijoituslupa-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.states :as states]))

(apply-remote-minimal)

(facts "YA Sijoituslupa basic case"
  (let [{:keys [id permitSubtype]} (create-and-submit-application pena
                                                                  :propertyId tampere-property-id
                                                                  :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        verdict-id (:verdictId (command jussi :new-verdict-draft :id id))
        now-ts     (now)
        verdicts   (:verdicts (query-application jussi id))]
    permitSubtype => "sijoituslupa"
    (count verdicts) => 1
    (-> verdicts first :sopimus) => falsey
    (fact "Jussi gives verdict by hand"
      (command jussi :save-verdict-draft
               :id id
               :verdictId verdict-id
               :backendId "321"
               :status 1
               :name "Juice"
               :given now-ts
               :official now-ts
               :text ""
               :agreement false :section "" :lang "fi") => ok?
      (command jussi :publish-verdict :id id :verdictId verdict-id :lang "fi") => ok?)

    (let [app-with-verdict (query-application pena id)]
      (:permitSubtype app-with-verdict) => "sijoituslupa"
      (fact "Sijoituslupa verdict state"
        (:state app-with-verdict) => "verdictGiven"))))

(facts "YA Sijoitussopimus happy case"
  (let [{:keys [id permitSubtype]} (create-and-submit-application pena
                                                                  :propertyId tampere-property-id
                                                                  :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        verdict-id (:verdictId (command jussi :new-verdict-draft :id id))
        now-ts     (now)
        verdicts   (:verdicts (query-application jussi id))]
    (fact "initially sijoituslupa" permitSubtype => "sijoituslupa")
    (count verdicts) => 1
    (-> verdicts first :sopimus) => falsey

    (fact "Jussi gives sopimus verdict by hand"
      (command jussi :save-verdict-draft
               :id id
               :verdictId verdict-id
               :backendId "321"
               :status 1
               :name "Juice"
               :given now-ts
               :official now-ts
               :text ""
               :agreement true :section "" :lang "fi") => ok?
      (fact "saving draft with agreement changes permitSubtype"
        (:permitSubtype (query-application jussi id)) => "sijoitussopimus")
      (command jussi :publish-verdict :id id :verdictId verdict-id :lang "fi") => ok?)

    (let [app-with-verdict (query-application pena id)]
      (:permitSubtype app-with-verdict) => "sijoitussopimus"
      (fact "Sijoitussopimus verdict state"
        (:state app-with-verdict) => "agreementPrepared"))

    (fact "Creating verdict draft in sijoitussopimus suggestes 'sopimus' by default"
      (let [verdict-id2 (:verdictId (command jussi :new-verdict-draft :id id))
            verdict (util/find-by-id verdict-id2 (:verdicts (query-application jussi id)))]
        (:sopimus verdict) => true))

    (fact "signing agreement"
      (command pena :sign-verdict :id id :verdictId verdict-id :password "pena" :lang "fi") => ok?
      (fact "state changed"
        (-> (query-application pena id) :state) => "agreementSigned")
      (states/terminal-state? states/ya-sijoitussopimus-state-graph :agreementSigned) => true)))

(facts "YA Sijoitussopimus errors"
  (let [{:keys [id]} (create-and-submit-application pena
                                                    :propertyId tampere-property-id
                                                    :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        pena-subtype (command pena :change-permit-sub-type :id id :permitSubtype "sijoitussopimus")
        jussi-subtype (command jussi :change-permit-sub-type :id id :permitSubtype "sijoitussopimus")
        verdict-id (:verdictId (command jussi :new-verdict-draft :id id))
        now-ts     (now)
        app        (query-application jussi id)
        verdicts   (:verdicts app)]
    pena-subtype => (partial expected-failure? :error.ya-subtype-change-authority-only)
    jussi-subtype => ok?
    (:permitSubtype app) => "sijoitussopimus"

    (count verdicts) => 1
    (fact "For sijoitussopimus, verdict is by default 'sopimus'"
      (-> verdicts first :sopimus) => true)

    (fact "If verdict is agremeent, subtype can't be 'sijoituslupa'"
      (command jussi :save-verdict-draft
               :id id
               :verdictId verdict-id
               :backendId "321"
               :status 1
               :name "Juice"
               :given now-ts
               :official now-ts
               :text "noup"
               :agreement true :section "" :lang "fi") => ok?
      (command jussi :change-permit-sub-type :id id :permitSubtype "sijoituslupa") => ok?
      (command jussi :publish-verdict :id id :verdictId verdict-id :lang "fi") => (partial expected-failure? :error.ya-sijoituslupa-invalid-subtype))

    (command jussi :change-permit-sub-type :id id :permitSubtype "sijoitussopimus") => ok?

    (fact "If verdict is not agremeent, subtype must be sijoituslupa"
      (command jussi :save-verdict-draft
               :id id
               :verdictId verdict-id
               :backendId "321"
               :status 1
               :name "Juice"
               :given now-ts
               :official now-ts
               :text "noup"
               :agreement false :section "" :lang "fi") => ok?
      (command jussi :publish-verdict :id id :verdictId verdict-id :lang "fi") => (partial expected-failure? :error.ya-sijoitussopimus-invalid-subtype))

    (fact "Jussi gives sijoitussopimus verdict by hand"
      (command jussi :save-verdict-draft
               :id id
               :verdictId verdict-id
               :backendId "321"
               :status 1
               :name "Juice"
               :given now-ts
               :official now-ts
               :text ""
               :agreement true :section "" :lang "fi") => ok?
      (command jussi :publish-verdict :id id :verdictId verdict-id :lang "fi") => ok?)

    (let [app-with-verdict (query-application pena id)]
      (:permitSubtype app-with-verdict) => "sijoitussopimus"
      (fact "Sijoitussopimus verdict state"
        (:state app-with-verdict) => "agreementPrepared"))))
