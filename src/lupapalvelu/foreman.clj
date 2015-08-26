(ns lupapalvelu.foreman
  (:require [lupapalvelu.domain :as domain]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]))

(defn other-project-document [application timestamp]
  (let [building-doc (domain/get-document-by-name application "uusiRakennus")
        kokonaisala (get-in building-doc [:data :mitat :kokonaisala :value])
        operation (get-in building-doc [:schema-info :op :name])]
    {:luvanNumero {:value (:id application)
                   :modified timestamp}
     :katuosoite {:value (:address application)
                  :modified timestamp}
     :rakennustoimenpide {:value operation
                          :modified timestamp}
     :kokonaisala {:value kokonaisala
                   :modified timestamp}
     :autoupdated {:value true
                   :modified timestamp}}))

(defn- get-foreman-hetu [foreman-application]
  (let [foreman-doc (domain/get-document-by-name foreman-application "tyonjohtaja-v2")]
    (get-in foreman-doc [:data :henkilotiedot :hetu :value])))

(defn- get-foreman-applications [foreman-application & [foreman-hetu]]
  (let [foreman-hetu (if (ss/blank? foreman-hetu)
                       (get-foreman-hetu foreman-application)
                       foreman-hetu)]
    (when-not (ss/blank? foreman-hetu)
      (mongo/select :applications {"primaryOperation.name" "tyonjohtajan-nimeaminen-v2"
                                   :documents {$elemMatch {"schema-info.name"              "tyonjohtaja-v2"
                                                           "data.henkilotiedot.hetu.value" foreman-hetu}}}))))

(defn get-foreman-project-applications
  "Based on the passed foreman application, fetches all project applications that have the same foreman as in
  the passed foreman application (personal id is used as key). Returns all the linked applications as a list"
  [foreman-application foreman-hetu]
  (let [foreman-apps    (->> (get-foreman-applications foreman-application foreman-hetu)
                             (remove #(= (:id %) (:id foreman-application))))
        foreman-app-ids (map :id foreman-apps)
        links           (mongo/select :app-links {:link {$in foreman-app-ids}})
        linked-app-ids  (remove (set foreman-app-ids) (distinct (mapcat #(:link %) links)))]
    (mongo/select :applications {:_id {$in linked-app-ids}})))

(defn- get-linked-app-operations [foreman-app-id link]
  (let [other-id  (first (remove #{foreman-app-id} (:link link)))]
    (get-in link [(keyword other-id) :apptype])))

(defn- unwrap [wrapped-value]
  (let [value (tools/unwrapped wrapped-value)]
    (if (and (or (coll? value) (string? value)) (empty? value))
      "ei tiedossa"
      value)))

(defn- get-history-data-from-app [app-links foreman-app]
  (let [foreman-doc     (domain/get-document-by-name foreman-app "tyonjohtaja-v2")

        municipality    (:municipality foreman-app)
        difficulty      (unwrap (get-in foreman-doc [:data :patevyysvaatimusluokka]))
        foreman-role    (unwrap (get-in foreman-doc [:data :kuntaRoolikoodi]))
        limitedApproval (unwrap (get-in foreman-doc [:data :tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta]))
        limitedApproval (if limitedApproval
                          "yes"
                          nil)

        relevant-link   (first (filter #(some #{(:id foreman-app)} (:link %)) app-links))
        project-app-id  (first (remove #{(:id foreman-app)} (:link relevant-link)))
        operation       (get-linked-app-operations (:id foreman-app) relevant-link)]

    {:municipality    municipality
     :difficulty      difficulty
     :jobDescription  foreman-role
     :operation       operation
     :limitedApproval limitedApproval
     :linkedAppId     project-app-id
     :foremanAppId    (:id foreman-app)
     :created         (:created foreman-app)}))

(defn get-foreman-history-data [foreman-app]
  (let [foreman-apps       (->> (get-foreman-applications foreman-app)
                                (remove #(= (:id %) (:id foreman-app))))
        links              (mongo/select :app-links {:link {$in (map :id foreman-apps)}})]
    (map (partial get-history-data-from-app links) foreman-apps)))

(defn- reduce-to-highlights [history-group]
  (let [history-group (sort-by :created history-group)
        difficulty-values (vec (map :name (:body schema/patevyysvaatimusluokka)))]
    (reduce (fn [highlights group]
              (if (pos? (util/compare-difficulty :difficulty difficulty-values (first highlights) group))
                (cons group highlights)
                highlights))
            nil
            history-group)))

(defn get-foreman-reduced-history-data [foreman-app]
  (let [history-data    (get-foreman-history-data foreman-app)
        grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn foreman-application-info [application]
  (-> (select-keys application [:id :state :auth :documents])
      (update-in [:documents] (fn [docs] (filter #(= (get-in % [:schema-info :name]) "tyonjohtaja-v2") docs)))))

(defn foreman-app? [application] (= :tyonjohtajan-nimeaminen-v2 (-> application :primaryOperation :name keyword)))

(defn notice?
  "True if application is foreman application and of type notice (ilmoitus)"
  [application]
  (and
    (foreman-app? application)
    (= "ilmoitus" (-> (domain/get-document-by-name application "tyonjohtaja-v2") :data :ilmoitusHakemusValitsin :value))))

(defn- validate-notice-or-application [application]
  (let [foreman-doc (domain/get-document-by-name application "tyonjohtaja-v2")
        type (get-in foreman-doc [:data :ilmoitusHakemusValitsin :value])]
    (when (ss/blank? type)
      (fail! :error.foreman.type-not-selected))))

(defn- validate-notice-submittable [{:keys [primaryOperation linkPermitData] :as application}]
  (when (notice? application)
    (when-let [link (some #(when (= (:type %) "lupapistetunnus") %) linkPermitData)]
      (when-not (states/post-verdict-states (keyword
                                              (get
                                                (mongo/select-one :applications {:_id (:id link)} {:state 1})
                                                :state)))
        (fail! :error.foreman.notice-not-submittable)))))

(defn validate-application
  "Validates foreman applications"
  [application]
  (when (foreman-app? application)
    (or
      (validate-notice-or-application application)
      (validate-notice-submittable application))))
