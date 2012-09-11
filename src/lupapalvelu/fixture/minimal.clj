(ns lupapalvelu.fixture.minimal)

(defn users []
  [{:id "777777777777777777000010" ;; Hakija Mikko Intonen, rakentaa Sipooseen omakotitalon, talousrakennuksen ja maalämmön.
    :email "mikko.intonen@sipoonmopedikorjaamo.fi"
    :role :applicant
    :personId "121212-1212"
    :firstName "Mikko"
    :lastName "Intonen"
    :phone "+35834343433"
    :username "mikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIu9zJl6HNkSIY9tdnGaL0eKhphW0iyicS"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "502cb9e58426c613c8b85abc"}}
   {:id "777777777777777777000016" ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti
    :email "veikko.viranomainen@tampere.fi"
    :role :authority
    :authority :tampere
    :personId "031112-1234"
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"}}
   {:id "777777777777777777000023" ;; Sonja Sibbo - Sipoon lupa-arkkitehti
    :email "sonja.sibbo@sipoo.fi"
    :role :authority
    :authority :sipoo
    :personId "kunta123"
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "sonja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"}},
   {:id "777777777777777777000024" ;; Seppo Sibbo - Sipoon lupa-arkkitehti
    :email "seppo.sibbo@sipoo.fi"
    :role :authority
    :authority :sipoo
    :personId "kunta124"
    :firstName "Seppo"
    :lastName "Simonen"
    :phone "03121991"
    :username "seppo"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"}}   
   ])

(defn applications []
  [{:id "777777777777777777000100"
    :title "Omakotitalon rakentaminen"
    :authority :sipoo
    :status :open
    :created 1330776303000
    :location {:lat 61.518362 :lon 23.622344}
    :streetAddress "Hunninsuonkatu 5 B"
    :postalCode "33560"
    :postalPlace "Tampere"
    :roles { :applicant {:userId "777777777777777777000010"
                         :displayName "Mikko Intonen"}}
    :attachments {"5049c08169a6a872c4154d50" {:name "Joku kuva"
                                              :fileName "marker-green.png"
                                              :contentType "image/png"
                                              :size 753}
                  "5049c08169a6a872c4154d51" {:name "Evil plan to conguer the world"
                                              :fileName "plan9.pdf"
                                              :contentType "application/pdf"
                                              :size 12323753}
                  "5049c08169a6a872c4154d52" {:name "Virallinen piirrustus"
                                              :size 0}}}])
