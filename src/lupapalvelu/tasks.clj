(ns lupapalvelu.tasks
  (:require [clojure.string :as s]
            [clojure.set :refer [rename-keys]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [def-]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]))

(def task-schemas-version 1)

(def- task-name-max-len 80)

(def task-types ["muu katselmus"
                 "muu tarkastus"
                 "aloituskokous"
                 "rakennuksen paikan merkitseminen"
                 "rakennuksen paikan tarkastaminen"
                 "pohjakatselmus"
                 "rakennekatselmus"
                 "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
                 "osittainen loppukatselmus"
                 "loppukatselmus"
                 "ei tiedossa"])

(def- katselmuksenLaji
  {:name "katselmuksenLaji"
   :type :select :sortBy :displayname
   :css [:dropdown--plain]
   :required true
   :readonly true
   :whitelist {:roles [:authority] :otherwise :disabled}
   :default "illegal"
   :body (mapv (partial hash-map :name) task-types)})

(def- katselmuksenLaji-ya
  {:name "katselmuksenLaji"
   :type :select :sortBy :displayname
   :css [:dropdown]
   :required true
   :whitelist {:roles [:authority] :otherwise :disabled}
   :body [{:name "Aloituskatselmus"}
          {:name "Loppukatselmus"}
          {:name "Muu valvontak\u00e4ynti"}]})

(def- task-katselmus-body
  [katselmuksenLaji
   {:name "vaadittuLupaehtona"
    :type :checkbox
    :inputType :check-string
    :readonly-after-sent true
    :whitelist {:roles [:authority] :otherwise :disabled}
    :i18nkey "vaadittuLupaehtona"}
   {:name "rakennus"
    :type :group
    :whitelist {:roles [:authority] :otherwise :disabled}
    :repeating true
    :i18nkey ""
    :uicomponent :docgen-review-buildings
    :body [{:name "rakennus" :type :group :body [{:name "jarjestysnumero" :type :string :subtype :digit :hidden true}
                                                 {:name "valtakunnallinenNumero" :type :string :subtype :rakennustunnus
                                                  :hidden true}
                                                 {:name "rakennusnro" :type :string :subtype :rakennusnumero :hidden true}
                                                 {:name "kiinttun" :type :string :subtype :kiinteistotunnus :hidden true}
                                                 {:name "kunnanSisainenPysyvaRakennusnumero" :type :string :hidden true}]}
           {:name "tila" :type :group :i18nkey "empty"
            :body [{:name "tila" :type :select :css [:dropdown] :sortBy :displayname
                    :readonly-after-sent true
                    :whitelist {:roles [:authority] :otherwise :disabled}
                    :i18nkey "task-katselmus.katselmus.tila"
                    :body [{:name "osittainen"} {:name "lopullinen"}]}
                   {:name "kayttoonottava" :readonly-after-sent true :type :checkbox :inputType :checkbox-wrapper
                    :whitelist {:roles [:authority] :otherwise :disabled}}
                   ]}]}
   {:name "katselmus" :type :group

    :whitelist {:roles [:authority] :otherwise :disabled}
    :body
    [{:name "tila" :type :select :css [:dropdown] :sortBy :displayname
      :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}
      :required true
      :body [{:name "osittainen"} {:name "lopullinen"}]}
     {:name "pitoPvm" :type :date :required true :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "pitaja" :type :string :required true :readonly-after-sent true
            :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "huomautukset" :type :group
      :body [{:name "kuvaus" :type :text :max-len 4000 :css []
              :whitelist {:roles [:authority] :otherwise :disabled} }
             {:name "maaraAika" :type :date :whitelist {:roles [:authority] :otherwise :disabled}}
             {:name "toteaja" :type :string :whitelist {:roles [:authority] :otherwise :disabled}}
             {:name "toteamisHetki" :type :date :whitelist {:roles [:authority] :otherwise :disabled}}]}
     {:name "lasnaolijat" :type :text :max-len 4000 :layout :full-width :css [] :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}
     {:name "poikkeamat" :type :text :max-len 4000 :layout :full-width :css [] :readonly-after-sent true
      :whitelist {:roles [:authority] :otherwise :disabled}}]}])

(def- task-katselmus-body-ya
  (concat [katselmuksenLaji-ya]
          (update-in (tools/schema-body-without-element-by-name task-katselmus-body "rakennus" "tila" "katselmuksenLaji")
                     [1 :body 2 :body 0] assoc :required true)))

