(ns lupapalvelu.document.yleiset-alueet-canonical-test-common)


(def municipality 753)

(def pena {:id "777777777777777777000020",
           :role "applicant",
           :firstName "Pena",
           :lastName "Panaani",
           :username "pena"})

(def sonja {:id "777777777777777777000023",
            :role "authority",
            :firstName "Sonja",
            :lastName "Sibbo",
            :username "sonja"})

(def location {:x 404335.789, :y 6693783.426})

(def statements [{:id "52382cea94a74fc25bb4be5d"
                  :given 1379415837074
                  :requested 1379413226349
                  :status "yes"
                  :person sonja
                  :text "Annanpa luvan."}])