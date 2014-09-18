(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.walk :as walk]
            [sade.util :refer [dissoc-in postwalk-map strip-nils]]
            [sade.strings :as ss]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.application :as a]
            [lupapalvelu.application-meta-fields :as app-meta-fields]
            [lupapalvelu.operations :as op]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

(defmigration schemas-be-gone-from-submitted
  {:apply-when (pos? (mongo/count :submitted-applications {:schema-version {$exists false}}))}
  (doseq [application (mongo/select :submitted-applications {:schema-version {$exists false}} {:documents true})]
    (mongo/update-by-id :submitted-applications (:id application) {$set {:schema-version 1
                                                                         :documents (map drop-schema-data (:documents application))}})))

(defn fix-invalid-schema-infos [{documents :documents operations :operations :as application}]
  (let [updated-documents (doall (for [o operations]
                                   (let [operation-name (keyword (:name o))
                                         target-document-name (:schema (operation-name op/operations))
                                         created (:created o)
                                         document-to-update (some (fn [d] (if
                                                                            (and
                                                                              (= created (:created d))
                                                                              (= target-document-name (get-in d [:schema-info :name])))
                                                                            d)) documents)
                                         updated (when document-to-update
                                                   (update-in document-to-update [:schema-info]
                                                     merge
                                                     {:op o
                                                      :removable (= "R" (:permitType application))}))
                                         ]
                                     updated)))
        unmatched-operations (filter
                               (fn [{id :id :as op}]
                                 (nil? (some
                                         (fn [d]
                                           (when
                                             (= id (get-in d [:schema-info :op :id]))
                                             d))
                                         updated-documents)))
                               operations)
        updated-documents (into updated-documents (for [o unmatched-operations]
                                                    (let [operation-name (keyword (:name o))
                                                          target-document-name (:schema (operation-name op/operations))
                                                          created (:created o)
                                                          document-to-update (some (fn [d]
                                                                                     (if
                                                                                       (and
                                                                                         (< created (:created d))
                                                                                         (= target-document-name (get-in d [:schema-info :name])))
                                                                                       d)) documents)
                                                          updated (when document-to-update
                                                                    (update-in document-to-update [:schema-info]
                                                                      merge
                                                                      {:op o
                                                                       :removable (= "R" (:permitType application))}))]
                                                      updated)))
        result (map
                 (fn [{id :id :as d}]
                   (if-let [r (some (fn [nd] (when (= id (:id nd)) nd)) updated-documents)]
                     r
                     d)) documents)
        new-operations (filter
                         (fn [{id :id :as op}]
                           (some
                             (fn [d]
                               (when
                                 (= id (get-in d [:schema-info :op :id]))
                                 d))
                             result))
                         operations)]
    (-> application
      (assoc :documents (vec result))
      (assoc :operations new-operations)
      )))

