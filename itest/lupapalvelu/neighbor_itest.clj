(ns lupapalvelu.neighbor-itest
  (:require [midje.sweet  :refer :all]
            [clojure.string :as s]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :refer [now]]
            [sade.util :refer [fn->] :as util]))

(apply-remote-minimal)

(defn invalid-token? [resp] (= resp {:ok false, :text "error.token-not-found"}))
(defn invalid-response? [resp] (= (dissoc resp :response) {:ok false, :text "error.invalid-response"}))
(defn invalid-vetuma? [resp] (= (dissoc resp :response) {:ok false, :text "error.invalid-vetuma-user"}))
(defn neighbor-marked-done? [resp] (= (dissoc resp :response) {:ok false, :text "error.neighbor-marked-done"}))

(facts "add neigbor with missing optional data"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id)]
    (comment-application pena application-id true) => ok?
    (fact "no name"   (command sonja "neighbor-add" :id application-id :propertyId "12312312341234"           :street "s" :city "c" :zip "z" :email "e") => ok?)
    (fact "no street" (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n"             :city "c" :zip "z" :email "e") => ok?)
    (fact "no city"   (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n" :street "s"           :zip "z" :email "e") => ok?)
    (fact "no zip"    (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n" :street "s" :city "c"          :email "e") => ok?)
    (fact "no email"  (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n" :street "s" :city "c" :zip "z") => ok?)))


(defn- create-app-with-neighbor [& args]
  (let [application-id (apply create-app-id pena args)
        _ (comment-application pena application-id true)
        resp (command sonja "neighbor-add" :id application-id :propertyId "12312312341234" :name "n" :street "s" :city "c" :zip "z" :email "e")
        neighborId (:neighborId resp)
        application (query-application pena application-id)
        neighbors (:neighbors application)]
    (fact resp => ok?)
    [application neighborId neighbors]))

(facts "create app, add neighbor"
  (let [[application neighborId neighbors] (create-app-with-neighbor)
        neighbor (util/find-by-id neighborId neighbors)]
    (fact neighbor => (contains {:propertyId "12312312341234"
                                 :owner {:name "n"
                                         :businessID nil
                                         :nameOfDeceased nil
                                         :type nil
                                         :address {:street "s" :city "c" :zip "z"}
                                         :email "e"}}))
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, update neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-update" :id application-id :neighborId neighborId :propertyId "12312312341200" :name "n2" :street "s2" :city "c2" :zip "z2" :email "e2")
        application (query-application pena application-id)
        neighbors (:neighbors application)
        neighbor (util/find-by-id neighborId neighbors)]
    (fact (count neighbors) => 1)
    (fact neighbor => (contains {:propertyId "12312312341200"
                                 :owner {:name "n2"
                                         :businessID nil
                                         :nameOfDeceased nil
                                         :type nil
                                         :address {:street "s2" :city "c2" :zip "z2"}
                                         :email "e2"}}))
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, remove neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-remove" :id application-id :neighborId neighborId)
        application (query-application pena application-id)
        neighbors (:neighbors application)]
    (fact (count neighbors) => 0)))

(facts "neighbor invite email has..."
  (let [neighbor-email-addr       "abba@example.com"
        [application neighbor-id] (create-app-with-neighbor :address "Naapurikuja 3")
        application-id            (:id application)
        _                         (command pena :neighbor-send-invite
                                                :id application-id
                                                :neighborId neighbor-id
                                                :email neighbor-email-addr)
        email                     (last-email)
        [_ a-id n-id token]       (re-find #"(?sm)/neighbor/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)" (get-in email [:body :plain]))
        expiration-date           (util/to-local-date (+ ttl/neighbor-token-ttl (now)))]

    (fact "correct to" (:to email) => neighbor-email-addr)
    (fact "correct subject" (:subject email) => "Lupapiste: Naapurikuja 3, Sipoo - ilmoitus naapureiden kuulemisesta")
    (fact "neighbor name field" (get-in email [:body :plain]) => (contains #"Hei n,"))
    (fact "correnct link"
      a-id => application-id
      n-id => neighbor-id
      token => #"[A-Za-z0-9]{48}")
    (fact "correct expiration date"
      (get-in email [:body :plain]) => (contains expiration-date))))

(facts* "neighbor invite & view on application"
  (let [[{application-id :id :as application} neighborId] (create-app-with-neighbor)
        neighborEmail   "abba@example.com"
        _               (upload-attachment-to-all-placeholders pena application)
        _               (command pena :neighbor-send-invite
                                      :id application-id
                                      :neighborId neighborId
                                      :email neighborEmail) => ok?
        application     (query-application pena application-id)
        hakija-doc-id   (:id (domain/get-applicant-document (:documents application)))
        uusirak-doc-id  (:id (domain/get-document-by-name application "uusiRakennus"))]

        (command pena :update-doc
                      :id application-id
                      :doc hakija-doc-id
                      :updates [["henkilo.henkilotiedot.etunimi"  "Zebra"]
                                ["henkilo.henkilotiedot.sukunimi" "Zorro"]
                                ["henkilo.henkilotiedot.hetu"     "080599-9158"]
                                ["henkilo.yhteystiedot.puhelin"   "040-1234567"]]) => ok?

        (command pena :update-doc
                      :id application-id
                      :doc uusirak-doc-id
                      :updates [["rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi"  "Gustav"]
                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.sukunimi" "Golem"]
                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu"     "080588-921L"]
                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.turvakieltoKytkin" true]
                                ["rakennuksenOmistajat.0.henkilo.osoite.katu"            "Katuosoite"]
                                ["rakennuksenOmistajat.0.henkilo.yhteystiedot.puhelin"   "040-2345678"]]) => ok?

        (fact "Pena adds second paapiirrustus and set's it not public"
          (upload-attachment pena application-id {:id "" :type {:type-group "paapiirustus" :type-id "pohjapiirustus"}} true) => truthy
          (let [{attachments :attachments} (query-application pena application-id)
                new-att (last attachments)]
            (fact "now two pohjapiirustus attachemnts exist"
              (count (filter (fn-> :type :type-id (= "pohjapiirustus")) attachments)) => 2)
            (get-in new-att [:type :type-group]) => "paapiirustus"
            (command pena :set-attachment-visibility :id application-id :attachmentId (:id new-att) :value "viranomainen") => ok?))

    (let [email               (query pena :last-email)
          body                (get-in email [:message :body :plain])
          [_ a-id n-id token] (re-find #"(?sm)/neighbor/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)" body)]

    token => truthy
    token =not=> #"="

    (fact "application query returns set document info with masked person ID"
     (let [application (query-application pena application-id)
           hakija-doc  (tools/unwrapped (domain/get-document-by-id application hakija-doc-id))
           uusirak-doc (tools/unwrapped (domain/get-document-by-id application uusirak-doc-id))]

       (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
       (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
       (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => "******-****"
       (-> hakija-doc :data :henkilo :yhteystiedot  :puhelin) => "040-1234567"
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :etunimi) => "Gustav"
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :sukunimi) => "Golem"
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu) => "******-****"
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :turvakieltoKytkin) => true
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :osoite :katu) => "Katuosoite"
       (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :yhteystiedot :puhelin) => "040-2345678"))

      (let [resp        (query pena :neighbor-application
                                    :applicationId application-id
                                    :neighborId neighborId
                                    :token token)
            application (:application resp)
            hakija-doc  (tools/unwrapped (domain/get-document-by-id application hakija-doc-id))
            uusirak-doc (tools/unwrapped (domain/get-document-by-id application uusirak-doc-id))]

        resp => truthy
        resp => (contains {:ok true})
        application => truthy

        (fact "neighbor application query does not return hetu"
          (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
          (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
          (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => nil
          (-> hakija-doc :data :henkilo :henkilotiedot :turvakieltoKytkin) => false
          (-> hakija-doc :data :henkilo :yhteystiedot  :puhelin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :etunimi) => "Gustav"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :sukunimi) => "Golem"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :turvakieltoKytkin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :yhteystiedot :puhelin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :osoite :katu) => nil)

        (fact "has no comments"
          (:comments application) => empty?)

        (fact "has coordinates (LPK-894)"
          (-> application :location :x) => pos?
          (-> application :location :y) => pos?)

        (let [document-types (set (map (comp :name :schema-info) (:documents application)))
              has-doc (fn [doc-schema-name] (document-types doc-schema-name))]
          (fact "has hakija document" "hakija-r" => has-doc)
          (fact "has hankkeen-kuvaus-rakennuslupa document" "hankkeen-kuvaus-rakennuslupa" => has-doc)
          (fact "has rakennuspaikka document" "rakennuspaikka" => has-doc)
          (fact "does not have paatoksen-toimitus-rakval document" "paatoksen-toimitus-rakval" =not=> has-doc))


        (fact "attachments"
          (fact "there are some attachments"
            (->> application :attachments count) => pos?)
          (fact "everyone is paapiirustus"
            (->> application :attachments (some (fn-> :type :type-group (not= "paapiirustus")))) => falsey)

          (fact "only one pohjapiirustus, as non-public is filtered"
            (->> (:attachments application)
              (filter (fn-> :type :type-id (= "pohjapiirustus")))
              count) => 1)

          (let [file-id (->> application :attachments first :latestVersion :fileId)]
            (fact "downloading should be possible"
              (raw nil "neighbor-download-attachment" :neighborId neighborId :token token :fileId file-id) => http200?)

            (fact "downloading with wrong token should not be possible"
              (raw nil "neighbor-download-attachment" :neighborId neighborId :token "h4x3d token" :fileId file-id) => http401?)))

        (fact "does not have auth information"
          (:auth application) => empty?))

    (fact "without tupas, neighbor can't give response"
      (command pena :neighbor-response
        :applicationId application-id
        :neighborId (name neighborId)
        :token token
        :stamp "INVALID"
        :response "ok"
        :message "kehno suunta") => invalid-vetuma?)

    (fact "with vetuma"
      (let [stamp (vetuma-stamp!)]

        (fact "neighbor cant give ill response"
          (command pena :neighbor-response
            :applicationId application-id
            :neighborId (name neighborId)
            :stamp stamp
            :token token
            :response "ime parsaa!"
            :message "kehno suunta") => invalid-response?)

        (fact "neighbor can give response"
          (command pena :neighbor-response
            :applicationId application-id
            :neighborId (name neighborId)
            :stamp stamp
            :token token
            :response "comments"
            :message "kehno suunta") => ok?)

        (fact "new invite can not be sent after response"
          (command pena :neighbor-send-invite
                   :id application-id
                   :neighborId neighborId
                   :email neighborEmail) => neighbor-marked-done?)

        (fact "applicant can not see neighbor's person id"
          (let [application (query-application pena application-id)
                userids (->> application
                          :neighbors
                          (map :status)
                          flatten
                          (map (comp :userid :vetuma)))]
            userids => (partial every? nil?)))

        (fact "neighbor can't re-give response 'cos vetuma has expired"
          (command pena :neighbor-response
            :applicationId application-id
            :neighborId (name neighborId)
            :stamp stamp
            :token token
            :response "comments"
            :message "kehno suunta") => invalid-vetuma?)

        (fact "neighbor can't re-give response with new tupas 'cos token has expired"
          (command pena :neighbor-response
            :applicationId application-id
            :neighborId (name neighborId)
            :stamp (vetuma-stamp!)
            :token token
            :response "comments"
            :message "kehno suunta") => invalid-token?)

        (fact "neighbor cant see application anymore"
          (query pena :neighbor-application
            :applicationId application-id
            :neighborId (name neighborId)
            :token token) => invalid-token?))))))
