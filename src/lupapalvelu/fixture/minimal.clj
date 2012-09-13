(ns lupapalvelu.fixture.minimal)

(defn users []
  [{:id "777777777777777777000010" ;; Hakija Mikko Intonen, rakentaa Sipooseen omakotitalon, talousrakennuksen ja maalämmön.
    :email "mikko.intonen@sipoonmopedikorjaamo.fi"
    :role :applicant
    :personId "121212-1212"
    :firstName "Mikko"
    :lastName "Intonen"
    :streetAddress "Vanhakatu 5"
    :postalCode "55550"
    :postalPlace "Viitasaari"
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
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5051ba0caa2480f374dcfeff"}}
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
    :state :open
    :created 1330776303000
    :location {:lat 61.518362 :lon 23.622344}
    :streetAddress "Hunninsuonkatu 5 B"
    :postalCode "33560"
    :postalPlace "Tampere"
    :permitType :buildingPermit
    :documents {"5049c08169a6a871a4154d50" {:documentType :hakijaTieto
                                            :content {:nimi "Mikko Intonen"
                                                      :katuosoite "Vanhakatu 5"
                                                      :postinumero "55550"
                                                      :postitoimipaikka "Viiselä"
                                                      :puhelinnumero "+3584522334455"
                                                      :sahkopostiosoite "mikko.intonen@example.com"}}
                "5049c08169a6a871a4154d52" {:documentType :paasuunnittelijaTieto
                                            :content {:nimi "Mirja Nieminen"
                                                      :katusosoite "Niemikatu 8 A"
                                                      :postinumero "44440"
                                                      :postitoimipaikka "Niemelä"
                                                      :puhelinnumero "+3584522334455"
                                                      :sahkopostiosoite "mirja.nieminen@example.com"
                                                      :koulutus "Arkkitehti"
                                                      :kokemus "10 v"}}
                
                "5049c08169a6a871a4154d53" {:documentType :toimenpide
                                            :type :buildNew
                                            :content {:otsikko "Omakotitalon rakentaminen"}}
                "5049c08169a6a871a4154d54" {:documentType :toimenpide
                                            :type :buildNew
                                            :content {:otsikko "Talousrakennus"}}
                "5049c08169a6a871a4154d55" {:documentType :toimenpide
                                            :type :buildOther
                                            :content {:otsikko "Maalämpö"}}
                "5049c08169a6a871a4154d56" {:documentType :rh1
                                            :parentDocument "5049c08169a6a871a4154d53" ;; Omakotitalon RH1
                                            :content {:rakennusaine "puu",
                                                      :kerrosluku 2,
                                                      :kerrosala 232}}
                "5049c08169a6a871a4154d57" {:documentType :rh1
                                            :parentDocument "5049c08169a6a871a4154d54" ;; Talousrakennuksen RH1
                                            :content {:rakennusaine "betoni",
                                                      :kerrosluku 1,
                                                      :kerrosala 42}}}
    
    :roles {:applicant {:userId "777777777777777777000010"
                        :displayName "Mikko Intonen"}
            :architect {:userId "777777777777777777000010"
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