(defmigration invalid-schema-infos-validation
  (doseq [collection [:applications :submitted-applications]]
    (let [applications (mongo/select collection {:infoRequest false})]
      (doall (map #(mongo/update-by-id collection (:id %) (fix-invalid-schema-infos %)) applications)))))

;; Old migration previoisly run to applications collection only

(defn- update-document-kuntaroolikoodi [{data :data :as document}]
  (let [to-update (tools/deep-find data [:patevyys :kuntaRoolikoodi])
        updated-document (when (not-empty to-update)
                           (assoc document :data (reduce
                                                   (fn [d [old-key v]]
                                                     (let [new-key (conj (subvec old-key 0 (count old-key)) :kuntaRoolikoodi)
                                                           cleaned-up (dissoc-in d (conj old-key :patevyys :kuntaRoolikoodi))]
                                                       (assoc-in cleaned-up new-key v)))
                                                   data to-update)))]
    (if updated-document
      updated-document
      document)))


(defn kuntaroolikoodiUpdate [{d :documents :as a}]
    (assoc a :documents (map update-document-kuntaroolikoodi d)))

(defmigration submitted-applications-kuntaroolikoodi-migraation
  (let [applications (mongo/select :submitted-applications)]
    (dorun (map #(mongo/update-by-id :submitted-applications (:id %) (kuntaroolikoodiUpdate %)) applications))))

(defn- strip-fax [doc]
  (postwalk-map (partial map (fn [[k v]] (when (not= :fax k) [k v]))) doc))

(defn- cleanup-uusirakennus [doc]
  (if (and (= "uusiRakennus" (get-in doc [:schema-info :name])) (get-in doc [:data :henkilotiedot]))
    (-> doc
      (dissoc-in [:data :userId])
      (dissoc-in [:data :henkilotiedot])
      (dissoc-in [:data :osoite])
      (dissoc-in [:data :yhteystiedot]))
    doc))

(defn- fix-hakija [doc]
  (if (and (= "hakija" (get-in doc [:schema-info :name])) (get-in doc [:data :henkilotiedot]))
    (if (or (get-in doc [:data :henkilo]) (get-in doc [:data :yritys]))
      ; Cleanup
      (-> doc
        (dissoc-in [:data :userId])
        (dissoc-in [:data :henkilotiedot])
        (dissoc-in [:data :osoite])
        (dissoc-in [:data :yhteystiedot]))
      ; Or move
      (assoc doc :data {:henkilo (:data doc)}))
    doc))

(defn- pre-verdict-applicationState [current-state]
  (let [current-state (keyword current-state)]
    (cond (#{:draft :open} current-state) current-state
          (= :cancelled current-state) :draft
          :else :submitted)))

(defn- post-verdict-applicationState [current-state] :verdictGiven)
;  (let [current-state (keyword current-state)]
;    (cond (= :closed current-state) :constructionStarted
;          (= :cancelled current-state) :verdictGiven
;          :else current-state))

(defn- set-applicationState-to-attachment [verdict-given state attachment]
  (let [first-version      (-> attachment :versions first)
        attachment-created (or (:created first-version) (:modified attachment) 0)]
    (if (and verdict-given (> attachment-created verdict-given))
      (assoc attachment :applicationState (post-verdict-applicationState state))
      (assoc attachment :applicationState (pre-verdict-applicationState state)))))

(defn- attachments-with-applicationState [application]
  (let [verdicts      (:verdicts application)
        verdict-given (when (-> verdicts count pos?) (-> verdicts first :timestamp))]
    (map (partial set-applicationState-to-attachment verdict-given (:state application)) (:attachments application))))

(defmigration document-data-cleanup-rel-1.12
  (doseq [collection [:applications :submitted-applications]]
    (let [applications (mongo/select collection)]
      (dorun
        (map
          (fn [app]
            (mongo/update-by-id collection (:id app)
              {$set {:documents (map
                                  (fn [doc] (-> doc
                                                strip-fax
                                                cleanup-uusirakennus
                                                fix-hakija
                                                strip-nils))
                                  (:documents app))}}))
          applications)))))

(defmigration vetuma-token-cleanup-LUPA-976
  (mongo/drop-collection :vetuma))

(defmigration set-missing-default-values-for-keys-in-applications-LUPA-642
  (let [missing-keys-in-mongo [:buildings :shapes]
        keys-and-default-values (select-keys domain/application-skeleton missing-keys-in-mongo)]
    (doseq [collection [:applications :submitted-applications]
            [k d] keys-and-default-values]
      (mongo/update-by-query collection {k {$exists false}} {$set {k d}}))))

(defmigration drop-pistesijanti-from-documents
  (while (not-empty (mongo/select :applications {"documents.data.osoite.pistesijanti" {$exists true}}))
    (mongo/update-by-query :applications {"documents" {$elemMatch {"data.osoite.pistesijanti" {$exists true}}}} {$unset {"documents.$.data.osoite.pistesijanti" 1}})))

(defmigration kryps-config
  (doseq [organization (mongo/select :organizations)]
    (when-let [url (:legacy organization)]
      (doseq [permit-type (map :permitType (:scope organization))]
        (mongo/update-by-id :organizations (:id organization)
          {$set {(str "krysp." permit-type ".url") url
                 (str "krysp." permit-type ".version") "2.1.2"}
           $unset {:legacy 1}})))

    (when-let [ftp (:rakennus-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.R.ftpUser ftp}
         $unset {:rakennus-ftp-user 1}}))

    (when-let [ftp (:yleiset-alueet-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.YA.ftpUser ftp}
         $unset {:yleiset-alueet-ftp-user 1}}))

    (when-let [ftp (:poikkari-ftp-user organization)]
      (mongo/update-by-id :organizations (:id organization)
        {$set {:krysp.P.ftpUser ftp}
         $unset {:poikkari-ftp-user 1}}))))


(defmigration statementPersons-to-statementGivers
  {:apply-when (pos? (mongo/count  :organizations {:statementPersons {$exists true}}))}
  (doseq [organization (mongo/select :organizations {:statementPersons {$exists true}})]
    (mongo/update-by-id :organizations (:id organization)
                        {$set {:statementGivers (:statementPersons organization)}
                         $unset {:statementPersons 1}})))

(defmigration wfs-url-to-support-parameters
  (doseq [organization (mongo/select :organizations)]
    (doseq [[permit-type krysp-data] (:krysp organization)]
      (when-let [url (:url krysp-data)]
        (when (or (= (name permit-type) lupapalvelu.permit/R) (= (name permit-type) lupapalvelu.permit/P))
          (mongo/update-by-id :organizations (:id organization)
                              {$set {(str "krysp." (name permit-type) ".url") (str url "?outputFormat=KRYSP")}}))))))

(defmigration default-krysp-R-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}
    {$set {:krysp.R.version "2.1.2"}}))

(defmigration default-krysp-P-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}
    {$set {:krysp.P.version "2.1.2"}}))

(defmigration default-krysp-YA-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}
    {$set {:krysp.YA.version "2.1.2"}}))


(defmigration attachment-applicationState
  (doseq [application (mongo/select :applications {"attachments.0" {$exists true}})]
    (mongo/update-by-id :applications (:id application)
      {$set {:attachments (attachments-with-applicationState application)}})))

(defn- remove-huoneistot-and-update-schema-name [document new-schema-name]
  (let [data (:data document)
        data-ilman-huoneistoja (dissoc data :huoneistot)]
    (->  document
      (assoc :data data-ilman-huoneistoja)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn get-operation-name [document]
  (get-in document [:schema-info :op :name]))

(defn get-schema-name [document]
  (get-in document [:schema-info :name]))

(defn remove-data-for [operation old-schema-name new-schema-name update-fn]
  (doseq [collection [:applications :submitted-applications]]
    (let [applications-to-update (mongo/select collection {:documents {$elemMatch {$and [{ "schema-info.op.name" operation} {"schema-info.name" old-schema-name}]}}})]
     (doseq [application applications-to-update]
       (let [new-documents (map (fn [document]
                                  (let [schema-name (get-schema-name document)
                                        operation-name (get-operation-name document)]
                                    (if (and (= operation-name operation) (= schema-name old-schema-name))
                                      (update-fn document new-schema-name)
                                      document)))
                                (:documents application))]
         (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))

(defn remove-huoneistot-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-huoneistot-and-update-schema-name))

(defmigration vapaa-ajan-asuinrakennus-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "vapaa-ajan-asuinrakennus"} {"schema-info.name" "uusiRakennus"}] }}}))}
  (remove-huoneistot-for "vapaa-ajan-asuinrakennus" "uusiRakennus" "uusi-rakennus-ei-huoneistoa"))
