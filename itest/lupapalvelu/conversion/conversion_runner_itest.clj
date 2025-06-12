(ns lupapalvelu.conversion.conversion-runner-itest
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.dmcity.client :as dm-cl]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion-test-util :refer [with-proxy]]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.conversion.kuntagml-converter :as conv-k]
            [lupapalvelu.conversion.location :as conv-xy]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review :as review]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(def non-terminal-testfile "dev-resources/krysp/0777-88-D.xml")

(def test-backend-id "99-0000-13-A")

(def non-terminal-test-backend-id "0777-88-D")
(def non-terminal-test-vrk-id "09200088-0777")

(defn jarvenpaa-xml
  []
  (conv-test/transform-xml
    (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))

(def jarvenpaa-id "14-0241-R 3")
(def jarvenpaa-vrk-id "18600014-0241")

(def non-kuntagml-file "dev-resources/mml/yhteystiedot-JU.xml")

(def vantaa-fallback-location-info {:address    "Kielotie 20 C"
                                    :propertyId "09206101180002"
                                    :x          391513.021
                                    :y          6685671.373})

(def poikkeamislupa-xml "dev-resources/krysp/poikkeamislupa.xml")
(def suunittelutarveratkaisu-xml "dev-resources/krysp/suunnittelutarveratkaisu.xml")

(defn parse-muu-tunnus
  "Returns [sovellus tunnus] or nil."
  [xml-string permit-type]
  (when-let [node (xml/select1 (xml/parse-string xml-string "utf8")
                               [(case permit-type
                                  "R" :rakval:luvanTunnisteTiedot
                                  "P" :ppst:luvanTunnistetiedot)
                                :yht:LupaTunnus :yht:muuTunnustieto :yht:MuuTunnus])]
    (mapv #(xml/get-text node [%]) [:yht:sovellus :yht:tunnus])))

(defn lp-id-xml-tests [filepath permit-type]
  (let [xml (slurp filepath)]
    (facts {:midje/description (format "XML with application id (%s)" permit-type)}
      (fact "The testfile does not contain any application id"
        (parse-muu-tunnus xml permit-type) => nil)

      (fact "Augment testfile with application id"
        (let [xmlstr (conv-doc/xml-with-application-id {:LP-id       "LP-MY-BETTER-IDENTIFIER"
                                                        :permit-type permit-type
                                                        :kuntagml    {:xml xml}})]
          (string? xmlstr) => true
          (parse-muu-tunnus xmlstr permit-type)
          => ["Lupapiste" "LP-MY-BETTER-IDENTIFIER"])))))

(defn conversions
  ([q]
   (seq (mongo/select :conversion q)))
  ([]
   (conversions {:organization "092-R"})))

(defn applications-search
  ([application-type include-converted?]
   (let [args [:applicationType application-type
               :includeConverted include-converted?
               :limit 100]]
     {:applications (-> (apply local-query vantaa-r-biller
                               :applications-search
                               args)
                        :data :applications)
      :total-count (-> (apply local-query vantaa-r-biller
                              :applications-search-total-count
                              args)
                       :data :totalCount)}))
  ([application-type]
   (applications-search application-type nil)))

(defn doc-value
  "`kwp` is keyword path between `:data` and `:value`."
  [doc kwp]
  (->> (util/kw-path :data kwp :value)
       util/split-kw-path
       (get-in doc)))

(defn doc-checks [doc kwp-value-map]
  (doseq [[kwp v] kwp-value-map]
    (fact {:midje/description (str (name kwp) " -> " v)}
      (doc-value doc kwp) => v)))

(defn config-tests
  "Refactored out of the too large main block."
  [test-dir]

  (facts "resolve-filepath"
    (conv-cfg/resolve-filepath "" "foo/bar.txt")
    => (conv-test/abspath "foo/bar.txt")
    (conv-cfg/resolve-filepath "/" "foo/bar.txt")
    => (conv-test/abspath "/foo/bar.txt")
    (conv-cfg/resolve-filepath "/" "bar.txt")
    => (conv-test/abspath "/bar.txt")
    (conv-cfg/resolve-filepath (conv-test/tmp-edn-file test-dir) "bar.txt")
    => (conv-test/abspath (str test-dir "/bar.txt"))
    (conv-cfg/resolve-filepath "/hii/hoo/doh.txt" "/foo/bar.txt")
    => (conv-test/abspath "/foo/bar.txt"))

  (fact "Configuration file"
    (fact "Write: Bad arguments"
      (conv-test/write-config test-dir "092-R" :permit-type "BAD"
                              :ids ["99-0001-13-A" "99-0002-13-A"])
      => (throws))
    (fact "Write: Ids are trimmed, bad and duplicate ids are ignored"
      (-> (conv-test/write-config test-dir "092-R" :permit-type "R"
                                  :ids ["foo" " 99-0001-13-A " "bar"
                                        "99-0002-13-A" " "
                                        "  99-0001-13-A  "])
          util/read-edn-file
          :backend-ids)
      => ["99-0001-13-A" "99-0002-13-A"])

    (fact "Write: id not found, file ignored"
      (let [cfg (conv-test/write-config test-dir "123-R" :glob non-kuntagml-file)]
        (util/read-edn-file cfg) => {:organization-id "123-R"}))

    (fact "Write: bad id, file ignored"
      (let [_   (conv-test/write-test-xml test-dir "bad-id-test.xml"
                                          :backend-id "bad to the file")
            cfg (conv-test/write-config test-dir "123-R" :glob "bad-id-test.xml")]
        (util/read-edn-file cfg) => {:organization-id "123-R"}))

    (fact "Relative paths"
      (util/read-edn-file (conv-test/write-config test-dir "123-R"
                                                  :glob "*.xml"))
      => {:organization-id "123-R"
          :files           ["99-0000-13-A.xml"]})

    (fact "Read: Bad data"
      (conv-cfg/read-configuration "foobar.edn") => (throws)
      (provided (sade.util/read-edn-file "foobar.edn") => {})
      (conv-cfg/read-configuration "foobar.edn") => (throws)
      (provided (sade.util/read-edn-file "foobar.edn") => {:organization-id "092-R"
                                                           :permit-type     "bad"}))
    (fact "Configuration can have missing files"
      (conv-cfg/read-configuration "foobar.edn")
      => {:organization-id "092-R"
          :permit-type     "R"
          :municipality    "092"
          :targets         [{:id "99-0001-13-A"}
                            {:id "99-0002-13-A"}
                            {:filename (conv-test/abspath "no-such-file.xml")}]}
      (provided (sade.util/read-edn-file "foobar.edn")
                => {:organization-id "092-R"
                    :permit-type     "R"
                    :backend-ids     ["99-0001-13-A" "99-0002-13-A"]
                    :files           ["no-such-file.xml"]}))
    (fact "Read: Good data"
      (conv-cfg/read-configuration "foobar.edn")
      => {:organization-id "092-R"
          :permit-type     "R"
          :municipality    "092"
          :overwrite?      true
          :targets         [{:id "99-0001-13-A"}
                            {:id "99-0002-13-A"}
                            {:filename (conv-test/abspath conv-test/testfile)}]}
      (provided (sade.util/read-edn-file "foobar.edn")
                => {:organization-id " 092-R "
                    :permit-type     " R "
                    :overwrite?      true
                    :backend-ids     ["  99-0001-13-A " " 99-0002-13-A "]
                    :files           [(str "  " conv-test/testfile "  ")]})
      (conv-cfg/read-configuration "foobar.edn")
      => {:organization-id "092-R" :municipality "092"
          :permit-type     "R"     :targets      [{:id "11-5555-FOO"}]}
      (provided (sade.util/read-edn-file "foobar.edn")
                => {:organization-id "092-R"
                    :permit-type     "R"
                    :backend-ids     ["11-5555-FOO"]}))))

(defn review-checks [apikey {:keys [tasks id]}]
  (let [review (util/find-first #(and (= (:state %) "sent")
                                      (= "review" (get-in % [:schema-info :subtype]))
                                      (= "background" (get-in % [:source :type])))
                                tasks)]
    (fact "Sent review"
      review => truthy)
    (fact "Authority can edit a sent review of a converted application"
      (local-command apikey :update-task :id id
                     :doc (:id review)
                     :updates [["katselmus.huomautukset.kuvaus" "Hello world!"]])
      => ok?)))

(let [test-dir      (conv-test/mk-test-dir)
      testfile-name (fs/file-name conv-test/testfile)
      testfile-path (str (fs/path test-dir testfile-name))]

  (with-test-system (-> {:fixture            "minimal"
                         :schema-validation? false}
                        test-system/with-remote-components)
    (doseq [testfile [conv-test/testfile non-kuntagml-file]]
      (spit (str (fs/path test-dir (fs/file-name testfile)))
            (conv-test/transform-xml testfile)))

    (with-proxy nil
      (against-background
          [(after :contents (conv-test/rm-test-dir test-dir))]

        (config-tests test-dir)

        (lp-id-xml-tests conv-test/testfile "R")

        (fact "No conversions pending"
          (conversions) => nil)
        (facts "Generate configuration file, store and run conversion"
          (let [edn-file (conv-test/write-config test-dir "092-R" :glob testfile-name
                                                 :tags ["one" "two"])]
            (fact "Write and read configuration"
              (conv-cfg/read-configuration edn-file)
              => {:organization-id "092-R"
                  :municipality    "092"
                  :tags            ["one" "two"]
                  :targets         [{:filename testfile-path}]})
            (fact "Store"
              (conv/execute-conversion-command {:cmd     :store
                                                :cfgfile edn-file
                                                :system  (test-system/get-common-components)}) => nil)
            (let [[r & _ :as results] (conversions)]
              (count results) => 1
              (:LP-id r) => nil
              r => (contains {:backend-id test-backend-id
                              :kuntagml   (just {:xml truthy})
                              :tags       ["one" "two"]
                              :n          1
                              :history    (just (just {:op "store" :ts pos?}))})
              (fact "No document application"
                (conv-doc/read-application (conv-doc/document "092-R" test-backend-id))
                => nil)
              (facts "Convert"
                (conv/execute-conversion-command {:cmd     :convert
                                                  :cfgfile edn-file
                                                  :system  (test-system/get-common-components)})
                (let [[{app-id :LP-id :as r} & _ :as results] (conversions)]
                  (count results) => 1
                  r => (contains {:backend-id  test-backend-id
                                  :kuntagml    (just {:xml truthy})
                                  :converted   true
                                  :suffix      "a"
                                  :reset-state "closed"
                                  :LP-id       truthy
                                  :tags        ["one" "two"]
                                  :n           1
                                  :history     (just (just {:op "store" :ts pos?})
                                                     (just {:op "convert" :ts pos?}))})
                  (fact "Document has application"
                    (conv-doc/read-application (conv-doc/document "092-R" test-backend-id)
                                               [:address :state])
                    => {:id    app-id :address (:address vantaa-fallback-location-info)
                        :state "closed"}
                    (-> (conv-doc/document "092-R" test-backend-id)
                        (assoc :conversion-error :unknown)
                        conv-doc/read-application)
                    => nil)

                  (fact "Application is created"
                    (let [{:keys [verdicts created primaryOperation secondaryOperations
                                  documents statements tasks attachments]
                           :as   a} (mongo/by-id :applications app-id)]
                      a => (contains {:facta-imported true
                                      :propertyId     (:propertyId vantaa-fallback-location-info)
                                      :address        (:address vantaa-fallback-location-info)
                                      :municipality   "092"
                                      :permitType     "R"
                                      :organization   "092-R"})
                      (conv-doc/read-application (conv-doc/document "092-R" test-backend-id))
                      => a
                      (fact "Verdict OK"
                        (count verdicts) => 1
                        (:kuntalupatunnus (first verdicts)) => test-backend-id)
                      (fact "Some reviews marked done"
                        (review-checks vantaa-r-biller a))
                      (facts "There are verdict and review attachments"
                        attachments => (just (contains {:type          {:type-group "paatoksenteko" :type-id "paatosote"}
                                                        :target        (just {:id      (-> verdicts first :id)
                                                                              :type    "verdict"
                                                                              :urlHash truthy})
                                                        :fetched       pos?
                                                        :latestVersion (contains {:filename "sample-verdict.pdf"})})
                                             (contains {:type          {:type-group "katselmukset_ja_tarkastukset"
                                                                        :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                        :target        (just {:id      truthy
                                                                              :type    "task"
                                                                              :urlHash truthy})
                                                        :fetched       pos?
                                                        :latestVersion (contains {:filename "sample-attachment.pdf"})})))
                      (against-background
                          [(sade.core/now) => 1716414616906]
                        (facts {:midje/description (str "Fetch missing review attachments for " app-id)}
                          (let [doc  (conv-doc/document {:LP-id app-id})
                                cfg  {:user            (usr/batchrun-user ["092-R"])
                                      :organization-id "092-R"
                                      :permit-type     "R"
                                      :created         (sade.core/now)
                                      :municipality    "092"
                                      :system          (test-system/get-common-components)}
                                cfg2 (dissoc cfg :user :created)
                                cfg3 (assoc cfg2 :targets [{:id (:backend-id doc)}])]

                            (facts "kuntagml-attachments-for-tasks"
                              (let [xml (conv-doc/read-xml doc)]
                                (facts "Nothing"
                                  (review/kuntagml-attachments-for-tasks nil xml)
                                  => (throws #"Assert failed: application-id")
                                  (review/kuntagml-attachments-for-tasks app-id
                                                                         (enlive/at xml
                                                                                    [:liite] nil
                                                                                    [:Liite] nil))
                                  => nil
                                  (review/kuntagml-attachments-for-tasks app-id nil) => nil
                                  (review/kuntagml-attachments-for-tasks app-id []) => nil
                                  (review/kuntagml-attachments-for-tasks nil nil) => (throws))
                                (fact "Success"
                                  (let [kaft        (review/kuntagml-attachments-for-tasks a xml)
                                        muu-tunnus  "HISSID0B0000000000CC00A000A0D0"
                                        [att1 att2] (vals kaft)
                                        link        (just (just {:liite (just {:kuvaus        "Katselmuksen pöytäkirja: Hissitarkastus"
                                                                               :linkkiliitteeseen
                                                                               #"/dev/sample-attachment.txt",
                                                                               :muokkausHetki "2024-05-22T17:02:41+03:00"
                                                                               :tyyppi        "katselmuksen_tai_tarkastuksen_poytakirja"})}))]

                                    (keys kaft) => (just muu-tunnus truthy :in-any-order)

                                    (count att1) => 1
                                    att1 => link
                                    att1 => att2
                                    (facts "No suitable muu tunnus, only generated id"
                                      (let [x  (review/kuntagml-attachments-for-tasks app-id
                                                                                      (enlive/at xml
                                                                                                 [:tunnus] (enlive/content "foobar")))
                                            kx (first (keys x))
                                            y  (review/kuntagml-attachments-for-tasks app-id
                                                                                      (-> (xml/element-to-string xml)
                                                                                          (ss/replace muu-tunnus app-id)
                                                                                          (xml/parse-string "utf8")))
                                            ky (first (keys y))]
                                        (= (count (keys x)) (count (keys y)) 1) => true
                                        kx => truthy
                                        ky => truthy
                                        (not= kx ky) => true
                                        (not= kx muu-tunnus) => true
                                        (not= ky muu-tunnus) => true
                                        (-> (get x kx)) => link
                                        (get y ky) => link))))))

                            (facts "Attachment missing, adds it"
                              (let [fetched? #(mongo/any? :applications {:_id app-id :attachments.target.type :task})
                                    edn-path (conv-test/write-config test-dir "092-R")]
                                (doseq [[txt fun] [["direct doc" #(conv-k/fetch-missing-review-attachments (merge cfg %) doc)]
                                                   ["by apps" #(conv/conversion--fetch-review-attachments (merge cfg2 %))]
                                                   ["by backend ids" #(conv/conversion--fetch-review-attachments (merge cfg3 %))]
                                                   ["batchrun for organization" (fn [_]
                                                                                  (conv/execute-conversion-command
                                                                                    {:cmd     :fetch-review-attachments
                                                                                     :cfgfile edn-path
                                                                                     :system  (test-system/get-common-components)}))]]]
                                  (fact "Reset the target application"
                                    (mongo/remove-by-id :applications app-id)
                                    (mongo/insert :applications a)
                                    (mongo/by-id :applications app-id) => a)
                                  (fact "Nothing missing/available, does nothing 1"
                                    (fun nil) => nil
                                    (mongo/by-id :applications app-id) => a)
                                  (fact {:midje/description txt}
                                    (mongo/update-by-id :applications app-id
                                                        {$pull {:attachments {:target.type :task}}}) => nil
                                    (fetched?) => false
                                    (when (contains? #{"by apps" "by backend-ids"} txt)
                                      (facts "Non matching queries"
                                        (fun {:years [2222]}) => nil
                                        (fun {:range [1000 2000]}) => nil
                                        (fun {:tags ["nope"]}) => nil
                                        (fetched?) => false))

                                    (fact "Must be converted application"
                                      (mongo/update-by-id :applications app-id {$unset {:facta-imported true}})
                                      (fun nil)
                                      (fetched?) => false)

                                    (mongo/update-by-id :applications app-id {$set {:facta-imported true}})

                                    (fact "Fetch now"
                                      (fun nil) => nil
                                      (fetched?) => true)

                                    (let [a2 (mongo/by-id :applications app-id)]
                                      (fact "Fetched"
                                        (:attachments a2)
                                        => (just (contains {:type          {:type-group "paatoksenteko" :type-id "paatosote"}
                                                            :latestVersion (contains {:filename "sample-verdict.pdf"})})
                                                 (contains {:type          {:type-group "katselmukset_ja_tarkastukset"
                                                                            :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                            :target        (just {:id      truthy
                                                                                  :type    "task"
                                                                                  :urlHash truthy})
                                                            :fetched       (:created cfg)
                                                            :latestVersion (contains {:filename "sample-attachment.pdf"})})))
                                      (fact "Nothing missing/available, does nothing 2"
                                        (fun nil) => nil
                                        (mongo/by-id :applications app-id) => a2))
                                    (fact "Does not add missing verdict attachment"
                                      (mongo/update-by-id :applications app-id {$pull {:attachments {:target.type :verdict}}})
                                      (fun nil)
                                      (:attachments (mongo/by-id :applications app-id))
                                      => (just (contains {:type          {:type-group "katselmukset_ja_tarkastukset"
                                                                          :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                          :target        (just {:id      truthy
                                                                                :type    "task"
                                                                                :urlHash truthy})
                                                          :fetched       (:created cfg)
                                                          :latestVersion (contains {:filename "sample-attachment.pdf"})})))
                                    (fact "No tasks, does nothing"
                                      (mongo/update-by-id :applications app-id {$set {:attachments []
                                                                                      :tasks       []}})
                                      (fun nil) => nil
                                      (mongo/by-id :applications app-id)
                                      => (contains {:tasks [] :attachments []})))))))))

                      ;; Add auth to the application
                      (mongo/update-by-id :applications app-id {$set  {:address "Conversion Corner"}
                                                                $push {:auth {:id "hello"}}})

                      (fact "Primary operation and its document"
                        (let [op-id (:id primaryOperation)
                              doc   (util/find-first (util/fn-> :schema-info :op :id (= op-id))
                                                     documents)]
                          op-id => truthy
                          primaryOperation => (contains {:name        "kerrostalo-rivitalo"
                                                         :description "Niinkö"})
                          (-> doc :schema-info :name) => "uusiRakennus"
                          (doc-checks doc
                                      {:huoneistot.32.WCKytkin                                    true
                                       :huoneistot.32.ammeTaiSuihkuKytkin                         true
                                       :huoneistot.32.huoneistoTyyppi                             "asuinhuoneisto"
                                       :huoneistot.32.huoneistoala                                "00"
                                       :huoneistot.32.huoneistonumero                             "000"
                                       :huoneistot.32.huoneluku                                   "0"
                                       :huoneistot.32.porras                                      "B"
                                       :huoneistot.33.WCKytkin                                    nil
                                       :kaytto.kayttotarkoitus                                    "000 muut asuinkerrostalot"
                                       :kaytto.rakentajaTyyppi                                    "liiketaloudellinen"
                                       :lammitys.lammitystapa                                     "vesikeskus"
                                       :lammitys.lammonlahde                                      "kauko tai aluelämpö"
                                       :luokitus.energialuokka                                    "C"
                                       :luokitus.energiatehokkuusluku                             "000"
                                       :luokitus.energiatehokkuusluvunYksikko                     "kWh/brm0/vuosi"
                                       :luokitus.paloluokka                                       "P0"
                                       :rakennuksenOmistajat.0._selected                          "yritys"
                                       :rakennuksenOmistajat.0.omistajalaji                       "valtio- tai kuntaenemmistöinen yritys"
                                       :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus        "000000-0"
                                       :rakennuksenOmistajat.0.yritys.osoite.katu                 "Wall Street 000"
                                       :rakennuksenOmistajat.0.yritys.osoite.postinumero          "00400"
                                       :rakennuksenOmistajat.0.yritys.osoite.postitoimipaikannimi "BONUSKAUPUNKI"
                                       :rakennuksenOmistajat.0.yritys.yritysnimi                  "Mega Hyvät Huippu Kämpät Oy"})))

                      (fact "Secondary operation and its document"
                        (let [op-id (-> secondaryOperations first :id)
                              doc   (util/find-first (util/fn-> :schema-info :op :id (= op-id))
                                                     documents)]
                          (count secondaryOperations) => 1
                          op-id => truthy
                          (first secondaryOperations) => (contains {:name        "kerrostalo-rivitalo"
                                                                    :description nil})
                          (-> doc :schema-info :name) => "uusiRakennus"
                          (doc-checks doc {:huoneistot.33.WCKytkin                                    true
                                           :huoneistot.33.ammeTaiSuihkuKytkin                         true
                                           :huoneistot.33.huoneistoTyyppi                             "asuinhuoneisto"
                                           :huoneistot.33.huoneistoala                                "00,0"
                                           :huoneistot.33.huoneistonumero                             "000"
                                           :huoneistot.33.huoneluku                                   "0"
                                           :huoneistot.33.keittionTyyppi                              "keittotila"
                                           :huoneistot.33.porras                                      "A"
                                           :kaytto.kayttotarkoitus                                    "000 muut asuinkerrostalot"
                                           :kaytto.rakentajaTyyppi                                    "liiketaloudellinen"
                                           :lammitys.lammitystapa                                     "vesikeskus"
                                           :lammitys.lammonlahde                                      "kauko tai aluelämpö"
                                           :luokitus.energialuokka                                    "C"
                                           :luokitus.energiatehokkuusluku                             "000"
                                           :luokitus.energiatehokkuusluvunYksikko                     "kWh/m0"
                                           :luokitus.paloluokka                                       "P0"
                                           :rakennuksenOmistajat.0._selected                          "yritys"
                                           :rakennuksenOmistajat.0.omistajalaji                       "valtio- tai kuntaenemmistöinen yritys"
                                           :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus        "000000-0"
                                           :rakennuksenOmistajat.0.yritys.osoite.katu                 "Wall Street 000"
                                           :rakennuksenOmistajat.0.yritys.osoite.postinumero          "00400"
                                           :rakennuksenOmistajat.0.yritys.osoite.postitoimipaikannimi "BONUSKAUPUNKI"
                                           :rakennuksenOmistajat.0.yritys.yritysnimi                  "Mega Hyvät Huippu Kämpät Oy"})))
                      (fact "Info document"
                        (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hankkeen-kuvaus"))
                                                     documents)
                                    {:kuvaus "Niinkö" :poikkeamat "Jou"}))

                      (fact "Designer documents"
                        (let [docs    (filter (util/fn-> :schema-info :subtype (= "suunnittelija"))
                                              documents)
                              kvv-doc (util/find-first (util/fn-> :data :kuntaRoolikoodi :value (= "KVV-suunnittelija"))
                                                       documents)]
                          (count docs) => 5
                          (doc-checks kvv-doc {:henkilotiedot.etunimi       "Milja"
                                               :henkilotiedot.sukunimi      "Mieletön"
                                               :osoite.katu                 "Torkkupolku 00"
                                               :osoite.postinumero          "00400"
                                               :osoite.postitoimipaikannimi "UNELA"
                                               :patevyys.koulutusvalinta    "LVI-insinööri"
                                               :patevyys.valmistumisvuosi   "0000"
                                               :yhteystiedot.email          "wolf00@hotmail.com"
                                               :yhteystiedot.puhelin        "000 0000 000"})))

                      (fact "Applicant document"
                        (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hakija-r"))
                                                     documents)
                                    {:_selected                                 "yritys"
                                     :yritys.liikeJaYhteisoTunnus               "0000000-0"
                                     :yritys.osoite.katu                        "Wall Street 000"
                                     :yritys.osoite.postinumero                 "00400"
                                     :yritys.osoite.postitoimipaikannimi        "BONUSKAUPUNKI"
                                     :yritys.yritysnimi                         "Huvikummun kaupunki/Mega Hyvät Huippu Kämpät Oy"
                                     :yritys.yhteyshenkilo.yhteystiedot.puhelin "000 000 0000"
                                     :yritys.yhteyshenkilo.yhteystiedot.email   "paula.pamppu@mhhk.fi"}))

                      (fact "Payer document"
                        (doc-checks (util/find-first (util/fn-> :schema-info :name (= "maksaja"))
                                                     documents)
                                    {:_selected                                 "yritys"
                                     :yritys.liikeJaYhteisoTunnus               "000000-0"
                                     :yritys.osoite.katu                        "Wall Street 000"
                                     :yritys.osoite.postinumero                 "00400"
                                     :yritys.osoite.postitoimipaikannimi        "BONUSKAUPUNKI"
                                     :yritys.yritysnimi                         "Mega Hyvät Huippu Kämpät Oy"
                                     :yritys.yhteyshenkilo.yhteystiedot.puhelin "000 000 0000"
                                     :yritys.yhteyshenkilo.yhteystiedot.email   "paula.pamppu@mhhk.fi"}))

                      (fact "Statement"
                        (count statements) => 1
                        (first statements)
                        => (contains {:given  1356991200000 ;; 1.1.2013
                                      :person (contains {:name "Huvikummun Voima"})
                                      :text   "Rakennus on liitettävissä kaukolämpöön.\nRakennus on liitettävissä sähköverkkoon."
                                      :state  "given"
                                      :status "puollettu"}))

                      (fact "Overwrite false leaves the application as is"
                        (conv/execute-conversion-command {:cmd     :convert
                                                          :cfgfile edn-file
                                                          :system  (test-system/get-common-components)})
                        (:address (mongo/by-id :applications app-id))
                        => "Conversion Corner"
                        (conversions) => results)

                      (let [edn-file   (conv-test/write-config test-dir "092-R"
                                                               :overwrite? true
                                                               :glob testfile-name)
                            old        (first results)
                            unchanged? (fn [& [err]]
                                         (fact "No conversion"
                                           (:address (mongo/by-id :applications app-id))
                                           => "Conversion Corner"
                                           (conversions) => (just (just (cond-> old
                                                                          err (assoc :conversion-error
                                                                                     (just {:code err
                                                                                            :text truthy})))))))]
                        (fact "Overwrite fails since the application is authed"
                          (conv/execute-conversion-command {:cmd     :convert
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (unchanged? "clean-failed"))
                        (mongo/update-by-id :applications app-id {$pull {:auth {:id "hello"}}})
                        (fact "Only previously imported can be overwritten"
                          (mongo/update-by-id :applications app-id {$unset {:facta-imported 1}})
                          (conv/execute-conversion-command {:cmd     :convert
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (unchanged? "clean-failed"))
                        (fact "Since conversion-error, plain overwrite? is not enough"
                          (mongo/update-by-id :applications app-id {$set {:facta-imported true
                                                                          :foo            "bar"}})
                          (conv/execute-conversion-command {:cmd     :convert
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (unchanged? "clean-failed"))
                        (fact "Successful overwrite, with retry option"
                          (conv/execute-conversion-command {:cmd     :convert
                                                            :cfgfile edn-file
                                                            :retry   true
                                                            :system  (test-system/get-common-components)})
                          (let [[{app-id2 :LP-id :as r} & _ :as results] (conversions)
                                a                                        (mongo/by-id :applications app-id)]
                            (count results) => 1
                            app-id2 => app-id
                            (:history r) => (just (just {:op "store" :ts pos?})
                                                  (just {:op "convert" :ts pos?})
                                                  (just {:op "clean" :ts pos?})
                                                  (just {:op "convert" :ts pos?}))
                            (:converted r) => true
                            (> (:created a) created) => true
                            (contains? a :foo) => false
                            (:address a) => (:address vantaa-fallback-location-info)))
                        (facts "Send messages to backing system"
                          (conv/execute-conversion-command {:cmd           :send
                                                            :cfgfile       edn-file
                                                            :state-changes true
                                                            :kuntagml      true
                                                            :system        (test-system/get-common-components)}) => nil
                          (provided
                            (dm-cl/send-messages anything [:all-states :application])
                            => nil :times 1)
                          (fact "Document history changed"
                            (-> (conversions) first :history last)
                            => (just {:op "send" :ts pos?})))

                        (facts "Send only the last message to the backing system"
                          (conv/execute-conversion-command {:cmd        :send
                                                            :cfgfile    edn-file
                                                            :last-state true
                                                            :system     (test-system/get-common-components)}) => nil
                          (provided
                            (dm-cl/send-messages anything [:last-state])
                            => nil :times 1))

                        (facts "Send command must have sensible options"
                          (conv/execute-conversion-command {:cmd           :send
                                                            :cfgfile       edn-file
                                                            :last-state    false
                                                            :state-changes false
                                                            :cmd-help      (constantly nil)
                                                            :system        (test-system/get-common-components)})
                          => nil
                          (provided (usr/batchrun-user anything) => nil :times 0)
                          (conv/execute-conversion-command {:cmd      :send
                                                            :cfgfile  edn-file
                                                            :cmd-help (constantly nil)
                                                            :system   (test-system/get-common-components)})
                          => nil
                          (provided (usr/batchrun-user anything) => nil :times 0)
                          (conv/execute-conversion-command {:cmd           :send
                                                            :cfgfile       edn-file
                                                            :last-state    true
                                                            :state-changes true
                                                            :cmd-help      (constantly nil)
                                                            :system        (test-system/get-common-components)})
                          => nil
                          (provided (usr/batchrun-user anything) => nil :times 0))))))))))

        (facts "Terminal state coercion"
          (fs/copy non-terminal-testfile test-dir)
          (let [convert-and-get        (fn [& [force?]]
                                         (let [edn-file (conv-test/write-config test-dir "092-R"
                                                                                :glob (fs/file-name non-terminal-testfile)
                                                                                :overwrite? true
                                                                                :force-terminal-state? force?)
                                               _        (conv/execute-conversion-command {:cmd     :store
                                                                                          :cfgfile edn-file
                                                                                          :system  (test-system/get-common-components)})
                                               _        (conv/execute-conversion-command {:cmd     :convert
                                                                                          :cfgfile edn-file
                                                                                          :system  (test-system/get-common-components)})
                                               results  (conversions {:backend-id non-terminal-test-backend-id})]
                                           (mongo/by-id :applications (-> results first :LP-id))))
                {forced-state :state}  (convert-and-get true)
                {non-forced-state :state
                 verdicts         :verdicts
                 app-id           :id} (convert-and-get)]

            (fact "Force-terminal-state true"
              forced-state => "closed")
            (fact "Force-terminal-state false"
              non-forced-state => "submitted")
            (fact "Draft verdict is created"
              verdicts => (just (just {:id              truthy
                                       :draft           true
                                       :kuntalupatunnus non-terminal-test-backend-id
                                       :vrk-lupatunnus  non-terminal-test-vrk-id
                                       :timestamp       nil
                                       :paatokset       []})))
            (fact "Subsequent store-backend-ids does nothing"
              (conv-k/store-backend-ids app-id {:kuntalupatunnus "hello"
                                                :vrk-lupatunnus  "world"} ) => 0
              (:verdicts (mongo/by-id :applications app-id [:verdicts]))
              => verdicts)))))

    (let [test-dir (conv-test/mk-test-dir)]
      (doseq [testfile [conv-test/testfile non-kuntagml-file]]
        (spit (str (fs/path test-dir (fs/file-name testfile)))
              (conv-test/transform-xml testfile)))

      (with-proxy nil
        (against-background
            [(after :contents (conv-test/rm-test-dir test-dir))]

          (facts "Non-Vantaa conversion"
            (let [xml-filepath (str test-dir "/jarvenpaa-kuntagml.xml")
                  _            (spit xml-filepath (jarvenpaa-xml))
                  edn-file     (conv-test/write-config test-dir "186-R"
                                                       :glob (fs/file-name xml-filepath))]
              (mongo/any? :applications {:organization "186-R"}) => false
              (conversions {:organization "186-R"}) => nil
              (conv/execute-conversion-command {:cmd     :store
                                                :cfgfile edn-file
                                                :system  (test-system/get-common-components)})
              (conv/execute-conversion-command {:cmd     :convert
                                                :cfgfile edn-file
                                                :system  (test-system/get-common-components)})
              (let [[{app-id :LP-id :as r} & _
                     :as results] (conversions {:organization "186-R"})
                    {:keys [attachments verdicts]
                     :as   a}     (mongo/by-id :applications app-id)
                    vid           (:id (first verdicts))]
                (count results) => 1
                r = > (contains {:converted    true
                                 :organization "186-R"})
                a => (contains {:facta-imported true
                                :municipality   "186"
                                :organization   "186-R"
                                :propertyId     "18600303560006"
                                :address        "Kylykuja 3-5 D 35b-c"
                                :verdicts       (just (contains {:kuntalupatunnus jarvenpaa-id
                                                                 :vrk-lupatunnus  jarvenpaa-vrk-id}))})
                (count attachments) => 1
                (first attachments)
                => (contains {:target        (contains {:id      vid
                                                        :type    "verdict"
                                                        :urlHash truthy})
                              :contents      "Päätösote"
                              :type          { :type-id   "paatosote"
                                              :type-group "paatoksenteko" }
                              :latestVersion (contains {:archivable     true
                                                        :autoConversion true
                                                        :filename       "sample-attachment.pdf"})})
                (fact "Add missing backend ids"
                  (fact "Remove vrk-lupatunnus"
                    (mongo/update-by-id :applications app-id {$unset {:verdicts.0.vrk-lupatunnus true}})
                    (mongo/any? :applications {:_id                     app-id
                                               :verdicts.vrk-lupatunnus {$exists true}})
                    => false)
                  (fact "Bad XML has been stored after convert. No backend-id update"
                    (mongo/update-by-query :conversion {:LP-id app-id}
                                           {$set {:conversion-error {:code :bad-kuntagml
                                                                     :text "Bad"}}})
                    => 1
                    (conv/execute-conversion-command {:cmd     :backend-ids
                                                      :cfgfile edn-file
                                                      :system  (test-system/get-common-components)})
                    (mongo/any? :applications {:_id                     app-id
                                               :verdicts.vrk-lupatunnus {$exists true}})
                    => false
                    (mongo/update-by-query :conversion {:LP-id app-id}
                                           {$unset {:conversion-error true}
                                            $set   {:kuntagml.error "Bad"}})
                    => 1
                    (conv/execute-conversion-command {:cmd     :backend-ids
                                                      :cfgfile edn-file
                                                      :system  (test-system/get-common-components)})
                    (mongo/any? :applications {:_id                     app-id
                                               :verdicts.vrk-lupatunnus {$exists true}})
                    => false
                    (mongo/update-by-query :conversion {:LP-id app-id}
                                           {$unset {:kuntagml.error true}})
                    => 1)
                  (fact "Add missing vrk-lupatunnus"
                    (conv/execute-conversion-command {:cmd     :backend-ids
                                                      :cfgfile edn-file
                                                      :system  (test-system/get-common-components)})

                    (let [{fixed-verdicts :verdicts} (mongo/by-id :applications app-id)]
                      fixed-verdicts => verdicts
                      (-> fixed-verdicts first :vrk-lupatunnus) => jarvenpaa-vrk-id
                      (fact "Not added if not needed"
                        (conv/execute-conversion-command {:cmd     :backend-ids
                                                          :cfgfile edn-file
                                                          :system  (test-system/get-common-components)})
                        (:verdicts (mongo/by-id :applications app-id))
                        => verdicts)))
                  (facts "Add missing verdict"
                    (mongo/update-by-id :applications app-id {$unset {:verdicts true}})
                    (fact "Verdicts are gone"
                      (:verdicts (mongo/by-id :applications app-id)) => nil)
                    (fact "Add missing draft verdict"
                      (conv/execute-conversion-command {:cmd     :backend-ids
                                                        :cfgfile edn-file
                                                        :system  (test-system/get-common-components)})

                      (let [{:keys [verdicts]} (mongo/by-id :applications app-id)]
                        verdicts => (just (just {:id              truthy
                                                 :draft           true
                                                 :kuntalupatunnus jarvenpaa-id
                                                 :vrk-lupatunnus  jarvenpaa-vrk-id
                                                 :timestamp       nil
                                                 :paatokset       []}))
                        (fact "Not added if not needed"
                          (conv/execute-conversion-command {:cmd     :backend-ids
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (:verdicts (mongo/by-id :applications app-id))
                          => verdicts)))
                    (facts "Add missing real verdict and reviews"
                      (mongo/update-by-id :applications app-id {$unset {:verdictDate true
                                                                        :verdicts    true
                                                                        :tasks       true}})
                      (conv/execute-conversion-command {:cmd     :fix-verdicts
                                                        :cfgfile edn-file
                                                        :system  (test-system/get-common-components)})

                      (let [{:keys [verdicts verdictDate
                                    tasks]} (mongo/by-id :applications app-id)
                            history         (:history (conv-doc/document {:LP-id app-id}))]
                        verdictDate => pos?
                        verdicts => (just (just {:id              truthy
                                                 :kuntalupatunnus jarvenpaa-id
                                                 :vrk-lupatunnus  jarvenpaa-vrk-id
                                                 :timestamp       truthy
                                                 :paatokset       (just truthy)}))
                        (last history) => (just {:op :fix-verdicts :ts truthy})
                        (fact "Not added if not needed"
                          (conv/execute-conversion-command {:cmd     :fix-verdicts
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (let [a (mongo/by-id :applications app-id)]
                            (:verdicts a) => verdicts
                            (:tasks a) => tasks
                            (:history (conv-doc/document {:LP-id app-id}))
                            => history))))
                    (facts "Add missing reviews only"
                      (fact "Remove aloitusilmoitus"
                        (mongo/update-by-id :applications app-id {$pull {:tasks {:taskname "Aloitusilmoitus"}}})
                        (mongo/any? :applications {:_id app-id :tasks.taskname :Aloitusilmoitus})
                        => false)
                      (fact "Fix reviews"
                        (let [old-tasks (:tasks (mongo/by-id :applications app-id [:tasks]))
                              _         (conv/execute-conversion-command {:cmd     :fix-reviews
                                                                          :cfgfile edn-file
                                                                          :system  (test-system/get-common-components)})
                              new-tasks (:tasks (mongo/by-id :applications app-id [:tasks]))]
                          (butlast new-tasks) => old-tasks
                          (last new-tasks) => (contains {:taskname "Aloitusilmoitus"
                                                         :state    "sent"
                                                         :source   {:type "background"}})))
                      (fact "Rerun does nothing"
                        (let [old-app (mongo/by-id :applications app-id)
                              old-doc (mongo/select-one :conversion {:LP-id app-id})]
                          (conv/execute-conversion-command {:cmd     :fix-reviews
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (mongo/by-id :applications app-id) => old-app
                          (mongo/select-one :conversion {:LP-id app-id}) => old-doc)))))
                (let [get-app     #(util/filter-map-by-val some? (mongo/by-id :applications app-id))
                      start-app   (get-app)
                      get-doc     #(conv-doc/document "186-R" jarvenpaa-id)
                      states-run? #(->> (get-doc)
                                        :history
                                        (util/find-by-key :op :fix-history)
                                        boolean)]
                  (facts "Reinitialize states"
                    (fact "No reinitialization needed"
                      (:reset-state (get-doc)) => :constructionStarted
                      (conv/execute-conversion-command {:cmd     :fix-history
                                                        :cfgfile edn-file
                                                        :system  (test-system/get-common-components)})
                      (get-app) => start-app
                      (:state start-app) => "constructionStarted"
                      (:reset-state (get-doc)) => :constructionStarted
                      (states-run?) => false)
                    (facts "Reinitialization not allowed"
                      (fact "State mismatch"
                        (mongo/update-by-id :applications app-id {$set {:state :sent}})
                        (let [app (get-app)]
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (get-app) => app
                          (:state app) => "sent"
                          (states-run?) => false))
                      (fact "History with user states"
                        (mongo/update-by-id :applications app-id {$set  {:state :constructionStarted}
                                                                  $push {:history {:state :constructionStarted
                                                                                   :ts    12345678
                                                                                   :user  {:id "hello"}}}})
                        (let [app (get-app)]
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (get-app) => app
                          (:state app) => "constructionStarted"
                          (states-run?) => false)))
                    (let [beginning {:spot "Beginning" :ts (date/timestamp "1.1.2014")}
                          middle    {:spot "Middle" :ts (date/timestamp "1.9.2014")}
                          end       {:spot "End" :ts (date/timestamp "1.11.2014")}]
                      (facts "Reinitialize"
                        (mongo/update-by-id :applications app-id
                                            {$set {:history          [{:state :open
                                                                       :ts    123400
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :submitted
                                                                       :ts    123410
                                                                       :user  {:id :conversion-user}}
                                                                      {:state :sent
                                                                       :ts    123420
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :complementNeeded
                                                                       :ts    123430
                                                                       :user  {:id :conversion-user}}
                                                                      {:state :sent
                                                                       :ts    123440
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :verdictGiven
                                                                       :ts    123450
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :constructionStarted
                                                                       :ts    123460
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :canceled
                                                                       :ts    123470
                                                                       :user  {:id :batchrun-user}}
                                                                      {:state :closed
                                                                       :ts    123480
                                                                       :user  {:id :batchrun-user}}
                                                                      beginning middle end]
                                                   :sent             1
                                                   :complementNeeded 2
                                                   :started          3
                                                   :canceled         4
                                                   :closed           5
                                                   :state            :closed}})
                        (mongo/update-by-query :conversion {:LP-id app-id}
                                               {$set {:reset-state :halt}}) => 1
                        (:state (get-app)) => "closed"
                        (:reset-state (get-doc)) => :halt
                        (fact "Bad XML has been stored after convert. No states update"
                          (mongo/update-by-query :conversion {:LP-id app-id}
                                                 {$set {:conversion-error {:code :bad-kuntagml
                                                                           :text "Bad"}}})
                          => 1
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (states-run?) => false
                          (mongo/update-by-query :conversion {:LP-id app-id}
                                                 {$unset {:conversion-error true}
                                                  $set   {:kuntagml.error "Bad"}})
                          => 1
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file
                                                            :system  (test-system/get-common-components)})
                          (states-run?) => false
                          (mongo/update-by-query :conversion {:LP-id app-id}
                                                 {$unset {:kuntagml.error true}})
                          => 1)

                        (conv/execute-conversion-command {:cmd     :fix-history
                                                          :cfgfile edn-file
                                                          :system  (test-system/get-common-components)})
                        (states-run?) => true
                        (let [app (get-app)
                              doc (conv-doc/document "186-R" jarvenpaa-id)]
                          (dissoc app :history) => (dissoc start-app :history :canceled)
                          (:history app) => (just beginning
                                                  (contains {:state "submitted"
                                                             :ts    (date/timestamp "21.8.2014")})
                                                  middle
                                                  (contains {:state "verdictGiven"
                                                             :ts    (date/timestamp "17.9.2014")})
                                                  (contains {:state "constructionStarted"
                                                             :ts    (date/timestamp "29.9.2014")})
                                                  end)
                          (:state app) => "constructionStarted"
                          (:opened app) => truthy
                          (:canceled app) => nil
                          (->> doc :history last) => (just {:op :fix-history
                                                            :ts pos?})
                          (:reset-state doc) => :constructionStarted)))
                    (facts "Reinitialize: force terminal state"
                      (let [edn-file2 (conv-test/tmp-edn-file test-dir)]
                        (util/write-edn-file edn-file2
                                             (assoc (util/read-edn-file edn-file)
                                                    :force-terminal-state? true))
                        (conv/execute-conversion-command {:cmd     :fix-history
                                                          :cfgfile edn-file2
                                                          :system  (test-system/get-common-components)})
                        (let [app (get-app)
                              doc (get-doc)]
                          (map (some-fn :state :spot) (:history app))
                          => ["Beginning" "submitted" "Middle" "verdictGiven"
                              "constructionStarted" "End" "closed"]
                          app => (contains {:created   pos? :opened pos?
                                            :submitted pos? :closed pos?
                                            :state     "closed"})
                          (:reset-state doc) => :closed
                          (fact "Reinitialize: in terminal state already"
                            (conv/execute-conversion-command {:cmd     :fix-history
                                                              :cfgfile edn-file2
                                                              :system  (test-system/get-common-components)})
                            (get-app) => app
                            (dissoc (get-doc) :history)
                            => (dissoc doc :history))
                          (fact "Reinitialize: remove terminal state"
                            (conv/execute-conversion-command {:cmd     :fix-history
                                                              :cfgfile edn-file
                                                              :system  (test-system/get-common-components)})
                            (let [app (get-app)
                                  doc (get-doc)]
                              (map (some-fn :state :spot) (:history app))
                              => ["Beginning" "submitted" "Middle" "verdictGiven"
                                  "constructionStarted" "End"]
                              app => (contains {:created   pos? :opened pos?
                                                :submitted pos?
                                                :state     "constructionStarted"})
                              (:closed app) => nil
                              (:reset-state doc) => :constructionStarted)))))
                    (facts "Reinitialize: verdict state"
                      (let [edn-file3 (conv-test/tmp-edn-file test-dir)]
                        (fact "Via override"
                          (util/write-edn-file edn-file3
                                               (assoc (util/read-edn-file edn-file)
                                                      :state-overrides {"14-0241-R 3" :sent} ))
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file3
                                                            :system  (test-system/get-common-components)})
                          (:state (get-app)) => "verdictGiven")
                        (fact "Via mapping"
                          (util/write-edn-file edn-file3
                                               (assoc (util/read-edn-file edn-file)
                                                      :state-mapping {"lupa hyväksytty"        :sent
                                                                      "rakennustyöt aloitettu" :sent}))
                          (conv/execute-conversion-command {:cmd     :fix-history
                                                            :cfgfile edn-file3
                                                            :system  (test-system/get-common-components)})
                          (:state (get-app)) => "verdictGiven"))))

                  (facts "Show conversion message"
                    ;; Insert non-converted app
                    (mongo/insert :applications {:id           "LP-092-2023-01122"
                                                 :organization "092-R"
                                                 :state        :submitted})
                    (fact "Not a converted application"
                      (local-raw vantaa-r-biller :conversion-message :id "LP-092-2023-01122")
                      => (err :error.not-found))
                    (fact "Converted application"
                      (local-raw vantaa-r-biller :conversion-message :id "LP-092-2013-90001")
                      => (contains {:status  200
                                    :body    #(ss/starts-with? % "<?xml version")
                                    :headers (contains {"Content-Type" "text/xml; charset=utf-8"})}))
                    (fact "Converted application, no KuntaGML stored"
                      (mongo/update-by-query :conversion {:LP-id "LP-092-2013-90001"}
                                             {$unset {:kuntagml true}}) => 1
                      (local-raw vantaa-r-biller :conversion-message :id "LP-092-2013-90001")
                      => (err :error.not-found)))

                  (fact "Check for verdict not supported"
                    (mongo/update-by-id :applications "LP-092-2013-90001" {$set {:state :submitted}})
                    (local-command vantaa-r-biller :check-for-verdict
                                   :id "LP-092-2013-90001")
                    => (err :error.converted-application)
                    (mongo/update-by-id :applications "LP-092-2013-90001" {$set {:state :ready}})
                    (local-command vantaa-r-biller :check-for-verdict-fix
                                   :id "LP-092-2013-90001"
                                   :verdict-fix-options {:send-notifications        false
                                                         :remove-verdict-attachment false
                                                         :update-bulletin           false})
                    => (err :error.converted-application))))))))

      (let [test-dir (conv-test/mk-test-dir)]

        (with-test-system (-> {:fixture "minimal"}
                              test-system/with-remote-components)
          (doseq [testfile [conv-test/testfile non-kuntagml-file]]
            (spit (str (fs/path test-dir (fs/file-name testfile)))
                  (conv-test/transform-xml testfile)))

          (with-proxy nil
            (against-background
                [(after :contents (conv-test/rm-test-dir test-dir))]

              (let [foo-id        "00-1234-FOO"
                    foo-xml       (conv-test/write-test-xml test-dir "foo-test.xml" :backend-id foo-id)
                    bar-id        "11-5678-BAR"
                    bar-xml       (conv-test/write-test-xml test-dir "bar-test.xml" :backend-id bar-id)
                    vakuus-id     "00-0022-77-VAK"
                    vakuus-xml    (conv-test/write-test-xml test-dir "vakuus-test.xml" :backend-id vakuus-id)
                    no-xml-id     "22-2233-HOW"
                    lp-id-id      "33-5566-DOH"
                    lp-id-xml     (conv-test/write-test-xml test-dir "lp-id.xml"
                                                            :application-id "LP-123"
                                                            :backend-id lp-id-id)
                    bad-xml-id    "44-9900-BAD"
                    bad-xml-id2   "44-9900-NIB"
                    bad-xml-id3   "44-9900-UGH"
                    blank-id-xml  (conv-test/write-test-xml test-dir "blank-id.xml" :backend-id "")
                    conv-count    #(mongo/count :conversion {:organization "092-R"})
                    store-convert (fn [& {:keys [filename id store-only? overwrite? location-overrides retry]}]
                                    (let [old-n    (conv-count)
                                          cfg-file (apply conv-test/write-config test-dir "092-R"
                                                          :overwrite? overwrite?
                                                          :location-overrides location-overrides
                                                          (if filename
                                                            [:glob (fs/file-name filename)]
                                                            [:ids [id]]))]
                                      (conv/execute-conversion-command {:cmd     :store
                                                                        :cfgfile cfg-file
                                                                        :system  (test-system/get-common-components)})
                                      (when-not store-only?
                                        (conv/execute-conversion-command (util/assoc-when {:cmd     :convert
                                                                                           :cfgfile cfg-file
                                                                                           :system  (test-system/get-common-components)}
                                                                                          :retry retry)))
                                      {:n (- (conv-count) old-n) :cfg cfg-file}))]
                (facts "Conversion errors"
                  (fact "Vakuustieto backend-id not supported"
                    (:n (store-convert :filename vakuus-xml)) => 1
                    (select-keys (conv-doc/document "092-R" vakuus-id)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :unsupported-backend-id
                                                           :text truthy})
                                  :kuntagml         {:xml   (slurp vakuus-xml)
                                                     :error "Unsupported backend-id"}}))
                  (fact "No XML"
                    (:n (store-convert :id no-xml-id)) => 1
                    (provided
                      (krysp/get-application-xml-by-backend-id anything no-xml-id true) => "")
                    (select-keys (conv-doc/document "092-R" no-xml-id)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :no-kuntagml
                                                           :text truthy})
                                  :kuntagml         {:error "No KuntaGML message found"}}))

                  (fact "XML cannot have application id"
                    (:n (store-convert :filename lp-id-xml)) => 1
                    (select-keys (conv-doc/document "092-R" lp-id-id)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :xml-application-id
                                                           :text truthy})
                                  :kuntagml         {:xml   (slurp lp-id-xml)
                                                     :error "KuntaGML already refers to LP-123"}}))

                  (fact "Bad KuntaGML: Not KuntaGML"
                    (:n (store-convert :id bad-xml-id)) => 1
                    (provided
                      (krysp/get-application-xml-by-backend-id anything bad-xml-id true)
                      => "<bad>BAD</bad>")
                    (select-keys (conv-doc/document "092-R" bad-xml-id)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :bad-kuntagml
                                                           :text truthy})
                                  :kuntagml         {:error "No supported permit type found."
                                                     :xml   "<bad>BAD</bad>"}}))

                  (fact "Bad KuntaGML: Stub"
                    (:n (store-convert :id bad-xml-id2)) => 1
                    (provided
                      (krysp/get-application-xml-by-backend-id anything bad-xml-id2 true)
                      => "<rakval:RakennusvalvontaAsia>BAD</rakval:RakennusvalvontaAsia>")
                    (select-keys (conv-doc/document "092-R" bad-xml-id2)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :bad-kuntagml
                                                           :text truthy})
                                  :kuntagml         {:error "No matching backend-id found in the KuntaGML message."
                                                     :xml   "<rakval:RakennusvalvontaAsia>BAD</rakval:RakennusvalvontaAsia>"}}))

                  (fact "Bad XML"
                    (:n (store-convert :id bad-xml-id3)) => 1
                    (provided
                      (krysp/get-application-xml-by-backend-id anything bad-xml-id3 true)
                      => "yikes")
                    (select-keys (conv-doc/document "092-R" bad-xml-id3)
                                 [:conversion-error :kuntagml :LP-id])
                    => (contains {:conversion-error (just {:code :bad-kuntagml
                                                           :text truthy})
                                  :kuntagml         {:error "Content is not allowed in prolog."
                                                     :xml   "yikes"}}))

                  (fact "Backend id already in use"
                    (mongo/insert :applications {:id           "LP-092-2023-01122"
                                                 :organization "092-R"
                                                 :state        :submitted
                                                 :verdicts     [{:kuntalupatunnus foo-id}]})
                    (:n (store-convert :filename foo-xml)) => 1
                    (let [doc (conv-doc/document "092-R" foo-id)]
                      (:LP-id doc) => nil
                      doc => (contains {:conversion-error (just {:code :duplicate-backend-id
                                                                 :text "Application(s) with backend-id 00-1234-FOO already exist: LP-092-2023-01122. No conversion."})
                                        :kuntagml         {:xml (slurp foo-xml)}})))

                  (fact "No backend id"
                    (:n (store-convert :filename blank-id-xml)) => 0)

                  (let [{cfg :cfg}  (store-convert :filename bar-xml :store-only? true)
                        try-convert (fn [retry?]
                                      (conv/execute-conversion-command (util/assoc-when {:cmd     :convert
                                                                                         :cfgfile cfg
                                                                                         :system  (test-system/get-common-components)}
                                                                                        :retry   retry?)))
                        check-error (fn [error-code]
                                      (let [{ err   :conversion-error
                                             app-id :LP-id} (conv-doc/document "092-R" bar-id)]
                                        (fact {:midje/description (name error-code)}
                                          err => (just {:code error-code :text truthy})
                                          app-id => truthy
                                          (mongo/any? :applications {:_id app-id}) => false)))]
                    (fact "No errors, no conversions with retry"
                      (try-convert true) => nil
                      (provided
                        (lupapalvelu.conversion.kuntagml-converter/run-conversion! anything)
                        => nil :times 0))

                    (fact "No application info"
                      (try-convert false) => nil
                      (provided
                        (krysp-reader/get-app-info-from-message anything bar-id anything) => nil)
                      (check-error :no-app-info))

                    (fact "No re-convert without retry option"
                      (try-convert false) => nil
                      (provided
                        (lupapalvelu.conversion.kuntagml-converter/run-conversion! anything)
                        => nil :times 0)
                      (check-error :no-app-info))

                    (fact "No location info"
                      (try-convert true) => nil
                      (provided
                        (krysp-reader/get-app-info-from-message anything bar-id anything)
                        => {:foo "bar"})
                      (check-error :no-location-info))

                    (fact "No property id"
                      (try-convert true) => nil
                      (provided
                        (krysp-reader/get-app-info-from-message anything bar-id anything)
                        => {:rakennuspaikka {:x 1 :y 2}})
                      (check-error :no-property-id)))

                  (facts "Bad data"
                    (let [backend-id "01-0012-00-TJO"
                          ;; Bad dates in the verdict
                          xml-path   "dev-resources/krysp/verdict-r-2.1.6-type-TJO.xml"
                          filename   "bad-tj.xml"
                          pathname   (str (fs/path test-dir filename))]
                      (fs/copy xml-path pathname)
                      (:n (store-convert :filename filename)) => 1
                      (conv-doc/document "092-R" backend-id)
                      => (contains {:conversion-error (just {:code :unknown
                                                             :text #"Text '2004-00-01' could not be parsed: Invalid value for MonthOfYear"})})
                      (fact "Previously bad foreman, currently fine."
                        (spit pathname
                              (-> (slurp xml-path)
                                  (ss/replace #"1003-00-01|0900-00-00|2004-00-01" "2000-01-01")
                                  (xml/parse-string "utf8")
                                  (enlive/at [:rakval:osapuolettieto] nil
                                             [:rakval:paatostieto] nil
                                             [:rakval:rakennuspaikkatieto] nil)
                                  xml/element-to-string))
                        (:n (store-convert :filename filename :overwrite? true :retry true)) => 0
                        (let [doc    (conv-doc/document "092-R" backend-id)
                              tj-app (mongo/by-id :applications (:LP-id doc))]
                          (fact "Empty hankkeen-kuvaus"
                            (let [kuvaus-doc (->> tj-app :documents
                                                  (util/find-first (util/fn-> :schema-info :subtype
                                                                              (= "hankkeen-kuvaus"))))]

                              (doc-checks kuvaus-doc {:kuvaus ""})))
                          (facts "Empty foreman document"
                            (let [tj-doc (->> tj-app :documents
                                              (util/find-first (util/fn-> :schema-info :name
                                                                          (= "tyonjohtaja-v2"))))]
                              (doc-checks tj-doc {:henkilotiedot.etunimi  ""
                                                  :henkilotiedot.sukunimi ""
                                                  :yhteystiedot.email     ""
                                                  :yhteystiedot.puhelin   ""})))
                          (facts "Cleanup"
                            (mongo/remove-by-id :applications (:LP-id doc))
                            (mongo/remove-by-id :conversion (:id doc)))))
                      (facts "Good foreman"
                        (spit pathname
                              (-> (slurp xml-path)
                                  (ss/replace #"1003-00-01|0900-00-00|2004-00-01" "2000-01-01")
                                  (ss/replace #"000790-006B" "170781-903M")
                                  (ss/replace #"IV-työnjohtaja" " Iv-Työnjohtaja ")))
                        (store-convert :filename filename)
                        (let [{:keys  [converted conversion-error
                                       LP-id]
                               doc-id :id} (conv-doc/document "092-R" backend-id)
                              {:keys [documents verdicts]
                               :as   app}  (mongo/by-id :applications LP-id)
                              tj-doc       (util/find-first (util/fn-> :schema-info :name (= "tyonjohtaja-v2"))
                                                            documents)
                              site-doc     (util/find-first (util/fn-> :schema-info :name (= "rakennuspaikka-kuntagml"))
                                                            documents)]
                          converted => true
                          conversion-error => nil
                          (fact "Basic information"
                            app => (contains {:foreman            "Nakuttelija Niina"
                                              :foremanRole        "IV-työnjohtaja"
                                              :permitSubtype      "tyonjohtaja-ilmoitus"
                                              :operation-name     "tyonjohtajan-nimeaminen-v2"
                                              :foremanTermination {:started (date/timestamp "1.2.2003")
                                                                   :ended   (date/timestamp "3.6.2004")}}))
                          (facts "Foreman document"
                            (doc-checks tj-doc {:henkilotiedot.etunimi                 "Niina"
                                                :henkilotiedot.sukunimi                "Nakuttelija"
                                                :henkilotiedot.hetu                    "170781-903M"
                                                :osoite.katu                           "Pömppäpolku 01 A 03"
                                                :osoite.postinumero                    "32610"
                                                :osoite.postitoimipaikannimi           "VAMPULA"
                                                :yhteystiedot.email                    "ab@cd.com"
                                                :yhteystiedot.puhelin                  "000-0130376"
                                                :patevyys-tyonjohtaja.koulutusvalinta  "LVI-teknikko"
                                                :patevyys-tyonjohtaja.valmistumisvuosi "0990"}))
                          (facts "Site document"
                            (doc-checks site-doc {:kiinteisto.kiinteistotunnus "1-7-70-13"
                                                  :kiinteisto.kylanimi         "Nämpnäs"
                                                  :osoite.kaupunginosanumero   "01" ;; From backend-id
                                                  :osoite.kunta                "000"
                                                  :osoite.osoitenumero         "0"
                                                  :osoite.osoitenimi           "Jees-miespolku / Ja-mansstigen"
                                                  :osoite.postinumero          "01000"
                                                  :osoite.postitoimipaikannimi "NÄMPNÄS"}))
                          (facts "Verdict"
                            (-> (first verdicts) :paatokset first :poytakirjat first)
                            => {:paatoksentekija "LVI-tarkastaja"
                                :paatoskoodi     "myönnetty"
                                :status          "1" ;; myönnetty
                                :paatospvm       946677600000 ;; 1.1.2000
                                :pykala          7})

                          (facts "fix-foremen batchrun"
                            (let [edn          (conv-test/write-config test-dir (:organization app))
                                  old-doc      (conv-doc/document doc-id)
                                  doc-changed? #(not= (conv-doc/document doc-id) old-doc)
                                  op-count     #(->> (conv-doc/document doc-id)
                                                     :history
                                                     (util/filter-by-key :op :fix-foremen)
                                                     count)
                                  fix-foremen  (fn []
                                                 (conv/execute-conversion-command {:cmd     :fix-foremen
                                                                                   :cfgfile edn
                                                                                   :system  (test-system/get-common-components)})
                                                 {:changed? (doc-changed?)
                                                  :ops      (op-count)})]
                              old-doc => truthy
                              (doc-changed?) => false
                              (op-count) => 0
                              (facts "nothing to do"
                                (fact "already has termination info"
                                  (fix-foremen) => {:changed? false :ops 0})
                                (mongo/update-by-id :applications LP-id {$unset {:foremanTermination true
                                                                                 :facta-imported     true}})
                                (fact "not a converted application"
                                  (fix-foremen) => {:changed? false :ops 0})
                                (mongo/update-by-id :applications LP-id {$set {:facta-imported        true
                                                                               :primaryOperation.name "foobar"}})
                                (fact "not a foreman application"
                                  (fix-foremen) => {:changed? false :ops 0}))
                              (mongo/update-by-id :applications LP-id
                                                  {$set {:facta-imported        true
                                                         :primaryOperation.name (get-in app [:primaryOperation :name])}})
                              (facts "fix foreman"
                                (fix-foremen) => {:changed? true :ops 1}
                                (mongo/by-id :applications LP-id) => app
                                (let [doc (conv-doc/document doc-id)]
                                  (fact "Rerun does not do anything"
                                    (fix-foremen) => {:changed? true :ops 1}
                                    (conv-doc/document doc-id) => doc)))))))))

                  (facts "Location"
                    (let [location {:x 384674.655 :y 6689171.929 :address "Test Town 10"}
                          xyz-id   "12-2010-XYZ"]
                      (conv-test/write-test-xml test-dir "xyz-test.xml" :backend-id xyz-id) => truthy
                      (:n (store-convert :filename "xyz-test.xml" :location-overrides {xyz-id location})) => 1
                      (let [{app-id :LP-id
                             :as    doc} (conv-doc/document "092-R" xyz-id)]
                        doc => (contains {:converted      true
                                          :location       location
                                          :reset-location (select-keys location [:x :y])})
                        (mongo/by-id :applications app-id)
                        => (contains {:location   [(:x location) (:y location)]
                                      :address    (:address location)
                                      :propertyId "09204100020009"}))
                      (facts "fix-locations batchrun"
                        (let [fix-lox (fn [bid lox & kvs]
                                        (->> (conv-test/write-config test-dir "092-R"
                                                                     :location-overrides {bid lox})
                                             (hash-map :cmd :fix-locations :cfgfile)
                                             (merge (apply hash-map kvs)
                                                    {:system (test-system/get-common-components)})
                                             conv/execute-conversion-command)
                                        (conv-doc/document "092-R" bid))
                              app-lox #'conv-xy/application-location]
                          (fact "No match"
                            (fix-lox "06-2024-DUM" location) => nil)
                          (fact "No changes"
                            (let [{app-id :LP-id
                                   :as    doc} (fix-lox xyz-id location)]
                              (util/find-by-key :op :fix-locations (:history doc))
                              => nil
                              (:location doc) => location
                              (app-lox doc)
                              => (assoc location :propertyId "09204100020009")))
                          (fact "Address and property id changes"
                            (let [new-lox      (assoc location :address "Fixer Fairway"
                                                      :propertyId "09204100020010")
                                  {app-id :LP-id
                                   :as    doc} (fix-lox xyz-id new-lox)]
                              (util/find-by-key :op :fix-locations (:history doc))
                              => truthy
                              (:location doc) => new-lox
                              (app-lox doc) => new-lox
                              (fact "Change just coordinates"
                                (let [xy           {:x 596442.349 :y 7339498.015}
                                      new-lox      (merge new-lox xy)
                                      {app-id :LP-id
                                       :as    doc} (fix-lox xyz-id xy)]
                                  (->>  (:history doc)
                                        (util/filter-by-key :op :fix-locations)
                                        count)
                                  => 2
                                  (:location doc) => new-lox
                                  (:reset-location doc) => xy
                                  (app-lox doc) => new-lox))))
                          (fact "Change everything"
                            (let [new-lox {:x          401549.181
                                           :y          6708247.7210183
                                           :propertyId "21145500100013"
                                           :address    "Awesome Avenue!"}
                                  doc     (fix-lox xyz-id new-lox)]
                              (->>  (:history doc)
                                    (util/filter-by-key :op :fix-locations)
                                    count)
                              => 3
                              (:location doc) => new-lox
                              (:reset-location doc) => (select-keys new-lox [:x :y])
                              (app-lox doc) => new-lox

                              (fact "Update document only"
                                (mongo/update-by-id :conversion (:id doc) {$unset {:converted      true
                                                                                   :reset-location true}})
                                (let [doc-lox {:x       411549.181
                                               :y       6808247.7210183
                                               :address "Document Drive"}
                                      merged  (merge new-lox doc-lox)
                                      doc     (fix-lox xyz-id doc-lox)]
                                  (:converted doc) => nil
                                  (->>  (:history doc)
                                        (util/filter-by-key :op :fix-locations)
                                        count)
                                  => 4
                                  (:location doc) => merged
                                  (:reset-location doc) => nil
                                  (app-lox doc) => nil
                                  (app-lox (assoc doc :converted true)) => new-lox
                                  merged =not=> new-lox

                                  (facts "Relax"
                                    (mongo/update-by-id :conversion (:id doc) {$set {:converted true}})
                                    (let [sysi {:address    "Sysikuja"
                                                :x          394271.0
                                                :y          6707830.0
                                                :propertyId "18640100100118"}
                                          doc  (assoc doc :converted true)]
                                      (fact "No reset location, no relax, no change"
                                        (:location (fix-lox xyz-id sysi)) => merged
                                        (app-lox doc) => new-lox)
                                      (fact "No reset location, relax, change"
                                        (:location (fix-lox xyz-id sysi :location-relax true))
                                        => sysi
                                        (app-lox doc) => sysi)))))))))))

                  (facts "Link to ARK"
                    (let [ark-id "99-5555-DIG"
                          lx-id  "LX-123-2023-01234"]
                      (conv-test/write-test-xml test-dir "ark-test.xml" :backend-id ark-id)
                      (mongo/insert :applications {:_id          lx-id
                                                   :permitType   "ARK"
                                                   :organization "092-R"
                                                   :verdicts     {:kuntalupatunnus ark-id}})
                      (fact "Convert"
                        (let [{:keys [n cfg]} (store-convert :filename "ark-test.xml")
                              {app-id :LP-id
                               :as    doc}    (conv-doc/document "092-R" ark-id)]
                          n => 1
                          doc => (contains {:converted true
                                            :app-links (just "00-0000-TJO 00" lx-id :in-any-order)})
                          app-id =not=> lx-id
                          (:linked doc) => nil
                          (mongo/select :applications {:verdicts.kuntalupatunnus ark-id} [:_id])
                          => (just {:id lx-id} {:id app-id} :in-any-order)
                          (mongo/any? :app-links {:link lx-id}) => false
                          (fact "Link"
                            (conv/execute-conversion-command {:cmd     :link
                                                              :cfgfile cfg
                                                              :system  (test-system/get-common-components)})
                            (conv-doc/document (:id doc))
                            => (contains {:linked    true
                                          :app-links (just "00-0000-TJO 00" lx-id :in-any-order)})
                            (mongo/count :app-links {:link lx-id}) => 1)))))

                  (facts "Link to paper permit (aiemmalla-luvalla-hakeminen)"
                    (let [paper-id "99-5555-R"
                          old-id   "LP-123-2023-02234"]
                      (conv-test/write-test-xml test-dir "paper-test.xml" :backend-id paper-id)
                      (mongo/insert :applications {:_id              old-id
                                                   :permitType       "R"
                                                   :organization     "092-R"
                                                   :state            :closed
                                                   :primaryOperation {:name :aiemmalla-luvalla-hakeminen}
                                                   :verdicts         {:kuntalupatunnus paper-id}})
                      (fact "Convert"
                        (let [{:keys [n cfg]} (store-convert :filename "paper-test.xml")
                              {app-id :LP-id
                               :as    doc}    (conv-doc/document "092-R" paper-id)]
                          n => 1
                          doc => (contains {:converted true
                                            :app-links (just "00-0000-TJO 00" old-id :in-any-order)})
                          app-id =not=> old-id
                          (:linked doc) => nil
                          (mongo/select :applications {:verdicts.kuntalupatunnus paper-id} [:state])
                          => (just {:id old-id :state "closed"}
                                   {:id app-id :state "closed"} :in-any-order)
                          (mongo/any? :app-links {:link old-id}) => false
                          (fact "Link"
                            (conv/execute-conversion-command {:cmd     :link
                                                              :cfgfile cfg
                                                              :system  (test-system/get-common-components)})
                            (conv-doc/document (:id doc))
                            => (contains {:linked    true
                                          :app-links (just "00-0000-TJO 00" old-id :in-any-order)})
                            (mongo/count :app-links {:link old-id}) => 1)))))

                  (facts "Import: tasks"
                    (let [backend-id "00-1234-I"
                          xmlfile    "import-test.xml"
                          ednpath    (str (fs/path test-dir "import-test.edn"))]
                      (conv-test/write-test-xml test-dir xmlfile :backend-id backend-id)
                      (store-convert :filename xmlfile)
                      (fact "Convert"
                        (let [{app-id :LP-id}    (conv-doc/document "092-R" backend-id)
                              {old-tasks :tasks
                               state     :state} (mongo/by-id :applications app-id [:tasks :state])
                              assignee           {:firstName "Lupapiste"
                                                  :lastName  "Eräajo"
                                                  :id        "batchrun-user"}
                              new-tasks          (mapv (fn [{:keys [taskname] :as task}]
                                                         (let [iv? (= taskname "Ilmanvaihtosuunnitelmat")
                                                               vv? (= taskname "Vesi- ja viemärisuunnitelmat")]
                                                           (cond-> task
                                                             (or iv? vv?)
                                                             (assoc :state "ok"
                                                                    :assignee assignee)
                                                             iv?
                                                             (assoc-in [:data :kuvaus :value]
                                                                       "Hello world\n\nSaapunut 5.2.2024.")
                                                             vv?
                                                             (assoc-in [:data :kuvaus :value]
                                                                       "Käsitelty 6.5.2024."))))
                                                       old-tasks)
                              new-tasks          (reduce (fn [tasks i]
                                                           (cond-> tasks
                                                             (= (:taskname (nth tasks i)) "Rakennesuunnitelmat")
                                                             (-> (update i (util/fn-> (assoc :state "ok"
                                                                                             :assignee assignee)
                                                                                      (assoc-in [:data :kuvaus :value]
                                                                                                "Käsitelty 7.2.2024.")))
                                                                 reduced)))
                                                         new-tasks
                                                         (range (count new-tasks)))]
                          state => "closed"
                          (conv-cfg/write-import-configuration
                            ednpath "092-R" :tasks [{:backend-id backend-id
                                                     :taskname   "Nope" :text "Nowhere"}
                                                    {:backend-id backend-id
                                                     :taskname   "  ILMANVAIHTOSUUNNITELMAT  "
                                                     :text       "  Hello world  "
                                                     :received   "2024-02-05"}
                                                    {:backend-id backend-id
                                                     :taskname   "VESI- JA viemÄrisuunnitelmat"}
                                                    {:backend-id backend-id
                                                     :taskname   "rakennesuunnitelmat"
                                                     :processed  1707307609007}])
                          ;; The following fails silently do to bad third item
                          (conv/execute-conversion-command {:cmd      :import :cfgfile ednpath
                                                            :cmd-help (constantly nil)
                                                            :system   (test-system/get-common-components)})
                          (mongo/any? :applications {:_id app-id :_sheriff-notes.note {$exists true}})
                          => false
                          (conv-cfg/write-import-configuration
                            ednpath "092-R" :tasks [{:backend-id backend-id
                                                     :taskname   "Nope" :text "Nowhere"}
                                                    {:backend-id backend-id
                                                     :taskname   "  ILMANVAIHTOSUUNNITELMAT  "
                                                     :text       "  Hello world  "
                                                     :received   "2024-02-05"}
                                                    {:backend-id backend-id
                                                     :taskname   "VESI- JA viemÄrisuunnitelmat"
                                                     :processed  "6.5.2024"}
                                                    {:backend-id backend-id
                                                     :taskname   "rakennesuunnitelmat"
                                                     :processed  1707307609007}
                                                    {:backend-id backend-id
                                                     :taskname   " RakenneSuunnitelmat "
                                                     :text       "Build!"
                                                     :received   (date/timestamp "1.1.2024")
                                                     :processed  (date/xml-date "2.2.2024")}])
                          (conv/execute-conversion-command {:cmd     :import
                                                            :cfgfile ednpath
                                                            :system  (test-system/get-common-components)})
                          (let [{notes :_sheriff-notes tasks :tasks
                                 :as   application} (mongo/by-id :applications app-id)]
                            (drop-last 2 tasks) =not=> old-tasks
                            (drop-last 2 tasks) => new-tasks
                            (take-last 2 tasks)
                            => (just (contains {:taskname    "Nope"
                                                :state       "ok"
                                                :schema-info (contains {:name "task-lupamaarays"})
                                                :data        (contains {:kuvaus (contains {:value "Nowhere"})})})
                                     (contains {:taskname    "RakenneSuunnitelmat"
                                                :state       "ok"
                                                :schema-info (contains {:name "task-lupamaarays"})
                                                :data        (contains {:kuvaus (contains {:value "Build!\n\nSaapunut 1.1.2024.\nKäsitelty 2.2.2024."})})}))

                            (map :note notes)
                            => (just #(ss/starts-with? % "LPK-6769: Import new task Nope")
                                     #(ss/starts-with? % "LPK-6769: Import task Ilmanvaihtosuunnitelmat")
                                     #(ss/starts-with? % "LPK-6769: Import task Vesi- ja viemärisuunnitelmat")
                                     #(ss/starts-with? % "LPK-6769: Import task Rakennesuunnitelmat")
                                     #(ss/starts-with? % "LPK-6769: Import new task Rakennesuunnitelmat")
                                     :in-any-order)
                            (fact "Reimport does not do anything"
                              (conv/execute-conversion-command {:cmd     :import
                                                                :cfgfile ednpath
                                                                :system  (test-system/get-common-components)})
                              (mongo/by-id :applications app-id) => application))))))

                  (facts "Applications search"
                    (fact "All: default"
                      (let [{:keys [applications total-count]} (applications-search "all")]
                        (count applications) => 2
                        total-count => 2
                        (map :id applications) => (just "LP-092-2023-01122"
                                                        "LP-123-2023-02234"
                                                        :in-any-order)))
                    (fact "Finalized: default"
                      (let [{:keys [applications total-count]} (applications-search "finalized")]
                        (map (juxt :id :state) applications) => [["LP-123-2023-02234" "closed"]]
                        total-count => 1))

                    (fact "All: include converted"
                      (let [{:keys [applications total-count]} (applications-search "all" true)]
                        (count applications) => 6
                        total-count => 6
                        (map :id applications) => (just "LP-092-2023-01122" "LP-123-2023-02234"
                                                        "LP-092-2012-90001" "LP-092-1999-90001"
                                                        "LP-092-1999-90002" "LP-092-2000-90003"
                                                        :in-any-order)))
                    (fact "Finalized: include converted"
                      (let [{:keys [applications total-count]} (applications-search "finalized" true)]
                        (count applications) => 5
                        total-count => 5
                        (map (juxt :id :state) applications)
                        => (just ["LP-123-2023-02234" "closed"]
                                 ["LP-092-2012-90001" "closed"]
                                 ["LP-092-1999-90001" "closed"]
                                 ["LP-092-1999-90002" "closed"]
                                 ["LP-092-2000-90003" "closed"]
                                 :in-any-order)))))

                (facts "Year resolution"
                  (let [id-2018  "18-9999-Y"
                        xml-2018 (conv-test/write-test-xml test-dir (str id-2018 ".xml")
                                                           :backend-id id-2018)
                        id-2010  "09-9999-X" ; Overriden in XML
                        xml-2010 (conv-test/write-test-xml test-dir (str id-2010 ".xml")
                                                           :backend-id id-2010
                                                           :submitted "5.6.2010")
                        xml-year #'conv-doc/xml-year]
                    (fact "Backend-id year"
                      (:n (store-convert :filename xml-2018)) => 1
                      (conv-doc/document "092-R" id-2018)
                      => (contains {:year 2018 :LP-id #"LP-092-2018-"}))

                    (fact "KuntaGML year"
                      (:n (store-convert :filename xml-2010)) => 1
                      (conv-doc/document "092-R" id-2010)
                      => (contains {:year 2010 :LP-id #"LP-092-2010-"}))

                    (fact "XML year"
                      (xml-year (conv-doc/document "092-R" id-2018)) => nil
                      (xml-year (conv-doc/document "092-R" id-2010)) => 2010))))

              (let [default-location #'conv-k/default-location]
                (fact "Default location"
                  (default-location {:organization-id "092-R"
                                     :conversion-doc  {:backend-id "123-foo"}} :fallback)
                  => vantaa-fallback-location-info
                  (default-location {:organization-id "092-R"
                                     :conversion-doc  {:backend-id "123-foo"}} :override)
                  => nil
                  (default-location {:conversion-doc  {:backend-id "123-foo"}
                                     :organization-id "505-R"} :fallback)
                  => nil
                  (default-location {:location-overrides {"123-foo" {:x 1 :y 2}}
                                     :conversion-doc     {:backend-id "123-foo"}
                                     :organization-id    "505-R"} :override)
                  => {:x 1 :y 2}
                  (default-location {:location-overrides {"123-foo" {:x 1 :y 2}}
                                     :conversion-doc     {:backend-id "123-foo"
                                                          :location   {:x 3 :y 4}}
                                     :organization-id    "505-R"} :override)
                  => {:x 3 :y 4}
                  (default-location {:location-overrides {"123-foo" {:x 1 :y 2}}
                                     :location-fallback  {:x 3 :y 4}
                                     :conversion-doc     {:backend-id "123-foo"}
                                     :organization-id    "505-R"} :fallback)
                  => {:x 3 :y 4})))))))))

(let [test-dir (conv-test/mk-test-dir)]

  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components)
    (doseq [testfile [conv-test/testfile non-kuntagml-file]]
      (spit (str (fs/path test-dir (fs/file-name testfile)))
            (conv-test/transform-xml testfile)))

    (with-proxy nil
      (against-background
          [(after :contents (conv-test/rm-test-dir test-dir))]

        (facts "P conversion"
          (letfn [(stonvert  [filename]
                    (fs/copy filename test-dir)
                    (let [cfg-file (conv-test/write-config test-dir "753-R"
                                                           :permit-type "P"
                                                           :glob (fs/file-name filename))]
                      (conv/execute-conversion-command {:cmd     :store
                                                        :cfgfile cfg-file
                                                        :system  (test-system/get-common-components)})
                      (conv/execute-conversion-command {:cmd     :convert
                                                        :cfgfile cfg-file
                                                        :system  (test-system/get-common-components)})))]
            (facts "Poikkeamislupa"
              (stonvert poikkeamislupa-xml)
              (let [doc         (conv-doc/document "753-R" "23-0235-POI")
                    {:keys [documents statements verdicts primaryOperation]
                     :as   app} (mongo/by-id :applications (:LP-id doc))]
                doc => (contains {:permit-type "P"
                                  :converted   true
                                  :year        2023})
                app => (contains {:permitType     "P"  :permitSubtype  "poikkeamislupa"
                                  :facta-imported true :operation-name "poikkeamis"
                                  :state          "appealed"})

                (fact "Primary operation and its (empty) document"
                  (let [op-id (-> :id primaryOperation)
                        doc   (util/find-first (util/fn-> :schema-info :op :id (= op-id))
                                               documents)]
                    op-id => truthy
                    (:name primaryOperation) => "poikkeamis"
                    (-> doc :schema-info :name) => "rakennushanke"))

                (fact "Applicant document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hakija"))
                                               documents)
                              {:_selected                                    "henkilo"
                               :henkilo.henkilotiedot.etunimi                "Poika"
                               :henkilo.henkilotiedot.sukunimi               "Poikolainen"
                               :henkilo.henkilotiedot.hetu                   "210447-8156"
                               :henkilo.kytkimet.suoramarkkinointilupa       false
                               :henkilo.kytkimet.vainsahkoinenAsiointiKytkin true
                               :henkilo.osoite.katu                          "Poikkeamanpolku 12 B1"
                               :henkilo.osoite.postinumero                   "00170"
                               :henkilo.osoite.postitoimipaikannimi          "HELSINKI"
                               :henkilo.yhteystiedot.email                   "poika.poikolainen@example.net"
                               :henkilo.yhteystiedot.puhelin                 "1122334455"}))

                (fact "Payer document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "maksaja"))
                                               documents)
                              {:_selected                              "henkilo"
                               :henkilo.henkilotiedot.etunimi          "Mammona"
                               :henkilo.henkilotiedot.sukunimi         "Maksanen"
                               :henkilo.henkilotiedot.hetu             "190373-094J"
                               :henkilo.kytkimet.suoramarkkinointilupa false
                               :henkilo.osoite.katu                    "Rahatie 1000"
                               :henkilo.osoite.postinumero             "00310"
                               :henkilo.osoite.postitoimipaikannimi    "HELSINKI"
                               :henkilo.yhteystiedot.email             "money@example.com"
                               :henkilo.yhteystiedot.puhelin           "99887766"
                               :laskuviite                             "Show me the money!"}))

                (fact "Info document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hankkeen-kuvaus"))
                                               documents)
                              {:kuvaus     "Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis, sem. Nulla consequat massa quis enim."
                               :poikkeamat "Donec vitae orci sed dolor rutrum auctor. Fusce egestas elit eget lorem."}))

                (fact "Site document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "rakennuspaikka-kuntagml"))
                                               documents)
                              {:hallintaperuste             "oma"
                               :kaavatilanne                "oikeusvaikutteinen yleiskaava"
                               :kiinteisto.tilanNimi        "CHATEAU SAMPLE"
                               :kiinteisto.kiinteistotunnus "753-418-1-35"
                               :osoite.kunta                "753"
                               :osoite.osoitenimi           "Hubba / Bubba"
                               :osoite.osoitenumero         "123"
                               :osoite.postinumero          "06780"
                               :osoite.postitoimipaikannimi "Skogendahl"}))

                (fact "Statement"
                  (count statements) => 1
                  (first statements)
                  => (contains {:given     1634590800000 ;; 19.10.2021
                                :requested 1633899600000 ;; 11.10.2021
                                :status    "lausunto"
                                :state     "given"
                                :text      "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor."
                                :person    (contains {:name "Sipoon Esimerkki - Sibbo Exempel"
                                                      :text "Viranomainen"})}))

                (fact "Verdict"
                  (count verdicts) => 1
                  (-> verdicts first :paatokset first :paivamaarat)
                  => {:aloitettava      1693602000000 ;; 2.9.2023
                      :lainvoimainen    1693688400000 ;; 3.9.2023
                      :voimassaHetki    1756760400000 ;; 2.9.2025
                      :anto             1692306000000 ;; 18.8.2023
                      :viimeinenValitus 1693429200000 ;; 31.8.2023
                      :julkipano        1692219600000 ;; 17.8.2023
                      }

                  (-> verdicts first :paatokset first :poytakirjat first)
                  => {:paatoksentekija "Viranhaltija"
                      :paatos          "Phasellus accumsan cursus velit. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Sed aliquam, nisi quis porttitor congue, elit erat euismod orci, ac placerat dolor lectus quis orci."
                      :paatoskoodi     "myönnetty"
                      :status          "1"
                      :pykala          456
                      :paatospvm       1692046800000}

                  (:verdictDate app) => 1692046800000)

                (lp-id-xml-tests poikkeamislupa-xml "P")))

            (facts "Suunnittelutarveratkaisu"
              (stonvert suunittelutarveratkaisu-xml)
              (let [doc         (conv-doc/document "753-R" "23-0123-SUU")
                    {:keys [documents statements verdicts primaryOperation]
                     :as   app} (mongo/by-id :applications (:LP-id doc))]
                doc => (contains {:permit-type "P"
                                  :converted   true
                                  :year        2023})
                app => (contains {:permitType     "P"  :permitSubtype  "suunnittelutarveratkaisu"
                                  :facta-imported true :operation-name "poikkeamis"
                                  :state          "closed"})

                (fact "Primary operation and its (empty) document"
                  (let [op-id (-> :id primaryOperation)
                        doc   (util/find-first (util/fn-> :schema-info :op :id (= op-id))
                                               documents)]
                    op-id => truthy
                    (:name primaryOperation) => "poikkeamis"
                    (-> doc :schema-info :name) => "rakennushanke"))

                (fact "Applicant document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hakija"))
                                               documents)
                              {:_selected                                    "henkilo"
                               :henkilo.henkilotiedot.etunimi                "Tarmo"
                               :henkilo.henkilotiedot.sukunimi               "Tarvetsa"
                               :henkilo.henkilotiedot.hetu                   "060434-6871"
                               :henkilo.kytkimet.suoramarkkinointilupa       false
                               :henkilo.kytkimet.vainsahkoinenAsiointiKytkin true
                               :henkilo.osoite.katu                          "Ratkaisunraitti 6 A"
                               :henkilo.osoite.postinumero                   "04320"
                               :henkilo.osoite.postitoimipaikannimi          "Toisaalla"
                               :henkilo.yhteystiedot.email                   "tarmo.tarvetsa@example.net"
                               :henkilo.yhteystiedot.puhelin                 "4422881100"}))

                (fact "Payer document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "maksaja"))
                                               documents)
                              {:_selected                              "henkilo"
                               :henkilo.henkilotiedot.etunimi          "Miss"
                               :henkilo.henkilotiedot.sukunimi         "Moneypenny"
                               :henkilo.henkilotiedot.hetu             "230969-3163"
                               :henkilo.kytkimet.suoramarkkinointilupa false
                               :henkilo.osoite.katu                    "Penninväylä 9"
                               :henkilo.osoite.postinumero             "04321"
                               :henkilo.osoite.postitoimipaikannimi    "Aarresaari"}))

                (fact "Info document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "hankkeen-kuvaus"))
                                               documents)
                              {:kuvaus "Nulla et ex et velit fringilla molestie in quis felis. Duis ultrices tempor ipsum nec blandit. Curabitur sagittis ligula nec porta laoreet. Phasellus accumsan mi et eleifend lacinia."}))

                (fact "Site document"
                  (doc-checks (util/find-first (util/fn-> :schema-info :name (= "rakennuspaikka-kuntagml"))
                                               documents)
                              {:hallintaperuste             "oma"
                               :kaavatilanne                "oikeusvaikutteinen yleiskaava"
                               :kiinteisto.tilanNimi        "Treasure Island"
                               :kiinteisto.kiinteistotunnus "753-402-10-19"
                               :osoite.kunta                "753"
                               :osoite.osoitenimi           "Hupsistie / Humbugsvägen"
                               :osoite.osoitenumero         "100"
                               :osoite.postinumero          "04320"
                               :osoite.postitoimipaikannimi "Away"}))

                (fact "Statement"
                  (count statements) => 1
                  (first statements)
                  => (contains {:given     1629234000000 ;; 18.8.2021
                                :requested 1628888400000 ;; 14.8.2021
                                :status    "lausunto"
                                :state     "given"
                                :text      "Donec at interdum quam, at varius sapien. Fusce orci augue, ornare ac rhoncus vel, rhoncus convallis velit. Aliquam scelerisque feugiat lorem, nec ornare enim condimentum id."
                                :person    (contains {:name "Sipoon Silmä - Sibbo Ögon"
                                                      :text "Viranomainen"})}))

                (fact "Verdict"
                  (count verdicts) => 1
                  (-> verdicts first :paatokset first :paivamaarat)
                  => {:aloitettava      1689454800000 ;; 16.7.2023
                      :lainvoimainen    1689541200000 ;; 17.7.2023
                      :voimassaHetki    1752613200000 ;; 16.7.2025
                      :anto             1686776400000 ;; 15.6.2023
                      :viimeinenValitus 1689368400000 ;; 15.7.2023
                      :julkipano        1686690000000 ;; 14.6.2023
                      }

                  (-> verdicts first :paatokset first :poytakirjat first)
                  => {:paatoksentekija "Valpas valiokunta"
                      :paatos          "Phasellus pharetra nunc sed malesuada dignissim. Donec rutrum tellus ac rhoncus mollis. Vivamus placerat leo et commodo dapibus. Mauris commodo leo nibh, vitae vehicula orci pellentesque at."
                      :paatoskoodi     "myönnetty"
                      :status          "1"
                      :pykala          789
                      :paatospvm       1686085200000}

                  (:verdictDate app) => 1686085200000)

                (lp-id-xml-tests suunittelutarveratkaisu-xml "P")))))))))
