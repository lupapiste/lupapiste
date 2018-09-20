(ns lupapalvelu.ya-sijoituslupa-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.states :as states]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "YA Sijoituslupa basic case"
  (let [{:keys [id permitSubtype]} (create-and-submit-application pena
                                                                  :propertyId tampere-property-id
                                                                  :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        now-ts     (now)]
    permitSubtype => "sijoituslupa"
    (fact "Jussi gives verdict by hand"
      (give-legacy-verdict jussi id))

    (let [app-with-verdict (query-application pena id)]
      (:permitSubtype app-with-verdict) => "sijoituslupa"
      (fact "Sijoituslupa verdict state"
        (:state app-with-verdict) => "verdictGiven"))

    (facts "saving another verdict draft"
      (command jussi :new-legacy-verdict-draft :id id)
      => ok?
      ;; The following check is futile in Pate since the
      ;; verdict/contract is selected according to the permit subtype
      (fact "subtype is not changed, because there is already published verdict"
        (let [app (query-application pena id)]
          (:permitSubtype app) => "sijoituslupa"
          (:state app) => "verdictGiven")))))

(facts "YA Sijoitussopimus happy case"
  (let [{:keys [id permitSubtype]} (create-and-submit-application pena
                                                                  :propertyId tampere-property-id
                                                                  :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        verdict-id (:verdictId (command jussi :new-legacy-verdict-draft :id id))
        now-ts     (now)
        verdicts   (:pate-verdicts (query-application jussi id))]
    (fact "initially sijoituslupa" permitSubtype => "sijoituslupa")
    (count verdicts) => 1
    (-> verdicts first :category) => "ya"

    (fact "Jussi changes subPermitType"
      (command jussi :change-permit-sub-type :id id :permitSubtype "sijoitussopimus") => ok?)


    (let [contract-id (give-legacy-contract jussi id)
          app-with-verdict (query-application pena id)]
      (:permitSubtype app-with-verdict) => "sijoitussopimus"
      (fact "Sijoitussopimus verdict state"
        (:state app-with-verdict) => "agreementPrepared")

      (fact "signing agreement"
        (command pena :sign-pate-contract :id id :verdict-id contract-id
                 :password "pena") => ok?
        (fact "state changed"
          (-> (query-application pena id) :state) => "agreementSigned")
        (states/terminal-state? states/ya-sijoitussopimus-state-graph :agreementSigned) => true))))

;; TODO: Check the restrictions with Pate
#_(facts "YA Sijoitussopimus errors"
  (let [{:keys [id]} (create-and-submit-application pena
                                                    :propertyId tampere-property-id
                                                    :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")
        pena-subtype (command pena :change-permit-sub-type :id id :permitSubtype "sijoitussopimus")
        jussi-subtype (command jussi :change-permit-sub-type :id id :permitSubtype "sijoitussopimus")
        verdict-id (:verdictId (command jussi :new-verdict-draft :id id))
        now-ts     (now)
        app        (query-application jussi id)
        verdicts   (:verdicts app)]
    pena-subtype => unauthorized?
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
