(ns lupapalvelu.pate.review
  (:require [lupapalvelu.tasks :as tasks]
            [lupapalvelu.pate.shared :as pate-shared]
            [sade.shared-util :as util]))

#_(defn- verdict->tasks [verdict meta application]
  (map
    (fn [{lupamaaraykset :lupamaaraykset}]
      (let [source {:type "verdict" :id (:id verdict)}]
        (concat
          (map (partial katselmus->task meta source application) (:vaaditutKatselmukset lupamaaraykset))
          (map #(new-task "task-lupamaarays" (:sisalto %) {:maarays (:sisalto %)} meta source)
               (filter #(-> % :sisalto ss/blank? not) (:maaraykset lupamaaraykset)))
          ; KRYSP yhteiset 2.1.5+
          (map #(new-task "task-lupamaarays" % {:vaaditutErityissuunnitelmat %} meta source)
               (filter #(-> %  ss/blank? not) (:vaaditutErityissuunnitelmat lupamaaraykset)))
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

; example of katselmus->task implementation
#_(defn katselmus->task [meta source {:keys [buildings]} katselmus]
    (let [task-name (or (:tarkastuksenTaiKatselmuksenNimi katselmus) (:katselmuksenLaji katselmus))
          rakennustieto (map :KatselmuksenRakennus (:katselmuksenRakennustieto katselmus))
          first-huomautus (first (get-in katselmus [:huomautukset]))
          katselmus-data {:tila (get katselmus :osittainen)
                          :pitaja (get katselmus :pitaja)
                          :pitoPvm (util/to-local-date (get katselmus :pitoPvm))
                          :lasnaolijat (get katselmus :lasnaolijat "")
                          :huomautukset {:kuvaus (or (-> first-huomautus :huomautus :kuvaus)
                                                     "")}
                          :poikkeamat (get katselmus :poikkeamat "")}
          data (merge {:katselmuksenLaji (get katselmus :katselmuksenLaji "muu katselmus")
                       :vaadittuLupaehtona true
                       :rakennus (merge-rakennustieto rakennustieto
                                                      (rakennus-data-from-buildings {} buildings))
                       :katselmus katselmus-data}
                      (get-muu-tunnus-data katselmus))

          schema-name (if (-> katselmus-data :tila (= "pidetty"))
                        "task-katselmus-backend"
                        ;; else
                        "task-katselmus")
          task (new-task schema-name task-name data meta source)]
      ;; (debugf "katselmus->task: made task with schema-name %s, id %s, katselmuksenLaji %s" schema-name (:id task) (:katselmuksenLaji data))
      task))

(defn review->task [{{reviews :reviews} :references :as verdict} buildings ts pate-review-id]
  (when-some [pate-review (util/find-by-id pate-review-id reviews)]
    (let [source {:type "verdict" :id (:id verdict)}
          data {:katselmuksenLaji   (pate-shared/review-type-map (or (keyword (:type pate-review)) :ei-tiedossa))
                :vaadittuLupaehtona true
                :rakennus           (tasks/rakennus-data-from-buildings {} buildings)
                :katselmus          {:tila nil                ; data should be empty, as this is just placeholder task
                                     :pitaja nil
                                     :pitoPvm nil
                                     :lasnaolijat ""
                                     :huomautukset {:kuvaus ""}
                                     :poikkeamat ""}}
          review-name (get-in pate-review [:name :fi])        ;; TODO localization should be stripped out and localized to lang defined in verdict
        ]
      (tasks/new-task "task-katselmus" review-name data {:created ts} source))))
