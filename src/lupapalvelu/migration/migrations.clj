(ns lupapalvelu.migration.migrations
  (:require [clj-time.coerce :as cljtc]
            [clj-time.core :as cljt]
            [clojure.set :refer [rename-keys] :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-meta-fields :as app-meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.accessibility :as attaccess]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.drawing :as draw]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.migration.attachment-type-mapping :as attachment-type-mapping]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :refer [task-doc-validation] :as tasks]
            [lupapalvelu.user :as user]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.excel-reader :as er]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.strings :as str]
            [sade.util :refer [dissoc-in postwalk-map strip-nils abs fn->>] :as util]
            [sade.validators :as v]
            [taoensso.timbre :refer [debug debugf info infof warn warnf error errorf]]
            [lupapalvelu.application :as application]
            [lupapalvelu.document.waste-schemas :as waste-schemas]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org])
  (:import [org.joda.time DateTime]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

#_(defmigration schemas-be-gone-from-submitted
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

#_(defmigration invalid-schema-infos-validation
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

#_(defmigration submitted-applications-kuntaroolikoodi-migraation
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

#_(defmigration document-data-cleanup-rel-1.12
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

#_(defmigration set-missing-default-values-for-keys-in-applications-LUPA-642
   (let [missing-keys-in-mongo [:buildings :shapes]
         keys-and-default-values (select-keys domain/application-skeleton missing-keys-in-mongo)]
     (doseq [collection [:applications :submitted-applications]
             [k d] keys-and-default-values]
       (mongo/update-by-query collection {k {$exists false}} {$set {k d}}))))

#_(defmigration kryps-config
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


#_(defmigration statementPersons-to-statementGivers
   {:apply-when (pos? (mongo/count  :organizations {:statementPersons {$exists true}}))}
   (doseq [organization (mongo/select :organizations {:statementPersons {$exists true}})]
     (mongo/update-by-id :organizations (:id organization)
                         {$set {:statementGivers (:statementPersons organization)}
                          $unset {:statementPersons 1}})))

#_(defmigration wfs-url-to-support-parameters
   (doseq [organization (mongo/select :organizations)]
     (doseq [[permit-type krysp-data] (:krysp organization)]
       (when-let [url (:url krysp-data)]
         (when (or (= (name permit-type) lupapalvelu.permit/R) (= (name permit-type) lupapalvelu.permit/P))
           (mongo/update-by-id :organizations (:id organization)
                               {$set {(str "krysp." (name permit-type) ".url") (str url "?outputFormat=KRYSP")}}))))))

#_(defmigration default-krysp-R-version
   {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}))}
   (mongo/update-by-query :organizations
     {$and [{:krysp.R {$exists true}} {:krysp.R.version {$exists false}}]}
     {$set {:krysp.R.version "2.1.2"}}))

#_(defmigration default-krysp-P-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.P {$exists true}} {:krysp.P.version {$exists false}}]}
    {$set {:krysp.P.version "2.1.2"}}))

#_(defmigration default-krysp-YA-version
  {:apply-when (pos? (mongo/count :organizations {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}))}
  (mongo/update-by-query :organizations
    {$and [{:krysp.YA {$exists true}} {:krysp.YA.version {$exists false}}]}
    {$set {:krysp.YA.version "2.1.2"}}))


#_(defmigration attachment-applicationState
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

(comment
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
    (remove-huoneistot-for "parveke-tai-terassi" "rakennuksen-muuttaminen" "rakennuksen-muuttaminen-ei-huoneistoja")))

(defn- update-krysp-version-for-all-orgs [permit-type from to]
  (let [path (str "krysp." permit-type ".version")]
    (mongo/update-by-query :organizations {path from} {$set {path to}})))

(comment
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
    (update-krysp-version-for-all-orgs "YI" "2.1.1" "2.1.2")))

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

(comment
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
    (remove-ominaisuudet-huoneistot-for "purkaminen" "purku" "purkaminen")))

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

#_(defmigration neighbors-to-sequable
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {} {:neighbors 1})]
     (when (map? (:neighbors application))
       (mongo/update-by-id collection (:id application)
         {$set {:neighbors (convert-neighbors (:neighbors application))}}))))

#_(defmigration remove-sijoituksen-and-tyon-tarkoitus
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


#_(defmigration move-operations-flags-into-their-scope
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

#_(defmigration generate-verdict-ids
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

#_(defmigration convert-task-source-ids-v2
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

#_(defmigration unify-attachment-latest-version
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

#_(defmigration fix-missing-organizations
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

#_(defmigration flatten-huoneisto
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {:infoRequest false})]
     (if (some seq (map #(tools/deep-find % :huoneistot) (:documents application)))
       (let [updated-documents (flatten-huoneisto-data application)]
         (mongo/update-by-id collection (:id application) {$set {:documents updated-documents}})))))


#_(defmigration add-location-to-rakennuspaikat
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

#_(defmigration tutkinto-mapping
   (let [mapping (er/read-map "tutkinto-mapping.xlsx")]
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

(def- operation-mappings
  {:asuinrakennus         [:kerrostalo-rivitalo :pientalo]
   :muu-uusi-rakentaminen [:muu-uusi-rakentaminen :teollisuusrakennus]
   :laajentaminen         [:kerrostalo-rt-laaj :pientalo-laaj :vapaa-ajan-rakennus-laaj :talousrakennus-laaj :teollisuusrakennus-laaj :muu-rakennus-laaj]
   :kayttotark-muutos     [:kayttotark-muutos :sisatila-muutos]
   :muu-laajentaminen     [:linjasaneeraus]
   :puun-kaataminen       [:puun-kaataminen :rak-valm-tyo]
   :jatkoaika             [:raktyo-aloit-loppuunsaat]})

(defn- copy-attachment-configs [old-op-attachments]
  (reduce (fn [new-attachment-configs [old-op-key old-attachment-config]]
            (if (old-op-key operation-mappings)
              (let [attachment-config (mapcat (fn [op-key] [op-key old-attachment-config]) (old-op-key operation-mappings))]
                (merge new-attachment-configs (apply hash-map attachment-config)))
              new-attachment-configs)) {} old-op-attachments))

(defn new-operations-attachments [old-operations-attachments]
  (merge old-operations-attachments (copy-attachment-configs old-operations-attachments)))

(defn new-selected-operations [old-ops]
  (when-not (nil? old-ops) ; no old ops -> do not mark explicit new ops
    (->> old-ops
         (map (fn [op]
                (if (operation-mappings op)
                  (operation-mappings op)
                  op)))
         flatten)))

#_(defmigration import-new-operations-for-organisations
   (doseq [org (mongo/select :organizations {:scope.permitType "R"})]
     (let [old-selected-operations (map keyword (:selected-operations org))
           old-operations-attachments (:operations-attachments org)]
       (organization/update-organization (:id org) {$set {:selected-operations    (new-selected-operations old-selected-operations)
                                                          :operations-attachments (new-operations-attachments old-operations-attachments)}}))))

(defn update-applications-array
  "Updates an array k in every application by mapping the array with f.
   Applications are fetched using the given query.
   Return the number of applications updated."
  [k f query]
  {:pre [(keyword? k) (fn? f) (map? query)]}
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection query {k 1})]
            (mongo/update-n collection {:_id (:id application)} {$set {k (map f (k application))}}))))

(defn update-bulletin-versions [k f query]
  "Maps f over each element of list with key k in application
   bulletins, obtained by the given query. Returns the number
   of bulletins updated."
  {:pre [(keyword? k) (fn? f) (map? query)]}
  (let [version-key (keyword (str "versions." (name k)))]
    (reduce + 0
      (for [bulletin (mongo/select :application-bulletins query {:versions 1})]
        (mongo/update-n :application-bulletins
          {:_id (:id bulletin)}
          {$set
            {:versions
              (map
                (fn [versio]
                  (assoc versio k (map f (get versio k))))
                (:versions bulletin))}})))))

(defn- populate-buildingids-to-doc [doc]
  (let [rakennusnro (get-in doc [:data :rakennusnro])
        manuaalinen-rakennusnro (get-in doc [:data :manuaalinen_rakennusnro])]
    (cond
      (-> manuaalinen-rakennusnro :value ss/blank? not) (update-in doc [:data] #(assoc % :buildingId (assoc manuaalinen-rakennusnro :value "other")))
      (:value rakennusnro) (update-in doc [:data] #(assoc % :buildingId rakennusnro))
      :else doc)))

#_(defmigration populate-buildingids-to-docs
   (update-applications-array
     :documents
     populate-buildingids-to-doc
     {:documents {$elemMatch {$or [{:data.rakennusnro.value {$exists true}} {:data.manuaalinen_rakennusnro.value {$exists true}}]}}}))

#_(defmigration populate-buildingids-to-buildings
   (update-applications-array
     :buildings
     #(assoc % :localShortId (:buildingId %), :nationalId nil, :localId nil)
     {:buildings.0 {$exists true}}))


#_(defmigration update-app-links-with-apptype
   (doseq [app-link (mongo/select :app-links)]
     (let [linkpermit-id (some
                           (fn [[k v]]
                             (when (= "linkpermit" (:type v))
                               (name k)))
                           app-link)
           app           (first (mongo/select :applications {:_id linkpermit-id}))
           apptype       (->> app :operations first :name)]
       (mongo/update-by-id
         :app-links
         (:id app-link)
         {$set {(str linkpermit-id ".apptype") apptype}}))))

(defn- merge-versions [old-versions {:keys [user version] :as new-version}]
  (let [next-ver (att/next-attachment-version (:version (last old-versions)) user)]
    (concat old-versions [(assoc new-version :version next-ver)])))

(defn- fixed-versions [required-flags-migration-time attachments-backup updated-attachments]
  (for [attachment attachments-backup
        :let [updated-attachment (some #(when (= (:id %) (:id attachment)) %) updated-attachments)]]
    (if updated-attachment
      (let [new-versions (filter (fn [v] (> (get v :created 0) required-flags-migration-time)) (:versions updated-attachment))]
        (update-in attachment [:versions] #(reduce merge-versions % new-versions)))
      attachment)))

(defn- removed-versions [attachments fs-file-ids]
  (for [attachment attachments]
    (update-in attachment [:versions] #(filter (fn [v] (fs-file-ids (:fileId v))) %))))

#_(defmigration restore-attachments
   (when (pos? (mongo/count :applicationsBackup))
     (let [fs-file-ids (set (map :id (mongo/select :fs.files)))
          required-flags-migration-time (:time (mongo/select-one :migrations {:name "required-flags-for-attachment-templates"}))]

       (assert (pos? required-flags-migration-time))

       (doseq [id (map :id (mongo/select :submitted-applications {} [:_id]))]
         (let [attachments-backup (:attachments (mongo/by-id :applicationsBackup id [:attachments]))
               restored-ids (set (map :id attachments-backup))

               current-attachments (:attachments (mongo/by-id :applications id [:attachments]))
               updated-attachments (filter #(some (fn [v] (> (get v :created 0) required-flags-migration-time)) (:versions %)) current-attachments)

               new-attachments (filter
                                 (fn [a] (> (:modified a) required-flags-migration-time))
                                 (remove #(restored-ids (:id %)) current-attachments))

               attachments (if (seq updated-attachments)
                             (concat
                               (fixed-versions required-flags-migration-time attachments-backup updated-attachments)
                               new-attachments)
                             (concat attachments-backup new-attachments))

               removed (removed-versions attachments fs-file-ids)
               latest-versions-updated (map (fn [a] (assoc a :latestVersion (-> a :versions last))) removed)
               ]
           (mongo/update-by-id :applications id {$set {:attachments latest-versions-updated}})
           )))))


(defn- is-attachment-added-on-application-creation [application attachment]
  ;; inside 100 ms window, just in case
  (< (abs (- (:modified attachment) (:created application))) 100))

#_(defmigration required-flags-for-attachment-templates-v2
   (doseq [collection [:applications :submitted-applications]
           application (mongo/select collection {"attachments.0" {$exists true}})]
     (mongo/update-by-id collection (:id application)
       {$set {:attachments (map
                             #(assoc % :required (is-attachment-added-on-application-creation application %))
                             (:attachments application))}})))

#_(defmigration new-forPrinting-flag
    (update-applications-array
      :attachments
      #(assoc % :forPrinting false)
      {:attachments.0 {$exists true}}))

#_(defmigration app-required-fields-filling-obligatory
  {:apply-when (pos? (mongo/count :organizations {:app-required-fields-filling-obligatory {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:app-required-fields-filling-obligatory {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:app-required-fields-filling-obligatory false}})))

#_(defmigration kopiolaitos-info
  {:apply-when (pos? (mongo/count :organizations {$or [{:kopiolaitos-email {$exists false}}
                                                       {:kopiolaitos-orderer-address {$exists false}}
                                                       {:kopiolaitos-orderer-email {$exists false}}
                                                       {:kopiolaitos-orderer-phone {$exists false}}]}))}
  (doseq [organization (mongo/select :organizations {$or [{:kopiolaitos-email {$exists false}}
                                                          {:kopiolaitos-orderer-address {$exists false}}
                                                          {:kopiolaitos-orderer-email {$exists false}}
                                                          {:kopiolaitos-orderer-phone {$exists false}}]})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:kopiolaitos-email           (or (:kopiolaitos-email organization) nil)
             :kopiolaitos-orderer-address (or (:kopiolaitos-orderer-address organization) nil)
             :kopiolaitos-orderer-email   (or (:kopiolaitos-orderer-email organization) nil)
             :kopiolaitos-orderer-phone   (or (:kopiolaitos-orderer-phone organization) nil)}})))

(def known-good-domains #{"luukku.com" "suomi24.fi" "turku.fi" "kolumbus.fi"
                          "gmail.fi" "gmail.com" "aol.com" "sweco.fi" "me.com"
                          "hotmail.com" "fimnet.fi" "hotmail.fi"
                          "welho.com" "parkano.fi" "rautjarvi.fi" "lupapiste.fi"
                          "elisanet.fi" "elisa.fi" "yit.fi" "jarvenpaa.fi" "jippii.fi"})

#_(defmigration cleanup-activation-collection-v3
   (let [active-emails (map :email (mongo/select :users {:enabled true, :role "applicant"} {:email 1}))]
     (mongo/remove-many :activation {:email {$in active-emails}}))
   (doseq [{:keys [id email]} (mongo/select :activation)]
     (let [known-domain (known-good-domains (ss/suffix email "@"))]
       (when (or (.endsWith email ".f") (and (not known-domain) (not (sade.dns/valid-mx-domain? email))))
        (mongo/remove :activation id)))))

#_(defmigration comment-roles-v2
   (update-applications-array
     :comments
     #(if (= (:roles %) ["applicant" "authority"]) (assoc % :roles [:applicant :authority :oirAuthority]) %)
     {"comments.0" {$exists true}, :openInfoRequest true}))

#_(defmigration select-all-operations-for-organizatio-if-none-selected
   (let [organizations (mongo/select :organizations {$or [{:selected-operations {$size 0}},
                                                          {:selected-operations {$exists false}},
                                                          {:selected-operations nil}]})]
        (doseq [organization organizations]
          (let [org-permit-types (set (map :permitType (:scope organization)))
                operations (map first (filter (fn [[_ v]] (org-permit-types (name (:permit-type v))))  op/operations))]
               (mongo/update-by-id :organizations (:id organization)
                                   {$set {:selected-operations operations}})))
     ))

#_(defmigration user-organization-cleanup
   {:apply-when (pos? (mongo/count :users {:organization {$exists true}}))}
   (mongo/update-by-query :users {:organization {$exists true}} {$unset {:organization 0}}))

(defmigration rename-foreman-competence-documents
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" {$regex #"^tyonjohtaja"}
                                                                         "data.patevyys"    {$exists true}}}}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (re-find #"^tyonjohtaja" (-> doc :schema-info :name))
        (update-in doc [:data] clojure.set/rename-keys {:patevyys :patevyys-tyonjohtaja})
        doc))
    {"documents.schema-info.name" {$regex #"^tyonjohtaja"}}))

(defmigration rename-suunnittelutarveratkaisun-lisaosa-changed-fields
  {:apply-when (pos? (mongo/count :applications {$or [{"documents.data.vaikutukset_yhdyskuntakehykselle.etaisyyys_alakouluun" {$exists true}}
                                                      {"documents.data.vaikutukset_yhdyskuntakehykselle.etaisyyys_ylakouluun" {$exists true}}]}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (= (-> doc :schema-info :name) "suunnittelutarveratkaisun-lisaosa")
        (update-in doc [:data :vaikutukset_yhdyskuntakehykselle]
                   clojure.set/rename-keys {:etaisyyys_alakouluun :etaisyys_alakouluun :etaisyyys_ylakouluun :etaisyys_ylakouluun})
        doc))
    {"documents.schema-info.name" "suunnittelutarveratkaisun-lisaosa"}))

(defmigration create-transfered-to-backing-system-transfer-entry
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {$and [{:transfers {$not {$elemMatch {:type "exported-to-backing-system"}}}}
                                                      {:sent {$ne nil}}]})]
    (mongo/update-by-id collection (:id application)
                        {$push {:transfers {:type "exported-to-backing-system"
                                            :timestamp (:sent application)}}})))

(defn- change-patevyys-muu-key [doc]
  (let [koulutusvalinta-path             [:data :patevyys             :koulutusvalinta :value]
        tyonjohtaja-koulutusvalinta-path [:data :patevyys-tyonjohtaja :koulutusvalinta :value]]
    (cond
     (= "muu" (get-in doc koulutusvalinta-path))             (assoc-in doc koulutusvalinta-path "other")
     (= "muu" (get-in doc tyonjohtaja-koulutusvalinta-path)) (assoc-in doc tyonjohtaja-koulutusvalinta-path "other")
     :else doc)))

(defmigration patevyys-muu-key-to-other
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {$or [{:data.patevyys.koulutusvalinta.value "muu"}
                                                                              {:data.patevyys-tyonjohtaja.koulutusvalinta.value "muu"}]}}}))}
  (update-applications-array
    :documents
    change-patevyys-muu-key
    {:documents {$elemMatch {$or [{:data.patevyys.koulutusvalinta.value "muu"}
                                  {:data.patevyys-tyonjohtaja.koulutusvalinta.value "muu"}]}}}))

(defmigration remove-organizations-field-from-dummy-and-applicant
  {:apply-when (pos? (mongo/count :users {$and [{:organizations {$exists true}} {:role {$in ["dummy", "applicant"]}}]}))}
  (mongo/update-by-query :users {$and [{:organizations {$exists true}} {:role {$in ["dummy", "applicant"]}}]} {$unset {:organizations 1}}))

(defn organizations->org-authz [coll]
  (let [users (mongo/select coll {:organizations {$exists true}})]
    (reduce + 0
            (for [{:keys [organizations id role]} users]
              (let [org-authz (into {} (for [org organizations] [(str "orgAuthz." org) [role]]))]
                (if (empty? org-authz)
                  (mongo/update-n coll {:_id id} {$set {:orgAuthz {}}
                                                  $unset {:organizations 1}})
                  (mongo/update-n coll {:_id id} {$set org-authz
                                                  $unset {:organizations 1}})))))))

(defmigration add-org-authz
  {:apply-when (pos? (mongo/count :users {:organizations {$exists true}}))}
  (organizations->org-authz :users))

(defmigration tyonjohtaja-v1-vastuuaika-cleanup
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" "tyonjohtaja", "data.vastuuaika" {$exists true}}}}))}
  (update-applications-array
    :documents
    (fn [doc]
      (if (= (-> doc :schema-info :name) "tyonjohtaja")
        (util/dissoc-in doc [:data :vastuuaika])
        doc))
    {:documents {$elemMatch {"schema-info.name" "tyonjohtaja", "data.vastuuaika" {$exists true}}}}))

(defmigration application-authority-default-keys
  {:apply-when (pos? (mongo/count :applications {:authority.lastName {$exists false}}))}
  (mongo/update-n :applications {:authority.lastName {$exists false}} {$set {:authority (:authority domain/application-skeleton)}} :multi true))

