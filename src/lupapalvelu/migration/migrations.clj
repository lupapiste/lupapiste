(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.operations :as op]
            [clojure.walk :as walk]
            [sade.util :refer [dissoc-in postwalk-map]]
            [sade.common-reader :refer [strip-nils]]))

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

(defn verdict-to-verdics [{verdict :verdict}]
  {$set {:verdicts (map domain/->paatos verdict)}
   $unset {:verdict 1}})

(defmigration verdicts-migraation
  {:apply-when (pos? (mongo/count  :applications {:verdict {$exists true}}))}
  (let [applications (mongo/select :applications {:verdict {$exists true}})]
    (doall (map #(mongo/update-by-id :applications (:id %) (verdict-to-verdics %)) applications))))


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
