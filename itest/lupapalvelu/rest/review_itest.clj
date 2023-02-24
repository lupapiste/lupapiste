(ns lupapalvelu.rest.review-itest
  (:require artemis-server
            [clojure.test.check.generators :as gen]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :as itest-util]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.rest.review :as rest.review]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.util :as util])
  (:import java.io.File
           java.time.LocalDate))

(mount/start #'mongo/connection)

(def- db-name (str "test_rest_review_" (now)))
(mongo/with-db db-name (fixture/apply-fixture "minimal"))


(def- vantaa-YA-org-id "092-YA")
(def- pate-template-id "5d88e6bf5df546ee8a459488")

(def- matti-rest-api-user (itest-util/find-user-from-minimal "matti-rest-api-user"))

(def- valid-review {:katselmuksen_laji "ALOITUSKATSELMUS"
                    :pitopaiva "2001-01-01"
                    :pitaja "Pitaja 1"
                    :huomautettavaa "Huomautus 1"
                    :maaraaika "2001-01-02"
                    :toteaja "Toteaja 1"
                    :toteamishetki "2001-01-03"
                    :lasnaolijat "Lasnaolijat 1"
                    :poikkeamat "Poikkeamat 1"
                    :liitetiedostot []})

(def valid-review-json (json/encode valid-review))

(defn- create-and-submit-application []
  (let [application (itest-util/create-and-open-application
                      itest-util/pena
                      :propertyId "09240900060124"
                      :operation "ya-kayttolupa-kattolumien-pudotustyot")
        doc-name->id (into {} (for [d (:documents application)]
                                [(:name (:schema-info d)) (:id d)]))]

    (doseq [[doc-name updates] {"yleiset-alueet-hankkeen-kuvaus-kayttolupa"
                                {"kayttotarkoitus" "Lorem ipsum"
                                 "varattava-pinta-ala" "123"}

                                "hakija-ya"
                                {"_selected" "henkilo"
                                 "henkilo.henkilotiedot.etunimi" "John"
                                 "henkilo.henkilotiedot.sukunimi" "Doe"
                                 "henkilo.henkilotiedot.hetu" "240573-872F"
                                 "henkilo.osoite.katu" "Puutarhakatu 13"
                                 "henkilo.osoite.postinumero" "12345"
                                 "henkilo.osoite.postitoimipaikannimi" "Ankkalinna"
                                 "henkilo.yhteystiedot.puhelin" "044 123 4567"
                                 "henkilo.yhteystiedot.email" "john.doe@example.com"}

                                "tyomaastaVastaava"
                                {"_selected" "henkilo"
                                 "henkilo.henkilotiedot.etunimi" "John"
                                 "henkilo.henkilotiedot.sukunimi" "Doe"
                                 "henkilo.osoite.katu" "Puutarhakatu 13"
                                 "henkilo.osoite.postinumero" "12345"
                                 "henkilo.osoite.postitoimipaikannimi" "Ankkalinna"
                                 "henkilo.yhteystiedot.puhelin" "044 123 4567"
                                 "henkilo.yhteystiedot.email" "john.doe@example.com"
                                 }

                                "yleiset-alueet-maksaja"
                                {"_selected" "henkilo"
                                 "henkilo.henkilotiedot.etunimi" "John"
                                 "henkilo.henkilotiedot.sukunimi" "Doe"
                                 "henkilo.henkilotiedot.hetu" "240573-872F"
                                 "henkilo.osoite.katu" "Puutarhakatu 13"
                                 "henkilo.osoite.postinumero" "12345"
                                 "henkilo.osoite.postitoimipaikannimi" "Ankkalinna"
                                 "henkilo.yhteystiedot.puhelin" "044 123 4567"
                                 "henkilo.yhteystiedot.email" "john.doe@example.com"}

                                "tyoaika"
                                {"tyoaika-alkaa-ms" 1570698000001
                                 "tyoaika-paattyy-ms" 1570698000001}}]
      (itest-util/command itest-util/pena :update-doc
                          :id (:id application)
                          :doc (get doc-name->id doc-name)
                          :updates (vec updates)))

    (itest-util/command
      itest-util/pena
      :set-attachment-not-needed
      :id (:id application)
      :attachmentId (:id (first (:attachments application)))
      :notNeeded true)

    (itest-util/command
      itest-util/pena
      :submit-application
      :id (:id application))

    (:id application)))

(defn- create-verdict-draft [application-id]
  (let [result (itest-util/command
                 itest-util/esa
                 :new-pate-verdict-draft
                 :id application-id
                 :template-id pate-template-id)]
    (:verdict-id result)))

(defn- fill-verdict [application-id verdict-id]
  (doseq [[k v] {:language :fi
                 :handler "John Doe"
                 :verdict-date 1570698000000
                 :anto 1570698000000
                 :start-date 1570698000000
                 :end-date 1570698000000
                 :verdict-type :kayttolupa
                 :verdict-code :hyvaksytty}]
    (itest-util/command
      itest-util/esa
      :edit-pate-verdict
      :id application-id
      :verdict-id verdict-id
      :path [k]
      :value v)))

(defn- publish-verdict [application-id verdict-id]
  (itest-util/command
    itest-util/esa
    :publish-pate-verdict
    :id application-id
    :verdict-id verdict-id))

(defn- create-application-with-verdict []
  (let [application-id (create-and-submit-application)
        verdict-id (create-verdict-draft application-id)]
    (fill-verdict application-id verdict-id)
    (publish-verdict application-id verdict-id)
    application-id))

(defn- review-tasks [application-id]
  (let [application (domain/get-application-as application-id matti-rest-api-user)]
    (set
      (for [t (:tasks application)
            :let [katselmuksenLaji (get-in t [:data :katselmuksenLaji :value])]
            :when (some? katselmuksenLaji)]
        {:katselmuksenLaji katselmuksenLaji
         :state (get-in t [:state])
         :katselmus {:pitoPvm (get-in t [:data :katselmus :pitoPvm :value])
                     :pitaja (get-in t [:data :katselmus :pitaja :value])
                     :huomautukset {:kuvaus        (get-in t [:data :katselmus :huomautukset :kuvaus :value])
                                    :maaraAika     (get-in t [:data :katselmus :huomautukset :maaraAika :value])
                                    :toteaja       (get-in t [:data :katselmus :huomautukset :toteaja :value])
                                    :toteamisHetki (get-in t [:data :katselmus :huomautukset :toteamisHetki :value])
                                    :lasnaolijat   (get-in t [:data :katselmus :lasnaolijat :value])
                                    :poikkeamat    (get-in t [:data :katselmus :poikkeamat :value])}}
         :muuTunnus         (get-in t [:data :muuTunnus :value])
         :muuTunnusSovellus (get-in t [:data :muuTunnusSovellus :value])
         :attachments (set
                        (for [a (:attachments application)
                              :when (= (:id (:target a)) (:id t))]
                          {:filename (:filename (:latestVersion a))
                           :type     (:type a)
                           :contents (:contents a)}))}))))

(def- gen-date (gen/fmap (fn [day-count]
                           (let [date (.minusDays (LocalDate/now) day-count)]
                             (.toString date)))
                         (gen/large-integer* {:min 0, :max 3000})))

(comment (gen/sample gen-date))

(defn- gen-maybe [g]
  (gen/one-of [g (gen/return nil)]))

(def- gen-review (gen/resize
                   40
                   (gen/hash-map
                     :katselmuksen_laji (gen/one-of
                                          [(gen/return "ALOITUSKATSELMUS")
                                           (gen/return "LOPPUKATSELMUS")
                                           (gen/return "MUU_VALVONTAKAYNTI")])
                     :pitopaiva gen-date
                     :pitaja gen/string
                     :huomautettavaa (gen-maybe gen/string)
                     :maaraaika (gen-maybe gen-date)
                     :toteaja (gen-maybe gen/string)
                     :toteamishetki (gen-maybe gen-date)
                     :lasnaolijat (gen-maybe gen/string)
                     :poikkeamat (gen-maybe gen/string)
                     :liitetiedostot (gen/return []))))

(comment (dorun (map clojure.pprint/pprint (gen/sample gen-review))))

(defn put-review
  "Simulates a PUT request to /rest/application/<application id>/review/<review id>"
  ([application-id review-id]
   (put-review {:application-id application-id
                :review-id review-id}))
  ([application-id review-id attributes]
   (put-review {:application-id application-id
                :review-id review-id
                :attributes attributes}))
  ([application-id review-id attributes request-parts]
   (put-review {:application-id application-id
                :review-id review-id
                :attributes attributes
                :request-parts request-parts}))
  ([{:keys [user application-id review-id attributes request-parts created]
     :or {user matti-rest-api-user
          application-id "LP-000-0000-00000"
          review-id "11111111-1111-1111-1111-111111111111"
          request-parts {}
          created (now)}}]
   (assoc
     (rest.review/upsert
       {:user      user
        :timestamp created
        :request   (merge {:application-id application-id
                           :review-id      review-id
                           :katselmus      (json/encode
                                             (merge (first (gen/sample gen-review 1))
                                                    attributes))}
                          request-parts)})
     :created
     created)))

(defn- file-upload [filename]
  {:filename filename
   :tempfile (File. "resources/ya-review-schema.json")})

(defn await-attachment-upload [application-id filename]
  (let [timeout-ms 10000
        deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [application (domain/get-application-as application-id matti-rest-api-user)]
        (when (empty? (filter #(= (:filename (:latestVersion %)) filename)
                              (:attachments application)))
          (if (>= (System/currentTimeMillis) deadline)
            (throw
              (RuntimeException.
                (str "Attachment " filename " wasn't added to "
                     application-id " in " timeout-ms " ms.")))
            (do
              (Thread/sleep 200)
              (recur))))))))