(defmigration strip-FI-from-y-tunnus
  {:apply-when (pos? (mongo/count :companies {:y {$regex #"^FI"}}))}
  (doseq [company (mongo/select :companies {:y {$regex #"^FI"}})]
    (mongo/update-by-id :companies (:id company) {$set {:y (subs (:y company) 2)}})))

(defmigration company-default-account-type
  {:apply-when (pos? (mongo/count :companies {:accountType {$exists false}}))}
  (mongo/update-n :companies {:accountType {$exists false}} {$set {:accountType "account15"}} :multi true))

(def invoicing-operator-mapping
  {
;   "003701274855102 OKOYFIHH" "OKOYFIHH"
;   "0036714377140" "003714377140"
;   "003703575029 " "003703575029"
;   "00370357529" "003703575029"
;   "003703675029" "003703575029"
;   "003708599126/Liaison Technologies Oy" "003708599126"
;   "003710948874 " "003710948874"
;   "003714377140 Enfo" "003714377140"
;   "003714377140ENFO" "003714377140"
;   "003721291126 " "003721291126"
;   "BASWARE (BAWCFI22)" "BAWCFI22"
;   "BAWCF122" "BAWCFI22"
;   "BasWare" "BAWCFI22"
;   "Basware" "BAWCFI22"
   "Basware Oyj" "BAWCFI22"
;   "CGI / 003703575029" "003703575029"
;   "Danske Bank" "DNBAFIHX"
;   "Enfo" "003714377140"
;   "Enfo Oyj" "003714377140"
;   "Enfo Oyj 003714377140" "003714377140"
;   "Enfo Zender Oy" "003714377140"
;   "Enfo Zender Oy / 003714377140" "003714377140"
;   "Liaison" "003708599126"
;   "Logica" "003703575029"
;   "Nordea (NDEAFIHH)" "NDEAFIHH"
;   "OKOYFIHH " "OKOYFIHH"
;   "Opus Capita Group Oy" "003710948874"
   "OpusCapita Group Oy " "003710948874"
;   "OpusCapita Group Oy" "003710948874"
;   "OpusCapita Group Oy  003710948874" "003710948874"
;   "Tieto Oyj" "003701011385"
;   "dabafihh" "DABAFIHH"
;   "enfo" "003714377140"
;   "logica 00370357502" "003703575029"
   "logica, 00370357502" "003703575029"
;   "003701011385 OKOYFIHH" "OKOYFIHH"
;   "003715482348" "OKOYFIHH"
   })

(defmigration convert-invoicing-operator-values-from-documents-v2
  (let [old-op-names (keys invoicing-operator-mapping)
        path         [:data :yritys :verkkolaskutustieto :valittajaTunnus :value]
        query        (->> (cons :documents path)
                          (map name)
                          (clojure.string/join "."))]
    (update-applications-array
      :documents
      (fn [doc]
        (if-let [current-val (get-in doc path)]
          (let [replace-val (get invoicing-operator-mapping current-val)]
            (assoc-in doc path replace-val))
          doc))
      {query {$in old-op-names}})))

; To find current unmapped operator values
(comment
  (let [cur-vals (mongo/distinct :applications "documents.data.yritys.verkkolaskutustieto.valittajaTunnus.value")]
    (remove (fn [val] (some #(= val %) (map :name lupapalvelu.document.schemas/e-invoice-operators))) cur-vals)))

(defmigration add-permanent-archive-property-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:permanent-archive-enabled {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:permanent-archive-enabled {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
      {$set {:permanent-archive-enabled false}})))

(defmigration unset-company-address2
  {:apply-when (pos? (mongo/count :companies {:address2 {$exists true}}))}
  (mongo/update-n :companies {:address2 {$exists true}} {$unset {:address2 1}} :multi true))

(defmigration tutkinto-mapping-in-own-info
  (let [target-keys-set (conj (set (map :name (:body lupapalvelu.document.schemas/koulutusvalinta))) "other")
        mapping (er/read-map "tutkinto-mapping.xlsx")]
    (doseq [user (mongo/select :users {"degree" {$exists true}} {:degree 1})]
      (when-let [koulutus (:degree user)]
        (let [normalized (-> koulutus ss/trim ss/lower-case)]
          (when-not (or (ss/blank? normalized) (target-keys-set normalized))
            (let [mapped (get mapping normalized "")]
              (debugf "%s/%s: Mapping '%s' to %s" :users (:id user) koulutus mapped)
              (mongo/update-by-id :users (:id user) {$set {:degree mapped}}))))))))

(comment
  (let [cur-vals (mongo/distinct :users "degree")]
    (remove (fn [val] (some #(= val %) (map :name (:body lupapalvelu.document.schemas/koulutusvalinta)))) cur-vals)))

(defmigration separate-operations-to-primary-and-secondary-operations
  {:apply-when (or (pos? (mongo/count :applications {:operations {$exists true}})) (pos? (mongo/count :submitted-applications {:operations {$exists true}})))}
  (doseq [collection [:applications :submitted-applications]
          application (mongo/select collection {:operations {$exists true}} {:operations 1})
          :let [primaryOperation (-> application :operations first)
                secondaryOperations (-> application :operations rest)]]
    (mongo/update-by-id collection (:id application) {$set   {:primaryOperation    primaryOperation
                                                              :secondaryOperations secondaryOperations}
                                                      $unset {:operations 1}})))

(defmigration hakija-documents-to-hakija-r
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "hakija" (:name schema-info))
        (assoc-in doc [:schema-info :name] "hakija-r")
        doc))
    {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hakija"}}}]}))

(defmigration add-subtype-for-maksaja-documents
  {:apply-when (or (pos? (mongo/count :applications {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}}))
                   (pos? (mongo/count :submitted-applications {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (and (= "maksaja" (:name schema-info)) (ss/blank? (:subtype schema-info)))
        (assoc-in doc [:schema-info :subtype] "maksaja")
        doc))
    {"documents" {$elemMatch {"schema-info.name" "maksaja", "schema-info.subtype" {$exists false}}}}))

(defmigration convert-statement-values
  {:apply-when (pos? (mongo/count :applications {"statements.status" {"$in" ["yes" "no" "condition"]}}))}
  (update-applications-array
      :statements
      (fn [{status :status :as statement}]
        (assoc statement :status (case status
                                   "yes"       "puoltaa"
                                   "no"        "ei-puolla"
                                   "condition" "ehdoilla"
                                   status)))
      {"statements.status" {"$in" ["yes" "no" "condition"]}}))

(defmigration location-to-array
  {:apply-when (pos? (mongo/count :applications {:location {$type 3}}))}
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection {} {:location 1})]
            (let [{:keys [x y]} (:location application)]
              (mongo/update-n collection {:_id (:id application)} {$set {:location [x y]}})))))

(defn- rakennustunnus [property-id building-number]
  (let [parts (rest (re-matches p/db-property-id-pattern property-id))]
    (apply format "%s-%s-%s-%s %s" (concat parts [building-number]))))

(defn- resolve-new-national-id? [building-number national-id]
  (and (= 3 (count building-number)) (ss/blank? national-id)))

(defn- find-national-id [conversion-table application-id property-id building-number]
  (let [lookup (rakennustunnus property-id building-number)
        new-id (mongo/select-one conversion-table {:RAKENNUSTUNNUS lookup})]
    (if (v/rakennustunnus? (:VTJ_PRT new-id))
      new-id
      (println application-id lookup new-id))))

(defn- building-raki-conversion [conversion-table application-id building]
  (let [old-id (:buildingId building)
        national-id (:nationalId building)
        property-id (:propertyId building)]
    (if (and (resolve-new-national-id? old-id national-id) (not (.endsWith property-id "00000000")))
      (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
        (assoc building
          :buildingId (:VTJ_PRT new-id)
          :nationalId (:VTJ_PRT new-id)
          :localId (:KUNNAN_PYSYVA_RAKNRO new-id))
        building)
      building)))

(defn- document-raki-conversion [conversion-table application-id property-id doc]
  (let [old-id (get-in doc [:data :buildingId :value])
        national-id (get-in doc [:data :valtakunnallinenNumero :value])]
    (if (resolve-new-national-id? old-id national-id)
      (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
        (-> doc
          (assoc-in [:data :buildingId :value] (:VTJ_PRT new-id))
          (assoc-in [:data :valtakunnallinenNumero :value] (:VTJ_PRT new-id)))
        doc)
      doc)))

(defn- task-raki-conversion [conversion-table application-id property-id task]
  (if (empty? (get-in task [:data :rakennus]))
    task
    (update-in task [:data :rakennus]
     #(reduce
        (fn [m [k v]]
          (assoc m k
            (let [old-id (get-in v [:rakennus :rakennusnro :value])
                  national-id (get-in v [:rakennus :valtakunnallinenNumero :value])]
              (if (resolve-new-national-id? old-id national-id) ; task has old style short id but no national id
                (if-let [new-id (find-national-id conversion-table application-id property-id old-id)]
                  (assoc-in v [:rakennus :valtakunnallinenNumero :value] (:VTJ_PRT new-id))
                  v)
                v)
              )))
        {} %))))

(defn- raki-conversion [municipality conversion-table]
  {:pre [(string? municipality) (string? conversion-table)]}
  (let [query {$and [{:municipality municipality}
                     {$or [{"buildings.0" {$exists true}}
                           {"documents.data.buildingId" {$exists true}}
                           {"tasks.data.rakennus" {$exists true}}]}]}]
    (doseq [collection [:applications :submitted-applications]
            {:keys [id buildings documents tasks propertyId]} (mongo/select collection query)]
      (mongo/update-by-id collection id
        {$set {:buildings (map (partial building-raki-conversion conversion-table id) buildings)
               :documents (map (partial document-raki-conversion conversion-table id propertyId) documents)
               :tasks (map (partial task-raki-conversion conversion-table id propertyId) tasks)}}))))

(defmigration jarvenpaa-raki (raki-conversion "186" "jarvenpaa_raki"))


(defmigration ilmoitusHakemusValitsin-permitSubtype
  (reduce + 0
    (for [collection [:applications :submitted-applications]
          application (mongo/select collection
                        {$or [{:primaryOperation.name "tyonjohtajan-nimeaminen-v2"}
                              {:primaryOperation nil, :documents.schema-info.name "tyonjohtaja-v2"}]}
                        {:documents 1})
          :let [doc (domain/get-document-by-name application "tyonjohtaja-v2")
                val (-> doc :data :ilmoitusHakemusValitsin :value)
                subtype (if (= "ilmoitus" val)
                          :tyonjohtaja-ilmoitus
                          :tyonjohtaja-hakemus)]]
      (mongo/update-n collection
        {:_id (:id application), :documents {$elemMatch {:id (:id doc)}}}
        {$set {:permitSubtype subtype}
         $unset {:documents.$.data.ilmoitusHakemusValitsin 1}}))))

(defn- add-approver-role-for-authority-in-org
  [[org org-roles]]
  [org (if (some (partial = "authority") org-roles)
         (conj org-roles "approver")
         org-roles)])

(defn- add-approver-roles-for-authority
  [{org-authz :orgAuthz :as user}]
  (->> org-authz
       (map add-approver-role-for-authority-in-org)
       (into {})
       (assoc user :orgAuthz)))

(defmigration approver-roles-for-authorities
  (doseq [authority (mongo/select :users {:role "authority" :orgAuthz {$exists true}})]
    (mongo/update-by-id :users (:id authority) (add-approver-roles-for-authority authority))))

(defmigration rip-rakennus-schema-part-from-ya-katselmukset
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {:schema-info.name "task-katselmus"}}}))}
  (update-applications-array
    :tasks
    (fn [task]
      (if (= "task-katselmus" (-> task :schema-info :name))
        (-> task
          (dissoc-in [:data :rakennus])
          (assoc-in [:schema-info :name] "task-katselmus-ya"))
        task))
    {:permitType "YA"
     :tasks {$elemMatch {:schema-info.name "task-katselmus"}}}))

(defn extract-credentials-from-krysp-address
  [[permit-type {url :url :as krysp-config}]]
  (when url
    (let [[_ protocol username password url] (re-matches #"(https?://)([^:]*:)?([^@]*@)?(.*$)" url)
          username (apply str (butlast username))
          password (apply str (butlast password))]
      (when (and url (not (empty? username)) (not (empty? password)))
        [permit-type (merge krysp-config {:url (str protocol url) :username username :password password})]))))

(defn update-krysp-config
  [{organization-id :id} permit-type {url :url username :username password :password version :version}]
  (when (and username password)
    (organization/set-krysp-endpoint organization-id url username password (name permit-type) version)))

(defn move-credentials-out-from-organization-krysp-addresses
  [{krysp-configs :krysp :as organization}]
  (doseq [[permit-type config] (map extract-credentials-from-krysp-address krysp-configs)]
    (update-krysp-config organization permit-type config)))

(defmigration move-credentials-out-from-krysp-addresses
  (doseq [organization (mongo/select :organizations)]
    (move-credentials-out-from-organization-krysp-addresses organization)))

(defmigration foreman-index-v2
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {} [:documents])]
        (count (map #(mongo/update-by-id collection (:id %) (app-meta-fields/foreman-index-update %)) applications))))))

; 2015-10-14:
;> db.applications.distinct("state", {"permitSubtype": "tyonjohtaja-hakemus"})
;[
;        "canceled",
;        "verdictGiven",
;        "draft",
;        "open",
;        "submitted",
;        "answered",
;        "sent",
;        "complement-needed"
;]

(defmigration tyonjohtaja-hakemus-verdict-given-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-hakemus", :state "verdictGiven"}))}
  (mongo/update-by-query :applications {:permitSubtype "tyonjohtaja-hakemus", :state "verdictGiven"} {$set {:state :foremanVerdictGiven}}))


; 2015-10-14:
;> db.applications.distinct("state", {"permitSubtype": "tyonjohtaja-ilmoitus"})
;[
;        "closed",
;        "verdictGiven",
;        "canceled",
;        "draft",
;        "open",
;        "submitted",
;        "complement-needed"
;]

(defmigration tyonjohtaja-ilmoitus-closed-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "closed"}))}
  (reduce + 0
    (for [{:keys [id closed]} (mongo/select :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "closed"} [:closed])]
      (mongo/update-by-query :applications {:_id id} {$set {:state :acknowledged, :acknowledged closed}, $unset {:closed 1}}))))

(defmigration tyonjohtaja-ilmoitus-verdict-given-mapping
  {:apply-when (pos? (mongo/count :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "verdictGiven"}))}
  (reduce + 0
    (for [{:keys [id verdicts]} (mongo/select :applications {:permitSubtype "tyonjohtaja-ilmoitus", :state "verdictGiven"} [:verdicts])
          :let [timestamp (->> (map :timestamp verdicts) (filter number?) (apply min))]]
      (mongo/update-by-query :applications {:_id id} {$set {:state :acknowledged, :acknowledged timestamp}}))))

(defn pena?
  "Pena has been deleted from prod db"
  [user] (= "777777777777777777000020" (:id user)))

(defn init-application-history [{:keys [created opened infoRequest convertedToApplication permitSubtype] :as application}]
  (let [owner-auth (first (auth/get-auths-by-role application :owner))
        owner-user (user/find-user (select-keys owner-auth [:id]))
        creator (cond
                  (= permitSubtype "muutoslupa") (:user (mongo/by-id :muutoslupa created))
                  owner-user owner-user
                  (pena? owner-auth) (merge owner-auth {:role "applicant" :firstName "Testaaja" :lastName "Solita"}))

        _ (assert (:id creator) (:id application))

        state (if (= permitSubtype "muutoslupa")
                (if (user/authority? creator) "open" "draft")
                (cond
                  (or infoRequest convertedToApplication)  "info"
                  (= opened created) "open"
                  (and (ss/blank? (:personId creator)) (user/authority? creator)) "open"
                  :else "draft"))]
    {$set {:history [{:state state, :ts created, :user (user/summary creator)}]}}))

(defmigration init-history
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:history.0 {$exists false}} [:created :opened :auth :infoRequest :convertedToApplication :permitSubtype])]
        (count (map #(mongo/update-by-id collection (:id %) (init-application-history %)) applications))))))

(defmigration complement-needed-camelcase
  (mongo/update-by-query :applications {:state "complement-needed"} {$set {:state "complementNeeded"}}))

(defn change-valid-pdfa-to-archivable [version]
  {:post [%]}
  (if (contains? version :valid-pdfa)
    (let [valid-pdfa? (boolean (:valid-pdfa version))]
      (-> (assoc version :archivable valid-pdfa? :archivabilityError (when-not valid-pdfa? :invalid-pdfa))
          (dissoc :valid-pdfa)))
    version))

(defn update-valid-pdfa-to-arhivable-on-attachment-versions [attachment]
  {:post [%]}
  (if (:latestVersion attachment)
    (-> (assoc attachment :versions (map change-valid-pdfa-to-archivable (:versions attachment)))
        (assoc :latestVersion (change-valid-pdfa-to-archivable (:latestVersion attachment))))
    attachment))

(defmigration set-general-archivability-boolean-v2
  {:apply-when (pos? (mongo/count :applications {"attachments.versions.valid-pdfa" {$exists true}}))}
  (update-applications-array
    :attachments
    update-valid-pdfa-to-arhivable-on-attachment-versions
    {"attachments.versions.valid-pdfa" {$exists true}}))

(defn update-document-tila-metadata [doc]
  (if-let [tila (get-in doc [:metadata :tila])]
    (let [new-tila (if (.equalsIgnoreCase "valmis" tila) :valmis :luonnos)]
      (assoc-in doc [:metadata :tila] new-tila))
    doc))

(defn update-array-metadata [application]
  (->> [:attachments :verdicts :statements]
       (map (fn [k] (if-let [docs (seq (k application))]
                      [k (map update-document-tila-metadata docs)]
                      nil)))
       (remove nil?)
       (into {})))

(defmigration update-tila-metadata-value-in-all-metadata-maps
  {:apply-when (pos? (mongo/count :applications {$and [{"metadata.tila" {$exists true}} {"metadata.tila" {$nin ["luonnos" "valmis" "arkistoitu" "arkistoidaan"]}}]}))}
  (doseq [application (mongo/select :applications {$and [{"metadata.tila" {$exists true}} {"metadata.tila" {$nin ["luonnos" "valmis" "arkistoitu" "arkistoidaan"]}}]})]
    (let [data-for-$set (-> (update-array-metadata application)
                            (merge {:metadata (:metadata (update-document-tila-metadata application))}))]
      (mongo/update-n :applications {:_id (:id application)} {$set data-for-$set}))))

(defmigration r-application-hankkeen-kuvaus-documents-to-hankkeen-kuvaus-rakennuslupa-v2
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]})))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "hankkeen-kuvaus" (:name schema-info))
        (assoc-in doc [:schema-info :name] "hankkeen-kuvaus-rakennuslupa")
        doc))
    {$and [{:permitType "R"} {:documents {$elemMatch {"schema-info.name" "hankkeen-kuvaus"}}}]}))

(defmigration validate-verdict-given-date
  {:apply-when (pos? (mongo/count :organizations {:validate-verdict-given-date {$exists false}}))}
  (mongo/update-n :organizations {} {$set {:validate-verdict-given-date true}} :multi true))

(defmigration set-validate-verdict-given-date-in-helsinki
  (mongo/update-n :organizations {:_id "091-R"} {$set {:validate-verdict-given-date false}}))

(defmigration reset-pdf2pdf-page-counter-to-zero
  {:apply-when (pos? (mongo/count :statistics {$and [{:type "pdfa-conversion"} {"years.2015" {$exists true}}]}))}
  (mongo/update :statistics {:type "pdfa-conversion"} {$unset {"years.2015" ""}}))

(defmigration ya-katselmukset-remove-tila-and-convert-katselmuksenLaji
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                                                                           {$or [{"data.tila" {$exists true}}
                                                                                 {"data.katselmuksenLaji.value" {$exists true
                                                                                                                 $nin ["Aloituskatselmus"
                                                                                                                       "Loppukatselmus"
                                                                                                                       "Muu valvontak\u00e4ynti"
                                                                                                                       ""]}}]}]}}}))}
  (update-applications-array
    :tasks
    (fn [task]
      (if (= "task-katselmus-ya" (-> task :schema-info :name))
        (let [new-task (-> task
                         (dissoc-in [:data :tila])  ;; cannot be transferred in YA Krysp
                         (#(if-not (-> % :data :katselmuksenLaji :value ((fn [laji]
                                                                           (or
                                                                             (ss/blank? laji)
                                                                             (#{"Aloituskatselmus" "Loppukatselmus" "Muu valvontak\u00e4ynti"} laji)))))
                             (update-in % [:data :katselmuksenLaji :value] (fn [old-katselmuksenLaji]
                                                                             (case old-katselmuksenLaji
                                                                               "aloituskokous" "Aloituskatselmus"
                                                                               "loppukatselmus" "Loppukatselmus"
                                                                               "Muu valvontak\u00e4ynti")))
                             %)))]
          new-task)
        task))
    {:permitType "YA"
     :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                               {$or [{"data.tila" {$exists true}}
                                     {"data.katselmuksenLaji.value" {$exists true
                                                                     $nin ["Aloituskatselmus"
                                                                           "Loppukatselmus"
                                                                           "Muu valvontak\u00e4ynti"
                                                                           ""]}}]}]}}}))


(defmigration ya-katselmukset-fix-remove-tila
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                                                                           {"data.katselmus.tila" {$exists true}}]}}}))}
  (update-applications-array :tasks
    (fn [task]
      (if (= "task-katselmus-ya" (-> task :schema-info :name))
        (dissoc-in task [:data :katselmus :tila])
        task))
    {:permitType "YA"
     :tasks {$elemMatch {$and [{"schema-info.name" "task-katselmus-ya"}
                               {"data.katselmus.tila" {$exists true}}]}}}))

(defn update-statement-state-by-status [{status :status state :state :as statement}]
  (if state
    statement
    (assoc statement :state (if status :given :requested))))

(defmigration statement-state
  {:apply-when (pos? (mongo/count :applications {:statements {$elemMatch {"state" {$exists false}}}}))}
  (update-applications-array :statements
                             update-statement-state-by-status
                             {:statements {$elemMatch {"state" {$exists false}}}}))