(defmigration varasto-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "varasto-tms"} {"schema-info.name" "uusiRakennus"}] }}}))}
  (remove-huoneistot-for "varasto-tms" "uusiRakennus" "uusi-rakennus-ei-huoneistoa"))
(defmigration julkisivu-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "julkisivu-muutos"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "julkisivu-muutos" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration markatila-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "markatilan-laajentaminen"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "markatilan-laajentaminen" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration takka-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "takka-tai-hormi"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "takka-tai-hormi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))
(defmigration parveke-muutos-updates
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{ "schema-info.op.name" "parveke-tai-terassi"} {"schema-info.name" "rakennuksen-muuttaminen"}] }}}))}
  (remove-huoneistot-for "parveke-tai-terassi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja"))

(defn- update-krysp-version-for-all-orgs [permit-type from to]
  (let [path (str "krysp." permit-type ".version")]
    (mongo/update-by-query :organizations {path from} {$set {path to}})))

(defmigration ymparistolupa-organization-krysp-212
  {:apply-when (pos? (mongo/count :organizations {"krysp.YL.version" "2.1.1"}))}
  (update-krysp-version-for-all-orgs "YL" "2.1.1" "2.1.2"))

(defmigration mal-organization-krysp-212
  {:apply-when (pos? (mongo/count :organizations {"krysp.MAL.version" "2.1.1"}))}
  (update-krysp-version-for-all-orgs "MAL" "2.1.1" "2.1.2"))

