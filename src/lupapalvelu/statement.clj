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

(def statement-states #{:requested :draft :given :replyable :replied})
(def post-given-states #{:given :replyable :replied})
(def pre-given-states (clojure.set/difference statement-states post-given-states))
(def post-repliable-states #{:replyable :replied})
(def pre-repliable-states (clojure.set/difference statement-states post-repliable-states))

(def- statement-statuses ["puoltaa" "ei-puolla" "ehdoilla"])
;; Krysp Yhteiset 2.1.5+
(def- statement-statuses-more-options
  (vec (concat statement-statuses 
               ["ei-huomautettavaa" "ehdollinen" "puollettu" "ei-puollettu" "ei-lausuntoa" 
                "lausunto" "kielteinen" "palautettu" "poydalle"])))

(def StatementGiver {:userId                          sc/Str
                     (sc/optional-key :id)            sc/Str
                     :text                            sc/Str
                     :email                           schemas/Email
                     :name                            sc/Str})

(def Reply          {:editor-id                       sc/Str
                     (sc/optional-key :saateText)     sc/Str
                     :nothing-to-add                  sc/Bool
                     (sc/optional-key :text)          sc/Str})

(def Statement      {:id                              sc/Str
                     :state                           (apply sc/enum statement-states)
                     (sc/optional-key :saateText)     sc/Str
                     (sc/optional-key :status)        (apply sc/enum statement-statuses-more-options)
                     (sc/optional-key :text)          sc/Str
                     (sc/optional-key :dueDate)       schemas/Timestamp
                     (sc/optional-key :requested)     schemas/Timestamp
                     (sc/optional-key :given)         schemas/Timestamp
                     (sc/optional-key :reminder-sent) schemas/Timestamp
                     (sc/optional-key :modified)      schemas/Timestamp
                     (sc/optional-key :modify-id)     sc/Str
                     (sc/optional-key :editor-id)     sc/Str
                     (sc/optional-key :reply)         Reply
                     :person                          StatementGiver
                     (sc/optional-key :metadata)      {sc/Any sc/Any}})

(defn create-statement [now metadata saate-text due-date person]
  (sc/validate Statement
               (cond-> {:id        (mongo/create-id)
                        :person    person
                        :requested now
                        :state     :requested}
                 saate-text     (assoc :saateText saate-text)
                 due-date       (assoc :dueDate due-date)
                 (seq metadata) (assoc :metadata metadata))))

(defn get-statement [{:keys [statements]} id]
  (util/find-by-id id statements))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= (user/canonize-email statement-email) (user/canonize-email user-email))
      (fail :error.not-statement-owner))))

(defn authority-or-statement-owner-applicant [{{role :role} :user :as command} application]
  (when-not (or
              (= :authority (keyword role))
              (and (= :applicant (keyword role)) (nil? (statement-owner command application))))
    (fail :error.not-authority-or-statement-owner-applicant)))

(defn statement-replyable [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword #{:replyable})
    (fail :error.statement-is-not-replyable)))

(defn reply-visible [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword post-repliable-states)
    (fail :error.statement-reply-is-not-visible)))

(defn reply-not-visible [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword pre-repliable-states)
    (fail :error.statement-reply-is-already-visible)))

(defn statement-not-given [{{:keys [statementId]} :data} application]
  (when-not (->> statementId (get-statement application) :state keyword pre-given-states)
    (fail :error.statement-already-given)))

(defn statement-given [{{:keys [statementId]} :data} application]
  (when-not (->> statementId (get-statement application) :state keyword post-given-states)
    (fail :error.statement-not-given)))

(defn replies-enabled [command {permit-type :permitType :as application}]
  (when-not (#{"YM" "YL" "VVVL" "MAL" "YI"} permit-type)
    (fail :error.organization-has-not-enabled-statement-replies)))

(defn- update-statement [statement modify-id prev-modify-id & updates]
  (if (or (= prev-modify-id (:modify-id statement)) (nil? (:modify-id statement)))
    (->> (apply assoc statement :modified (now) :modify-id modify-id updates)
         (util/strip-nils)
         (sc/validate Statement))
    (fail! :error.statement-updated-after-last-save :statementId (:id statement))))

(defn update-draft [statement text status modify-id prev-modify-id editor-id]
  (update-statement statement modify-id prev-modify-id :state :draft :text text :status status :editor-id editor-id))

(defn give-statement [statement text status modify-id prev-modify-id editor-id]
  (update-statement statement modify-id prev-modify-id :state :given :text text :status status :given (now) :editor-id editor-id))

(defn update-reply-draft [{reply :reply :as statement} text nothing-to-add modify-id prev-modify-id editor-id]
  (->> (assoc reply :text text :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id prev-modify-id :state :replyable :reply)))

(defn reply-statement [{reply :reply :as statement} text nothing-to-add modify-id prev-modify-id editor-id]
  (->> (assoc reply :text (when-not nothing-to-add text) :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id prev-modify-id :state :replied :reply)))

(defn request-for-reply [{reply :reply modify-id :modify-id :as statement} text user-id]
  (->> (assoc reply :saateText text :nothing-to-add false :editor-id user-id)
       (update-statement statement modify-id modify-id :state :replyable :reply)))

;;
;; Statement givers
;;

(defn fetch-organization-statement-givers [org-id]
  (let [organization (organization/get-organization org-id)
        permitPersons (or (:statementGivers organization) [])]
    (ok :data permitPersons)))

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
