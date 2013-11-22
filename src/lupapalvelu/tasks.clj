(ns lupapalvelu.tasks
  (:require [clojure.string :as s]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(def task-schemas-version 1)

(schemas/defschemas
  task-schemas-version
  [{:info {:name "task-katselmus"}
   :body (schemas/body
           [{:name "katselmuksenLaji"
             :type :select
             :required true
             :body [{:name "muu katselmus"}
                    {:name "muu tarkastus"}
                    {:name "aloituskokous"}
                    {:name "rakennuksen paikan merkitseminen"}
                    {:name "rakennuksen paikan tarkastaminen"}
                    {:name "pohjakatselmus"}
                    {:name "rakennekatselmus"}
                    {:name "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"}
                    {:name "osittainen loppukatselmus"}
                    {:name "loppukatselmus"}
                    {:name "ei tiedossa"}]}]
           schemas/rakennuksen-valitsin
           [{:name "pitoPvm" :type :date}
            {:name "tilanneKoodi" :type :string}
            {:name "pitaja" :type :string}
            {:name "vaadittuLupaehtona" :type :boolean}
            {:name "huomautukset" :type :group :repeating true
             :body [{:name "kuvaus" :required true}
                    {:name "maaraAika" :type :date}
                    {:name "toteamisHetki" :type :date}
                    {:name "toteaja" :type :string}]}
            {:name "lasnaolijat" :type :text}
            {:name "poikkeamat" :type :text}])}
   {:info {:name "task-lupamaarays"}
    :body []} ; TODO
   {:info {:name "task-vaadittu-tyonjohtaja"}
    :body []} ; TODO -- link to document or application?
   ])

(defn- ->task [schema-name task-name data {:keys [created assignee] :as meta} source]
  {:pre [schema-name
         source
         (or (map? data) (nil? data))]}
  {:schema-info {:name schema-name :version task-schemas-version}
   :id (mongo/create-id)
   :source source
   :taskname task-name
   :status :open
   :data (when data (-> data tools/wrapped (tools/timestamped created)))
   :assignee (select-keys assignee [:id :firstName :lastName])
   :duedate nil
   :created created})

(defn- verdict->tasks [verdict {:keys [created] :as meta}]
  (map-indexed
   (fn [idx {lupamaaraykset :lupamaaraykset}]
     (let [source {:type "verdict" :id (str (:kuntalupatunnus verdict) \/ (inc idx))}]
       (concat
        (map
          #(->task "task-katselmus" (or (:tarkastuksenTaiKatselmuksenNimi %) (:katselmuksenLaji %))
             {:katselmuksenLaji (:katselmuksenLaji %) :vaadittuLupaehtona true} meta source)
          (:vaaditutKatselmukset lupamaaraykset))
        (map #(->task "task-lupamaarays" (:sisalto %) {} meta source)
          (filter #(not (s/blank? (:sisalto %))) (:maaraykset lupamaaraykset )))
        (when-not (s/blank? (:vaaditutTyonjohtajat lupamaaraykset))
          (map #(->task "task-vaadittu-tyonjohtaja" % {} meta source)
            (s/split (:vaaditutTyonjohtajat lupamaaraykset) #"(,\s*|\s+ja\s+|\s+och\s+)"))))))
   (:paatokset verdict)))

(defn verdicts->tasks [application timestamp]
  (let [meta {:created timestamp
              :assignee (first (domain/get-auths-by-role application :owner))}]
    (flatten (map #(verdict->tasks % meta) (:verdicts application)))))