(defmigration vvvl-organization-krysp-213
  {:apply-when (pos? (mongo/count :organizations {"krysp.VVVL.version" "2.1.1"}))}
  (update-krysp-version-for-all-orgs "VVVL" "2.1.1" "2.1.3"))

(defmigration yi-organization-krysp-212
  {:apply-when (pos? (mongo/count :organizations {"krysp.YI.version" "2.1.1"}))}
  (update-krysp-version-for-all-orgs "YI" "2.1.1" "2.1.2"))

(defn- remove-ominaisuustiedot-and-update-schema-name [document new-schema-name]
  (let [data (-> (:data document)
               (dissoc :lammitys)
               (dissoc :verkostoliittymat)
               (dissoc :varusteet)
               (dissoc :luokitus))]
    (-> document
      (assoc :data data)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn- remove-ominaisuustiedot-huoneistot-and-update-schema-name [document new-schema-name]
  (let [data (-> (:data document)
               (dissoc :lammitys)
               (dissoc :verkostoliittymat)
               (dissoc :varusteet)
               (dissoc :huoneistot)
               (dissoc :luokitus))]

    (-> document
      (assoc :data data)
      (assoc-in  [:schema-info :i18name] (-> document :schema-info :name))
      (assoc-in  [:schema-info :name] new-schema-name))))

(defn remove-ominaisuudet-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-ominaisuustiedot-and-update-schema-name))

(defn remove-ominaisuudet-huoneistot-for [operation old-schema-name new-schema-name]
  (remove-data-for operation old-schema-name new-schema-name remove-ominaisuustiedot-huoneistot-and-update-schema-name))

(defmigration rakennuksen-ominaistieto-updates-julkisivu-muutos
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "julkisivu-muutos"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
  (remove-ominaisuudet-for "julkisivu-muutos" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

(defmigration rakennuksen-ominaistieto-updates-markatila
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "markatilan-laajentaminen"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
  (remove-ominaisuudet-for "markatilan-laajentaminen" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

(defmigration rakennuksen-ominaistieto-updates-takka
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "takka-tai-hormi"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
  (remove-ominaisuudet-for "takka-tai-hormi" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

(defmigration rakennuksen-ominaistieto-updates-parveke
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "parveke-tai-terassi"} {"schema-info.name" "rakennuksen-muuttaminen-ei-huoneistoja"}] }}}))}
  (remove-ominaisuudet-for "parveke-tai-terassi" "rakennuksen-muuttaminen-ei-huoneistoja" "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"))

(defmigration rakennuksen-ominaistieto-updates-purku
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.op.name" "purkaminen"} {"schema-info.name" "purku"}] }}}))}
  (remove-ominaisuudet-huoneistot-for "purkaminen" "purku" "purkaminen"))

(defn- convert-neighbors [orig-neighbors]
  (if-not (empty? orig-neighbors)
    (for [[k v] orig-neighbors
            :let [propertyId (-> v :neighbor :propertyId)
                  owner (-> v :neighbor :owner)
                  status (-> v :status)]]
        {:id (name k)
         :propertyId propertyId
         :status status
         :owner owner})
    []))

(defmigration neighbors-to-sequable
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {} {:neighbors 1})]
    (when (map? (:neighbors application))
      (mongo/update-by-id collection (:id application)
        {$set {:neighbors (convert-neighbors (:neighbors application))}}))))

