(ns lupapalvelu.tasks
  (:require [clojure.string :as s]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [def-]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]))

(def- task-schemas-version 1)

(def- task-name-max-len 80)


(def- task-katselmus-body
  [{:name "katselmuksenLaji"
    :type :select :sortBy :displayname
    :required true
    :whitelist {:roles [:authority] :otherwise :disabled}
    :default "muu katselmus"
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
   {:name "vaadittuLupaehtona"
    :type :checkbox
    :whitelist {:roles [:authority] :otherwise :disabled}
    :i18nkey "vaadittuLupaehtona"}
   {:name "rakennus"
    :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :repeating true
    :body [{:name "rakennus" :type :group :body schemas/uusi-rakennuksen-valitsin}
           {:name "tila" :type :group
            :body [{:name "tila" :type :select :sortBy :displayname :body [{:name "osittainen"} {:name "lopullinen"}]}
                   {:name "kayttoonottava" :type :checkbox}]}]}
   {:name "katselmus" :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :body
    [{:name "pitoPvm" :type :date :required true}
     {:name "pitaja" :type :string}
     {:name "huomautukset" :type :group
      :body [{:name "kuvaus" :required true :type :text :max-len 4000}
             {:name "maaraAika" :type :date}
             {:name "toteaja" :type :string}
             {:name "toteamisHetki" :type :date}]}
     {:name "lasnaolijat" :type :text :max-len 4000 :layout :full-width}
     {:name "poikkeamat" :type :text :max-len 4000 :layout :full-width}
     {:name "tila" :type :select :sortBy :displayname :body [{:name "osittainen"} {:name "lopullinen"}]}]}])

(def- task-katselmus-body-ya
  (tools/schema-body-without-element-by-name task-katselmus-body "rakennus"))

(schemas/defschemas
  task-schemas-version
  [{:info {:name "task-katselmus" :type :task :order 1 :i18nprefix "task-katselmus.katselmuksenLaji"} ; Had :i18npath ["katselmuksenLaji"]
    :body task-katselmus-body}

   {:info {:name "task-katselmus-ya" :type :task :order 1 :i18nprefix "task-katselmus.katselmuksenLaji"} ; Had :i18npath ["katselmuksenLaji"]
    :body task-katselmus-body-ya}

   {:info {:name "task-vaadittu-tyonjohtaja" :type :task :order 10}
    :body [{:name "osapuolena" :type :checkbox}
           {:name "asiointitunnus" :type :string :max-len 17}]}

   {:info {:name "task-lupamaarays" :type :task :order 20}
    :body [{:name "maarays" :type :text :max-len 10000 :readonly true :layout :full-width}
           {:name "kuvaus"  :type :text :max-len 4000 :layout :full-width}]}])

(defn new-task [schema-name task-name data {:keys [created assignee state] :as meta :or {state :requires_user_action}} source]
  {:pre [schema-name source (or (map? data) (nil? data))]}
  (util/deep-merge
    (model/new-document (schemas/get-schema task-schemas-version schema-name) created)
    {:source source
     :taskname (when task-name
                 (if (> (.length task-name) task-name-max-len)
                   (str (ss/substring task-name 0 (- task-name-max-len 3)) "...")
                   task-name))
     :state state
     :data (when data (-> data tools/wrapped (tools/timestamped created)))
     :assignee (select-keys assignee [:id :firstName :lastName])
     :duedate nil
     :created created
     :closed nil}))

(defn- katselmus->task [meta source katselmus]
  (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
        data {:katselmuksenLaji (:katselmuksenLaji katselmus)
              :vaadittuLupaehtona true}]
    (new-task "task-katselmus" task-name data meta source)))

(defn- verdict->tasks [verdict meta]
  (map
    (fn [{lupamaaraykset :lupamaaraykset}]
      (let [source {:type "verdict" :id (:id verdict)}]
        (concat
          (map (partial katselmus->task meta source) (:vaaditutKatselmukset lupamaaraykset))
          (map #(new-task "task-lupamaarays" (:sisalto %) {:maarays (:sisalto %)} meta source)
            (filter #(-> % :sisalto s/blank? not) (:maaraykset lupamaaraykset)))
          ; KRYSP yhteiset 2.1.5+
          (map #(new-task "task-lupamaarays" % {:vaaditutErityissuunnitelmat %} meta source)
            (filter #(-> %  s/blank? not) (:vaaditutErityissuunnitelmat lupamaaraykset)))
          (if (seq (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.1+
            (map #(new-task "task-vaadittu-tyonjohtaja" % {} meta source) (:vaadittuTyonjohtajatieto lupamaaraykset))
            ; KRYSP yhteiset 2.1.0 and below
            (when-not (s/blank? (:vaaditutTyonjohtajat lupamaaraykset))
              (map #(new-task "task-vaadittu-tyonjohtaja" % {} meta source)
                (s/split (:vaaditutTyonjohtajat lupamaaraykset) #"(,\s*)"))))
          ;; from YA verdict
          (map #(new-task "task-lupamaarays" % {:maarays %} meta source)
            (filter #(-> %  s/blank? not) (:muutMaaraykset lupamaaraykset))))))
    (:paatokset verdict)))

(defn verdicts->tasks [application timestamp]
  (let [owner (first (domain/get-auths-by-role application :owner))
        meta {:created timestamp
              :assignee (user/get-user-by-id (:id owner))}]
    (flatten (map #(verdict->tasks % meta) (:verdicts application)))))

(defn task-schemas [{:keys [schema-version permitType]}]
  (let [allowed-task-schemas (permit/get-metadata permitType :allowed-task-schemas)]
    (filter
      #(and
         (= :task (-> % :info :type))
         (allowed-task-schemas (-> % :info :name)))
      (vals (schemas/get-schemas schema-version)))))
