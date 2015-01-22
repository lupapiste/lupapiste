(ns lupapalvelu.foreman
  (:require [lupapalvelu.domain :as domain]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.tools :as tools]
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
  (when-let [foreman-hetu (if (ss/blank? foreman-hetu)
                            (get-foreman-hetu foreman-application)
                            foreman-hetu)]
    (mongo/select :applications {"operations.name" "tyonjohtajan-nimeaminen-v2"
                                 :documents {$elemMatch {"schema-info.name" "tyonjohtaja-v2"
                                                         "data.henkilotiedot.hetu.value" foreman-hetu}}})))

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
    (if (empty? value)
      "ei_tiedossa"            ; TODO: ei_tiedossa -> ei tiedossa kun kaannos excelissa
      value)))

(defn- get-history-data-from-app [app-links foreman-app]
  (let [foreman-doc     (domain/get-document-by-name foreman-app "tyonjohtaja-v2")

        municipality    (:municipality foreman-app)
        difficulty      (unwrap (get-in foreman-doc [:data :patevyysvaatimusluokka]))
        foreman-role    (unwrap (get-in foreman-doc [:data :kuntaRoolikoodi]))

        relevant-link   (first (filter #(some #{(:id foreman-app)} (:link %)) app-links))
        project-app-id  (first (remove #{(:id foreman-app)} (:link relevant-link)))
        operation       (get-linked-app-operations (:id foreman-app) relevant-link)]

    {:municipality   municipality
     :difficulty     difficulty
     :jobDescription foreman-role
     :operation      operation
     :linkedAppId    project-app-id
     :foremanAppId   (:id foreman-app)
     :created        (:created foreman-app)}))

(defn get-foreman-history-data [foreman-app]
  (let [foreman-apps       (->> (get-foreman-applications foreman-app)
                                (remove #(= (:id %) (:id foreman-app))))
        links              (mongo/select :app-links {:link {$in (map :id foreman-apps)}})]
    (map (partial get-history-data-from-app links) foreman-apps)))

(defn- more-difficult-than [old new]
  (let [old (:difficulty old)
        new (:difficulty new)]
    (if (nil? new)
      false
      (or
        (nil? old)
        (> (count new) (count old))                         ; because "AA" > "A" o_0
        (pos? (compare old new))))))

(defn- reduce-to-highlights [history-group]
  (let [history-group (sort-by :created history-group)]
    (reduce (fn [highlights group]
              (if (more-difficult-than (first highlights) group)
                (cons group highlights)
                highlights))
            nil
            history-group)))

(defn get-foreman-reduced-history-data [foreman-app]
  (let [history-data    (get-foreman-history-data foreman-app)
        grouped-history (group-by (juxt :municipality :jobDescription) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn foreman-application-info [application]
  (-> (select-keys application [:id :state :auth :documents])
      (update-in [:documents] (fn [docs] (filter #(= (get-in % [:schema-info :name]) "tyonjohtaja-v2") docs))))
)