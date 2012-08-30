(ns lupapalvelu.fixture.minimal)

(defn partys []
  [{:id "777777777777777777000010" ;; Hakija Mikko Intonen, rakentaa Sipooseen omakotitalon, talousrakennuksen ja maalämmön.
    :role :applicant
    :personId "121212-1212"
    :firstName "Mikko"
    :lastName "Intonen"
    :phone "+35834343433"
    :username "mikko"
    :private {:password "mikko"
              :apikey "502cb9e58426c613c8b85abc"}
    }
   {:id "777777777777777777000016" ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti
    :role :authority
    :authority :tampere
    :personId "031112-1234"
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "veikko"}
    }
   {:id "777777777777777777000023" ;; Sonja Sibbo - Sipoon lupa-arkkitehti
    :role :authority
    :authority :sipoo
    :personId "230112-1234"
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "sonja"
    :private {:password "sonja"}}
   ])

(defn applications []
  [ {:id "777777777777777777000100"
     :title "Omakotitalon rakentaminen"
     :permitType :buildingPermit
     :authority :sipoo
     :status :active
     :created 1330776303000
     :location {:lat 61.518362 :lon 23.622344} 
     :streetAddress "Hunninsuonkatu 5 B"
     :postalCode "33560"
     :postalPlace "Tampere"
     :roles [ {:partyId "777777777777777777000010"
               :displayName "Mikko Intonen"
               :role :applicant}
              {:partyId "777777777777777777000023" 
               :displayName "Veikko Viranomainen"
               :role :authority}]
     }])