(ns lupapalvelu.ya-sijoituslupa-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.itest-util :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "Sijoituslupa basic case"
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

(facts "Sijoitussopimus"
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
        (:sopimus verdict) => true))))
