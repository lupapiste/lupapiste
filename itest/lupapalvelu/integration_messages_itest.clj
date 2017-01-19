(ns lupapalvelu.integration-messages-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.integrations-api :as iapi]))

(apply-remote-minimal)

(facts "transfers with case managenment"
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
      (command velho :approve-application :id app-id :lang "fi") => (partial expected-failure? :error.integration.asianhallinta-available))

    (fact "form a case management message"
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?)

    (fact "applicant can not access transfer info"
      (query pena :integration-messages :id app-id) => unauthorized?)

    (fact {:midje/description (str "list transfers of " app-id)}
      (let [{:keys [krysp ah] :as resp} (query velho :integration-messages :id app-id)]
        resp => ok?
        (facts "case management"
          (fact "no files transferred" (:ok ah) => empty?)
          (fact "no files in errors" (:error ah) => empty?)
          (fact "at least one file waits to be transferred" (count (:waiting ah)) => pos?)
          (fact "all filenames start with application id" (map :name (:waiting ah)) => (has every? #(.startsWith % app-id)))
          (fact "file has modification time" (map :modified (:waiting ah)) => (has every? pos?)))

        (facts "krysp"
          (fact "no files transferred" (:ok krysp) => empty?)
          (fact "no files in errors" (:error krysp) => empty?)
          #_(fact "no files waits to be transferred" (:waiting krysp) => empty?)))))) ; FIXME: clean target path before test

(facts "transfers without case management"
  (let [application (create-and-submit-application
                         pena
                         :propertyId kuopio-property-id
                         :operation "kerrostalo-rivitalo"
                         :propertyId "29703401070020"
                         :y 6965051.2333374 :x 535179.5
                         :address "Suusaarenkierto 45")
        app-id (:id application)]

    (generate-documents application pena)

    (fact "form a case management message"
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => (partial expected-failure? :error.integration.asianhallinta-disabled))

    (fact "form a krysp message"
      (command velho :approve-application :id app-id :lang "fi") => ok?)

    (fact {:midje/description (str "list transfers of " app-id)}
      (let [{:keys [krysp ah] :as resp} (query velho :integration-messages :id app-id)]
        resp => ok?
        (facts "krysp"
          (fact "no files transferred" (:ok krysp) => empty?)
          (fact "no files in errors" (:error krysp) => empty?)
          (fact "at least one file waits to be transferred" (count (:waiting krysp)) => pos?)
          (fact "all filenames start with application id" (map :name (:waiting krysp)) => (has every? #(.startsWith % app-id)))
          (fact "file has modification time" (map :modified (:waiting krysp)) => (has every? pos?)))

        (facts "case management"
          (fact "no files transferred" (:ok ah) => empty?)
          (fact "no files in errors" (:error ah) => empty?)
          (fact "no files waits to be transferred" (:waiting ah) => empty?))))))

(facts "transfers without krysp or case management"
  (let [app (create-and-submit-application
             pena
             :operation "kerrostalo-rivitalo"
             :propertyId oulu-property-id
             :y 6965051.2333374 :x 535179.5
             :address "Torikatu 45")]
    (command olli :approve-application :id (:id app) :lang "fi") => ok?

    (fact "Integration messages not available, if KRYSP is not set"
      (query olli :integration-messages :id (:id app)) => (partial expected-failure? :error.sftp.user-not-set))))

(facts "filename validation"
  (let [some-id "LP-123-1234-12345"
        mkcmd (fn [id n] {:data {:filename n, :id id}})]
    (fact "missing" (iapi/validate-integration-message-filename (mkcmd some-id nil)) => fail?)
    (fact "empty" (iapi/validate-integration-message-filename (mkcmd some-id "")) => fail?)

    (fact "valid txt" (iapi/validate-integration-message-filename (mkcmd some-id (str some-id "_error.txt"))) => nil?)
    (fact "valid xml" (iapi/validate-integration-message-filename (mkcmd some-id (str some-id "_123.xml"))) => nil?)

    (fact "application pdf" (iapi/validate-integration-message-filename (mkcmd some-id (str some-id "_current_application.pdf"))) => fail?)

    (fact "id mismatch" (iapi/validate-integration-message-filename (mkcmd some-id "LP-123-1234-12346_123.xml")) => fail?)

    (fact "directory traversal"
      (iapi/validate-integration-message-filename (mkcmd some-id "../../other-user/rakennus/LP-124-1234-21345_123.xml")) => fail?
      (iapi/validate-integration-message-filename (mkcmd some-id (str "../../other-user/rakennus/" some-id "_123.xml"))) => fail?)))

(facts "response"
  (let [hetu "121212-1212"
        filename "LP-123.xml"
        content (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?><yht:Osapuoli><yht:henkilo><yht:henkilotunnus>" hetu "</yht:henkilotunnus></yht:henkilo></yht:Osapuoli>")
        {:keys [status headers body] :as resp} (iapi/transferred-file-response filename content)
        content-type (get headers "Content-Type")]

    (fact "status OK" status => 200)
    (fact "xml mime type" content-type => "application/xml")
    (fact "person id is masked"
      body =not=> (contains hetu)
      body => (contains "******x****"))))
