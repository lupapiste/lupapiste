(ns lupapalvelu.statement
  (:require [clojure.set]
            [schema.core :as sc]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.schemas :as schemas]
            [sade.strings :as ss]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]))

;;
;; Common
;;

(def statement-states #{:requested :draft :given :responded})
(def post-given-states #{:given :responded})
(def pre-given-states (clojure.set/difference statement-states post-given-states))

(def- statement-statuses ["puoltaa" "ei-puolla" "ehdoilla"])
;; Krysp Yhteiset 2.1.5+
(def- statement-statuses-more-options
  (vec (concat statement-statuses 
               ["ei-huomautettavaa" "ehdollinen" "puollettu" "ei-puollettu" "ei-lausuntoa" 
                "lausunto" "kielteinen" "palautettu" "poydalle"])))

(def StatementGiver {:userId                          sc/Str
                     :id                              sc/Str
                     :text                            sc/Str
                     :email                           schemas/Email
                     :name                            sc/Str})

(def Statement      {:id                              sc/Str
                     :state                           (apply sc/enum statement-states)
                     (sc/optional-key :status)        (apply sc/enum statement-statuses-more-options)
                     (sc/optional-key :text)          sc/Str
                     (sc/optional-key :requested)     sc/Num
                     (sc/optional-key :given)         sc/Num
                     (sc/optional-key :reminder-sent) sc/Num
                     (sc/optional-key :modified)      sc/Num
                     (sc/optional-key :modify-id)     sc/Str
                     :person                          StatementGiver
                     (sc/optional-key :metadata)      {}})

(defn create-statement [now metadata person]
  (sc/validate Statement
               (cond-> {:id        (mongo/create-id)
                        :person    person
                        :requested now
                        :state     :requested}
                 (seq metadata) (assoc :metadata metadata))))

(defn get-statement [{:keys [statements]} id]
  (first (filter #(= id (:id %)) statements)))

(defn statement-exists [{{:keys [statementId]} :data} application]
  (when-not (get-statement application statementId)
    (fail :error.no-statement :statementId statementId)))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= (user/canonize-email statement-email) (user/canonize-email user-email))
      (fail :error.not-statement-owner))))

(defn authority-or-statement-owner-applicant [{{role :role} :user :as command} application]
  (when-not (or
              (= :authority (keyword role))
              (and (= :applicant (keyword role)) (nil? (statement-owner command application))))
    (fail :error.not-authority-or-statement-owner-applicant)))

(defn statement-given? [application statementId]
  (->> statementId (get-statement application) :state keyword post-given-states))

(defn statement-not-given [{{:keys [statementId]} :data} application]
  (when (statement-given? application statementId)
    (fail :error.statement-already-given)))

(defn- update-statement [statement modify-id prev-modify-id & updates]
  (if (or (= prev-modify-id (:modify-id statement)) (nil? (:modify-id statement)))
    (->> (apply assoc statement :modified (now) :modify-id modify-id updates)
         (remove (comp util/empty-or-nil? val))
         (into {})
         (sc/validate Statement))
    (fail! :error.statement-updated-after-last-save :statementId (:id statement))))

(defn update-draft [statement text status modify-id prev-modify-id]
  (update-statement statement modify-id prev-modify-id :state :draft :text text :status status))

(defn give-statement [statement text status modify-id prev-modify-id]
  (update-statement statement modify-id prev-modify-id :state :given :text text :status status :given (now)))

;;
;; Statuses
;;


(defn possible-statement-statuses [application]
  (let [{permit-type :permitType municipality :municipality} application
        extra-statement-statuses-allowed? (permit/get-metadata permit-type :extra-statement-selection-values)
        organization (organization/resolve-organization municipality permit-type)
        version (get-in organization [:krysp (keyword permit-type) :version])
        yht-version (if version (mapping-common/get-yht-version permit-type version) "0.0.0")]
    (if (and extra-statement-statuses-allowed? (util/version-is-greater-or-equal yht-version {:major 2 :minor 1 :micro 5}))
      (set statement-statuses-more-options)
      (set statement-statuses))))