(schemas/defschemas
  task-schemas-version
  [{:info {:name "task-katselmus"
           :type :task
           :subtype :review
           :order 1
           :section-help "authority-fills"
           :i18nprefix "task-katselmus.katselmuksenLaji"
           } ; Had :i18npath ["katselmuksenLaji"]
        :rows [["katselmuksenLaji" "vaadittuLupaehtona"]
           ["rakennus::4"]
           ["katselmus/tila" "katselmus/pitoPvm" "katselmus/pitaja"]
           {:h2 "task-katselmus.huomautukset"}
           ["katselmus/huomautukset/kuvaus::3"]
           ["katselmus/huomautukset/maaraAika" "katselmus/huomautukset/toteaja" "katselmus/huomautukset/toteamisHetki"]
           ["katselmus/lasnaolijat::3"]
           ["katselmus/poikkeamat::3"]]
    :template "form-grid-docgen-group-template"
    :body task-katselmus-body}

   {:info {:name "task-katselmus-ya"
           :type :task
           :subtype :review
           :order 1
           :section-help "authority-fills"
           :i18name "task-katselmus"
           :i18nprefix "task-katselmus.katselmuksenLaji"
           } ; Had :i18npath ["katselmuksenLaji"]
    :rows [["katselmuksenLaji" "katselmus/pitoPvm" "katselmus/pitaja" "vaadittuLupaehtona"]
           {:h2 "task-katselmus.huomautukset"}
           ["katselmus/huomautukset/kuvaus::3"]
           ["katselmus/huomautukset/maaraAika" "katselmus/huomautukset/toteaja" "katselmus/huomautukset/toteamisHetki"]
           ["katselmus/lasnaolijat::3"]
           ["katselmus/poikkeamat::3"]]
    :template "form-grid-docgen-group-template"
    :body task-katselmus-body-ya}

   {:info {:name "task-vaadittu-tyonjohtaja" :type :task :order 10}
    :body [{:name "osapuolena" :type :checkbox}
           {:name "asiointitunnus" :type :string :max-len 17}]}

   {:info {:name "task-lupamaarays" :type :task :order 20}
    :rows [["maarays::3"] ["kuvaus::3"]]
    :template "form-grid-docgen-group-template"
    :body [{:name "maarays" :type :text :inputType :paragraph :max-len 20000 :readonly true}
           {:name "kuvaus"  :type :text :max-len 4000 }
           {:name "vaaditutErityissuunnitelmat" :type :text :hidden true}]}])

(defn task-doc-validation [schema-name doc]
  (let [schema (schemas/get-schema task-schemas-version schema-name)
        info   (model/document-info doc schema)]
    (model/validate-fields nil info nil (:data doc) [])))

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
     :data (if data (-> data tools/wrapped (tools/timestamped created)) {})
     :assignee (select-keys assignee [:id :firstName :lastName])
     :duedate nil
     :created created
     :closed nil}))

(defn rakennus-data-from-buildings [initial-rakennus buildings]
  (reduce
    (fn [acc build]
      (let [index (->> (map (comp #(Integer/parseInt %) name) (keys acc))
                       (apply max -1) ; if no keys, starting index (inc -1) => 0
                       inc
                       str
                       keyword)
            rakennus {:rakennus {:jarjestysnumero                    (:index build)
                                 :kiinttun                           (:propertyId build)
                                 :rakennusnro                        (:localShortId build)
                                 :valtakunnallinenNumero             (:nationalId build)
                                 :kunnanSisainenPysyvaRakennusnumero (:localId build)}
                      :tila {:tila ""
                             :kayttoonottava false} }]
        (assoc acc index rakennus)))
    initial-rakennus
    buildings))

(defn- katselmus->task [meta source {buildings :buildings} katselmus]
  (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
        data {:katselmuksenLaji (get katselmus :katselmuksenLaji "muu katselmus")
              :vaadittuLupaehtona true
              :rakennus (rakennus-data-from-buildings {} buildings)}]
    (new-task "task-katselmus" task-name data meta source)))

(defn- verdict->tasks [verdict meta application]
  (map
    (fn [{lupamaaraykset :lupamaaraykset}]
      (let [source {:type "verdict" :id (:id verdict)}]
        (concat
          (map (partial katselmus->task meta source application) (:vaaditutKatselmukset lupamaaraykset))
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
  (let [owner (first (auth/get-auths-by-role application :owner))
        meta {:created timestamp
              :assignee (user/get-user-by-id (:id owner))}]
    (flatten (map #(verdict->tasks % meta application) (:verdicts application)))))

(defn task-schemas [{:keys [schema-version permitType]}]
  (let [allowed-task-schemas (permit/get-metadata permitType :allowed-task-schemas)]
    (filter
      #(and
         (= :task (-> % :info :type))
         (allowed-task-schemas (-> % :info :name)))
      (vals (schemas/get-schemas schema-version)))))
