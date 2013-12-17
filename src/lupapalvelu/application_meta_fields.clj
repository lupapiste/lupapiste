(ns lupapalvelu.application-meta-fields
  (:require [clojure.string :as s]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.neighbors :as neighbors]
            [lupapalvelu.core :refer :all]
;            [lupapalvelu.document.canonical-common :refer [by-type]]
;            [sade.util :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]))

(defn get-applicant-name [_ app]
  (if (:infoRequest app)
    (let [{first-name :firstName last-name :lastName} (first (domain/get-auths-by-role app :owner))]
      (str first-name \space last-name))
    (when-let [body (:data (domain/get-applicant-document app))]
      (if (= (get-in body [:_selected :value]) "yritys")
        (get-in body [:yritys :yritysnimi :value])
        (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
          (str (:value first-name) \space (:value last-name)))))))

(defn get-application-operation [app]
  (first (:operations app)))

(defn- count-unseen-comment [user app]
  (let [last-seen (get-in app [:_comments-seen-by (keyword (:id user))] 0)]
    (count (filter (fn [comment]
                     (and (> (:created comment) last-seen)
                          (not= (get-in comment [:user :id]) (:id user))
                          (not (s/blank? (:text comment)))))
                   (:comments app)))))

(defn- count-unseen-statements [user app]
  (if-not (:infoRequest app)
    (let [last-seen (get-in app [:_statements-seen-by (keyword (:id user))] 0)]
      (count (filter (fn [statement]
                       (and (> (or (:given statement) 0) last-seen)
                            (not= (ss/lower-case (get-in statement [:person :email])) (ss/lower-case (:email user)))))
                     (:statements app))))
    0))

(defn- count-unseen-verdicts [user app]
  (if (and (= (:role user) "applicant") (not (:infoRequest app)))
    (let [last-seen (get-in app [:_verdicts-seen-by (keyword (:id user))] 0)]
      (count (filter (fn [verdict] (> (or (:timestamp verdict) 0) last-seen)) (:verdicts app))))
    0))

(defn- count-attachments-requiring-action [user app]
  (if-not (:infoRequest app)
    (let [count-attachments (fn [state] (count (filter #(and (= (:state %) state) (seq (:versions %))) (:attachments app))))]
      (case (keyword (:role user))
        :applicant (count-attachments "requires_user_action")
        :authority (count-attachments "requires_authority_action")
        0))
    0))

(defn- count-document-modifications-per-doc [user app]
  (if (and (env/feature? :docIndicators) (= (:role user) "authority") (not (:infoRequest app)))
    (into {} (map (fn [doc] [(:id doc) (model/modifications-since-approvals doc)]) (:documents app)))
    {}))


(defn- count-document-modifications [user app]
  (if (and (env/feature? :docIndicators) (= (:role user) "authority") (not (:infoRequest app)))
    (reduce + 0 (vals (:documentModificationsPerDoc app)))
    0))

(defn- indicator-sum [_ app]
  (reduce + (map (fn [[k v]] (if (#{:documentModifications :unseenStatements :unseenVerdicts :attachmentsRequiringAction} k) v 0)) app)))

(def meta-fields [{:field :applicant :fn get-applicant-name}
                  {:field :neighbors :fn neighbors/normalize-neighbors}
                  {:field :documentModificationsPerDoc :fn count-document-modifications-per-doc}
                  {:field :documentModifications :fn count-document-modifications}
                  {:field :unseenComments :fn count-unseen-comment}
                  {:field :unseenStatements :fn count-unseen-statements}
                  {:field :unseenVerdicts :fn count-unseen-verdicts}
                  {:field :attachmentsRequiringAction :fn count-attachments-requiring-action}
                  {:field :indicators :fn indicator-sum}])

(defn with-meta-fields [user app]
  (reduce (fn [app {field :field f :fn}] (assoc app field (f user app))) app meta-fields))


(defn enrich-with-link-permit-data [app]
  (let [app-id (:id app)
        resp (mongo/select :app-links {:link {$in [app-id]}})]
    (if (seq resp)
      ;; Link permit data was found
      (let [convert-fn (fn [link-data]
                         (let [link-array (:link link-data)
                               app-index (.indexOf link-array app-id)
                               link-permit-id (link-array (if (= 0 app-index) 1 0))
                               link-permit-type (:linkpermittype ((keyword link-permit-id) link-data))]
                           (if (= (:type ((keyword app-id) link-data)) "application")

                             ;; TODO: Jos viiteluvan tyyppi on myös jatkolupa, niin sitten :operation pitaa hakea
                             ;;       viela kauempaa, eli viiteluvan viiteluvalta. Eli looppia tahan?
                             ;; TODO: Jos viitelupa on kuntalupatunnus, ei saada operaatiota!
                             ;;
                             (let [link-permit-app-op (when (= link-permit-type "lupapistetunnus")
                                                        (-> (mongo/by-id "applications" link-permit-id {:operations 1})
                                                          :operations first :name))]
                               {:id link-permit-id :type link-permit-type :operation link-permit-app-op})
                             {:id link-permit-id})))
            our-link-permits (filter #(= (:type ((keyword app-id) %)) "application") resp)
            apps-linking-to-us (filter #(= (:type ((keyword app-id) %)) "linkpermit") resp)]

        (-> app
          (assoc :linkPermitData (if (seq our-link-permits)
                                   (into [] (map convert-fn our-link-permits))
                                   nil))
          (assoc :appsLinkingToUs (if (seq apps-linking-to-us)
                                    (into [] (map convert-fn apps-linking-to-us))
                                    nil))))
      ;; No link permit data found
      (-> app
        (assoc :linkPermitData nil)
        (assoc :appsLinkingToUs nil)))))

