(ns lupapalvelu.tasks
  (:require [clojure.string :as s]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(def task-schemas-version 1)

(schemas/defschemas
  task-schemas-version
  [{:info {:name "task-katselmus" :order 1 :i18nprefix "verdict.katselmus" :i18npath ["katselmuksenLaji"]}
    :body [{:name "katselmuksenLaji"
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
                   {:name "ei tiedossa"}]}
           {:name "vaadittuLupaehtona" :type :checkbox}
           {:name "rakennus"
            :type :group
            :repeating true
            :body [{:name "rakennus" :type :group :body schemas/rakennuksen-valitsin}
                   {:name "tila" :type :group
                    :body [{:name "tila" :type :select :body [{:name "osittainen"} {:name "lopullinen"}]}
                           {:name "kayttoonottava" :type :checkbox}]}]}
           {:name "katselmus" :type :group
            :body
            [{:name "pitoPvm" :type :date}
            {:name "pitaja" :type :string}
            {:name "huomautukset" :type :group
             :body [{:name "kuvaus" :required true :type :text}
                    {:name "maaraAika" :type :date}
                    {:name "toteamisHetki" :type :date}
                    {:name "toteaja" :type :string}]}
            {:name "lasnaolijat" :type :text :max-len 4000 :layout :full-width}
            {:name "poikkeamat" :type :text :max-len 4000 :layout :full-width}
            {:name "tila" :type :select :body [{:name "osittainen"} {:name "lopullinen"}]}]}]}
   {:info {:name "task-vaadittu-tyonjohtaja" :order 10}
    :body []} ; TODO -- link to document or application?
   {:info {:name "task-lupamaarays" :order 20}
    :body []} ; TODO
   ])

(defn- ->task [schema-name task-name data {:keys [created assignee] :as meta} source]
  {:pre [schema-name
         source
         (or (map? data) (nil? data))]}
  {:schema-info {:name schema-name :version task-schemas-version}
   :id (mongo/create-id)
   :source source
   :taskname task-name
   :state (if (user/applicant? assignee) :requires_user_action :requires_authority_action)
   :data (when data (-> data tools/wrapped (tools/timestamped created)))
   :assignee (select-keys assignee [:id :firstName :lastName])
   :duedate nil
   :created created
   :closed nil})

(defn- katselmus->task [meta source katselmus]
  (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
        data {:katselmuksenLaji (:katselmuksenLaji katselmus)
              :vaadittuLupaehtona true}]
    (->task "task-katselmus" task-name data meta source)))

(defn- verdict->tasks [verdict {:keys [created] :as meta}]
  (map-indexed
   (fn [idx {lupamaaraykset :lupamaaraykset}]
     (let [source {:type "verdict" :id (str (:kuntalupatunnus verdict) \/ (inc idx))}]
       (concat
        (map (partial katselmus->task meta source) (:vaaditutKatselmukset lupamaaraykset))
        (map #(->task "task-lupamaarays" (:sisalto %) {} meta source)
          (filter #(not (s/blank? (:sisalto %))) (:maaraykset lupamaaraykset )))
        (when-not (s/blank? (:vaaditutTyonjohtajat lupamaaraykset))
          (map #(->task "task-vaadittu-tyonjohtaja" % {} meta source)
            (s/split (:vaaditutTyonjohtajat lupamaaraykset) #"(,\s*)"))))))
   (:paatokset verdict)))

(defn verdicts->tasks [application timestamp]
  (let [owner (first (domain/get-auths-by-role application :owner))
        meta {:created timestamp
              :assignee (user/get-user-by-id (:id owner))}]
    (flatten (map #(verdict->tasks % meta) (:verdicts application)))))
