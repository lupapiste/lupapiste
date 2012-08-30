(ns lupapalvelu.data)

(defn users []
  [
   {:id "502b8bb3e101655ce6000010"
    :type :applicant
    :personId "121212-1212"
    :firstName "Mikko"
    :lastName "Intonen"
    :phone "+35834343433"
    :username "mikko@solita.com"
    :private {:password "mikko"
              :apikey "502cb9e58426c613c8b85abc"}
   }
   {:id "502b8bb3e101655ce6000013"
    :personId "011112-1234"
    :type :authority
    :firstName "Kari"
    :lastName "Rahikainen"
    :phone "044 445544"
    :username "kari@tampere.fi"
    :private {:password "kari"}
   }
   ])

(defn applications []
  [
   {:id "502d9060e1011308aa000100" 
     :title "Omakotitalon rakentaminen"
     :created 1330776303000
     :municipality :tampere
     :location {:lat 61.518362 :lon 23.622344}
     :streetAddress "Hunninsuonkatu 5 B"
     :postalCode "33560"
     :postalPlace "Tampere"
     :roles [{:partyId "502b8bb3e101655ce6000010"
              :displayName "Mikko Intonen"
              :role :applicant }
             {:partyId "502b8bb3e101655ce6000013"
              :displayName "Kari Rahikainen"
              :role :authority }]
    }
  ])