;; BSON type 8 == Boolean (https://docs.mongodb.org/manual/reference/operator/query/type/)
(defmigration convert-attachments-requestedByAuthority-to-boolean
  {:apply-when (pos? (mongo/count :applications {:attachments {$elemMatch {$and [{"requestedByAuthority" {$exists true}}
                                                                                 {"requestedByAuthority" {$not {$type 8}}}]}}}))}
  (update-applications-array :attachments
    (fn [attachment]
      (if-not (boolean? (:requestedByAuthority attachment))
        (update attachment :requestedByAuthority boolean)
        attachment))
    {:attachments {$elemMatch {$and [{"requestedByAuthority" {$exists true}}
                                     {"requestedByAuthority" {$not {$type 8}}}]}}}))


(def maisematyo-operations ["muu-tontti-tai-kort-muutos" "kortteli-yht-alue-muutos"
                            "muu-maisema-toimenpide" "paikoutysjarjestus-muutos"
                            "rak-valm-tyo" "puun-kaataminen" "kaivuu"
                            "tontin-jarjestelymuutos" "tontin-ajoliittyman-muutos"])

(defmigration maisematyo-ilman-hankeilmoitusta
  {:apply-when (pos? (mongo/count :applications {$and [{:primaryOperation.name {$in maisematyo-operations}} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "rakennuspaikka" (:name schema-info))
        (-> doc
          (assoc-in [:schema-info :name] "rakennuspaikka-ilman-ilmoitusta")
          (update :data dissoc :hankkeestaIlmoitettu))
        doc))
    {$and [{:primaryOperation.name {$in maisematyo-operations}} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration municipality-map-server-password-encrypted
  {:apply-when (pos? (mongo/count :organizations {:map-layers.server.crypto-iv {$exists false}
                                                  :map-layers.server.password {$nin [nil ""]}}))}
  (doseq [org (mongo/select :organizations {:map-layers.server.crypto-iv {$exists false}
                                            :map-layers.server.password {$nin [nil ""]}})
          :let [{:keys [server]} (:map-layers org)
                {:keys [url username password]} server]]
    (organization/update-organization-map-server (:id org) url username password)))

(defmigration poikkarit-ilman-hankeilmoitusta
  {:apply-when (pos? (mongo/count :applications {:permitType "P", :documents.data.hankkeestaIlmoitettu {$exists true}}))}
  (update-applications-array
    :documents
    (fn [{schema-info :schema-info :as doc}]
      (if (= "poikkeusasian-rakennuspaikka" (:name schema-info))
        (update doc :data dissoc :hankkeestaIlmoitettu)
        doc))
    {:permitType "P", :documents.data.hankkeestaIlmoitettu {$exists true}}))

(defmigration generate-attachment-auths
  (update-applications-array
    :attachments
    (fn [{versions :versions :as attachment}]
      (assoc attachment :auth (distinct (map attaccess/auth-from-version versions))))
    {:attachments.0 {$exists true}}))

(defn add-missing-person-data-to-statement [statement]
  (let [user-id (-> (get-in statement [:person :email] "")
                    user/get-user-by-email
                    :id)]
    (->> (:person statement)
         (merge {:userId (or user-id "") :email "" :name "" :text ""})
         (assoc statement :person))))

(defmigration statement-person-userId
  {:apply-when (pos? (mongo/count :applications {:statements {$elemMatch {:person.email {$exists true},
                                                                          :person.userId {$exists false}}}}))}
  (update-applications-array :statements
                             add-missing-person-data-to-statement
                             {:statements {$elemMatch {:person.email {$exists true},
                                                       :person.userId {$exists false}}}}))

(defn convert-coordinates [application]
  (let [location       (:location application)
        location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 location)]
    {$set {:location-wgs84 location-wgs84}}))

(defmigration add-wgs84-location-for-applications
  {:apply-when (pos? (mongo/count :applications {:location-wgs84 {$exists false}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:location-wgs84 {$exists false}})]
        (count (map #(mongo/update-by-id collection (:id %) (convert-coordinates %)) applications))))))


(defmigration cleanup-numeric-history-state
  {:apply-when (pos? (mongo/count :applications {"history.state" {$type 18}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-n :applications {"history.state" {$type 18}} {$pull {:history {:state {$type 18}}}} :multi true))))

(defmigration cleanup-construction-started-history-state
  {:apply-when (pos? (mongo/count :applications {"history.state" "constructionStarted"}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-n :applications {"history.state" "constructionStarted"} {$pull {:history {:state "constructionStarted"}}} :multi true))))

(defn populate-application-history [{:keys [opened submitted sent canceled started complementNeeded closed startedBy closedBy history verdicts] :as application}]
  (let [user-summary (fn [user] (when (seq user) (user/summary user)))
        history-states (set (map :state history))
        verdict-state (sm/verdict-given-state application)
        verdict-ts  (:timestamp (->>  verdicts (remove :draft) (sort-by :timestamp) first))
        all-entries [(when (and opened (not (history-states "open"))) {:state :open, :ts opened, :user nil})
                     (when (and submitted(not (history-states "submitted"))) {:state :submitted, :ts submitted, :user nil})
                     (when (and sent (not (history-states "sent"))) {:state :sent, :ts sent, :user nil})
                     (when (and complementNeeded (not (history-states "complementNeeded"))) {:state :complementNeeded, :ts complementNeeded, :user nil})
                     (when (and verdict-ts (not (history-states verdict-state))) {:state verdict-state, :ts verdict-ts, :user nil})
                     (when (and started (not (history-states "constructionStarted"))) {:state :constructionStarted, :ts started, :user (user-summary startedBy)})
                     (when (and closed (not (history-states "closed"))) {:state :closed, :ts closed, :user (user-summary closedBy)})
                     (when (and canceled (not (history-states "canceled"))) {:state :canceled, :ts canceled, :user nil})]
        new-entries (remove nil? all-entries)]
    {$push {:history {$each new-entries}}}))

(defmigration populate-history-v2
  {:apply-when (pos? (mongo/count :applications {:history.1 {$exists false}, :state {$nin ["draft", "open", "canceled"]}, :infoRequest false}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:state {$ne "draft"}, :infoRequest false} [:opened :sent :submitted :canceled :complementNeeded :started :closed :startedBy :closedBy :history :verdicts :permitType :primaryOperation :state])]
        (count (map #(mongo/update-by-id collection (:id %) (populate-application-history %)) applications))))))

(defn user-summary [{email :email id :id first-name :firstName last-name :lastName :as user}]
  (user/summary
   (if (and (= id "-") (= first-name "Lupapiste"))
     (assoc user :username "eraajo@lupapiste.fi")
     user)))

(defn remove-unwanted-fields-from-attachment-auth [attachment]
  (update attachment :auth (partial mapv user-summary)))

(defmigration cleanup-attachment-auth-user-summary-v2
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                         :username {$exists false}}}}}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                         :username {$exists false}}}}}}]})))}
  (update-applications-array :attachments
                             remove-unwanted-fields-from-attachment-auth
                             {$or [{:attachments {$elemMatch {:auth {$elemMatch {:email    {$exists true}}}}}}
                                   {:attachments {$elemMatch {:auth {$elemMatch {:id       {$exists true}
                                                                                 :username {$exists false}}}}}}]}))

(defn remove-unwanted-fields-from-attachment-versions-user [attachment]
  (update attachment :versions (partial mapv (fn [v] (update v :user user-summary)))))

(defn remove-unwanted-fields-from-attachment-latestVersion-user [attachment]
  (if (:latestVersion attachment)
    (update-in attachment [:latestVersion :user] user-summary)
    attachment))

(defn remove-unwanted-fields-from-attachment-versions-user-and-latestVersion-user [attachment]
  (-> attachment
      remove-unwanted-fields-from-attachment-versions-user
      remove-unwanted-fields-from-attachment-latestVersion-user))

(defmigration cleanup-attachment-versions-user-summary-v2
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                             :user.username {$exists false}}}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                                      :latestVersion.user.username {$exists false}}}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                           {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                             :user.username {$exists false}}}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                           {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                                      :latestVersion.user.username {$exists false}}}}]})))}
  (update-applications-array :attachments
                             remove-unwanted-fields-from-attachment-versions-user-and-latestVersion-user
                             {$or [{:attachments {$elemMatch {:versions {$elemMatch {:user.email    {$exists true}}}}}}
                                   {:attachments {$elemMatch {:versions {$elemMatch {:user.id       {$exists true}
                                                                                     :user.username {$exists false}}}}}}
                                   {:attachments {$elemMatch {:latestVersion.user.email    {$exists true}}}}
                                   {:attachments {$elemMatch {:latestVersion.user.id       {$exists true}
                                                              :latestVersion.user.username {$exists false}}}}]}))

(defn merge-required-fields-into-attachment [attachment]
  (merge {:locked               false
          :applicationState     "draft"
          :target               nil
          :requestedByAuthority false
          :notNeeded            false
          :op                   nil
          :signatures           []
          :auth                 []}
         attachment))

(defmigration add-required-fields-into-attachments-v2
  {:apply-when (pos? (mongo/count :applications {$or [{:attachments {$elemMatch {:locked               {$exists false}}}}
                                                      {:attachments {$elemMatch {:applicationState     {$exists false}}}}
                                                      {:attachments {$elemMatch {:target               {$exists false}}}}
                                                      {:attachments {$elemMatch {:requestedByAuthority {$exists false}}}}
                                                      {:attachments {$elemMatch {:notNeeded            {$exists false}}}}
                                                      {:attachments {$elemMatch {:op                   {$exists false}}}}
                                                      {:attachments {$elemMatch {:signatures           {$exists false}}}}
                                                      {:attachments {$elemMatch {:auth                 {$exists false}}}}]}))}
  (update-applications-array :attachments
                             merge-required-fields-into-attachment
                             {$or [{:attachments {$elemMatch {:locked               {$exists false}}}}
                                   {:attachments {$elemMatch {:applicationState     {$exists false}}}}
                                   {:attachments {$elemMatch {:target               {$exists false}}}}
                                   {:attachments {$elemMatch {:requestedByAuthority {$exists false}}}}
                                   {:attachments {$elemMatch {:notNeeded            {$exists false}}}}
                                   {:attachments {$elemMatch {:op                   {$exists false}}}}
                                   {:attachments {$elemMatch {:signatures           {$exists false}}}}
                                   {:attachments {$elemMatch {:auth                 {$exists false}}}}]}))

(defn applicationState-as-camelCase [attachment]
  (if (= (:applicationState attachment) "complement-needed")
    (assoc attachment :applicationState "complementNeeded")
    attachment))

(defmigration attachment-applicationState-as-camelCase
  {:apply-when (pos? (+ (mongo/count :applications {:attachments.applicationState "complement-needed"})
                        (mongo/count :submitted-applications {:attachments.applicationState "complement-needed"})))}
  (update-applications-array :attachments
                             applicationState-as-camelCase
                             {:attachments.applicationState "complement-needed"}))

(defn set-target-timestamps-as-nil [{target :target :as attachment}]
  (if (number? target)
    (assoc attachment :target nil)
    attachment))

(defmigration cleanup-attachment-target-with-timestamp-as-value
  {:apply-when (pos? (+ (mongo/count :applications {:attachments {$elemMatch {:target {$type 18}}}})
                        (mongo/count :submitted-applications {:attachments {$elemMatch {:target {$type 18}}}})))}
  (update-applications-array :attachments
                             set-target-timestamps-as-nil
                             {:attachments {$elemMatch {:target {$type 18}}}}))

(defn set-target-with-nil-valued-map-as-nil [{target :target :as attachment}]
  (if (or (every? nil? (vals target)) (every? (partial = "undefined") (vals target)))
    (assoc attachment :target nil)
    attachment))

(defmigration cleanup-attachment-target-nil-valued-maps
  (update-applications-array :attachments
                             set-target-with-nil-valued-map-as-nil
                             {$or [{:attachments {$elemMatch {:target.type {$type 10}}}}
                                   {:attachments {$elemMatch {:target.type "undefined"}}}]}))

(defn set-verdict-id-for-nil-valued-verdict-targets [verdict-id {{target-id :id target-type :type} :target :as attachment}]
  (if (and (nil? target-id) (= "verdict" target-type))
    (assoc-in attachment [:target :id] verdict-id)
    attachment))

(defn update-verdict-id-in-attachment-target [query]
  (reduce + 0
    (for [collection [:applications :submitted-applications]
          {attachments :attachments verdicts :verdicts app-id :id :as a} (mongo/select collection query {:verdicts 1 :attachments 1})]
      (mongo/update-n collection
                      {:_id app-id}
                      {$set {:attachments (map (partial set-verdict-id-for-nil-valued-verdict-targets (:id (first verdicts))) attachments)}}))))

(defmigration update-attachment-target-verdict-id-when-nil
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                      :verdicts {$size 1}})
                        (mongo/count :submitted-applications
                                     {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                      :verdicts {$size 1}})))}
  (update-verdict-id-in-attachment-target {:attachments {$elemMatch {:target.id   {$type 10}
                                                                :target.type "verdict"}}
                                           :verdicts {$size 1}}))

(defn remove-attachment-op-operation-type [attachment]
  (update attachment :op dissoc :operation-type))

(defmigration remove-operation-type-from-attachment-op
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments {$elemMatch {:op.operation-type {$exists true}}}})
                        (mongo/count :submitted-applications
                                     {:attachments {$elemMatch {:op.operation-type {$exists true}}}})))}
  (update-applications-array :attachments
                             remove-attachment-op-operation-type
                             {:attachments {$elemMatch {:op.operation-type {$exists true}}}}))

(defn remove-attachment-versions-accepted [attachment]
  (update attachment :versions (partial map #(dissoc % :accepted))))

(defn remove-attachment-latestVersion-accepted [{latest-version :latestVersion :as attachment}]
  (if latest-version
    (update attachment :latestVersion dissoc :accepted)
    attachment))

(defn remove-accepted-field-from-attachment-versions-and-latest-version [attachment]
  (-> attachment
      remove-attachment-versions-accepted
      remove-attachment-latestVersion-accepted))

(defmigration remove-accepted-from-attachment-versions
  {:apply-when (pos? (+ (mongo/count :applications
                                     {$or [{:attachments.versions.accepted {$exists true}}
                                           {:attachments.latestVersion.accepted {$exists true}}]})
                        (mongo/count :submitted-applications
                                     {$or [{:attachments.versions.accepted {$exists true}}
                                           {:attachments.latestVersion.accepted {$exists true}}]})))}
  (update-applications-array :attachments
                             remove-accepted-field-from-attachment-versions-and-latest-version
                             {$or [{:attachments.versions.accepted {$exists true}}
                                   {:attachments.latestVersion.accepted {$exists true}}]}))

(defn pull-auth-update [{:keys [id role]}]
  {$pull {:auth {:role role :id id}}})

(defn remove-owners-double-auth-updates [auths]
  (let [{owner-id :id :as owner-auth} (some
                                        #(when (= (:role %) "owner") %)
                                        auths)]
    (when-let [removable-auths (seq
                                 (filter
                                   #(and
                                     (= (:id %) owner-id)
                                     (or (= (:role %) "writer") (= (:role %) "reader")))
                                   auths))]
      (map pull-auth-update removable-auths))))

(defmigration double-auths-in-foreman-applications ; LPK-1331
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection
                                          {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                           :auth.2 {$exists true} ; >= 3 auths
                                           :created {$gt 1444780800000}} ; since version 1.108 14.10.2015
                                          [:auth])]
            (if-let [updates (remove-owners-double-auth-updates (:auth application))]
              (do
                (doseq [update-clause updates]
                  (mongo/update-by-id collection (:id application) update-clause))
                1) ; one application updated
              0))))

(defmigration duplicate-comments-cleanup
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                application (mongo/select collection
                                          {:attachments.versions.archivable true ; for applications that have pdf/a checked attachments
                                           :created {$gt 1433462400000}}
                                          [:comments])]
            (let [comments (:comments application)
                  distinct-comments (distinct comments)]
              (if (< (count distinct-comments) (count comments))
                (mongo/update-n collection {:_id (:id application)} {$set {:comments distinct-comments}})
                0)))))

(defn set-signature-fileId [versions {version :version created :created :as signature}]
  (->> (filter #(and (= (:version %) version) (< (:created %) created)) versions)
       first
       :fileId
       (assoc signature :fileId)))

(defn add-fileId-for-signatures [{signatures :signatures versions :versions :as attachment}]
  (->> (map (partial set-signature-fileId versions) signatures)
       (filter :fileId)
       (assoc attachment :signatures)))

(defmigration add-fileId-for-attachment-signatures
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}})
                        (mongo/count :submitted-applications
                                     {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}})))}
  (update-applications-array :attachments
                             add-fileId-for-signatures
                             {:attachments.signatures {$elemMatch {:version {$exists true} :fileId {$exists false}}}}))

;; Cleanup QA attachments
(defmigration rename-not-needed
  {:apply-when (pos? (mongo/count :applications {:attachments.not-needed {$exists true}}))}
  (update-applications-array :attachments
    #(-> % (assoc :notNeeded (:not-needed %)) (dissoc :not-needed))
    {:attachments.not-needed {$exists true}}))

;; Cleanup QA attachments
(defmigration rename-requested-by-authority
  {:apply-when (pos? (mongo/count :applications {:attachments.requested-by-authority {$exists true}}))}
  (update-applications-array :attachments
    #(-> % (assoc :requestedByAuthority (:requested-by-authority %)) (dissoc :requested-by-authority))
    {:attachments.requested-by-authority {$exists true}}))

(defn guest-authority? [roles]
  (contains? (set roles) "guestAuthority"))

(defn remove-guest-authorities [org-auths]
  (reduce (fn [acc [k v]]
            (assoc acc k (remove #(= "guestAuthority" %) v))) {} org-auths))

(defmigration no-more-guest-authority-org-authz
  (let [users (->> (mongo/select :users {:orgAuthz {$exists true}})
                   (filter (fn [u] (some guest-authority? (vals (:orgAuthz u))))))]
    (doseq [{id :id auths :orgAuthz} users]
      (mongo/update-by-id :users id {$set {:orgAuthz (remove-guest-authorities auths)}}))))

(defn rename-kaupunkikuvatoimenpide-documents-with-op [operations-to-rename doc]
  (if (and (= "kaupunkikuvatoimenpide" (get-in doc [:schema-info :name]))
           (set operations-to-rename)  (get-in doc [:schema-info :op :name]))
    (update doc :schema-info assoc :name "kaupunkikuvatoimenpide-ei-tunnusta" :i18name "kaupunkikuvatoimenpide")
    doc))

(defmigration schemas-without-building-identifier []
  {:apply-when (pos? (+ (mongo/count :applications
                                     {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                              :schema-info.op.name {$in ["aita"]}}}})
                        (mongo/count :submitted-applications
                                     {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                              :schema-info.op.name {$in ["aita"]}}}})))}
  (update-applications-array :documents
                             (partial rename-kaupunkikuvatoimenpide-documents-with-op #{"aita"})
                             {:documents {$elemMatch {:schema-info.name "kaupunkikuvatoimenpide",
                                                      :schema-info.op.name {$in ["aita"]}}}}))

(defn add-original-file-id-for-version [{:keys [fileId originalFileId] :as version}]
  (assoc version :originalFileId (or originalFileId fileId)))

(defn add-original-file-id-for-versions-and-latestVersion [{latest-version :latestVersion :as attachment}]
  (if latest-version
    (-> attachment
        (update :versions (partial map add-original-file-id-for-version))
        (update :latestVersion add-original-file-id-for-version))
    attachment))

(defmigration add-original-file-id-for-attachment-versions
  {:apply-when (pos? (mongo/count :applications
                                  {:attachments.versions {$elemMatch {:fileId {$exists true}
                                                                      :originalFileId {$exists false}}}}))}
  (update-applications-array :attachments
                             add-original-file-id-for-versions-and-latestVersion
                             {:attachments.versions {$elemMatch {:fileId {$exists true}
                                                                 :originalFileId {$exists false}}}}))

#_(defmigration rename-hankkeen-kuvaus-rakennuslupa-back-to-hankkeen-kuvaus ;; TODO: migrate, LPK-1448
  {:apply-when (or (pos? (mongo/count :applications {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}}))
                   (pos? (mongo/count :submitted-applications {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}})))}
  (update-applications-array
    :documents
    (fn [{{name :name} :schema-info :as doc}]
      (if (= "hankkeen-kuvaus-rakennuslupa" (:name schema-info))
        (-> (update doc :data dissoc :hankkeenVaativuus)
            (assoc-in [:schema-info :name] "hankkeen-kuvaus"))
        doc))
    {:documents {"schema-info.name" "hankkeen-kuvaus-rakennuslupa"}}))

