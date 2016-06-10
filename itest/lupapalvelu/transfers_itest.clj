(ns lupapalvelu.transfers-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            ))

(apply-remote-minimal)

(facts "transfers"
  (let [application (create-and-submit-application
                         pena
                         :propertyId kuopio-property-id
                         :operation "poikkeamis"
                         :propertyId "29703401070020"
                         :y 6965051.2333374 :x 535179.5
                         :address "Suusaarenkierto 45")
        app-id (:id application)]

    (generate-documents application pena)

    (fact "form a krysp message"
      (command velho :approve-application :id app-id :lang "fi") => ok?)

    (fact "request-for-complement before resend"
      (command velho :request-for-complement :id app-id))

    (fact "form a case management message"
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?)

    (fact "applicant can not access transfer info"
      (query pena :transfers :id app-id) => unauthorized?)

    (fact {:midje/description (str "list transfers of " app-id)}
      (let [{:keys [krysp ah] :as resp} (query velho :transfers :id app-id)]
        resp => ok?
        (doseq [[dirs n] [[krysp "krysp"] [ah "ah"]]
                :let [{:keys [ok error waiting]} dirs]]
          (fact {:midje/description n}
            (fact "no files transferred" ok => empty?)
            (fact "no files in errors" error => empty?)
            (fact "at least one file waits to be transferred" (count waiting) => pos?)
            (fact "all filenames start with application id" waiting => (has every? #(.startsWith % app-id))))))))
  )