(defn- has-updated-modified-ts [previous]
  (fn [{:keys [modified]}]
    (fact "modified updated"
      modified => integer?
      (< previous modified) => true)))


(itest-util/with-local-actions
  (mongo/with-db db-name

    (facts "PUT /rest/application/<application id>/review/<review id>"

      (fact "returns 401 if the user is unauthenticated"
        (let [response (put-review {:user nil})]
          (:status response) => 401))

      (fact "returns 403 if the user is authenticated but is not a REST user"
        (let [response (put-review {:user (itest-util/find-user-from-minimal "pena")})]
          (:status response) => 403))

      (fact "returns 400 if the application-id is invalid"
        (let [response (put-review "LP-invalid" "11111111-1111-1111-1111-111111111111")]
          (:status response) => 400))

      (fact "returns 400 if the review-id is invalid"
        (let [response (put-review "LP-000-0000-00000" "invalid")]
          (:status response) => 400))

      (fact "returns 404 if there's no such application"
        (let [response (put-review "LP-000-0000-00000" "11111111-1111-1111-1111-111111111111")]
          (:status response) => 404))

      (fact "returns 404 if the application has no verdicts"
        (let [app-id (create-and-submit-application)
              response (put-review app-id "11111111-1111-1111-1111-111111111111")]
          (:status response) => 404))

      (fact "returns 404 if the application's only has a draft verdict"
        (let [app-id (create-and-submit-application)
              _ (create-verdict-draft app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111")]
          (:status response) => 404))

      (fact "returns 400 if the katselmus json is of invalid form"
        (let [app-id (create-application-with-verdict)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {}
                                   {:katselmus "invalid-json"})]
          (:status response) => 400))

      (fact "returns 400 if request body doesn't match defined json schema"
        (let [app-id (create-application-with-verdict)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:invalid "review"})]
          (:status response) => 400))

      (fact "returns 201 and updates the corresponding task if there's no existing review with the given type"
        (let [app-id (create-application-with-verdict)
              modified-before (:modified (domain/get-application-as app-id matti-rest-api-user))
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:katselmuksen_laji "ALOITUSKATSELMUS"
                                    :pitopaiva "2001-01-01"
                                    :pitaja "Pitaja 1"
                                    :huomautettavaa "Huomautus 1"
                                    :maaraaika "2001-01-02"
                                    :toteaja "Toteaja 1"
                                    :toteamishetki "2001-01-03"
                                    :lasnaolijat "Lasnaolijat 1"
                                    :poikkeamat "Poikkeamat 1"
                                    :liitetiedostot []})
              app-after (domain/get-application-as app-id matti-rest-api-user)]
          (:status response) => 201
           app-after => (has-updated-modified-ts modified-before)
          (->> (review-tasks app-id)
               ;; Ignore attachments as they are not relevant for this test.
               (map #(dissoc % :attachments))
               set) => (-> (->> original-review-tasks
                                (map #(dissoc % :attachments))
                                (remove #(= (:katselmuksenLaji %) "Aloituskatselmus"))
                                set)
                           (conj {:katselmuksenLaji "Aloituskatselmus"
                                  :state "sent"
                                  :katselmus {:pitoPvm "01.01.2001"
                                              :pitaja "Pitaja 1"
                                              :huomautukset {:kuvaus "Huomautus 1"
                                                             :maaraAika "02.01.2001"
                                                             :toteaja "Toteaja 1"
                                                             :toteamisHetki "03.01.2001"
                                                             :lasnaolijat "Lasnaolijat 1"
                                                             :poikkeamat "Poikkeamat 1"}}
                                  :muuTunnus "11111111-1111-1111-1111-111111111111"
                                  :muuTunnusSovellus "MATTI"}))))

      (fact "returns 201 and creates a new task if there's an existing review with the given type but a different ID"
        (let [app-id (create-application-with-verdict)
              modified-before (:modified (domain/get-application-as app-id matti-rest-api-user))
              _ (put-review app-id "11111111-1111-1111-1111-111111111111"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"})
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "22222222-2222-2222-2222-222222222222"
                                   {:katselmuksen_laji "ALOITUSKATSELMUS"
                                    :pitopaiva "2002-02-01"
                                    :pitaja "Pitaja 2"
                                    :huomautettavaa "Huomautus 2"
                                    :maaraaika "2002-02-02"
                                    :toteaja "Toteaja 2"
                                    :toteamishetki "2002-02-03"
                                    :lasnaolijat "Lasnaolijat 2"
                                    :poikkeamat "Poikkeamat 2"
                                    :liitetiedostot []})
              app-after (domain/get-application-as app-id matti-rest-api-user)]
          (:status response) => 201
          app-after => (has-updated-modified-ts modified-before)
          (->> (review-tasks app-id)
               ;; Ignore attachments as they are not relevant for this test.
               (map #(dissoc % :attachments))
               set) => (-> (->> original-review-tasks
                                (map #(dissoc % :attachments))
                                set)
                           (conj {:katselmuksenLaji "Aloituskatselmus"
                                  :state "sent"
                                  :katselmus {:pitoPvm "01.02.2002"
                                              :pitaja "Pitaja 2"
                                              :huomautukset {:kuvaus "Huomautus 2"
                                                             :maaraAika "02.02.2002"
                                                             :toteaja "Toteaja 2"
                                                             :toteamisHetki "03.02.2002"
                                                             :lasnaolijat "Lasnaolijat 2"
                                                             :poikkeamat "Poikkeamat 2"}}
                                  :muuTunnus "22222222-2222-2222-2222-222222222222"
                                  :muuTunnusSovellus "MATTI"}))))

      (fact "returns 204 and updates the corresponding task if there's an existing review with the given type and the same ID"
        (let [app-id (create-application-with-verdict)
              _ (put-review app-id "11111111-1111-1111-1111-111111111111"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"})
              _ (put-review app-id "22222222-2222-2222-2222-222222222222"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"})
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:katselmuksen_laji "ALOITUSKATSELMUS"
                                    :pitopaiva "2003-03-01"
                                    :pitaja "Pitaja 3"
                                    :huomautettavaa "Huomautus 3"
                                    :maaraaika "2003-03-02"
                                    :toteaja "Toteaja 3"
                                    :toteamishetki "2003-03-03"
                                    :lasnaolijat "Lasnaolijat 3"
                                    :poikkeamat "Poikkeamat 3"
                                    :liitetiedostot []})]
          (:status response) => 204
          (->> (review-tasks app-id)
               ;; Ignore attachments as they are not relevant for this test.
               (map #(dissoc % :attachments))
               set) => (-> (->> original-review-tasks
                                (map #(dissoc % :attachments))
                                (remove #(= (:muuTunnus %) "11111111-1111-1111-1111-111111111111"))
                                set)
                           (conj {:katselmuksenLaji "Aloituskatselmus"
                                  :state "sent"
                                  :katselmus {:pitoPvm "01.03.2003"
                                              :pitaja "Pitaja 3"
                                              :huomautukset {:kuvaus "Huomautus 3"
                                                             :maaraAika "02.03.2003"
                                                             :toteaja "Toteaja 3"
                                                             :toteamisHetki "03.03.2003"
                                                             :lasnaolijat "Lasnaolijat 3"
                                                             :poikkeamat "Poikkeamat 3"}}
                                  :muuTunnus "11111111-1111-1111-1111-111111111111"
                                  :muuTunnusSovellus "MATTI"}))))

      (fact "returns 400 and does nothing if there's an existing review with the same ID but a different type"
        (let [app-id (create-application-with-verdict)
              _ (put-review app-id "11111111-1111-1111-1111-111111111111"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"})
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:katselmuksen_laji "LOPPUKATSELMUS"})]
          (:status response) => 400
          (review-tasks app-id) => original-review-tasks))

      (fact "returns 400 and does nothing if the request has parts that are not review attachments"
        (let [app-id (create-application-with-verdict)
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:liitetiedostot []}
                                   {:unknown-request-part "..."})]
          (:status response) => 400
          (review-tasks app-id) => original-review-tasks))

      (fact "returns 400 and does nothing if the review refers to attachments for which there is no corresponding request part"
        (let [app-id (create-application-with-verdict)
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:liitetiedostot
                                    [{:request_part "no-such-request-part"
                                      :tyyppi "KATSELMUKSEN_POYTAKIRJA"
                                      :kuvaus nil}]})]
          (:status response) => 400
          (review-tasks app-id) => original-review-tasks))

      (fact "deletes any existing attachments and adds new ones from the request when updating the task"
        (let [app-id (create-application-with-verdict)
              valvontakaynti-review-add-ts (now)
              ;; An unrelated review with attachments, should not be touched.
              _ (put-review app-id "22222222-2222-2222-2222-222222222222"
                            {:katselmuksen_laji "MUU_VALVONTAKAYNTI"
                             :liitetiedostot
                             [{:request_part "xxxxx"
                               :tyyppi "KATSELMUKSEN_POYTAKIRJA"
                               :kuvaus "Kuvaus xxxxx"}]}
                            {"xxxxx" (file-upload "xxxxx.txt")}) ;; Testing with a string type id, too.
              aloituskatselmus-review-add-ts (now)
              _ (put-review app-id "11111111-1111-1111-1111-111111111111"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"
                             :liitetiedostot
                             [{:request_part "aaaaa"
                               :tyyppi "KATSELMUKSEN_POYTAKIRJA"
                               :kuvaus "Kuvaus aaaaa"}]}
                            {:aaaaa (file-upload "aaaaa.txt")})
              ;; Uploaded text files are automatically converted to PDF.
              _ (await-attachment-upload app-id "xxxxx.pdf")
              _ (await-attachment-upload app-id "aaaaa.pdf")
              original-review-tasks (review-tasks app-id)
              response (put-review app-id "11111111-1111-1111-1111-111111111111"
                                   {:katselmuksen_laji "ALOITUSKATSELMUS"
                                    :liitetiedostot
                                    [{:request_part "bbbbb"
                                      :tyyppi "KATSELMUKSEN_POYTAKIRJA"
                                      :kuvaus "Kuvaus bbbbb"}
                                     ;; TODO: Check the other attachment types
                                     {:request_part "ccccc"
                                      :tyyppi "VALOKUVA"
                                      :kuvaus "Kuvaus ccccc"}]}
                                   {:bbbbb (file-upload "bbbbb.txt")
                                    :ccccc (file-upload "ccccc.jpg")})
              katselmus-attachment-name-matcher (fn [timestamp]
                                                  (re-pattern (str app-id
                                                                   " Katselmuksen pöytäkirja "
                                                                   (date/finnish-date timestamp :zero-pad)
                                                                   " (\\d{2}\\.\\d{2})"
                                                                   ".pdf")))]
          (await-attachment-upload app-id "bbbbb.pdf")
          (await-attachment-upload app-id "ccccc.jpg")
          (->> (review-tasks app-id)
               (map #(select-keys % [:muuTunnus :attachments]))
               set) => (just #{;; There are two placeholder tasks and we only touch
                               ;; one so the other remains empty.
                               {:muuTunnus ""
                                :attachments #{}}
                               (just {:muuTunnus "11111111-1111-1111-1111-111111111111"
                                      :attachments (just #{{:filename "bbbbb.pdf"
                                                            :type {:type-group "katselmukset_ja_tarkastukset"
                                                                   :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                            :contents "Kuvaus bbbbb"}
                                                           {:filename "ccccc.jpg"
                                                            :type {:type-group "yleiset-alueet"
                                                                   :type-id "valokuva"}
                                                            :contents "Kuvaus ccccc"}
                                                           ;; In addition to the supplied attachments, the review gets an
                                                           ;; automatically generated review minutes document.
                                                           (just {:contents "Aloituskatselmus suomeksi"
                                                                  :filename (katselmus-attachment-name-matcher aloituskatselmus-review-add-ts)
                                                                  :type {:type-group "katselmukset_ja_tarkastukset"
                                                                         :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}}
                                                                 :in-any-order)})}
                                     :in-any-order)
                               ;; The unrelated review should still have its attachments.
                               (just {:muuTunnus "22222222-2222-2222-2222-222222222222"
                                      :attachments (just #{{:filename "xxxxx.pdf"
                                                            :type {:type-group "katselmukset_ja_tarkastukset"
                                                                   :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                            :contents "Kuvaus xxxxx"}
                                                           (just {:contents "Muu valvontakäynti suomeksi"
                                                                  :filename (katselmus-attachment-name-matcher valvontakaynti-review-add-ts)
                                                                  :type {:type-group "katselmukset_ja_tarkastukset"
                                                                         :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}}
                                                                 :in-any-order)})}
                                     :in-any-order)})))

      (fact "adds attachments to a newly created task"
        (let [app-id (create-application-with-verdict)
              modified-before (:modified (domain/get-application-as app-id matti-rest-api-user))
              ;; Use up the template task to force a new task to be created for
              ;; the next review.
              _ (put-review app-id "11111111-1111-1111-1111-111111111111"
                            {:katselmuksen_laji "ALOITUSKATSELMUS"})
              response (put-review app-id "22222222-2222-2222-2222-222222222222"
                                   {:katselmuksen_laji "ALOITUSKATSELMUS"
                                    :liitetiedostot
                                    [{:request_part "bbbbb"
                                      :tyyppi "KATSELMUKSEN_POYTAKIRJA"
                                      :kuvaus "Kuvaus bbbbb"}]}
                                   {:bbbbb (file-upload "bbbbb.txt")})
              app-after (domain/get-application-as app-id matti-rest-api-user)]
          (:status response) => 201
          (await-attachment-upload app-id "bbbbb.pdf")
          app-after => (has-updated-modified-ts modified-before)
          (->> (review-tasks app-id)
               (map #(select-keys % [:muuTunnus :attachments]))
               (filter #(= (:muuTunnus %) "22222222-2222-2222-2222-222222222222"))
               set) => #{{:muuTunnus "22222222-2222-2222-2222-222222222222"
                          :attachments #{{:filename "bbbbb.pdf"
                                          :type {:type-group "katselmukset_ja_tarkastukset"
                                                 :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
                                          :contents "Kuvaus bbbbb"}
                                         ;; In addition to the supplied attachments, the review gets an
                                         ;; automatically generated review minutes document.
                                         {:filename (str app-id
                                                         " Katselmuksen pöytäkirja "
                                                         (date/finnish-datetime (:created response) :zero-pad)
                                                         ".pdf")
                                          :type {:type-group "katselmukset_ja_tarkastukset"
                                                 :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
                                          :contents "Aloituskatselmus"}}}})))))
