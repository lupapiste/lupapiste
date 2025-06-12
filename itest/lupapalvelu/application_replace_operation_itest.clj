(ns lupapalvelu.application-replace-operation-itest
  (:require [lupapalvelu.application-replace-operation :as replace-operation]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components)
  (facts "Replacing operations"

    (fact "Add BIM to required attachments for kerrostalo-rivitalo in Tampere"
      (let [old-defaults (->> (query tampere :organization-by-user
                                     :organizationId "837-R"
                                     :attachment-types-layout "map")
                              :operation-attachment-settings
                              :operation-nodes :kerrostalo-rivitalo
                              :default-attachments
                              (mapcat (fn [[group ids]]
                                        (mapv (partial vector (name group)) ids)))
                              seq)]
        old-defaults => truthy
        (command tampere :organization-operations-attachments
                 :organizationId "837-R"
                 :operation "kerrostalo-rivitalo"
                 :attachments (cons ["tietomallit" "rakennuksen_tietomalli_BIM"] old-defaults))
        => ok?))

    (let [app          (create-application pena :operation "kerrostalo-rivitalo" :propertyId tampere-property-id)
          app-id       (:id app)
          op-id        (-> app :primaryOperation :id)
          {bim-id :id} (->> app :attachments
                            (util/find-first (util/fn-> :type :type-id (= "rakennuksen_tietomalli_BIM"))))]

      (fact "BIM attachment exists and Pena connects it to the operation"
        bim-id => truthy
        (command pena :set-attachment-meta :id app-id
                 :attachmentId bim-id
                 :meta {:group {:groupType  "operation"
                                :operations [{:id op-id :name "kerrostalo-rivitalo"}] }})
        => ok?)

      (fact "Upload file to the last attachment and set it to the operation."
        (let [att-id (-> app :attachments last :id)]
          (upload-attachment pena app-id {:id att-id :op-id op-id} true) => att-id))

      (fact "Fill some fields"
        (command pena :update-doc :id app-id :collection "documents"
                 :doc (->> app :documents (util/find-first (comp :op :schema-info)) :id)
                 :updates [["rakennuksenOmistajat.0.omistajalaji" "pankki tai vakuutuslaitos"]
                           ["kaytto.rakentajaTyyppi" "liiketaloudellinen"]
                           ["kaytto.kayttotarkoitus" "111 myymälähallit"]])
        => ok?)

      (fact "Pena adds operation"
        (command pena :add-operation :id app-id :operation "varasto-tms"))

      (fact "Pena replaces primary operation"
        (command pena :replace-operation :id app-id :opId op-id :operation "asuinrakennus") => ok?)

      (let [updated-app     (query-application pena app-id)
            replaced-op     (replace-operation/get-operation-by-key updated-app :name "asuinrakennus")
            secondary-op-id (-> updated-app :secondaryOperations (first) :id)]

        (fact "Application has new primaryOperation"
          (-> updated-app :primaryOperation :name) => "asuinrakennus"
          (-> updated-app :secondaryOperations count) => 1)

        (fact "New operation has new id"
          (:id replaced-op) =not=> op-id)

        (fact "Valid fields are copied from the old operation"
          (let [{data :data} (util/find-first (util/fn-> :schema-info :op :name  (= "asuinrakennus"))
                                              (:documents updated-app))]
            (-> data :rakennuksenOmistajat :0 :omistajalaji :value)
            => "pankki tai vakuutuslaitos"
            (-> data :kaytto :rakentajaTyyppi :value) => "liiketaloudellinen"
            (fact "Invalid are not"
              (-> data :kaytto :kayttotarkoitus :value) => ss/blank?)))

        (fact "Application has correct attachment templates"
          (map :type (att/get-attachments-by-operation updated-app (:id replaced-op)))
          => (just [{:type-group "paapiirustus" :type-id "asemapiirros"}
                    {:type-group "paapiirustus" :type-id "julkisivupiirustus"}
                    {:type-group "paapiirustus" :type-id "leikkauspiirustus"}
                    {:type-group "paapiirustus" :type-id "pohjapiirustus"}
                    {:type-group "suunnitelmat" :type-id "piha_tai_istutussuunnitelma"}]
                   :in-any-order))

        (fact "Asemapiirros has two operations"
          (->> updated-app
               :attachments
               (filter #(-> % :type (= {:type-group "paapiirustus" :type-id "asemapiirros"})))
               first
               :op
               count) => 2)

        (fact "BIM attachment is gone and there are no bad attachments"
          (util/find-by-id bim-id (:attachments updated-app)) => nil
          (every? :id (:attachments updated-app)) => true)

        (fact "Application has new primaryOperation"
          (-> updated-app :primaryOperation :name) => "asuinrakennus")

        (fact "Pena replaces the secondary operation"
          (command pena :replace-operation :id app-id :opId secondary-op-id :operation "auto-katos")))

      (let [updated-app           (query-application pena app-id)
            replaced-secondary-op (-> updated-app :secondaryOperations (first) :id)]

        (fact "There is correct secondary operation"
          (-> updated-app :secondaryOperations (first) :name) => "auto-katos"
          (-> updated-app :secondaryOperations (count)) => 1)

        (fact "Autokatos has no attachment templates"
          (att/get-attachments-by-operation updated-app (:id replaced-secondary-op))
          => empty?))))

  (facts "Replace-operation command is:
 1) explicitly NOT needed for permit types YI, YM, YL, YA, VVVL, MAL and A (see e.g. LPK-6316)
 2) does NOT work unless there there is at least one operations within a permit type
    having add-operation-allowed true because otherwise there is no operation swap to.

 NOTE: :ARK permit-type is not included in the used test fixture and is not tested here."
    (let [has-some-addable-operations?            (fn [[_ operations]] (some :add-operation-allowed operations))
          excluded-from-test-fixture?             (comp #{:ARK} keyword first)
          to-test-case                            (fn [op] {:test-case  (str "Does not work anyway in " (:permit-type op))
                                                            :op-to-test (:id op)})
          replace-operations-is-not-going-to-work (->> lupapalvelu.operations/operations
                                                       (map (fn [[k v]] (assoc v :id k)))
                                                       (group-by :permit-type)
                                                       (remove excluded-from-test-fixture?)
                                                       (remove has-some-addable-operations?)
                                                       (map (comp to-test-case first shuffle second)))
          replace-operation-is-simply-not-needed  [{:test-case  "YI example"
                                                    :op-to-test "leirintaalueilmoitus"}
                                                   {:test-case  "YL example"
                                                    :op-to-test "pima"}
                                                   {:test-case  "YM example"
                                                    :op-to-test "muistomerkin-rauhoittaminen"}
                                                   {:test-case  "YA example"
                                                    :op-to-test "ya-kayttolupa-mainostus-ja-viitoitus"}
                                                   {:test-case  "A example"
                                                    :op-to-test "promootio"}]]
      (doseq [{:keys [test-case op-to-test]} (concat replace-operations-is-not-going-to-work
                                                     replace-operation-is-simply-not-needed)]
        (fact {:midje/description test-case}
          (let [app    (create-application pena :operation op-to-test :propertyId sipoo-property-id)
                app-id (:id app)
                op-id  (-> app :primaryOperation :id)]
            (command pena :replace-operation :id app-id :opId op-id :operation op-to-test) => {:ok false :text "error.unsupported-permit-type"})))))

  (facts "Adding attachments and replacing operations"
    (let [app                     (create-application pena :operation "asuinrakennus" :propertyId tampere-property-id)
          app-id                  (:id app)
          op-id                   (-> app :primaryOperation :id)

          _                       (command pena :add-operation :id app-id :operation "varasto-tms")
          _                       (upload-attachment-to-all-placeholders pena app)

          updated-app             (query-application pena app-id)
          attachments             (:attachments updated-app)
          secondary-op-id         (-> updated-app :secondaryOperations (first) :id)

          _                       (command pena :replace-operation :id app-id :opId op-id :operation "kerrostalo-rivitalo")
          _                       (command pena :replace-operation :id app-id :opId secondary-op-id :operation "auto-katos")

          updated-attachments     (:attachments (query-application pena app-id))

          attachment-has-version? (fn [att] (->> att :versions (not-empty)))
          not-empty-att-count     (fn [atts] (->> atts
                                                  (map attachment-has-version?)
                                                  (remove nil?)
                                                  (count)))]

      (fact "Originally no attachments"
        (not-empty-att-count (:attachments app)) => 0)

      (fact "Uploaded attachment count is positive"
        (> (not-empty-att-count attachments) 0) => true)

      (fact "Adding attachments and replacing operation does not remove uploaded attachments"
        (not-empty-att-count attachments) => (not-empty-att-count updated-attachments))))

  (facts "Replacing operation keeps correct documents or updates them"
    (let [app               (create-application pena :operation "tontin-ajoliittyman-muutos"
                                                :propertyId tampere-property-id)
          doc-names         (->> app :documents (map #(-> % :schema-info :name)) (set))
          app-id            (:id app)
          op-id             (-> app :primaryOperation :id)

          _                 (command pena :replace-operation :id app-id :opId op-id :operation "auto-katos")
          updated-app       (query-application pena app-id)
          updated-doc-names (->> updated-app :documents (map #(-> % :schema-info :name)) (set))]

      (fact "originally documents are as they should"
        (doc-names "laajennettuRakennusjateselvitys-v2") => nil?
        (doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy
        (doc-names "rakennuspaikka") => nil?
        (doc-names "paasuunnittelija") = nil?)

      (fact "after replacing operation documents are as they should"
        (updated-doc-names "laajennettuRakennusjateselvitys-v2") => truthy
        (updated-doc-names "rakennuspaikka-ilman-ilmoitusta") => nil?
        (updated-doc-names "rakennuspaikka") => truthy
        (updated-doc-names "paasuunnittelija") => truthy))

    (let [app                 (create-application pena
                                                  :operation "tontin-ajoliittyman-muutos"
                                                  :propertyId tampere-property-id)
          doc-names           (->> app :documents (map #(-> % :schema-info :name)) (set))
          app-id              (:id app)

          _                   (command pena :add-operation :id app-id :operation "auto-katos")
          updated-app         (query-application pena app-id)
          updated-doc-names   (->> updated-app :documents (map #(-> % :schema-info :name)) (set))
          secondary-op-id     (->> updated-app :secondaryOperations (first) :id)

          _                   (command pena :replace-operation :id app-id :opId secondary-op-id :operation "varasto-tms")
          updated-app-2       (query-application pena app-id)
          updated-doc-names-2 (->> updated-app-2 :documents (map #(-> % :schema-info :name)) (set))]

      (fact "at first no rakennusjatesuunnitelma nor paasuunnittelija"
        (doc-names "laajennettuRakennusjateselvitys-v2") => nil?
        (doc-names "paasuunnittelija") => nil?)

      (fact "at first rakennuspaikka-ilman-ilmoitusta"
        (doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy)

      (fact "after adding second operation there is rakennusjatesuunnitelma and paasuunnittelija"
        (updated-doc-names "laajennettuRakennusjateselvitys-v2") => truthy
        (updated-doc-names "paasuunnittelija") => truthy)

      (fact "after adding second operation there is no change in rakennuspaikka"
        (updated-doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy
        (updated-doc-names "rakennuspaikka") => nil?)

      (fact "after replacing second operation there is new rakennuspaikka document"
        (updated-doc-names-2 "rakennuspaikka-ilman-ilmoitusta") => nil?
        (updated-doc-names-2 "rakennuspaikka") => truthy)

      (fact "waste document as it should be"
        (updated-doc-names-2 "laajennettuRakennusjateselvitys-v2") => truthy))

    (let [app                 (create-application pena :operation "auto-katos" :propertyId tampere-property-id)
          doc-names           (->> app :documents (map #(-> % :schema-info :name)) (set))
          app-id              (:id app)
          op-id               (-> app :primaryOperation :id)

          _                   (command pena :add-operation :id app-id :operation "kaivuu")
          updated-app         (query-application pena app-id)
          updated-doc-names   (->> updated-app :documents (map #(-> % :schema-info :name)) (set))

          _                   (command pena :replace-operation :id app-id :opId op-id :operation "tontin-ajoliittyman-muutos")
          updated-app-2       (query-application pena app-id)
          updated-doc-names-2 (->> updated-app-2 :documents (map #(-> % :schema-info :name)) (set))]

      (fact "at first there are correct documents"
        (doc-names "laajennettuRakennusjateselvitys-v2") => truthy
        (doc-names "rakennuspaikka") => truthy
        (doc-names "rakennuspaikka-ilman-ilmoitusta") => nil?)

      (fact "adding operation that has mini-rakval-schema does not change that"
        (updated-doc-names "laajennettuRakennusjateselvitys-v2") => truthy
        (updated-doc-names "rakennuspaikka") => truthy
        (updated-doc-names "rakennuspaikka-ilman-ilmoitusta") => nil?)

      (fact "changing the common-rakval-schema operation changes that"
        (updated-doc-names-2 "rakennusjatesuunnitelma") => nil?
        (updated-doc-names-2 "rakennuspaikka") => nil?
        (updated-doc-names-2 "rakennuspaikka-ilman-ilmoitusta") => truthy))))