(defmigration init-designer-subtype
              (update-applications-array
                :documents
                (fn [doc]
                  (if (re-matches #".*suunnittelija" (str (get-in doc [:schema-info :name])))
                    (assoc-in doc [:schema-info :subtype] "suunnittelija")
                    doc))
                {"documents.schema-info.name" {$regex #".*suunnittelija"}}))

(defmigration init-designer-index
              (reduce + 0
                      (for [collection [:applications :submitted-applications]]
                        (let [applications (mongo/select collection {:documents.schema-info.subtype "suunnittelija"} {:documents 1})]
                          (count (map #(mongo/update-by-id collection (:id %) (app-meta-fields/designers-index-update %)) applications))))))

(defn update-attachment-type [mapping type-key attachment]
  (let [type (->> (type-key attachment) (map (fn [kv-pair] (mapv keyword kv-pair))) (into {}))]
    (assoc attachment type-key (get mapping type type))))

(defmigration application-attachment-type-update
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/attachment-mapping :type)
                             {:permitType  {$in ["R" "P"]}
                              :attachments {$gt {$size 0}}}))

(defmigration application-ya-osapuoli-attachment-type-update
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/osapuoli-attachment-mapping :type)
                             {:permitType  {$in ["YA"]}
                              :attachments {$gt {$size 0}}}))

(defmigration update-user-attachments
  (reduce (fn [cnt user] (+ cnt (mongo/update-n :users {:_id (:id user)}
                                                {$set {:attachments (->> (:attachments user)
                                                                         (map (partial update-attachment-type attachment-type-mapping/osapuoli-attachment-mapping :attachment-type)))}})))
          0
          (mongo/select :users {:attachments {$gt {$size 0}}} {:attachments 1})))

(def r-or-p-operation? (->> (filter (comp #{"R" "P"} :permit-type val) op/operations) keys set))
(def ya-operation? (->> op/ya-operations keys set))

(defn update-operations-attachment-type [mapping [type-group type-id]]
  (let [{new-group :type-group new-id :type-id} (-> {:type-group (keyword type-group) :type-id (keyword type-id)}
                                                    attachment-type-mapping/attachment-mapping)]
    (if (and new-group new-id)
      [new-group  new-id]
      [type-group type-id])))

(defn update-operations-attachments-types [mapping operation-pred [operation attachment-types]]
  (if (operation-pred (keyword operation))
    [operation (->> attachment-types (map (partial update-operations-attachment-type mapping)) distinct)]
    [operation attachment-types]))

(defmigration organization-operation-attachments-type-update
  (reduce (fn [cnt org] (+ cnt (mongo/update-n :organizations {:_id (:id org)}
                                               {$set {:operations-attachments (->> (:operations-attachments org)
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/attachment-mapping r-or-p-operation?))
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/osapuoli-attachment-mapping ya-operation?))
                                                                                   (into {}))}})))
          0
          (mongo/select :organizations {:operations-attachments {$gt {$size 0}}} {:operations-attachments 1})))

(defmigration add-id-for-verdicts-paatokset-v2
  (update-applications-array :verdicts
                             (fn [verdict] (update verdict :paatokset (fn [paatokset] (map #(if (:id %) % (assoc % :id (mongo/create-id))) paatokset))))
                             {:verdicts.paatokset {$elemMatch {:id {$exists false}, :poytakirjat {$exists true}}}}))

(defmigration clean-vaadittuTyonjohtajatieto-in-verdicts
  (update-applications-array :verdicts
                             (fn [verdict]
                               (update verdict :paatokset
                                          (fn [paatokset] (map (fn [paatos]
                                                                 (update-in paatos [:lupamaaraykset :vaadittuTyonjohtajatieto]
                                                                            (fn [tj] (cond (map? tj)    [(get-in tj [:VaadittuTyonjohtaja :tyonjohtajaLaji])]
                                                                                           (vector? tj) (map #(or (get-in % [:VaadittuTyonjohtaja :tyonjohtajaLaji]) %) tj)
                                                                                           :else        tj))))
                                                               paatokset))))
                             {:verdicts.paatokset.lupamaaraykset.vaadittuTyonjohtajatieto {$type 3}}))

(defmigration application-attachment-type-update-v2
  (update-applications-array :attachments
                             (partial update-attachment-type attachment-type-mapping/attachment-mapping :type)
                             {:permitType  {$in ["R" "P"]}
                              :attachments.type.type-id {$in [:selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta
                                                              :aitapiirustus
                                                              :vesi_ja_viemariliitoslausunto_tai_kartta
                                                              :sopimusjaljennos
                                                              :karttaaineisto
                                                              :selvitys_rakennuspaikan_korkeusasemasta
                                                              :riskianalyysi]}}))

(defmigration organization-operation-attachments-type-update-v2
  (reduce (fn [cnt org] (+ cnt (mongo/update-n :organizations {:_id (:id org)}
                                               {$set {:operations-attachments (->> (:operations-attachments org)
                                                                                   (map (partial update-operations-attachments-types attachment-type-mapping/attachment-mapping r-or-p-operation?))
                                                                                   (into {}))}})))
          0
          (mongo/select :organizations {:operations-attachments {$gt {$size 0}}} {:operations-attachments 1})))

(defn update-katselmus-buildings
  "Assocs missing buildings from buildings array to repeating :rakennus data for katselmus tasks."
  [buildings {{rakennus :rakennus} :data :as katselmus-doc}]
  (let [saved-building-indices (map (util/fn-> :rakennus :jarjestysnumero :value) (vals rakennus))
        unselected-buildings   (remove (fn [{idx :index}] (some #(= idx %) saved-building-indices)) buildings)
        building-keys          [:index :nationalId :localShortId :propertyId :localId]
        rakennus-keys          [:jarjestysnumero :valtakunnallinenNumero :rakennusnro :kiinttun :kunnanSisainenPysyvaRakennusnumero]
        new-rakennus-map       (reduce
                                 (fn [acc building]
                                   (let [next-idx-kw (->> (map (comp #(Integer/parseInt %) name) (keys acc))
                                                          (apply max -1) ; if no keys, starting index (inc -1) => 0
                                                          inc
                                                          str
                                                          keyword)
                                         rakennus-data (-> (rename-keys building (zipmap building-keys rakennus-keys))
                                                           (select-keys rakennus-keys))]
                                     (assoc acc next-idx-kw {:rakennus (tools/wrapped rakennus-data)})))
                                 rakennus
                                 unselected-buildings)]
    (when (seq unselected-buildings)
      (let [updated-katselmus (assoc-in katselmus-doc [:data :rakennus] new-rakennus-map)]
        (if (model/has-errors? (task-doc-validation "task-katselmus" updated-katselmus))
          (errorf "Migration: task-katselmus validation error for doc-id %s" (:id katselmus-doc))
          updated-katselmus)))))

(defmigration buildings-to-katselmus-tasks
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                {:keys [id tasks buildings]} (mongo/select collection {:tasks {$elemMatch {:schema-info.name "task-katselmus" :state {$ne "sent"}}}} [:tasks :buildings])]
            (let [katselmus-tasks (filter #(and
                                            (= (get-in % [:schema-info :name]) "task-katselmus")
                                            (not= "sent" (:state %)))
                                          tasks)
                  updated-tasks   (->> (map (partial update-katselmus-buildings buildings) katselmus-tasks)
                                       (remove nil?))
                  task-updates    (when (seq updated-tasks)
                                    (apply merge
                                           (map #(mongo/generate-array-updates :tasks
                                                                               tasks
                                                                               (fn [task] (= (:id task) (:id %)))
                                                                               "data.rakennus"
                                                                               (get-in % [:data :rakennus]))
                                                updated-tasks)))]
              (if (map? task-updates)
                (mongo/update-n collection {:_id id} {$set task-updates})
                0)))))

(defn remove-empty-buildings [{{rakennus :rakennus} :data :as katselmus-doc}]
  (let [rakennus-keys (keys rakennus)
        new-rakennus-map (reduce
                           (fn [acc index]
                             (let [{rakennus-data :rakennus} (index acc)
                                   unwrapped-data (tools/unwrapped rakennus-data)
                                   all-empty? (every? util/empty-or-nil? (vals unwrapped-data))]
                               (if all-empty?
                                 (dissoc acc index)
                                 acc)))
                           rakennus
                           rakennus-keys)]
    (when-not (= (count rakennus-keys) (count (keys new-rakennus-map)))
      ; keys have been dissociated, return katselmus document with updated rakennus data
      (assoc-in katselmus-doc [:data :rakennus] new-rakennus-map))))


(defmigration cleanup-empty-buildings-from-tasks
  (reduce + 0
          (for [collection [:applications :submitted-applications]
                {:keys [id tasks]} (mongo/select collection {:tasks {$elemMatch {:schema-info.name "task-katselmus" :state {$ne "sent"}}}} [:tasks])]
            (let [katselmus-tasks (filter #(and
                                            (= (get-in % [:schema-info :name]) "task-katselmus")
                                            (not= "sent" (:state %)))
                                          tasks)
                  updated-tasks   (->> (map remove-empty-buildings katselmus-tasks)
                                       (remove nil?))
                  task-updates    (when (seq updated-tasks)
                                    (apply merge
                                           (map #(mongo/generate-array-updates :tasks
                                                                               tasks
                                                                               (fn [task] (= (:id task) (:id %)))
                                                                               "data.rakennus"
                                                                               (get-in % [:data :rakennus]))
                                                updated-tasks)))]
              (if (map? task-updates)
                (mongo/update-n collection {:_id id} {$set task-updates})
                0)))))

(defmigration image-jpeg
  {:apply-when (pos? (mongo/count :fs.files {:contentType "image/jpg"}))}
  (mongo/update-n :fs.files {:contentType "image/jpg"} {$set {:contentType "image/jpeg"}} :multi true))

(defmigration fix-content-types
  (let [content-types (mongo/distinct :fs.files "contentType")
        all-unallowed-types (set (remove #(re-matches mime/mime-type-pattern %) content-types))
        localized-unallowed-types #{"application/vnd.ms-outlook" "application/msworks"}
        unallowed-types (set/difference all-unallowed-types localized-unallowed-types)]

    (reduce + 0
      (for [f (mongo/select :fs.files {:contentType {$in unallowed-types}})
           :let [{:keys [id filename]} f
                 content-type (mime/mime-type filename)]]
        (mongo/update-n :fs.files {:_id id} {$set {:contentType content-type}})))))

(defn remove-FI-prefix-from-auth-y [k auth]
  (if-let [company-y (get auth k)]
    (assoc auth k (ss/replace company-y "FI" ""))
    auth))

(defmigration sanitize-auth-company-y
  {:apply-when (pos? (mongo/count :applications {:auth.y #"FI"}))}
  (update-applications-array :auth (partial remove-FI-prefix-from-auth-y :y) {:auth.y #"FI"}))

(defmigration sanitize-auth-company-username
  {:apply-when (pos? (mongo/count :applications {:auth.username #"FI"}))}
  (update-applications-array :auth (partial remove-FI-prefix-from-auth-y :username) {:auth.username #"FI"}))

(defn change-empty-role-as-reader [{role :role :as auth}]
  (if (= role "")
    (assoc auth :role "reader")
    auth))

(defmigration sanitize-auth-role
  {:apply-when (pos? (mongo/count :applications {:auth.role ""}))}
  (update-applications-array :auth change-empty-role-as-reader {:auth.role ""}))

(defn remove-auth-inviter-nils [{inviter :inviter :as auth}]
  (if (nil? inviter)
    (dissoc auth :inviter)
    auth))

(defmigration sanitize-auth-inviter
  {:apply-when (pos? (mongo/count :applications {:auth.inviter {$type 10}}))}
  (update-applications-array :auth remove-auth-inviter-nils {:auth.inviter {$type 10}}))

(defn remove-auth-invite-documentId-null-strings [{{document-id :documentId} :invite :as auth}]
  (if (= document-id "null")
    (assoc-in auth [:invite :documentId] nil)
    auth))

(defmigration sanitize-auth-invite-documentId
  {:apply-when (pos? (mongo/count :applications {:auth.invite.documentId "null"}))}
  (update-applications-array :auth remove-auth-invite-documentId-null-strings {:auth.invite.documentId "null"}))

(defn remove-auth-invite-nil-fields [{{:keys [path text documentId documentName] :as invite} :invite :as auth}]
  (if (:email invite)
    (cond-> auth
      (nil? path)              (update :invite #(dissoc % :path))
      (nil? text)              (update :invite #(dissoc % :text))
      (ss/blank? documentId)   (update :invite #(dissoc % :documentId))
      (ss/blank? documentName) (update :invite #(dissoc % :documentName)))
    auth))

(defmigration sanitize-auth-invite-optional-fields
  {:apply-when (pos? (mongo/count :applications {$or [{:auth.invite.path {$type 10}}
                                                      {:auth.invite.text {$type 10}}
                                                      {:auth.invite.documentId {$type 10}}
                                                      {:auth.invite.documentId ""}
                                                      {:auth.invite.documentName {$type 10}}
                                                      {:auth.invite.documentName ""}]}))}
  (update-applications-array :auth
                             remove-auth-invite-nil-fields
                             {$or [{:auth.invite.path {$type 10}}
                                                      {:auth.invite.text {$type 10}}
                                                      {:auth.invite.documentId {$type 10}}
                                                      {:auth.invite.documentId ""}
                                                      {:auth.invite.documentName {$type 10}}
                                                      {:auth.invite.documentName ""}]}))

(def statement-status->new
  {"puoltaa"           "puollettu"
   "ei-puolla"         "ei-puollettu"
   "ehdoilla"          "ehdollinen"
   "jatetty-poydalle"  "poydalle"

   "puollettu"         "puollettu"
   "ei-puollettu"      "ei-puollettu"
   "ehdollinen"        "ehdollinen"
   "poydalle"          "poydalle"
   "ei-huomautettavaa" "ei-huomautettavaa"
   "ei-lausuntoa"      "ei-lausuntoa"
   "lausunto"          "lausunto"
   "kielteinen"        "kielteinen"
   "palautettu"        "palautettu"})

(defn- update-statement-status [{status :status :as statement}]
  (if status
    (update statement :status statement-status->new)
    statement))

(defmigration map-statement-statuses-into-new-krysp
  {:apply-when (pos? (mongo/count :applications {:statements.status {$in ["puoltaa" "ei-puolla" "ehdoilla" "jatetty-poydalle"]}}))}
  (update-applications-array :statements
                             update-statement-status
                             {:statements.status {$in ["puoltaa" "ei-puolla" "ehdoilla" "jatetty-poydalle"]}}))

(defn- remove-nils-from-statement-fields [{given :given status :status reminder-sent :reminder-sent metadata :metadata :as statement}]
  (cond-> statement
    (nil? given) (dissoc :given)
    (nil? status) (dissoc :status)
    (nil? reminder-sent) (dissoc :reminder-sent)
    (nil? metadata) (dissoc :metadata)))

(defmigration sanitize-nils-in-statement-fields
  {:apply-when (pos? (mongo/count :applications {$or [{:statements.given {$type 10}}
                                                      {:statements.status {$type 10}}
                                                      {:statements.reminder-sent {$type 10}}
                                                      {:statements.metadata {$type 10}}]}))}
  (update-applications-array :statements
                             remove-nils-from-statement-fields
                             {$or [{:statements.given {$type 10}}
                                   {:statements.status {$type 10}}
                                   {:statements.reminder-sent {$type 10}}
                                   {:statements.metadata {$type 10}}]}))

(defmigration hakija-tj-v2
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                                 :documents.schema-info.name "hakija-r"}))}
  (letfn [(pred [doc] (= "hakija-r" (get-in doc [:schema-info :name])))]
    (reduce + 0
      (for [{:keys [id documents]} (mongo/select :applications
                                     {:primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                                      :documents.schema-info.name "hakija-r"}
                                     [:documents])]
        (let [updates {$set (mongo/generate-array-updates :documents documents pred "schema-info.name" "hakija-tj")}]
          (mongo/update-n :applications {:_id id} updates))))))

(defn- flatten-kesto [kesto]
  (apply merge
         (select-keys kesto [:alku :loppu])
         (->> (dissoc kesto :alku :loppu) vals)))

(defn- change-kesto-doc-as-repeating [doc]
  (if (and (= (get-in doc [:schema-info :name]) "ymp-ilm-kesto")
           (get-in doc [:data :kesto :arki]))
    (update-in doc [:data :kesto] (fn->> flatten-kesto (hash-map :0)))
    doc))

(defmigration change-meluilmoitus-kesto-as-repeating
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {:data.kesto.arki {$exists true}
                                                                         :schema-info.name "ymp-ilm-kesto"}}}))}
  (update-applications-array :documents
                             change-kesto-doc-as-repeating
                             {:documents {$elemMatch {:data.kesto.arki {$exists true}
                                                      :schema-info.name "ymp-ilm-kesto"}}}))

(defmigration add-archived-timestamps
  {:apply-when (pos? (mongo/count :applications {:archived {$exists false}}))}
  (mongo/update-by-query :applications {:archived {$exists false}} {$set {:archived {:application nil
                                                                                     :completed nil}}}))

(defmigration add-archiving-start-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:permanent-archive-in-use-since {$exists false}}))}
  (mongo/update-by-query :organizations {:permanent-archive-in-use-since {$exists false}} {$set {:permanent-archive-in-use-since 0}}))

(defn add-ym-in-scope [org]
  (let [ym-municipalities (->> (filter (comp #{"YM"} :permitType) (:scope org))
                               (map :municipality)
                               (set))]
    (->> (map :municipality (:scope org))
         (distinct)
         (remove ym-municipalities)
         (reduce #(update %1 :scope conj {:open-inforequest-email nil,
                                          :open-inforequest false,
                                          :new-application-enabled true,
                                          :inforequest-enabled true,
                                          :municipality %2,
                                          :permitType "YM"}) org))))

(defmigration add-ym-permit-type-to-all-ymp-orgs
  {:apply-when (pos? (mongo/count :organizations {:_id #"YMP" :scope.0 {$exists true} :scope.permitType {$not {"$eq" "YM"}}}))}
  (->> (mongo/select :organizations {:_id #"YMP" :scope.permitType {$not {"$eq" "YM"}}})
       (map add-ym-in-scope)
       (run! #(mongo/update-by-id :organizations (:id %) {$set {:scope (:scope %)}}))))

(defmigration add-missing-submit-rights-to-company-users-v2
  {:apply-when (pos? (mongo/count :users {:company.id {$exists true}
                                          :company.submit nil}))}
  (mongo/update-by-query :users
                         {:company.id {$exists true}
                          :company.submit nil}
                         {$set {:company.submit true}}))

(defmigration add-missing-submit-rights-to-company-tokens-v2
  {:apply-when (pos? (mongo/count :token {:token-type {$in [:new-company-user :invite-company-user]}
                                          :data.submit {$exists false}}))}
  (mongo/update-by-query :token
                         {:token-type {$in [:new-company-user :invite-company-user]}
                          :data.submit {$exists false}}
                         {$set {:data.submit true}}))


;; update also bulletins as per change-meluilmoitus-kesto-as-repeating
(defmigration change-meluilmoitus-bulletins-kesto-as-repeating
  {:apply-when (pos? (mongo/count :application-bulletins {:versions.documents {$elemMatch {:data.kesto.arki {$exists true}
                                                                         :schema-info.name "ymp-ilm-kesto"}}}))}
  (update-bulletin-versions :documents
                            change-kesto-doc-as-repeating
                            {:versions.documents {$elemMatch {:data.kesto.arki {$exists true}
                                                     :schema-info.name "ymp-ilm-kesto"}}}))

(defmigration add-calendars-property-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:calendars-enabled {$exists false}}))}
  (doseq [organization (mongo/select :organizations {:calendars-enabled {$exists false}})]
    (mongo/update-by-id :organizations (:id organization)
                        {$set {:calendars-enabled false}})))

(defmigration remove-invites-application-id
  {:apply-when (pos? (mongo/count :applications {:auth.invite.application {$exists true}}))}
  (update-applications-array
    :auth
    (fn [a] (util/dissoc-in a [:invite :application]))
    {:auth.invite.application {$exists true}}))

(defn set-missing-metadata-laskentaperuste [{:keys [metadata] :as attachment}]
  (if (and (= "toistaiseksi" (get-in metadata [:sailytysaika :arkistointi])) (nil? (get-in metadata [:sailytysaika :laskentaperuste])))
    (assoc-in attachment [:metadata :sailytysaika :laskentaperuste] "rakennuksen_purkamisp\u00e4iv\u00e4")
    attachment))

(defmigration add-missing-laskentaperuste-to-metadata-maps
  {:apply-when (pos? (mongo/count :applications {$or [{:metadata.sailytysaika.arkistointi "toistaiseksi"
                                                       :metadata.sailytysaika.laskentaperuste {$exists false}}
                                                      {:processMetadata.sailytysaika.arkistointi "toistaiseksi"
                                                       :processMetadata.sailytysaika.laskentaperuste {$exists false}}
                                                      {:attachments {$elemMatch {:metadata.sailytysaika.arkistointi "toistaiseksi"
                                                                                 :metadata.sailytysaika.laskentaperuste {$exists false}}}}]}))}
  (doseq [collection [:applications :submitted-applications]]
    (mongo/update-by-query collection
                           {:metadata.sailytysaika.arkistointi "toistaiseksi"
                            :metadata.sailytysaika.laskentaperuste {$exists false}}
                           {$set {:metadata.sailytysaika.laskentaperuste "rakennuksen_purkamisp\u00e4iv\u00e4"}})
    (mongo/update-by-query collection
                           {:processMetadata.sailytysaika.arkistointi "toistaiseksi"
                            :processMetadata.sailytysaika.laskentaperuste {$exists false}}
                           {$set {:processMetadata.sailytysaika.laskentaperuste "rakennuksen_purkamisp\u00e4iv\u00e4"}}))
  (update-applications-array :attachments set-missing-metadata-laskentaperuste {:attachments {$elemMatch {:metadata.sailytysaika.arkistointi            "toistaiseksi"
                                                                                                          :metadata.sailytysaika.laskentaperuste {$exists false}}}}))

(defmigration paasuunnittelija-is-removable
  {:apply-when (pos? (mongo/count :applications {:documents {$elemMatch {"schema-info.name" "paasuunnittelija", "schema-info.removable" false}}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (mongo/update-by-query collection
        {:documents {$elemMatch {:schema-info.name "paasuunnittelija", :schema-info.removable false}}}
        {$set {:documents.$.schema-info.removable true}}))))

(defn operation-cleanup-updates-for-application [{attachments :attachments}]
  (->>  ["description" "optional" "created" "attachment-op-selector"]
        (map #(mongo/generate-array-updates :attachments attachments (constantly true) (str "op." %) ""))
        (apply merge)
        (hash-map $unset)))

(defmigration attachment-operation-cleanup
  {:apply-when (or (pos? (mongo/count :applications {$or [{:attachments.op.description {$exists true}}
                                                          {:attachments.op.optional {$exists true}}
                                                          {:attachments.op.created {$exists true}}
                                                          {:attachments.op.attachment-op-selector {$exists true}}]}))
                   (pos? (mongo/count :submitted-applications {$or [{:attachments.op.description {$exists true}}
                                                                    {:attachments.op.optional {$exists true}}
                                                                    {:attachments.op.created {$exists true}}
                                                                    {:attachments.op.attachment-op-selector {$exists true}}]})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection
                                    {$or [{:attachments.op.description {$exists true}}
                                          {:attachments.op.optional {$exists true}}
                                          {:attachments.op.created {$exists true}}
                                          {:attachments.op.attachment-op-selector {$exists true}}]}
                                    {:attachments true})]
         (mongo/update-by-id collection (:id application) (operation-cleanup-updates-for-application application))))

(defmigration unset-organization-municipalities-legacy-key
  {:apply-when (pos? (mongo/count :organizations {:municipalities {$exists true}}))}
  (mongo/update-by-query :organizations {:municipalities {$exists true}} {$unset {:municipalities 1}}))

(defmigration attachment-operation-cleanup-v2
  {:apply-when (or (pos? (mongo/count :applications {$or [{:attachments.op.description {$exists true}}
                                                          {:attachments.op.optional {$exists true}}
                                                          {:attachments.op.created {$exists true}}
                                                          {:attachments.op.attachment-op-selector {$exists true}}]}))
                   (pos? (mongo/count :submitted-applications {$or [{:attachments.op.description {$exists true}}
                                                                    {:attachments.op.optional {$exists true}}
                                                                    {:attachments.op.created {$exists true}}
                                                                    {:attachments.op.attachment-op-selector {$exists true}}]})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection
                                    {$or [{:attachments.op.description {$exists true}}
                                          {:attachments.op.optional {$exists true}}
                                          {:attachments.op.created {$exists true}}
                                          {:attachments.op.attachment-op-selector {$exists true}}]}
                                    {:attachments true})]
    (mongo/update-by-id collection (:id application) (operation-cleanup-updates-for-application application))))

;; migraatio, joka ajettiin vain kerran perumaan yhden batchrunin tulokset, koska
;; generoidut pdf:t eivat olleet valideja.
#_(defmigration verdict-polling-pdf-failure-removal
  {:apply-when (pos? (mongo/count :applications {:tasks.source.type "background"}))}
  (println "PDF korjausmigraatio: background-sourcellisia hakemuksia on "
    (mongo/count :applications {:tasks.source.type "background"}))
  (doseq [failed (mongo/select :applications {:tasks.source.type "background"})]
    (doseq [task (:tasks failed)]
      (if (= "background" (:type (:source task)))
        (do
          (println " - failannut taski" (:id task))
          (doseq [att (:attachments failed)]
            (if (= (:id task) (:id (:source att)))
              (try
                (println "   + poistetaan taskiin " (:id task) " linkattu liite " (:id att) " hakemukselta " (:_id failed))
                (att/delete-attachments! failed (:id att))
                (catch Exception e
                  (println "   + Virhe poistettaessa liitetta")))))
          (println " - poistetaan taski " (:id task) " hakemukselta " (:id failed))
          (action/update-application
            (action/application->command failed)
            {$pull {:tasks {:id (:id task)}}})
          (println " - taski poistettu"))))))

(defn operation-id-cleanup-updates
  "For attachments that have op.id set to nil, nillify whole op ({\"attachments.$.op\" nil})"
  [{attachments :attachments}]
  (mongo/generate-array-updates :attachments attachments #(and (:op %) (nil? (get-in % [:op :id]))) "op" nil))

(defmigration attachment-op-nil-cleanup
  {:apply-when (or (pos? (mongo/count :applications {:attachments.op.id {$type 10}}))
                   (pos? (mongo/count :submitted-applications {:attachments.op.id {$type 10}})))}
  (doseq [collection  [:applications :submitted-applications]
          application (mongo/select collection {:attachments.op.id {$type 10}} [:attachments])]
    (mongo/update-by-id collection (:id application) {$set (operation-id-cleanup-updates application)})))

(defmigration add-use-attachment-links-integration-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:use-attachment-links-integration {$exists false}}))}
  (mongo/update-by-query :organizations {:use-attachment-links-integration {$exists false}} {$set {:use-attachment-links-integration false}}))

(defmigration remove-old-foreman-operation-from-organization-selected-operations
  {:apply-when (pos? (mongo/count :organizations {:selected-operations "tyonjohtajan-nimeaminen"}))}
  (mongo/update :organizations {} {$pull {:selected-operations "tyonjohtajan-nimeaminen"}} :multi true))

; schema change results: changed document title and no more hankkeestaIlmoitettu element inside document
(defn- change-rakennuspaikka-to-toiminnan-sijainti [doc]
  (if (= "rakennuspaikka" (get-in doc [:schema-info :name]))
    (-> doc
      (assoc-in [:schema-info :name] "toiminnan-sijainti")
      (update :data dissoc :hankkeestaIlmoitettu))
  doc))

(defmigration rakennuspaikka-to-toiminnan-sijainti
  {:apply-when (or (pos? (mongo/count :applications {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))
                   (pos? (mongo/count :submitted-applications {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]})))}
  (update-applications-array :documents
                             change-rakennuspaikka-to-toiminnan-sijainti
                             {$and [{:permitType "YL"} {:documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration rakennuspaikka-to-toiminnan-sijainti-bulletins
  {:apply-when (pos? (mongo/count :application-bulletins {$and [{:versions.permitType "YL"} {:versions.documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))}
  (update-bulletin-versions :documents
                            change-rakennuspaikka-to-toiminnan-sijainti
                            {$and [{:versions.permitType "YL"} {:versions.documents {$elemMatch {"schema-info.name" "rakennuspaikka"}}}]}))

(defmigration remove-suunnittelija-from-puun-kaataminen-applications
  {:apply-when (pos? (mongo/count :applications {$and [{:primaryOperation.name "puun-kaataminen"}
                                                       {:state {$in ["draft" "open"]}}
                                                       {:documents.schema-info.name "suunnittelija"}]}))}
  (mongo/update-n :applications
                  {$and [{:primaryOperation.name "puun-kaataminen"}
                         {:state {$in ["draft" "open"]}}
                         {:documents.schema-info.name "suunnittelija"}]}
                  {$pull {:documents {:schema-info.name "suunnittelija"}}}
                  :multi true))

(defmigration organization-name-english-defaults
  {:apply-when (pos? (mongo/count  :organizations {:name.en nil}))}
  (let [changed-organizations (mongo/count  :organizations {:name.en nil})]
    (doseq [organization (mongo/select :organizations {:name.en nil})]
      (mongo/update-by-id :organizations (:id organization)
                          {$set {:name.en (-> organization :name :fi)}}))
    changed-organizations))

(defn- english-default-for-link-name [link]
  (if (not (-> link :name :en))
    (assoc-in link [:name :en] (-> link :name :fi))
    link))

(defmigration set-english-defaults-for-organization-link-names
  {:apply-when (pos? (mongo/count :organizations
                                  {$and
                                   [{:links {$exists true}}
                                    {:links {$elemMatch {:name.en nil}}}]}))}
  (reduce + 0
          (for [organization (mongo/select :organizations
                                           {$and
                                            [{:links {$exists true}}
                                             {:links {$elemMatch {:name.en nil}}}]}
                                           {:links 1})]
            (mongo/update-n :organizations
                            {:_id (:id organization)}
                            {$set {:links (map english-default-for-link-name
                                               (:links organization))}}))))

(defn- missing-url-map [lang]
  {(keyword (str "url." (name lang))) nil})

(defn- default-urls-for-link [link]
  (let [default-url (if (string? (:url link))
                      (:url link)
                      (-> link :url :fi))]
    (assert (string? default-url) (str "was actually " default-url))
    (assoc link :url
           (merge-with (fn [a b]
                         (or a b))
                       (i18n/localization-schema default-url)
                       (if (map? (:url link))
                         (:url link)
                         {})))))

(defmigration set-language-defaults-for-organization-link-urls
  {:apply-when (pos? (mongo/count :organizations
                                  {:links {$elemMatch {$or (map missing-url-map
                                                                i18n/supported-langs)}}}))}
  (reduce + 0
          (for [organization (mongo/select :organizations
                                           {:links {$elemMatch {$or (map missing-url-map
                                                                         i18n/supported-langs)}}}
                                           {:links 1})]
            (mongo/update-n :organizations
                            {:_id (:id organization)}
                            {$set {:links (map default-urls-for-link
                                               (:links organization))}}))))

(defn- english-default-for-link-url [link]
  (if (not (-> link :url :en))
    (assoc-in link [:url :en] (-> link :url :fi))
    link))

(defmigration set-english-defaults-for-organization-link-urls
  {:apply-when (pos? (mongo/count :organizations
                                  {$and
                                   [{:links {$exists true}}
                                    {:links {$elemMatch {:url.en nil}}}]}))}
  (reduce + 0
          (for [organization (mongo/select :organizations
                                           {$and
                                            [{:links {$exists true}}
                                             {:links {$elemMatch {:url.en nil}}}]}
                                           {:links 1})]
            (mongo/update-n :organizations
                            {:_id (:id organization)}
                            {$set {:links (map english-default-for-link-url
                                               (:links organization))}}))))

(defn not-needed-to-false                                   ;; LP-6232
  "If there are versions, it doesn't make sense to flag attachment as 'not needed'.
   Sets notNeeded to false for attachment in such state."
  [{:keys [notNeeded versions] :as attachment}]
  (if (and notNeeded (seq versions))
    (assoc attachment :notNeeded false)
    attachment))

(defmigration set-attachments-with-versions-to-needed          ;; LP-6232
  {:apply-when (pos? (mongo/count :applications {:attachments
                                                 {$elemMatch {$and [{:notNeeded true} ,
                                                                     {:versions {$exists true,
                                                                                 $not {$size 0}}}]}}}))}
  (update-applications-array :attachments
                             not-needed-to-false
                             {:attachments
                              {$elemMatch {$and [{:notNeeded true} ,
                                                 {:versions {$exists true,
                                                             $not {$size 0}}}]}}}))

(defmigration set-attachments-with-versions-to-needed-v2          ;; LPK-2275
  {:apply-when (pos? (mongo/count :applications {:attachments
                                                 {$elemMatch {$and [{:notNeeded true} ,
                                                                    {:versions {$exists true,
                                                                                $not {$size 0}}}]}}}))}
  (update-applications-array :attachments
                             not-needed-to-false
                             {:attachments
                              {$elemMatch {$and [{:notNeeded true} ,
                                                 {:versions {$exists true,
                                                             $not {$size 0}}}]}}}))


;; Create-change-permit command has not been copying location-wgs84 property.
;; The copying has been fixed, but we need to run the old migration again.
(defmigration add-wgs84-location-for-applications-again
  {:apply-when (pos? (mongo/count :applications {:location-wgs84 {$exists false}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:location-wgs84 {$exists false}})]
        (count (map #(mongo/update-by-id collection (:id %) (convert-coordinates %)) applications))))))

(defn coerce-time-string [time-string]
  (if (ss/not-blank? time-string)
    (->> (re-seq #"\d+" time-string)
         ((fn [[h m s d]] [(or h "00") (or m "00") s d]))
         (remove nil?)
         (interleave ["" ":" ":" "."])
         (apply str))
    time-string))

(defn coerce-kesto-row-time-strings [row]
  (merge row
         (->> (select-keys row [:arkiAlkuAika :arkiLoppuAika :lauantaiAlkuAika :lauantaiLoppuAika :sunnuntaiAlkuAika :sunnuntaiLoppuAika])
              (util/map-values #(update % :value coerce-time-string)))))

(defn coerce-ymp-ilm-kesto-time-strings [doc]
  (if (= (get-in doc [:schema-info :name]) "ymp-ilm-kesto")
    (update-in doc [:data :kesto] (partial util/map-values coerce-kesto-row-time-strings))
    doc))

(defmigration coerce-time-string-in-ymp-ilm-kesto-docs
  (update-applications-array :documents
                             coerce-ymp-ilm-kesto-time-strings
                             {:documents.schema-info.name "ymp-ilm-kesto"}))

(def ddMMyyyy-formatter (clj-time.format/formatter "dd.MM.yyyy"))

(defn- date-string-to-ms [date]
  (clj-time.coerce/to-long (clj-time.format/parse ddMMyyyy-formatter date)))

(defn- valid-date-string [date]
  (try
    (instance? DateTime (clj-time.format/parse ddMMyyyy-formatter date))
    (catch Exception e
      false)))

(defn- add-start-timestamp [document]
  (if (and (= "tyoaika" (get-in document [:schema-info :name]))
           (valid-date-string (get-in document [:data :tyoaika-alkaa-pvm :value]))
           (nil? (get-in document [:data :tyoaika-alkaa-ms :value])))
      (assoc-in document [:data :tyoaika-alkaa-ms :value] (date-string-to-ms (get-in document [:data :tyoaika-alkaa-pvm :value])))
    document))

(defn- add-end-timestamp [document]
  (if (and (= "tyoaika" (get-in document [:schema-info :name]))
           (valid-date-string (get-in document [:data :tyoaika-paattyy-pvm :value]))
           (nil? (get-in document [:data :tyoaika-paattyy-ms :value])))
      (assoc-in document [:data :tyoaika-paattyy-ms :value] (date-string-to-ms (get-in document [:data :tyoaika-paattyy-pvm :value])))
    document))

(defmigration add-ms-timestamp-for-work-started-and-ended
  {:apply-when (pos? (mongo/count :applications
                                  {:documents
                                  {$elemMatch {$and
                                               [{$and [{:schema-info.name "tyoaika"},
                                                     {:data.tyoaika-alkaa-pvm.modified {$exists true, $gt 0}},
                                                     {:data.tyoaika-alkaa-pvm.value {$exists true, $ne ""}},
                                                     {:data.tyoaika-alkaa-ms.value {$exists false}}]}
                                               {$and [{:schema-info.name "tyoaika"},
                                                      {:data.tyoaika-paattyy-pvm.modified {$exists true, $gt 0}},
                                                      {:data.tyoaika-paattyy-pvm.value {$exists true, $ne ""}},
                                                      {:data.tyoaika-paattyy-ms.value {$exists false}}]}]}}}))}

  (update-applications-array :documents
                             add-start-timestamp
                             {:documents
                              {$elemMatch {$and [{:schema-info.name "tyoaika"},
                                                 {:data.tyoaika-alkaa-pvm.modified {$exists true, $gt 0}},
                                                 {:data.tyoaika-alkaa-pvm.value {$exists true, $ne ""}},
                                                 {:data.tyoaika-alkaa-ms.value {$exists false}}]}}})
  (update-applications-array :documents
                           add-end-timestamp
                           {:documents
                            {$elemMatch {$and [{:schema-info.name "tyoaika"},
                                               {:data.tyoaika-paattyy-pvm.modified {$exists true, $gt 0}},
                                               {:data.tyoaika-paattyy-pvm.value {$exists true, $ne ""}},
                                               {:data.tyoaika-paattyy-ms.value {$exists false}}]}}}))

(defn- build-approvals
  "Approvals is original-file-id - approval map, where approval includes state
  and approved information.Explicit approved/rejected flag is no
  longer used. The flag is determined by the version state. In
  migration, we create approval for the latestVersion and remove old
  state and approved."
  [{:keys [state approved latestVersion] :as attachment}]
  (merge (dissoc attachment :state :approved)
         (when-let [filekey (some-> latestVersion :originalFileId keyword)]
           {:approvals {filekey (assoc (select-keys approved [:timestamp :user])
                                      :state state)}})))

(defmigration version-specific-attachment-state
  {:apply-when (pos? (mongo/count :applications {:attachments.state {$exists true}}))}
  (update-applications-array :attachments
                             build-approvals
                             {:attachments.0 {$exists true}}))

(def al-retention-ruling "AL/17413/07.01.01.03.01/2016")

(defn change-retention-to-permanent [{:keys [metadata] :as attachment}]
  (if (and (#{"toistaiseksi" "m\u00e4\u00e4r\u00e4ajan"} (get-in metadata [:sailytysaika :arkistointi]))
           (not= "arkistoitu" (:tila metadata)))
    (assoc-in attachment [:metadata :sailytysaika] {:arkistointi "ikuisesti"
                                                    :perustelu al-retention-ruling})
    attachment))

(def in-non-perm-retention {$in ["toistaiseksi" "m\u00e4\u00e4r\u00e4ajan"]})

(defmigration change-retention-to-permanent-for-archived-R-docs
  {:apply-when (pos? (mongo/count :applications {:organization {"$regex" ".*-R"}
                                                 $or [{:metadata.sailytysaika.arkistointi in-non-perm-retention
                                                       :metadata.tila {$ne "arkistoitu"}}
                                                      {:processMetadata.sailytysaika.arkistointi in-non-perm-retention
                                                       :processMetadata.tila {$ne "arkistoitu"}}
                                                      {:attachments {$elemMatch {:metadata.sailytysaika.arkistointi in-non-perm-retention
                                                                                 :metadata.tila {$ne "arkistoitu"}}}}]}))}
  (doseq [collection [:applications :submitted-applications]]
    (mongo/update-by-query collection
                           {:organization {"$regex" ".*-R"}
                            :metadata.sailytysaika.arkistointi in-non-perm-retention
                            :metadata.tila {$ne "arkistoitu"}}
                           {$set {:metadata.sailytysaika.arkistointi "ikuisesti"
                                  :metadata.sailytysaika.perustelu al-retention-ruling}})
    (mongo/update-by-query collection
                           {:organization {"$regex" ".*-R"}
                            :processMetadata.sailytysaika.arkistointi in-non-perm-retention
                            :processMetadata.tila {$ne "arkistoitu"}}
                           {$set {:processMetadata.sailytysaika.arkistointi "ikuisesti"
                                  :processMetadata.sailytysaika.perustelu al-retention-ruling}}))
  (update-applications-array :attachments
                             change-retention-to-permanent
                             {:organization {"$regex" ".*-R"}
                              :attachments {$elemMatch {:metadata.sailytysaika.arkistointi in-non-perm-retention
                                                        :metadata.tila {$ne "arkistoitu"}}}}))

(def archival-completed-query
  {:archived.completed nil
   :processMetadata.tila :arkistoitu
   :metadata.tila :arkistoitu
   :attachments {$not {$elemMatch {:versions {$gt []}
                                   :metadata.sailytysaika.arkistointi {$exists true
                                                                       $ne :ei}
                                   :metadata.tila {$ne :arkistoitu}}}}
   :state {$in [:closed :extinct :foremanVerdictGiven :acknowledged]}})

(defmigration mark-applications-with-attachments-missing-metadata-but-otherwise-archived-as-archived
  {:apply-when (pos? (mongo/count :applications archival-completed-query))}
  (mongo/update-by-query :applications
                         archival-completed-query
                         {$set {:archived.completed (System/currentTimeMillis)}}))

(defn- add-wgs84-coordinates [drawing]
  (if-let [geom (draw/wgs84-geometry drawing)]
    (assoc drawing :geometry-wgs84 geom)
    (dissoc drawing :geometry-wgs84)))

(defmigration add-wgs84-coordinates-for-drawings
  {:apply-when (pos? (mongo/count :applications
                                  {:drawings
                                   {$elemMatch {$and
                                                [{:geometry {$exists true, $ne ""}},
                                                 {:geometry-wgs84 {$exists false}}]}}}))}
  (update-applications-array :drawings
                             add-wgs84-coordinates
                             {:drawings
                              {$elemMatch {$and
                                           [{:geometry {$exists true, $ne ""}},
                                            {:geometry-wgs84 {$exists false}}]}}}))

(defn- change-attachment-type [attachment]
  (if (= (get-in attachment [:type :type-id]) "lupaehto")
    (assoc-in attachment [:type :type-id] "muu")
    attachment))

(defmigration change-attachment-type-lupaehto-to-muu
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                  :attachments {$elemMatch {:type.type-id "lupaehto"}}}))}
  (update-applications-array :attachments
                             change-attachment-type
                             {:permitType "YA"
                              :attachments {$elemMatch {:type.type-id "lupaehto"}}}))

(defn- set-attachment-groupType-and-op-nil [attachment]
  (assoc attachment
         :groupType nil
         :op nil))

(let [attachments-with-groupType-or-op-match
      {$and [{:permitType "YA"}
             {$or [{:attachments {$elemMatch {:groupType {$ne nil}}}}
                   {:attachments {$elemMatch {:op {$ne nil}}}}]}]}]
  (defmigration remove-groupType-and-op-from-YA-applications-v5
    {:apply-when (pos? (mongo/count :applications attachments-with-groupType-or-op-match))}
    (update-applications-array :attachments
                               set-attachment-groupType-and-op-nil
                               attachments-with-groupType-or-op-match)))

(defn get-asemapiirros-multioperation-updates-for-application [application]
  (->> (app/get-operations application)
       (map #(select-keys % [:id :name]))
       (hash-map :attachments.$.groupType "operation" :attachments.$.op)
       (hash-map $set)
       (vector {:_id (:id application) :attachments.type.type-id "asemapiirros"})))

(defmigration update-asemapiirros-grouping-as-multioperation
  (let [query      {:permitType {$in ["R" "P"]} :attachments.type.type-id "asemapiirros"}
        projection [:primaryOperation :secondaryOperations]]
    (->> (mongo/select :applications query projection)
         (map get-asemapiirros-multioperation-updates-for-application)
         (run! (partial apply mongo/update-by-query :applications)))
    (->> (mongo/select :submitted-applications query projection)
         (map get-asemapiirros-multioperation-updates-for-application)
         (run! (partial apply mongo/update-by-query :submitted-applications)))))

(defn- clean-up-attachment-op [attachment]
  (if (sequential? (:op attachment))
    (update attachment :op #(map att/->attachment-operation %))
    attachment))

(defmigration cleanup-attachment-operations-v3
  {:apply-when (pos? (mongo/count :applications {:attachments.op.groupType {$exists true}}))}
  (update-applications-array :attachments clean-up-attachment-op {:attachments.op.groupType {$exists true}}))

(defmigration create-general-handler-for-organizations
  {:apply-when (pos? (mongo/count :organizations {:handler-roles {$exists false}}))}
  (->> (mongo/select :organizations {:handler-roles.id {$exists false}} [:_id])
       (run! #(mongo/update-by-id :organizations (:id %) {$set {:handler-roles [{:id      (mongo/create-id)
                                                                                 :name    {:fi "K\u00e4sittelij\u00e4"
                                                                                           :sv "Handl\u00e4ggare"
                                                                                           :en "Handler"}
                                                                                 :general true}]}}))))

(defn- set-handler-for-application [role-id {id :id authority :authority :as application}]
  (when (:id authority)
    (let [handler (user/create-handler nil role-id authority)]
      (mongo/update-by-id :applications           id {$set {:handlers [handler]}})
      (mongo/update-by-id :submitted-applications id {$set {:handlers [handler]}}))))

(defn- set-handler-for-organizations-applications [{org-id :id roles :handler-roles}]
  (when-let [role-id (:id (util/find-by-key :general true roles))]
    (->> (mongo/select :applications {:organization org-id} [:authority])
         (run! (partial set-handler-for-application role-id)))))

(defmigration copy-application-authority-as-general-handler-in-applications
  {:apply-when (pos? (mongo/count :applications {:authority.id {$type 2} :handlers {$exists false}}))}
  (->> (mongo/select :organizations {:handler-roles.general {$exists true}} [:handler-roles])
       (run! set-handler-for-organizations-applications)))

(defn handler-map-to-array [{:keys [handlers id]}]
  (when (map? handlers)
    (mongo/update-by-id :applications id {$set {:handlers (vals handlers)}})))

(defmigration application-handler-fix                       ; fix handlers after deploy of 8.2.2017
  (->> (mongo/select :applications {:modified {$gt 1486588980000} :handlers {$exists true}} [:handlers])
       (run! handler-map-to-array)))

(defmigration assignment-target-to-targets
  {:apply-when (pos? (mongo/count :assignments {$or [{:target {$exists true}}
                                                     {:targets {$exists false}}]}))}
  (reduce + 0
          (for [assignment (mongo/select :assignments {} {:target 1})]
            (let [target (:target assignment)]
              (mongo/update-n :assignments {:_id (:id assignment)} {$set {:targets [target]}
                                                                    $unset {:target ""}})))))
(defn- add-empty-handler-array [collection {handlers :handlers id :id :as application}]
  (when-not handlers
    (mongo/update-by-id collection id {$set {:handlers []}})))

(defmigration handlers-to-all-applications
  (->> (mongo/select :applications {:handlers {$exists false}} [:handlers])
       (run! (partial add-empty-handler-array :applications)))
  (->> (mongo/select :submitted-applications {:handlers {$exists false}} [:handlers])
       (run! (partial add-empty-handler-array :submitted-applications))))

(defn ensure-op-is-not-object [attachment]
  (update attachment :op #(if (map? %) [%] %)))

(defmigration attachment-op-to-array
  (update-applications-array :attachments ensure-op-is-not-object {:attachments.op {$type 3}}))

(defmigration add-assignment-trigger
  {:apply-when (pos? (mongo/count :assignments {:trigger {$exists false}}))}
  (mongo/update-n :assignments
                  {:trigger {$exists false}}
                  {$set {:trigger assignment/user-created-trigger}}
                  :multi true))

(defmigration add-assignment-target-timestamps
  {:apply-when (pos? (mongo/count :assignments {:targets.timestamp {$exists false}}))}
  (reduce + 0
          (for [assignment (mongo/select :assignments
                                         {:targets.timestamp {$exists false}}
                                         {:targets true, :states true})]
            (let [targets           (:targets assignment)
                  created-timestamp (-> assignment :states (get 0) :timestamp)]
              (mongo/update-n :assignments {:_id (:id assignment)}
                              {$set {:targets (map (fn [target]
                                                     (assoc target :timestamp created-timestamp))
                                                   targets)}})))))

(defn migrate-rakennusJaPurkujate
  "1) Other selection to vaarallisetJatteet 2) Other selection to
  muuJate 3) Migrate old muuJate values to new values."
  [{{{:keys[vaarallisetJatteet muuJate]} :rakennusJaPurkujate} :data :as doc}]
  (let [rakennus-ja-purku (assoc (-> doc :data :rakennusJaPurkujate)
                                 :vaarallisetJatteet (reduce (fn [acc [index {jate :jate :as row}]]
                                                               (assoc acc index (assoc-in (dissoc row :jate)
                                                                                          [:jate-group :jate]
                                                                                          jate)))
                                                             {}
                                                             vaarallisetJatteet)
                                 :muuJate (reduce (fn [acc [index {jate :jate :as row}]]
                                                    (assoc acc index (assoc (dissoc row :jate)
                                                                            :jate-group
                                                                            (case (:value jate)
                                                                              ("betoni" "tiilet") {:jate {:value "betoni-tiili"
                                                                                                          :modified (:modified jate)}}
                                                                              ("pinnoittamatonPuu" "pinnoitettuPuu") {:jate {:value "puu"
                                                                                                                             :modified (:modified jate)}}
                                                                              "sekajate" {:muu {:value "Sekaj\u00e4te"
                                                                                                :modified (:modified jate)}
                                                                                          :jate {:value "muu"
                                                                                                 :modified (:modified jate)}}
                                                                              {:jate {:value nil}}))))
                                                  {}
                                                  muuJate))]
    (assoc-in doc [:data :rakennusJaPurkujate] rakennus-ja-purku)))

(defmigration extended-waste-report-changes
  (update-applications-array :documents
                             migrate-rakennusJaPurkujate
                             {:documents {$elemMatch {:schema-info.name "laajennettuRakennusjateselvitys"}}}))

(def project-description-types
  #{:hankkeen-kuvaus
   :hankkeen-kuvaus-rakennuslupa
   :hankkeen-kuvaus-minimum
   :hankkeen-kuvaus-jatkoaika
   :hankkeen-kuvaus-vesihuolto
   :yleiset-alueet-hankkeen-kuvaus-sijoituslupa
   :yleiset-alueet-hankkeen-kuvaus-kaivulupa
   :yleiset-alueet-hankkeen-kuvaus-kayttolupa})

(defn- add-subtype [document]
  (if (contains? project-description-types (keyword (get-in document [:schema-info :name])))
    (assoc-in document [:schema-info :subtype] :hankkeen-kuvaus)
    document))

(defmigration add-project-description-subtype-to-documents
  {:apply-when (pos? (mongo/count :applications
                                  {:documents
                                  {$elemMatch {:schema-info.name {$in ["hankkeen-kuvaus",
                                                                              "hankkeen-kuvaus-rakennuslupa",
                                                                              "hankkeen-kuvaus-minimum",
                                                                              "hankkeen-kuvaus-jatkoaika",
                                                                              "hankkeen-kuvaus-vesihuolto",
                                                                              "yleiset-alueet-hankkeen-kuvaus-sijoituslupa",
                                                                              "yleiset-alueet-hankkeen-kuvaus-kaivulupa",
                                                                              "yleiset-alueet-hankkeen-kuvaus-kayttolupa"]},
                                               :schema-info.subtype {$exists false}}}}))}
  (update-applications-array :documents
                             add-subtype
                             {:documents
                              {$elemMatch {:schema-info.name {$in ["hankkeen-kuvaus",
                                                                          "hankkeen-kuvaus-rakennuslupa",
                                                                          "hankkeen-kuvaus-minimum",
                                                                          "hankkeen-kuvaus-jatkoaika",
                                                                          "hankkeen-kuvaus-vesihuolto",
                                                                          "yleiset-alueet-hankkeen-kuvaus-sijoituslupa",
                                                                          "yleiset-alueet-hankkeen-kuvaus-kaivulupa",
                                                                          "yleiset-alueet-hankkeen-kuvaus-kayttolupa"]},
                                           :schema-info.subtype {$exists false}}}}))

(defmigration project-description-index
  (reduce +
    (for [collection [:applications :submitted-applications]]
      (let [applications (mongo/select collection {:documents.schema-info.subtype "hankkeen-kuvaus"} {:documents 1})]
        (count (map #(mongo/update-by-id collection (:id %) (app-meta-fields/update-project-description-index %)) applications))))))

(defn dissoc-rakennusJaPurkujate-from-wrong-documents
  "Above migration associated :rakennusJaPurkuJate key to every document.
  This function dissociates that data from wrong documents"
  [{schema :schema-info :as doc}]
  (if (= (:name schema) "laajennettuRakennusjateselvitys")
    doc                                                     ; Migration target was laajennettuRakennusjateselvitys, it will be left intact
    (-> (update-in doc [:data :rakennusJaPurkujate] dissoc :vaarallisetJatteet :muuJate) ; Remove accidentaly added keys
        (update :data (fn [{:keys [rakennusJaPurkujate] :as data}]
                        (if (empty? rakennusJaPurkujate)    ; this preserves old data in "rakennusjatesuunnitelma" document
                          (dissoc data :rakennusJaPurkujate)
                          data))))))

(defmigration fix-waste-data-migration
  (update-applications-array :documents
                             dissoc-rakennusJaPurkujate-from-wrong-documents
                             {:documents {$elemMatch {:schema-info.name "laajennettuRakennusjateselvitys"}}}))

(defmigration fix-empty-foreman-user
  {:apply-when (pos? (mongo/count :users {:email "" :id "58afe17a28e06f2484a6e82f"}))}
  (let [malformed-user-id "58afe17a28e06f2484a6e82f"]
    (mongo/remove :users malformed-user-id)
    (mongo/update-by-query :submitted-applications
                           {:auth.id malformed-user-id}
                           {$pull {:auth {:id malformed-user-id}}})
    (mongo/update-by-query :applications
                           {:auth.id malformed-user-id}
                           {$pull {:auth {:id malformed-user-id}}})))

(def ya-katulupa-types
  #{:ya-katulupa-vesi-ja-viemarityot
    :ya-katulupa-maalampotyot
    :ya-katulupa-kaukolampotyot
    :ya-katulupa-kaapelityot
    :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat})

(defn- op-ref-from-tyomaastaVastaava-to-hankkeen-kuvaus-update [documents]
  (letfn [(find-doc-index [schema-name]
            (first (util/positions #(= (get-in % [:schema-info :name]) schema-name) documents)))]
    (let [source-doc-index (find-doc-index "tyomaastaVastaava")
          target-doc-index (find-doc-index "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
          op-ref           (get-in documents [source-doc-index :schema-info :op])]
      (if (and source-doc-index target-doc-index op-ref)
        {$unset {(util/kw-path :documents source-doc-index :schema-info :op) 1}
         $set   {(util/kw-path :documents target-doc-index :schema-info :op) op-ref}}))))

(defn- do-fix-katulupa-op-documents [collection]
  (let [applications (mongo/select collection
                                   {:documents
                                    {$elemMatch {:schema-info.name    "tyomaastaVastaava"
                                                 :schema-info.op.name {$in (->> ya-katulupa-types vec (map name))}}}})]
    (doseq [{:keys [id documents]} applications]
      (mongo/update-by-id collection id
                          (op-ref-from-tyomaastaVastaava-to-hankkeen-kuvaus-update documents)))
    (count applications)))

(defmigration fix-katulupa-op-documents
  {:apply-when (pos? (mongo/count :applications
                                  {:documents
                                   {$elemMatch {:schema-info.name    "tyomaastaVastaava"
                                                :schema-info.op.name {$in (->> ya-katulupa-types vec (map name))}}}}))}
  (reduce + 0
    (for [collection [:applications :submitted-applications]]
      (do-fix-katulupa-op-documents collection))))

(defn- trigger-description [organization trigger-id]
  (->> organization
       :assignment-triggers
       (util/find-first #(= (:id %) trigger-id))
       :description))

(defmigration fix-automatic-assignment-empty-descriptions
  {:apply-when (pos? (mongo/count :assignments
                                  {:trigger    {$ne "user-created"}
                                   :description ""}))}
  (let [assignments (mongo/select :assignments {:trigger {$ne "user-created"}, :description ""})]
    (doseq [{:keys [id application trigger] :as assignment} assignments]
      (let [organization (mongo/select-one :organizations {:_id (:organization application)})]
        (mongo/update-by-id :assignments
                            id
                            {$set {:description (trigger-description organization trigger)}})))
    (count assignments)))

(defn update-ya-subtypes-for-applications [coll]
  (for [ya-subtype (->> (map (comp first :subtypes) (vals op/ya-operations)) ; kayttolupa :tyolupa :sijoituslupa
                        (remove nil?)
                        (distinct))
        :let [operations (filter (fn [[_ op-data]]
                                   (some (partial = ya-subtype) (:subtypes op-data)))
                                 op/ya-operations)]
        :when (not= :sijoitussopimus ya-subtype)]
    (mongo/update-by-query coll
                           {:permitType "YA" :permitSubtype {$not {$type 2}}
                            :primaryOperation.name {$in (map first operations)}}
                           {$set {:permitSubtype ya-subtype}})))



(defmigration set-YA-subtypes
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :permitSubtype {$not {$type 2}}
                                                 :primaryOperation.name {$ne "ya-jatkoaika"}}))}
  (update-ya-subtypes-for-applications :submitted-applications)
  (reduce + 0 (update-ya-subtypes-for-applications :applications)))

(defn get-state-for-sijoitussopimus
  "Changes old sijoitussopimus post-verdict states (verdictGiven, constructionStarted, closed) to new state."
  [{:keys [verdicts state]}]
  (when-not (contains? #{"canceled" "complementNeeded" "sent" "submitted" "draft" "open"} state) ; could be pre-verdict state event if verdicts exist
    (let [sopimus-verdict (->> verdicts
                               (remove :draft)
                               (util/find-first :sopimus))]
      (if (not-empty (:signatures sopimus-verdict))
        "agreementSigned"
        "agreementPrepared"))))

(defn update-sijoituslupa-to-sopimus [coll]
  (for [app (mongo/select coll {:permitType "YA" :verdicts {$elemMatch {"sopimus" true "draft" false}} :permitSubtype "sijoituslupa"})
        :let [non-draft-verdicts (remove :draft (:verdicts app))]
        :when (-> non-draft-verdicts first :sopimus)]
    (mongo/update-n coll {:_id (:id app)} {$set (util/assoc-when {:permitSubtype "sijoitussopimus"}
                                                                 :state (get-state-for-sijoitussopimus app))})))

(defmigration set-sijoitussopimus-subtypes
  {:apply-when (pos? (mongo/count :applications {:permitType "YA"
                                                 :verdicts {$elemMatch {:sopimus true :draft false}}
                                                 :permitSubtype "sijoituslupa"}))}
  (update-sijoituslupa-to-sopimus :submitted-applications)
  (reduce + 0 (update-sijoituslupa-to-sopimus :applications)))

; Tyolupa is same as default application graph. Sijoitussopimus is migrated in set-sijoitussopimus-subtypes.
;(= lupapalvelu.states/ya-tyolupa-state-graph lupapalvelu.states/default-application-state-graph)
; => true
; Sijoituslupa and kayttolupa doesn't have closed or constructionStarted states anymore.
; Jatkoaika doesn't have verdictGiven, constructionStarted nor closed state.
(defmigration update-ya-states
  (second
    (for [coll [:submitted-applications :applications]]
      (+ (mongo/update-by-query coll {:permitType "YA" :permitSubtype "sijoituslupa" :state {$in ["closed" "constructionStarted"]}} {$set {:state "finished"}})
         (mongo/update-by-query coll {:permitType "YA" :permitSubtype "kayttolupa" :state {$in ["closed" "constructionStarted"]}} {$set {:state "finished"}})
         (mongo/update-by-query coll {:permitType "YA" :primaryOperation.name "ya-jatkoaika" :state {$in ["verdictGiven" "closed" "constructionStarted"]}} {$set {:state "finished"}})))))


(def ya-post-verdict-states #{:finished :agreementPrepared :agreementSigned})

; jatkoaika -> verdictGiven -> finished
; kayttolupa -> verdictGiven kopioidaan finished
; tyolupa ei muutoksia
; sijoituslupa -> lisataan finished
; sijoitussopimus -> verdictGiven kopioidaan agreementSigned/agreementPrepared
(defmigration ya-history-and-timestamp-updates
  {:apply-when (or (pos? (mongo/count :applications {:permitType "YA"
                                                     :state {$in ya-post-verdict-states}
                                                     :history.state {$ne "finished"}
                                                     :permitSubtype "sijoituslupa"}))
                   (pos? (mongo/count :applications {:permitType "YA"
                                                     :state {$in ya-post-verdict-states}
                                                     :history.state {$nin ["agreementPrepared" "agreementSigned"]}
                                                     :permitSubtype "sijoitussopimus"}))
                   (pos? (mongo/count :applications {:permitType "YA"
                                                     :state {$in ya-post-verdict-states}
                                                     :history.state {$ne "finished"}
                                                     :permitSubtype "kayttolupa"}))
                   (pos? (mongo/count :applications {:permitType "YA"
                                                     :state {$in ya-post-verdict-states}
                                                     :history.state {$ne "finished"}
                                                     :primaryOperation.name "ya-jatkoaika"})))}
  (reduce + 0
    (for [coll [:submitted-applications :applications]
          app (mongo/select coll
                            {:permitType "YA"
                             :state {$in ya-post-verdict-states}
                             :history {$elemMatch {:state {$nin ["agreementPrepared" "agreementSigned" "finished"]}}}}
                            [:state :permitSubtype :permitType :primaryOperation :history])
          :let [verdict-state       (sm/verdict-given-state app)
                state-history       (app-state/state-history-entries (:history app))
                history-states-set  (->> state-history (map (comp keyword :state)) set)
                verdict-history     (util/find-first #(= "verdictGiven" (:state %)) state-history)
                verdict-ts          (:ts verdict-history)
                verdict-state-in-history? (contains? history-states-set verdict-state)
                migration-target-state (if verdict-state-in-history? (:state app) verdict-state)]
          :when (or (not verdict-state-in-history?)
                    (not= verdict-state (keyword (:state app))))]
      (mongo/update-n coll {:_id (:id app)} {$push {:history {:state migration-target-state
                                                              :ts verdict-ts
                                                              :user usr/migration-user-summary}}
                                             $set {(get app-state/timestamp-key (keyword migration-target-state)) verdict-ts}}))))

(defn kayttolupa-to-tyolupa-state [{:keys [state history]}]
  (if (= "finished" state)
    (or (->> (app-state/state-history-entries history)
             (sort-by :ts)
             reverse
             (map :state)
             (some #{"closed" "constructionStarted"}))      ; From history, take either closed or constructionStartd, which one was previous
        "constructionStarted")                              ; If history doesn't have state, put it to construction started.
    state))

(defn update-liikennealue-application-to-katulupa [coll {:keys [documents id infoRequest] :as app}]
  {:pre [(sequential? documents) (string? id)]}
  (let [new-op-doc-name (get-in op/ya-operations [:ya-katulupa-muu-liikennealuetyo :schema])
        _ (assert (= new-op-doc-name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"))
        operation-doc (->> documents
                           (util/find-first #(= "ya-kayttolupa-muu-liikennealuetyo"
                                                (get-in % [:schema-info :op :name]))))
        to-be-op-doc  (->> documents
                           (util/find-first #(= "yleiset-alueet-hankkeen-kuvaus-kayttolupa"
                                                (get-in % [:schema-info :name]))))
        updates (util/assoc-when
                  {$set (merge {:primaryOperation.name "ya-katulupa-muu-liikennealuetyo"
                                :state (kayttolupa-to-tyolupa-state app)
                                :permitSubtype "tyolupa"}
                               (when (seq documents)
                                 (mongo/generate-array-updates :documents documents #(= (:id to-be-op-doc) (:id %))
                                                               "schema-info.name" new-op-doc-name ; "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
                                                               "schema-info.op" (-> (get-in operation-doc [:schema-info :op])
                                                                                    (assoc :name new-op-doc-name))
                                                               "data.sijoitusLuvanTunniste" {:value nil})))}

                  $unset (when (seq documents) (mongo/generate-array-updates :documents documents #(= (:id operation-doc) (:id %)) "schema-info.op" 1))
                  $pull (when-not infoRequest {:history {:state "finished"}}))]
    (mongo/update-by-id coll id updates))
  1)

(defmigration muu-liikennealue-lupa
  {:apply-when (pos? (mongo/count :applications {:permitType "YA" :primaryOperation.name "ya-kayttolupa-muu-liikennealuetyo"}))}
  (reduce + 0
          (for [coll [:submitted-applications :applications]
                app  (mongo/select coll
                                   {:permitType "YA"
                                    :primaryOperation.name "ya-kayttolupa-muu-liikennealuetyo"}
                                   [:state :primaryOperation :history :documents :infoRequest])]
            (update-liikennealue-application-to-katulupa coll app))))

(defmigration muu-liikennealue-lupa-selected-operation
  {:apply-when (pos? (mongo/count :organizations {:selected-operations "ya-kayttolupa-muu-liikennealuetyo"}))}
  (mongo/update-by-query :organizations
                         {:selected-operations "ya-kayttolupa-muu-liikennealuetyo"}
                         {$set {:selected-operations.$ "ya-katulupa-muu-liikennealuetyo"}}))

(defmigration clean-post-verdict-original-application-states
  {:apply-when (pos? (mongo/count :applications {:attachments {$elemMatch {:originalApplicationState
                                                                           {$in states/post-verdict-states}}}}))}
  (letfn [(do-cleanup [attachment]
            (if (contains? states/post-verdict-states (keyword (:originalApplicationState attachment)))
              (dissoc attachment :originalApplicationState)
              attachment))]
    (update-applications-array :attachments
                               do-cleanup
                               {:attachments {$elemMatch {:originalApplicationState
                                                          {$in states/post-verdict-states}}}})))

(def default-stamp
  [{:id (mongo/create-id)
    :name "Oletusleima"
    :position {:x 10 :y 200}
    :background 0
    :page :first
    :qrCode true
    :rows [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type "current-date"}]
           [{:type :backend-id}]
           [{:type :organization}]]}])

(defmigration add-default-stamp-template-to-organizations
              {:apply-when (pos? (mongo/count :organizations {:stamps {$exists false}}))}
              (doseq [organization (mongo/select :organizations {:stamps {$exists false}})]
                (mongo/update-by-id :organizations (:id organization)
                                    {$set {:stamps default-stamp}})))

(defn next-available-lp-id-for-app! [{:keys [municipality created]}]
  (let [year    (cljt/year (cljtc/from-long created))
        sequence-name (ss/join "-" ["applications" municipality year])
        counter (if (env/feature? :prefixed-id)
                  (format "9%04d" (mongo/get-next-sequence-value sequence-name))
                  (format "%05d"  (mongo/get-next-sequence-value sequence-name)))]
    (ss/join "-" ["LP" municipality year counter])))

(defn conform-application-id-for-application! [{id :id :as application}]
  (let [new-id (next-available-lp-id-for-app! application)]
    (mongo/insert :applications (assoc application :id new-id))
    (mongo/update :fs.files {:metadata.application id} {$set {:metadata.application new-id}} :multi true)
    (mongo/remove :applications id)
    (when-let [submitted-application (mongo/select-one :submitted-applications {:_id id})]
      (mongo/insert :submitted-applications (assoc submitted-application :id new-id))
      (mongo/remove :submitted-applications id))))

(defmigration conform-application-ids
  {:apply-when (pos? (mongo/count :applications {:_id #"^(?!LP-).*"}))}
  (->> (mongo/select :applications {:_id #"^(?!LP-).*"})
       (run! conform-application-id-for-application!)))

(def original-default-stamp-query
  {:stamps {$elemMatch {:name "Oletusleima"
                        :position {:x 10 :y 200}
                        :background 0
                        :page :first
                        :qrCode true
                        :rows {$all [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type "current-date"}]
                                     [{:type :backend-id}]
                                     [{:type :organization}]]
                               $size 3}}}})

(def updated-default-stamp-rows
  [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type "current-date"}]
   [{:type :backend-id}]
   [{:type :section}]
   [{:type :user}]
   [{:type :building-id}]
   [{:type :organization}]
   [{:type :extra-text
     :text ""}]])

(defmigration update-default-stamp-contents
  {:apply-when (pos? (mongo/count :organizations original-default-stamp-query))}
  (mongo/update-by-query :organizations
                         original-default-stamp-query
                         {$set {:stamps.$.rows updated-default-stamp-rows}}))

(defn tos-function-query [tos-function]
  {:organization "680-R"
   :created {$lt 1495530000000}
   :tosFunction tos-function})

(defmigration change-tos-functions-for-680
  (let [tf01 (doall (mongo/select :applications (tos-function-query "10 03 00 01") [:id]))
        tf02 (doall (mongo/select :applications (tos-function-query "10 03 00 02") [:id]))
        tf03 (doall (mongo/select :applications (tos-function-query "10 03 00 03") [:id]))
        tf04 (doall (mongo/select :applications (tos-function-query "10 03 00 04") [:id]))
        tf05 (doall (mongo/select :applications (tos-function-query "10 03 00 05") [:id]))
        tf06 (doall (mongo/select :applications (tos-function-query "10 03 00 06") [:id]))
        tf07 (doall (mongo/select :applications (tos-function-query "10 03 00 07") [:id]))
        tf08 (doall (mongo/select :applications (tos-function-query "10 03 00 08") [:id]))
        tf09 (doall (mongo/select :applications (tos-function-query "10 03 00 09") [:id]))]
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 03"}})) tf01))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 05"}})) tf02))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 07"}})) tf03))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 09"}})) tf04))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 11"}})) tf05))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 01"}})) tf06))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 02"}})) tf07))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 10"}})) tf08))
    (doall (map (fn [app] (mongo/update :applications {:_id (:id app)} {$set {:tosFunction "10 03 00 04"}})) tf09))))

; LPK-2933 When comparing ya-kayttolupa-general and ya-kayttolupa-with-tyomaastavastaava, latter has tyomaastaVastaava schema
(defmigration vaihtolavat-to-kayttolupa-general-documents-cleanup
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name :ya-kayttolupa-vaihtolavat
                                                 :documents.schema-info.name "tyomaastaVastaava"}))}
  (mongo/update-by-query :submitted-applications
                         {:primaryOperation.name :ya-kayttolupa-vaihtolavat
                          :permitSubtype "kayttolupa"
                          :documents.schema-info.name "tyomaastaVastaava"}
                         {$pull {:documents {:schema-info.name "tyomaastaVastaava"}}})
  (mongo/update-by-query :applications
                         {:primaryOperation.name :ya-kayttolupa-vaihtolavat
                          :permitSubtype "kayttolupa"
                          :documents.schema-info.name "tyomaastaVastaava"}
                         {$pull {:documents {:schema-info.name "tyomaastaVastaava"}}}))

; For certain kayttolupa subtypes, state machine is changed, thus deprecating "finished" state.
(defmigration kayttolupa-to-tyolupa-workflow-finished-state-change                ; LPK-2933
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name {$in [:ya-kayttolupa-nostotyot
                                                                              :ya-kayttolupa-kattolumien-pudotustyot
                                                                              :ya-kayttolupa-talon-julkisivutyot
                                                                              :ya-kayttolupa-talon-rakennustyot
                                                                              :ya-kayttolupa-muu-tyomaakaytto]}
                                                 :permitSubtype "kayttolupa"
                                                 :state "finished"}))}
  (let [ts (now)]
    (mongo/update-by-query :applications
                           {:primaryOperation.name {$in [:ya-kayttolupa-nostotyot ; kayttolupa-with-tyomaastavastaava
                                                         :ya-kayttolupa-kattolumien-pudotustyot
                                                         :ya-kayttolupa-talon-julkisivutyot
                                                         :ya-kayttolupa-talon-rakennustyot
                                                         :ya-kayttolupa-muu-tyomaakaytto]}
                            :permitSubtype "kayttolupa"
                            :state "finished"}
                           {$set  {:state "closed"
                                   :closed ts}
                            $push {:history {:state "closed"
                                             :ts ts
                                             :user usr/migration-user-summary}}})))

; LPK-2917 add new attribute :automatic-ok-for-attachments-enabled which is true by default
(defmigration add-automatic-ok-for-attachments-attribute-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:automatic-ok-for-attachments-enabled {$exists false}}))}
   (doseq [organization (mongo/select :organizations {:automatic-ok-for-attachments-enabled {$exists false}})]
     (mongo/update-by-id :organizations (:id organization)
                         {$set {:automatic-ok-for-attachments-enabled true}})))

(defmigration add-person-id-source-for-users-with-person-id
  {:apply-when (pos? (mongo/count :users {:personId {$type :string} :personIdSource {$exists false}}))}
  (mongo/update :users
                {:personId {$type :string} :personIdSource {$exists false}}
                {$set {:personIdSource :identification-service}}
                :multi true))

(def orphaned-company-users-query
  {:personId nil, :role :applicant, :company.role {$exists false}})

(defmigration disable-orphaned-company-users
  {:apply-when (pos? (mongo/count :users orphaned-company-users-query))}
  (mongo/update :users
                orphaned-company-users-query
                {$set {:enabled false :role :dummy}
                 $unset {:private ""}}
                :multi true))

(defmigration reset-application-archived-ts
  {:apply-when (pos? (mongo/count :applications {:archived.application {$gt 0}
                                                 :tosFunction nil
                                                 :state {$ne "canceled"}}))}
  (mongo/update-by-query :applications
                         {:archived.application {$gt 0}
                          :tosFunction nil
                          :state {$ne "canceled"}}
                         {$set {:archived.application nil}}))

(defmigration disable-orphaned-company-users-v2             ; run again for LPK-3034
  {:apply-when (pos? (mongo/count :users orphaned-company-users-query))}
  (mongo/update :users
                orphaned-company-users-query
                {$set {:enabled false :role :dummy}
                 $unset {:private ""}}
                :multi true))

(defmigration add-docstore-info
  {:apply-when (pos? (mongo/count :organizations {:docstore-info {$exists false}}))}
  (mongo/update :organizations
                {:docstore-info {$exists false}}
                {$set {:docstore-info
                       {:docStoreInUse false
                        :documentPrice 0.0
                        :organizationDescription (i18n/supported-langs-map
                                                  (constantly ""))}}}
                :multi true))

(defmigration add-digitizer-tools-settings-to-orgs
  {:apply-when (pos? (mongo/count :organizations {:digitizer-tools-enabled {$exists false}}))}
  (mongo/update :organizations
                {:digitizer-tools-enabled {$exists false}}
                {$set {:digitizer-tools-enabled false}}
                :multi true))

(def docstore-price-query
  {$or [{:docstore-info.documentPrice {$type "double"}}
        {:docstore-info.documentPrice {$gt 0 $lt 100}}]})

(defmigration change-docstore-prices-to-cents
  {:apply-when (pos? (mongo/count :organizations docstore-price-query))}
  (let [orgs (mongo/find-maps :organizations
                              docstore-price-query)]
    (doseq [{:keys [_id docstore-info]} orgs]
      (let [current-price (:documentPrice docstore-info)
            new-price (long (* current-price 100))]
        (mongo/update-by-id :organizations
                            _id
                            {$set {:docstore-info.documentPrice new-price}})))))

(def mangled-utf-to-correct-utf
  {"\u00c3\u00a4" "\u00e4",
   "\u00c3\u00b6" "\u00f6",
   "\u00c3\u00a5" "\u00e5",
   "\u00c3\u0084" "\u00c4",
   "\u00c3\u0096" "\u00d6",
   "\u00c3\u0085" "\u00c5"})

(defn ungarble-utf8-string [text]
  (reduce
    (fn [new-text [search replacement]]
      (str/replace new-text search replacement))
    text
    mangled-utf-to-correct-utf))

(def mangled-utf-regex
  (str ".*" (str/join "|" (keys mangled-utf-to-correct-utf)) ".*"))

(def suspect-user-keys
  [:firstName :lastName :street :city])

(def invalid-ut8-users-query
  {$or (map #(-> {% {$regex mangled-utf-regex}}) suspect-user-keys)})

(defmigration fix-invalid-utf8-in-users
  {:apply-when (pos? (mongo/count :users invalid-ut8-users-query))}
  (doseq [user (mongo/find-maps :users invalid-ut8-users-query)]
    (let [update-map (reduce
                       (fn [updates kw]
                         (let [orig-str (kw user)
                               new-str (ungarble-utf8-string orig-str)]
                           (if-not (= orig-str new-str)
                             (assoc updates kw new-str)
                             updates)))
                       {}
                       suspect-user-keys)]
      (when-not (empty? update-map)
        (mongo/update-by-id :users
                            (:_id user)
                            {$set update-map})))))

(defn copy-auth-id-from-invite [{{{id :id} :user} :invite invite-type :type :as auth}]
  (cond-> auth
          (and id (util/=as-kw invite-type :company)) (assoc :id id :company-role :admin)))

(defmigration allow-reader-access-for-invited-company-users
  {:apply-when (pos? (mongo/count :applications {:auth.id ""}))}
  (update-applications-array :auth copy-auth-id-from-invite {:auth.id ""}))

(defmigration lpk-3198-faulty-review-attachments
  {:apply-when (pos? (mongo/count :applications {:tasks {$elemMatch {:state "faulty_review_task"
                                                                     :faulty {$exists false}}}}))}
  (doseq [{:keys [modified tasks] :as application} (mongo/select :applications {:tasks {$elemMatch {:state "faulty_review_task"
                                                                                                    :faulty {$exists false}}}})]
    (doseq [{task-id :id} (filter #(and (util/=as-kw (:state %) :faulty_review_task)
                                        (nil? (:faulty %)))
                                  tasks)]
      (tasks/task->faulty (assoc (action/application->command application)
                                 :created modified)
                          task-id))))

(defmigration add-earliest-allowed-archiving-date-to-orgs
  {:apply-when (pos? (mongo/count :organizations {:earliest-allowed-archiving-date {$exists false}}))}
  (mongo/update :organizations
                {:earliest-allowed-archiving-date {$exists false}}
                {$set {:earliest-allowed-archiving-date 0}}
                :multi true))

(defmigration add-default-for-multiple-operations-supported-to-orgs
  {:apply-when (pos? (mongo/count :organizations {:multiple-operations-supported {$exists false}}))}
  (mongo/update :organizations
                {:multiple-operations-supported {$exists false}}
                {$set {:multiple-operations-supported true}}
                :multi true))

(defmigration status-to-integration-messages
  {:apply-when (pos? (mongo/count :integration-messages {:status {$exists false}}))}
  (mongo/update-by-query :integration-messages {:status {$exists false}} {$set {:status "done"}}))

(defmigration enable-vantaa-R-bulletins
  {:apply-when (pos? (mongo/count :organizations {:scope {$elemMatch {:permitType "R" :municipality "092" :bulletins {$exists false}}}}))}
  (mongo/update-by-query :organizations
                         {:scope {$elemMatch {:permitType "R" :municipality "092"}}}
                         {$set {:scope.$.bulletins.enabled true
                                :scope.$.bulletins.url     "http://julkipano.lupapiste.fi/vantaa"}}))

(defmigration change-oauth-callback-to-uri
  {:apply-when (pos? (mongo/count :users {:oauth.callback {$exists true}}))}
  (doseq [user (mongo/find-maps :users {:oauth.callback {$exists true}})]
    (let [parts (str/split (get-in user [:oauth :callback :success-url]) #"/")
          callback-url (str (first parts) "//" (nth parts 2))]
      (mongo/update-by-id :users
                          (:_id user)
                          {$unset {:oauth.callback ""}
                           $set {:oauth.callback-url callback-url}}))))

(defn set-missing-created-timestamp [{ts :created id :id :as task}]
  (if (nil? ts)
    (assoc task :created (.getTime (util/object-id-to-date id)))
    task))

(defmigration created-timestamps-for-backend-reviews
  {:apply-when (pos? (mongo/count :applications {:tasks.created {$type "null"}}))}
  (update-applications-array :tasks set-missing-created-timestamp {:tasks.created {$type "null"}}))


(def ^:private bg-id-regex-pattern "^([0-9a-zA-Z]+)-([a-zA-Z]+)$")

(defn- background-id-and-application-suffix
  "Return a 2-length vector containing the new background id and
  application suffix, or nil if the id and suffix cannot be obtained"
  [task]
  {:post [(or (nil? %) (and (sequential? %) (= (count %) 2)))]}
  (some->> task :data :muuTunnus :value
           (re-matches (re-pattern bg-id-regex-pattern))
           (drop 1)))

(defmigration update-task-background-ids
  {:apply-when (pos? (mongo/count :applications
                                  {:tasks {$elemMatch {:data.muuTunnus.value {$regex bg-id-regex-pattern}
                                                       :data.muuTunnusSovellus {$exists false}}}}))}
  (update-applications-array
   :tasks
   (fn [task]
     (if-let [[new-bg-id bg-application] (background-id-and-application-suffix task)]
       (-> task
           (assoc-in [:data :muuTunnus :value] new-bg-id)
           (assoc-in [:data :muuTunnusSovellus] {:value bg-application :modified nil}))
       task))
   {:tasks {$elemMatch {:data.muuTunnus.value {$regex bg-id-regex-pattern}
                        :data.muuTunnusSovellus {$exists false}}}}))

(defmigration empty-dash-only-task-background-ids
  {:apply-when (pos? (mongo/count :applications
                                  {:tasks.data.muuTunnus.value "-"}))}
  (update-applications-array
   :tasks
   (fn [task]
     (if (= "-" (-> task :data :muuTunnus :value))
       (assoc-in task [:data :muuTunnus :value] "")
       task))
   {:tasks.data.muuTunnus.value "-"}))

(def deleted-task-ids                                       ; LP-301431
  #{"591a877d28e06f222a49019c" "591a877d28e06f222a49019d" "591a877a28e06f222a490108" "591a877a28e06f222a49010a" "591a877a28e06f222a49010b" "591a877a28e06f222a49010d" "591a877a28e06f222a49010e" "591a877a28e06f222a49010f" "591a877a28e06f222a490111" "591a877d28e06f222a4901bc" "591a877e28e06f222a4901ef" "591a877e28e06f222a4901f0" "591a877e28e06f222a4901ee" "591a877e28e06f222a4901e9" "591a877e28e06f222a4901ea" "591a877e28e06f222a4901eb" "591a877e28e06f222a4901ec" "591a878128e06f222a49029a" "591a878128e06f222a49029f" "591a878128e06f222a4902a7" "591a878128e06f222a4902a8" "591a878128e06f222a4902a9" "591a878128e06f222a4902aa" "591a878128e06f222a4902af" "591a878128e06f222a4902b6" "591a878928e06f222a490350" "591a879428e06f222a49036a" "591a879528e06f222a490372" "591a879f28e06f222a4903a2" "591a87b528e06f222a4903e0" "591a87aa28e06f222a4903ac" "591a87b528e06f222a4903ce" "591a87c528e06f222a490497" "591a87c528e06f222a4904ae" "591a87c528e06f222a490498" "591a87c528e06f222a4904a9" "591a87c528e06f222a4904ac" "591a87c228e06f222a49040c" "591a87c228e06f222a49040d" "591a87c228e06f222a49040e" "591a87c228e06f222a49040f" "591a87c228e06f222a490410" "591a87d228e06f222a4905aa" "591a87d228e06f222a4905ab" "591a87d128e06f222a490546" "591a87d128e06f222a490547" "591a87d128e06f222a490545" "591a87d128e06f222a490548" "591a87d128e06f222a490549" "591a87d128e06f222a49054b" "591a87d128e06f222a49054c" "591a87d128e06f222a49054d" "591a87e328e06f222a490646" "591a87e928e06f222a490697" "591a87ef28e06f222a4906f5" "591a87ef28e06f222a4906e9" "591a87f028e06f222a49070d" "591a87f528e06f222a490727" "591a87f628e06f222a49073b" "591a880c28e06f222a4907ae" "591a881e28e06f222a4907d1" "591a882728e06f222a49080e" "591a882e28e06f222a490865" "591a882e28e06f222a49086f" "591a883628e06f222a49088b" "591a884828e06f222a4908d2" "591a884c28e06f222a4908f1" "591a885328e06f222a490905" "591a885928e06f222a490928" "591a885928e06f222a490914" "591a885b28e06f222a490959" "591a886128e06f222a490995" "591a886128e06f222a490996" "591a886028e06f222a490965" "591a886028e06f222a490967" "591a886028e06f222a490981" "591a886028e06f222a490982" "591a886128e06f222a4909ab" "591a886828e06f222a4909bd" "591a886828e06f222a4909c9" "591a886728e06f222a4909b5" "591a887128e06f222a490a4f" "591a886f28e06f222a4909f1" "591a887128e06f222a490a31" "591a887028e06f222a4909fd" "591a887028e06f222a4909fe" "591a887028e06f222a4909ff" "591a887028e06f222a490a00" "591a887128e06f222a490a3b" "591a887728e06f222a490a71" "591a887728e06f222a490a70" "591a887828e06f222a490a97" "591a887f28e06f222a490aa6" "591a887f28e06f222a490aaf" "591a888928e06f222a490ae4" "591a888828e06f222a490ad9" "591a888a28e06f222a490b15" "591a889128e06f222a490b3d" "591a889228e06f222a490b51" "591a889128e06f222a490b21" "591a889928e06f222a490b99" "591a889928e06f222a490b9a" "591a889f28e06f222a490c01" "591a889a28e06f222a490bb5" "591a88a028e06f222a490c4a" "591a88a028e06f222a490c4b" "591a88a628e06f222a490c95" "591a88a628e06f222a490c96" "591a88a628e06f222a490c98" "591a88a628e06f222a490c9b" "591a88ae28e06f222a490cec" "591a88b128e06f222a490d36" "591a88b128e06f222a490d37" "591a88b128e06f222a490d38" "591a88af28e06f222a490cfb" "591a88af28e06f222a490cfa" "591a88af28e06f222a490cfc" "591a88af28e06f222a490d19" "591a88af28e06f222a490d18" "591a88af28e06f222a490d17" "591a88b728e06f222a490d76" "591a88b628e06f222a490d62" "591a88bd28e06f222a490e00" "591a88bd28e06f222a490deb" "591a88bd28e06f222a490dec" "591a88bc28e06f222a490dce" "591a88bc28e06f222a490dcc" "591a88bc28e06f222a490dcd" "591a88bb28e06f222a490dac" "591a88bb28e06f222a490dad" "591a88bb28e06f222a490dab" "591a88bb28e06f222a490d8d" "591a88bb28e06f222a490d8e" "591a877c28e06f222a490185" "591a877d28e06f222a4901bd" "591a878028e06f222a490257" "591a877e28e06f222a4901e7" "591a877e28e06f222a4901e8" "591a877e28e06f222a4901da" "591a878328e06f222a490322" "591a878128e06f222a490299" "591a878128e06f222a4902c5" "591a878828e06f222a490331" "591a878828e06f222a490346" "591a879928e06f222a490382" "591a879a28e06f222a490391" "591a87c328e06f222a49045a" "591a87c328e06f222a490459" "591a87c228e06f222a490419" "591a87d028e06f222a490504" "591a87d028e06f222a4904f4" "591a87da28e06f222a4905dd" "591a87da28e06f222a4905dc" "591a87da28e06f222a4905c5" "591a87e328e06f222a49063c" "591a87e428e06f222a49067e" "591a87e428e06f222a49067d" "591a87e928e06f222a490696" "591a87ea28e06f222a4906cc" "591a87ea28e06f222a4906cb" "591a87f028e06f222a490719" "591a87ef28e06f222a490702" "591a87f628e06f222a490732" "591a880528e06f222a49075c" "591a880b28e06f222a4907a4" "591a882728e06f222a490840" "591a882728e06f222a490827" "591a882d28e06f222a490851" "591a883728e06f222a490899" "591a884128e06f222a4908b2" "591a884828e06f222a4908e1" "591a884728e06f222a4908c4" "591a885928e06f222a490915" "591a886028e06f222a490966" "591a886928e06f222a4909d2" "591a887028e06f222a490a01" "591a887128e06f222a490a3c" "591a887228e06f222a490a5b" "591a887228e06f222a490a5c" "591a887828e06f222a490a85" "591a888928e06f222a490ae5" "591a888928e06f222a490ae2" "591a888928e06f222a490ae3" "591a888a28e06f222a490b0b" "591a889128e06f222a490b3e" "591a889228e06f222a490b6f" "591a889328e06f222a490b79" "591a889228e06f222a490b52" "591a889228e06f222a490b53" "591a889128e06f222a490b22" "591a889128e06f222a490b23" "591a889328e06f222a490b84" "591a889328e06f222a490b83" "591a889928e06f222a490bab" "591a889f28e06f222a490c00" "591a88a028e06f222a490c49" "591a88b728e06f222a490d75" "591a88bd28e06f222a490dff" "591a88bb28e06f222a490da1"})

(defmigration cleanup-deleted-task-attachments              ; LP-301431
  {:apply-when (pos? (mongo/count :applications {:organization "305-R" :attachments.target.id {$in deleted-task-ids}}))}
  (doseq [app (mongo/select :applications {:organization "305-R" :attachments.target.id {$in deleted-task-ids}} [:attachments :organization])
          :let [task-attachments (->> (:attachments app)
                                      (filter #(contains? deleted-task-ids (get-in % [:target :id]))))]]
    (att/delete-attachments! app (map :id task-attachments))))

(defn hankkeen-kuvaus-rakennuslupa->hankkeen-kuvaus [{schema-info :schema-info data :data :as doc}]
  (cond-> doc
    (= (get-in doc [:schema-info :name]) "hankkeen-kuvaus-rakennuslupa")
    (assoc :schema-info (-> schema-info
                            (assoc  :name "hankkeen-kuvaus")
                            (dissoc :i18name))
           :data        (dissoc data :hankkeenVaativuus))))

(defmigration hankkeen-kuvaus-rakennuslupa-depricated
  {:apply-when (pos? (mongo/count :applications {:documents.schema-info.name "hankkeen-kuvaus-rakennuslupa"}))}
  (update-applications-array :documents hankkeen-kuvaus-rakennuslupa->hankkeen-kuvaus {:documents.schema-info.name "hankkeen-kuvaus-rakennuslupa"}))

(defmigration enable-automatic-review-fetch-for-all-organizations
  {:apply-when (pos? (mongo/count :organizations {:automatic-review-fetch-enabled {$exists false}}))}
  (mongo/update-by-query :organizations
                         {:automatic-review-fetch-enabled {$exists false}}
                         {$set {:automatic-review-fetch-enabled true}}))

(def backend-systems-r (util/read-edn-resource "migrations/backend_systems_r.edn"))

(defmigration set-r-organization-backend-systems
  (run! #(mongo/update :organizations
                       {:scope {$elemMatch {:municipality % :permitType "R"}} :krysp.R {$exists true}}
                       {$set {:krysp.R.backend-system (backend-systems-r %)}})
        (keys backend-systems-r)))

(defmigration recalculate-wgs84-coordinates-for-drawings
  (update-applications-array :drawings
                             add-wgs84-coordinates
                             {:drawings.geometry {$exists true, $ne ""}}))

(defmigration bulletins-text-vantaa
  {:apply-when (pos? (mongo/count :organizations {:_id "092-R" :local-bulletins-page-settings {$exists false}}))}
  (mongo/update-by-query :organizations
                         {:_id "092-R" :local-bulletins-page-settings {$exists false}}
                         {$set
                          {:local-bulletins-page-settings
                           {:texts {:fi {:heading1 "Vantaan kaupunki",
                                         :heading2 "Rakennusvalvonnan julkipanot",
                                         :caption ["Rakennuslupajaoston rakennuslupap\u00e4\u00e4t\u00f6kset annetaan julkipanon j\u00e4lkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon. Valitusaika on 30 p\u00e4iv\u00e4\u00e4."
                                                   "Viranhaltijan p\u00e4\u00e4t\u00f6ksi\u00e4 tehd\u00e4\u00e4n p\u00e4ivitt\u00e4in. Oikaisuvaatimusaika on 14 p\u00e4iv\u00e4\u00e4 p\u00e4\u00e4t\u00f6sten tiedoksiannosta."
                                                   "Julkipanolistat ovat virallisesti n\u00e4ht\u00e4viss\u00e4 maank\u00e4yt\u00f6n asiakaspalvelussa (Kielotie 13, katutaso) sek\u00e4 alla olevassa listauksessa."]},
                                    :sv {:heading1 "Vanda stad",
                                         :heading2 "Offentlig delgivning av beslut om bygglov",
                                         :caption ["Bygglovssektionens beslut om bygglov meddelas efter den offentliga delgivningen d\u00e5 de anses ha kommit till vederb\u00f6randes k\u00e4nnedom. Besv\u00e4rstiden \u00e4r 30 dagar."
                                                   "Tj\u00e4nsteinnehavarbeslut fattas dagligen. Besv\u00e4rstiden \u00e4r 14 dagar fr\u00e5n det att besluten kungjorts."
                                                   "F\u00f6rteckningarna finns offentligt fra\u03c0mlagda p\u00e5 anslagstavlan i byggnadstillsynens entr\u00e9hall och p\u00e5 listan som finns nedanf\u00f6r."]}}}}}))


(def bad-review-attachments-query {:attachments {$elemMatch {$and [{:target.type "task"}
                                                                   {:type.type-id "paatos"}
                                                                   {:latestVersion.user.username "eraajo@lupapiste.fi"}]}}})

;; Old fetched review attachments had a wrong type (paatos).
(defmigration katselmuspoytakirja-type-fix
  {:apply-when (pos? (mongo/count :applications bad-review-attachments-query))}
  (update-applications-array :attachments
                             (fn [{:keys [target type latestVersion] :as attachment}]
                               (if (and (= (:type target) "task")
                                        (= (:type-id type) "paatos")
                                        (= (get-in latestVersion [:user :username]) "eraajo@lupapiste.fi"))
                                 (assoc attachment
                                        :type {:type-group "katselmukset_ja_tarkastukset"
                                               :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"})
                                 attachment))
                             bad-review-attachments-query))

(defmigration add-docterminal-use-info
  {:apply-when (pos? (mongo/count :organizations {:docstore-info.docTerminalInUse {$exists false}}))}
  (mongo/update :organizations
                {:docstore-info.docTerminalInUse {$exists false}}
                {$set {:docstore-info.docTerminalInUse false}}
                :multi true))

(defmigration add-docterminal-allowed-attachment-types
  {:apply-when (pos? (mongo/count :organizations {:docstore-info.allowedTerminalAttachmentTypes {$exists false}}))}
  (mongo/update :organizations
                {:docstore-info.allowedTerminalAttachmentTypes {$exists false}}
                {$set {:docstore-info.allowedTerminalAttachmentTypes []}}
                :multi true))

(def missing-onkalo-file-id-query
  {:attachments {$elemMatch {:metadata.tila :arkistoitu
                             :latestVersion.onkaloFileId {$exists false}}}})

(defmigration add-onkalo-file-id-to-attachments
  {:apply-when (pos? (mongo/count :applications missing-onkalo-file-id-query))}
  (update-applications-array :attachments
                             (fn [{:keys [id latestVersion versions metadata] :as attachment}]
                               (if (and (= :arkistoitu (keyword (:tila metadata)))
                                        (not (:onkaloFileId latestVersion)))
                                 (let [lv (-> (last versions)
                                              (assoc :onkaloFileId id))]
                                   (-> (assoc-in attachment [:latestVersion :onkaloFileId] id)
                                       (assoc :versions (-> (drop-last versions)
                                                            (concat [lv])))))
                                 attachment))
                             missing-onkalo-file-id-query))

(def archiving-project-file-query
  {:permitType "ARK"
   :attachments {$elemMatch {:metadata.tila :arkistoitu
                             :latestVersion.fileId {$type "string"}}}})

(defmigration remove-archived-archiving-project-files
  {:apply-when (pos? (mongo/count :applications archiving-project-file-query))}
  (doseq [app (mongo/select :applications archiving-project-file-query [:_id :attachments :permitType])]
    (doseq [{:keys [metadata latestVersion] :as attachment} (:attachments app)]
      (when (and (= "ARK" (:permitType app))
                 (= :arkistoitu (keyword (:tila metadata)))
                 (:onkaloFileId latestVersion))
        (att/delete-archived-attachments-files-from-mongo! app attachment)))))

(defn review-muutunnus-equality-fields [task]
  (when-let [muu-tunnus (get-in task [:data :muuTunnus :value])]
    (->> [muu-tunnus
          (get-in task [:data :muuTunnusSovellus :value])
          (get-in task [:state])]
         (remove util/empty-or-nil?)
         not-empty)))

(defn get-duplicate-task-ids-by [equality-fields-getter {tasks :tasks}]
  (-> (reduce (fn [{processed :processed :as result} task]
                (if-let [fields (equality-fields-getter task)]
                  (if (processed fields)
                    (update result :duplicate-task-ids conj (:id task))
                    (update result :processed conj fields))
                  result))
              {:duplicate-task-ids #{} :processed #{}}
              tasks)
      :duplicate-task-ids))

(defn cleanup-task-attachments-for-removed-tasks! [{attachments :attachments :as app} deleted-task-ids]
  (->> attachments
       (filter (fn->> :target :id (contains? deleted-task-ids)))
       (map :id)
       (att/delete-attachments! app)))

(defn remove-tasks-by-ids! [{app-id :id} task-ids]
  (mongo/update :applications {:_id app-id} {$pull {:tasks {:id {$in task-ids}}}}))

(defmigration remove-duplicate-task-ids-by-muutunnus
  (->> (mongo/select :applications {:permitType "R" :tasks.data.muuTunnus.value {$exists true $ne ""}} [:tasks :attachments])
       (reduce (fn [counter app]
                 (let [duplicate-task-ids (get-duplicate-task-ids-by review-muutunnus-equality-fields app)]
                   (remove-tasks-by-ids! app duplicate-task-ids)
                   (cleanup-task-attachments-for-removed-tasks! app duplicate-task-ids)
                   (cond-> counter (not-empty duplicate-task-ids) inc)))
               0)))

(defmigration permit-subtype-required-key                   ; some 140 applications are missing permitSubtype key, add as nil
  {:apply-when (pos? (mongo/count :applications {:permitSubtype {$exists false}}))}
  (mongo/update-by-query :submitted-applications {:permitSubtype {$exists false}} {$set {:permitSubtype nil}})
  (mongo/update-by-query :applications {:permitSubtype {$exists false}} {$set {:permitSubtype nil}}))

(defmigration pate-to-matti-integration-messages
  {:apply-when (pos? (mongo/count :integration-messages {:partner "pate"}))}
  (mongo/update-by-query :integration-messages {:partner "pate"} {$set {:partner "matti"}}))

(defn id-to-file-id [attachment]
  (set/rename-keys attachment {:id :fileId}))

(defmigration bulletin-comment-attachments-fileids
  {:apply-when (pos? (mongo/count :application-bulletin-comments {:attachments.id {$exists true}}))}
  (doseq [comment (mongo/select :application-bulletin-comments {:attachments.id {$exists true}})
          :let [attachments (->> (:attachments comment)
                                 (map id-to-file-id)
                                 (map #(dissoc % :metadata)))]]
    (when-let [err (some bulletins/comment-file-checker attachments)]
      (throw (ex-info "bulletin-comment migration failure" {:err (pr-str err) :comment (:id comment)})))
    (mongo/update-by-id :application-bulletin-comments (:id comment) {$set {:attachments attachments}})))

(defmigration bulletin-comment-attachments-metadata
  {:apply-when (pos? (mongo/count :application-bulletin-comments {:attachments.metadata {$exists true}}))}
  (doseq [comment (mongo/select :application-bulletin-comments {:attachments.metadata {$exists true}})
          :let [attachments (->> (:attachments comment)
                                 (map #(dissoc % :metadata)))]]
    (when-let [err (some bulletins/comment-file-checker attachments)]
      (throw (ex-info "bulletin-comment-metadata migration failure" {:err (pr-str err) :comment (:id comment)})))
    (mongo/update-by-id :application-bulletin-comments (:id comment) {$set {:attachments attachments}})))

(defn fix-company-auth
  "Fix some legacy company invites, which are missing 'company-role' and possibly 'id'"
  [{:keys [type company-role invite id] :as auth}]
  (if (and (= "company" type) (nil? company-role) (map? invite))
    (assoc auth :company-role "admin" :id (get-in invite [:user :id] id))
    auth))

(defmigration legacy-company-auth-invites
  {:apply-when (pos? (mongo/count :applications {:auth
                                                 {$elemMatch
                                                  {:type "company",
                                                   :company-role {$exists false},
                                                   :invite {$exists true}}}}))}
  (update-applications-array :auth fix-company-auth {:auth
                                                     {$elemMatch
                                                      {:type "company",
                                                       :company-role {$exists false},
                                                       :invite {$exists true}}}}))

(defmigration add-document-request-info
  {:apply-when (pos? (mongo/count :organizations {:docstore-info.documentRequest {$exists false}}))}
  (mongo/update-by-query :organizations
                         {:docstore-info.documentRequest {$exists false}}
                         {$set {:docstore-info.documentRequest.enabled false
                                :docstore-info.documentRequest.email ""
                                :docstore-info.documentRequest.instructions (i18n/supported-langs-map (constantly ""))}}))

(defmigration remove-foreman-app-bulletins
  {:apply-when (pos? (mongo/count :application-bulletins {:versions.primaryOperation.name "tyonjohtajan-nimeaminen-v2"}))}
  (mongo/remove-many :application-bulletins {:versions.primaryOperation.name "tyonjohtajan-nimeaminen-v2"}))

(defmigration registry-notification-change
  {:apply-when (pos? (mongo/count :users {:notification.title   "Muutos Lupapisteen rekisteri- ja tietosuojaselosteeseen"
                                          :notification.message #"liiketoimintajohtaja"}))}
  (mongo/update-by-query :users
                         {:notification.title   "Muutos Lupapisteen rekisteri- ja tietosuojaselosteeseen"
                          :notification.message #"liiketoimintajohtaja"}
                         {$set {:notification.message "Lupapisteen henkil\u00f6rekisterin pit\u00e4j\u00e4 vaihtui 1.5.2017 Solita Oy:st\u00e4 Evolta Oy:ksi. Muutos ei vaikuta k\u00e4ytt\u00f6ehtoihin. Lis\u00e4tietoja asiasta antaa Niina Syrj\u00e4rinne, puh. 0400 613 756."}}))

(defn application-owner-as-creator [{auth :auth :as application}]
  (-> (util/find-first (comp #{"owner"} :role) auth)
      (usr/summary)
      (dissoc :role :type)))

(defn owner-auth-as-writer [{role :role type :type :as auth-entry}]
  (cond-> auth-entry
    (= "owner" role) (assoc :role "writer")
    (= "owner" type) (dissoc :type)))

(defn update-application-owner-to-writer [collection {app-id :id :as application}]
  (mongo/update-by-id collection app-id {$set {:creator (application-owner-as-creator application)
                                               :auth    (map owner-auth-as-writer (:auth application))}}))

(defmigration applications-owner-to-writer
  {:apply-when (pos? (mongo/count :applications {:auth.role "owner"}))}
  (doseq [collection [:applications :submitted-applications]]
    (->> (mongo/select collection {} [:auth])
         (run! (partial update-application-owner-to-writer collection)))))

(defmigration company-default-billing-type
  {:apply-when (pos? (mongo/count :companies {:billingType {$exists false}}))}
  (mongo/update-by-query :companies {:billingType {$exists false}} {$set {:billingType "yearly"}}))

(defmigration remove-extra-file-id-and-content-type-keys
  {:apple-when (pos? (mongo/count :applications {$or [{:contentType {$exists true}} {:fileId {$exists true}}]}))}
  (mongo/update-by-query :applications
                         {$or [{:contentType {$exists true}} {:fileId {$exists true}}]}
                         {$unset {:contentType ""
                                  :fileId ""}}))

(defn- get-document-schema-names-for-operation [organization operation]
  (let [op-info (op/operations (keyword (:name operation)))]
    (->> (when (not-empty (:org-required op-info)) ((apply juxt (:org-required op-info)) organization))
         (concat (:required op-info)))))

(defn waste-document-fix [lp-id]
  (let [application (domain/get-application-no-access-checking lp-id)
        schema-version (:schema-version application)
        organization (org/get-organization (:organization application))
        waste-schema-name (waste-schemas/construction-waste-plan-for-organization organization)
        application-does-not-contain-waste-plan? (when application
                                                   (->> application
                                                        :documents
                                                        (filter #(-> % :schema-info :name (= waste-schema-name)))
                                                        (empty?)))
        [op-name _] (when application-does-not-contain-waste-plan? ;; get the operation that needs the waste document
                      (->> (cons (:primaryOperation application) (:secondaryOperations application))
                           (reduce #(assoc %1 (keyword (:name %2)) (get-document-schema-names-for-operation organization %2)) {})
                           (filter (fn [[_ v]] ((set v) waste-schema-name)))
                           (first)))
        waste-document (when op-name
                         (->> (schemas/get-schema schema-version waste-schema-name)
                              (application/make-document application op-name (sade.core/now) nil)))]
    (when (and waste-document (not= (:state application) "canceled"))
      (action/update-application (action/application->command application)
                                 {$push {:documents waste-document}}))))

(defn get-correct-rakennuspaikka-doc [docs-by-op]
  (loop [return-op nil
         return-doc-name nil
         docs-by-op docs-by-op]
    (let [[op docs] (first docs-by-op)
          doc-set (set docs)]
      (cond
        (empty? docs-by-op) [return-op return-doc-name]
        (doc-set "rakennuspaikka") [op "rakennuspaikka"]
        (doc-set "rakennuspaikka-ilman-ilmoitusta") (recur op "rakennuspaikka-ilman-ilmoitusta" (rest docs-by-op))
        :else (recur return-op return-doc-name (rest docs-by-op))))))

(defn rakennuspaikka-document-fix [lp-id]
  (let [application (domain/get-application-no-access-checking lp-id)
        schema-version (:schema-version application)
        organization (:organization application)
        application-does-not-contain-rakennuspaikka? (when application
                                                       (->> application
                                                            :documents
                                                            (filter #(-> % :schema-info :type (keyword) (= :location)))
                                                            (empty?)))
        [op-name doc-name] (when application-does-not-contain-rakennuspaikka? ;; get the operation that needs the rakennuspaikka document
                             (->> (cons (:primaryOperation application) (:secondaryOperations application))
                                  (reduce #(assoc %1 (keyword (:name %2)) (get-document-schema-names-for-operation organization %2)) {})
                                  (filter (fn [[_ v]] (let [doc-set (set v)] (or (doc-set "rakennuspaikka") (doc-set "rakennuspaikka-ilman-ilmoitusta")))))
                                  (get-correct-rakennuspaikka-doc)))
        rakennuspaikka-schema (when op-name (schemas/get-schema schema-version (keyword doc-name)))
        rakennuspaikka-document (when rakennuspaikka-schema
                                  (application/make-document application op-name (sade.core/now) nil rakennuspaikka-schema))]
    (when (and rakennuspaikka-document (not= (:state application) "canceled"))
      (action/update-application (action/application->command application)
                                 {$push {:documents rakennuspaikka-document}}))))

(def replace-operation-broken-apps
  ["LP-536-2018-00148" "LP-837-2018-00915" "LP-423-2018-00130" "LP-837-2017-03424" "LP-753-2017-00828"
   "LP-257-2017-01621" "LP-936-2018-00006" "LP-020-2018-00115" "LP-498-2018-00029" "LP-734-2018-00112"
   "LP-271-2018-00066" "LP-445-2018-00066" "LP-091-2018-01471" "LP-529-2018-00228" "LP-109-2018-00263"
   "LP-240-2018-00028" "LP-091-2018-00634" "LP-753-2018-90051" "LP-186-2018-00331" "LP-092-2018-90144"
   "LP-536-2018-00198" "LP-491-2018-00241" "LP-153-2018-00120" "LP-430-2017-00253" "LP-422-2018-00051"
   "LP-927-2018-00134" "LP-020-2018-00118" "LP-611-2017-00281" "LP-710-2017-01158" "LP-678-2018-00168"
   "LP-908-2018-00181" "LP-562-2018-00070" "LP-091-2018-02355" "LP-445-2018-00061" "LP-153-2018-00086"
   "LP-020-2018-00117" "LP-536-2018-00142" "LP-740-2018-00021" "LP-491-2018-00523" "LP-108-2018-00009"
   "LP-423-2017-00217" "LP-837-2018-00222" "LP-430-2018-00172" "LP-261-2018-00088" "LP-091-2018-02009"
   "LP-529-2018-00195" "LP-887-2018-00014" "LP-680-2018-00182" "LP-208-2018-00119" "LP-702-2018-00008"
   "LP-623-2018-00060" "LP-261-2018-00043" "LP-710-2017-01182" "LP-858-2018-00147" "LP-086-2018-00084"
   "LP-106-2017-01369" "LP-908-2018-00190" "LP-491-2018-00485" "LP-837-2018-00833" "LP-249-2018-00049"
   "LP-249-2017-00361" "LP-244-2018-00099" "LP-638-2018-00209" "LP-322-2018-00248" "LP-261-2018-00101"
   "LP-781-2018-00009" "LP-300-2018-00031" "LP-092-2018-01516" "LP-322-2018-00280" "LP-895-2017-00637"
   "LP-444-2018-00454" "LP-102-2018-00022" "LP-507-2018-00157" "LP-702-2018-00003" "LP-444-2018-00492"
   "LP-143-2018-90002" "LP-092-2018-01749" "LP-491-2018-00374" "LP-285-2018-00138" "LP-322-2018-00153"
   "LP-091-2017-06303" "LP-980-2017-00014" "LP-322-2017-00242" "LP-895-2018-00254" "LP-895-2018-00155"
   "LP-092-2018-90134" "LP-091-2018-01712" "LP-543-2018-00219" "LP-182-2018-00078" "LP-322-2018-00124"
   "LP-536-2018-00094" "LP-740-2018-00232" "LP-300-2018-00028" "LP-075-2018-00088" "LP-444-2018-00541"
   "LP-734-2018-00361" "LP-740-2018-00164" "LP-305-2018-00333" "LP-507-2017-00117" "LP-623-2018-00071"
   "LP-858-2018-00260" "LP-753-2018-90053" "LP-167-2018-00272" "LP-244-2018-00149" "LP-211-2017-00220"
   "LP-109-2018-00107" "LP-892-2018-00041" "LP-729-2018-00046" "LP-167-2018-00218" "LP-271-2018-00051"
   "LP-858-2018-00291" "LP-491-2017-02048" "LP-092-2018-00874" "LP-638-2018-00289" "LP-020-2017-00318"
   "LP-753-2018-90034" "LP-444-2017-02647" "LP-444-2017-01785" "LP-475-2018-00062" "LP-109-2018-00336"
   "LP-430-2017-00250" "LP-297-2018-00365" "LP-091-2018-00302" "LP-142-2018-90010" "LP-297-2018-00366"
   "LP-430-2018-00181" "LP-091-2018-01862" "LP-908-2017-00643" "LP-562-2018-00104" "LP-091-2018-02080"
   "LP-837-2018-00024" "LP-758-2018-00085" "LP-734-2018-00294" "LP-081-2018-00049" "LP-211-2017-00257"
   "LP-592-2017-00106" "LP-092-2018-01477" "LP-790-2015-00313" "LP-615-2018-00191" "LP-322-2018-00125"
   "LP-734-2017-01428" "LP-491-2018-00177" "LP-837-2017-03274" "LP-265-2018-00013" "LP-837-2016-01060"
   "LP-425-2018-00027" "LP-046-2018-00031" "LP-297-2018-00408" "LP-765-2018-90020" "LP-740-2018-00171"
   "LP-245-2018-00254" "LP-543-2018-00323" "LP-908-2017-00631" "LP-109-2018-00278" "LP-092-2018-01390"
   "LP-092-2018-90113" "LP-245-2018-00274" "LP-047-2018-00040" "LP-020-2018-00116" "LP-081-2018-00064"
   "LP-109-2018-00134" "LP-097-2018-00021" "LP-091-2018-01964" "LP-505-2018-00116" "LP-508-2018-00032"
   "LP-541-2018-00027" "LP-245-2018-00285"])

(defmigration replace-operation-fix-waste-document
  (map waste-document-fix broken-apps))

(defmigration replace-operation-fix-rakennuspaikka-document
  (map rakennuspaikka-document-fix broken-apps))

(defmigration jatkoaikalupa-state-to-ready
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name {$in [:raktyo-aloit-loppuunsaat
                                                                              :jatkoaika]}
                                                 :state {$in [:verdictGiven
                                                              :constructionStarted
                                                              :closed]}}))}
  (let [ts (now)]
    (mongo/update-by-query :applications
                           {:primaryOperation.name {$in [:raktyo-aloit-loppuunsaat
                                                         :jatkoaika]}
                            :state {$in [:verdictGiven
                                         :constructionStarted
                                         :closed]}}
                           {$set  {:state "ready"}
                            $push {:history {:state "ready"
                                             :ts ts
                                             :user usr/migration-user-summary}}})))

(defmigration muutoslupa-state-to-ready
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name {$exists true}
                                                 :permitSubtype "muutoslupa"
                                                 :state {$in [:verdictGiven
                                                              :constructionStarted
                                                              :inUse
                                                              :closed]}}))}
  (let [ts (now)]
    (mongo/update-by-query :applications
                           {:primaryOperation.name {$exists true}
                            :permitSubtype "muutoslupa"
                            :state {$in [:verdictGiven
                                         :constructionStarted
                                         :inUse
                                         :closed]}}
                           {$set  {:state "ready"}
                            $push {:history {:state "ready"
                                             :ts ts
                                             :user usr/migration-user-summary}}})))

(defmigration add-history-entry-for-extinct-jatkoaika
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name {$in [:raktyo-aloit-loppuunsaat
                                                                              :jatkoaika]}
                                                 :state "extinct"
                                                 :history.state {$ne "ready"}}))}
  (let [ts (now)]
    (mongo/update-by-query :applications
                           {:primaryOperation.name {$in [:raktyo-aloit-loppuunsaat
                                                         :jatkoaika]}
                            :state "extinct"
                            :history.state {$ne "ready"}}
                           {$push {:history {:state "ready"
                                             :ts ts
                                             :user usr/migration-user-summary}}})))

(defmigration add-history-entry-for-appealed-muutoslupa
  {:apply-when (pos? (mongo/count :applications {:primaryOperation.name {$exists true}
                                                 :permitSubtype "muutoslupa"
                                                 :state "appealed"
                                                 :history.state {$ne "ready"}}))}
  (let [ts (now)]
    (mongo/update-by-query :applications
                           {:primaryOperation.name {$exists true}
                            :permitSubtype "muutoslupa"
                            :state "appealed"
                            :history.state {$ne "ready"}}
                           {$push {:history {:state "ready"
                                             :ts ts
                                             :user usr/migration-user-summary}}})))

(defmigration remove-handlers-from-drafts
  {:apply-when (pos? (mongo/count :applications {:state      "draft"
                                                 :handlers.0 {$exists true}}))}
  (mongo/update-by-query :applications
                         {:state      "draft"
                          :handlers.0 {$exists true}}
                         {$set {:handlers []}}))

;;
;; ****** NOTE! ******
;;  1) When you are writing a new migration that goes through subcollections
;;     in the collections "Applications" and "Submitted-applications"
;;     do not manually write like this
;;       (doseq [collection [:applications :submitted-applications] ...)
;;     but use the "update-applications-array" function existing in this namespace.
;;
;;  2) Check if migration needs to be done for "bulletins" (julkipano) also. See update-bulletin-versions.
;;
;; *******************