(defmigration applicant-index
  (doseq [collection [:applications :submitted-applications]]
    (let [applications (mongo/select collection)]
      (dorun (map #(mongo/update-by-id collection (:id %) (app-meta-fields/applicant-index-update %)) applications)))))

(defmigration remove-sijoituksen-and-tyon-tarkoitus
  (doseq [collection [:applications :submitted-applications]]
    (let [applications-to-update (mongo/select collection {:documents {$elemMatch {$or [{:schema-info.name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"}
                                                                                        {:schema-info.name "sijoituslupa-sijoituksen-tarkoitus"}]}}})]
      (doseq [application applications-to-update]
        (let [new-documents (map
                              #(if (= "yleiset-alueet-hankkeen-kuvaus-kaivulupa" (-> % :schema-info :name))
                                 (update-in % [:data] dissoc :sijoituksen-tarkoitus)
                                 %)
                              (:documents application))
              new-documents (remove #(= "sijoituslupa-sijoituksen-tarkoitus" (-> % :schema-info :name)) new-documents)]
          (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))


(defmigration move-operations-flags-into-their-scope
  {:apply-when (pos? (mongo/count :organizations {:new-application-enabled {$exists true}}))}
  ;; Let's expect all (un-migrated) organizations to have the "new-application-enabled" flag.
 (doseq [organization (mongo/select :organizations {:new-application-enabled {$exists true}})]
   (let [new-scopes (map
                      #(merge % {:inforequest-enabled     (or (:inforequest-enabled organization) false)
                                 :new-application-enabled (or (:new-application-enabled organization) false)
                                 :open-inforequest        (or (:open-inforequest organization) false)
                                 :open-inforequest-email  (:open-inforequest-email organization)})
                      (:scope organization))]
     (mongo/update-by-id :organizations (:id organization)
      {$unset {:inforequest-enabled ""
               :new-application-enabled ""
               :open-inforequest ""
               :open-inforequest-email ""}
       $set {:scope new-scopes}}))))

(defmigration unset-invalid-user-ids
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection)]
    (let [updates (a/generate-remove-invalid-user-from-docs-updates application)]
      (when (seq updates)
        (mongo/update-by-id collection (:id application) {$unset updates})))))

(defmigration cleanup-activation-collection-v2
  (let [users (map :email (mongo/select :users {} {:email 1}))]
    (mongo/remove-many :activation {:email {$nin users}})))

(defmigration generate-verdict-ids
  (doseq [application (mongo/select :applications {"verdicts.0" {$exists true}} {:verdicts 1, :attachments 1})]
    (let [verdicts (map #(assoc % :id (mongo/create-id)) (:verdicts application))
          id-for-urlhash (reduce
                            #(let [hashes (->> %2 :paatokset (map :poytakirjat) flatten (map :urlHash) (remove nil?))]
                               (merge %1 (zipmap hashes (repeat (:id %2)))))
                            {} verdicts)
          attachments (map
                        (fn [{:keys [target] :as a}]
                          (if (= "verdict" (:type target ))
                            (if-let [hash (:id target)]
                              ; Attachment for verdict from krysp
                              (assoc a :target (assoc target :id (id-for-urlhash hash) :urlHash hash))
                              ; Attachment for manual verdict
                              (assoc-in a [:target :id] (:id (first verdicts))))
                            a))
                        (:attachments application))]

      (mongo/update-by-id :applications (:id application) {$set {:verdicts verdicts, :attachments attachments}}))))

(defmigration convert-task-source-ids-v2
  (doseq [{verdicts :verdicts :as application} (mongo/select :applications {"tasks.0" {$exists true}} {:verdicts 1, :tasks 1})]
    (let [id-for-kuntalupatunnus (reduce #(if (:kuntalupatunnus %2) (assoc %1 (:kuntalupatunnus %2) (:id %2)) %1) {} verdicts)
          verdict-ids (set (map :id verdicts))
          tasks (map
                  (fn [{:keys [source] :as task} ]
                    (if (and (= "verdict" (:type source)) (not (verdict-ids (:id source))) )
                      (if (empty? verdicts)
                        (assoc task :source {}) ; no verdicts, clear invalid source
                        (let [kuntalupatunnus (first (clojure.string/split (:id source) #"/"))
                              verdict-id (or
                                           (id-for-kuntalupatunnus kuntalupatunnus)
                                           (:id (last verdicts)))]
                          (if verdict-id
                            (assoc-in task [:source :id] verdict-id)
                            (do
                              (warnf "Unable to resolve source id,\n  application id: %s,\n  task: %s,\n  id-for-kuntalupatunnus: %s \n" (:id application) task id-for-kuntalupatunnus)
                              task))))
                      task))
                  (:tasks application))]
      (when-not (= tasks (:tasks application))
        (mongo/update-by-id :applications (:id application) {$set {:tasks tasks}})))))

(defmigration comment-roles
  (doseq [application (mongo/select :applications {"comments.0" {$exists true}} {:comments 1})]
    (mongo/update-by-id :applications (:id application)
      {$set (mongo/generate-array-updates :comments (:comments application) (constantly true) :roles [:applicant :authority])})))

(defmigration unify-attachment-latest-version
  (doseq [application (mongo/select :applications {"state" {$in ["sent", "verdictGiven" "complement-needed", "constructionStarted"]}} {:attachments 1})
          {:keys [latestVersion] :as attachment} (:attachments application)]
    (let [last-version (last (:versions attachment))
          last-version-index (dec (count (:versions attachment)))]
      (when (and last-version (not= latestVersion last-version))
        (println (:id application) (:id attachment) "last version is out of sync")

        (assert (= (:version last-version) (:version latestVersion)))

        (when-not (= (:fileId last-version) (:fileId latestVersion)) (mongo/delete-file-by-id (:fileId last-version)))

        (println "Replacing " last-version " with " (:latestVersion attachment))

        (assert
          (pos?
            (mongo/update-by-query
              :applications
              {:_id (:id application), :attachments {$elemMatch {:id (:id attachment)}}}
              {$set {(str "attachments.$.versions." last-version-index) (:latestVersion attachment)}})))))))

(defmigration fix-missing-organizations
  {:apply-when (pos? (mongo/count :applications {:organization nil}))}
  (doseq [{:keys [id municipality permitType]} (mongo/select :applications {:organization nil} {:municipality 1, :permitType 1})]
    (let [organization-id (:id (organization/resolve-organization municipality permitType))]
      (assert organization-id)
      (mongo/update-by-id :applications id {$set {:organization organization-id}}))))

(defn flatten-huoneisto-data [{documents :documents}]
  (map
    (fn [doc]
      (if-let [to-update (seq (tools/deep-find doc :huoneistot ))]
        (reduce
          #(let [[p v] %2
                 path (conj p :huoneistot)]
             (reduce
               (fn [old-doc [n _]]
                 (update-in old-doc (conj path n)
                            (fn [old-data]
                              (-> old-data
                                (merge (:huoneistoTunnus old-data))
                                (dissoc :huoneistoTunnus)
                                (merge (:huoneistonTyyppi old-data))
                                (dissoc :huoneistonTyyppi)
                                (merge (:varusteet old-data))
                                (dissoc :varusteet)
                                ))))
               %1
               v)) doc to-update)
        doc
        )) documents))

(defmigration flatten-huoneisto
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {:infoRequest false})]
    (if (some seq (map #(tools/deep-find % :huoneistot) (:documents application)))
      (let [updated-documents (flatten-huoneisto-data application)]
        (mongo/update-by-id collection (:id application) {$set {:documents updated-documents}})))))


(defmigration add-location-to-rakennuspaikat
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$and [{"schema-info.name" {$in ["rakennuspaikka"
                                                                                                         "poikkeusasian-rakennuspaikka"
                                                                                                         "vesihuolto-kiinteisto"
                                                                                                         "kiinteisto"]}}
                                                                               {:schema-info.type {$exists false}}]}}}))}
  (doseq [collection [:applications :submitted-applications]]
    (let [names #{"rakennuspaikka" "poikkeusasian-rakennuspaikka" "vesihuolto-kiinteisto" "kiinteisto"}
          applications-to-update (mongo/select collection)]
      (doseq [application applications-to-update]
        (let [new-documents (map
                              #(if (contains? names (-> % :schema-info :name))
                                (update-in % [:schema-info] assoc :type "location")
                                %)
                              (:documents application))]
          (mongo/update-by-id collection (:id application) {$set {:documents new-documents}}))))))

(defmigration tutkinto-mapping
  {:apply-when (pos? (mongo/count :applications {"documents.data.patevyys.koulutus.value" {$exists true}}))}
  (let [mapping (sade.excel-reader/read-map "tutkinto-mapping.xlsx")]
    (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {"documents.data.patevyys.koulutus.value" {$exists true}} {:documents 1})]
     (let [id (:id application)
           documents (map
                       (fn [doc]
                         (if-let [koulutus (get-in doc [:data :patevyys :koulutus :value])]
                           (let [normalized (-> koulutus ss/trim ss/lower-case)]
                             (if-not (ss/blank? normalized)
                               (let [mapped     (get mapping normalized "muu")
                                     modified (get-in doc [:data :patevyys :koulutus :modified])]
                                 (debugf "%s/%s: Mapping '%s' to %s" collection id koulutus mapped)
                                 (assoc-in doc [:data :patevyys :koulutusvalinta] {:value mapped, :modified modified}))
                               doc))
                           doc)) (:documents application))]
       (mongo/update-by-id collection id {$set {:documents documents}})))))

