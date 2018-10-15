(ns lupapalvelu.integrations.ely
  "ELY-keskus USPA integraatio"
  (:require [sade.core :refer :all]
            [sade.env :as env]))

(defn ely-statement-giver [subtype]
  {:userId "ely-uspa"
   :id     (env/value :ely :sftp-user)
   :email  "ely-uspa@lupapiste.fi"
   :name   "ELY-keskus"
   :text   subtype})

(def r-statement-types
  "ELY statement types for R.
  07.01.05 Poikkeamisluvat tai poikkeamis-, suunnittelutarveratkaisu- ja rakennusj\u00e4rjestysp\u00e4\u00e4t\u00f6kset; rakennus-, toimenpide-, purkamis- ja maisematy\u00f6luvat.
  07.01.09 Rakennussuojelu
  06.05.09  Teiden suoja- ja n\u00e4kem\u00e4alueelle rakentaminen (poikkeamisluvat).
  06.05.10 Teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentaminen (naapurin kuuleminen)"
  ["Lausuntopyynt\u00f6 rakennusluvasta"
   "Lausuntopyynt\u00f6 naapurin kuulemisesta teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentamisesta"
   "Lausuntopyynt\u00f6 maisematy\u00f6luvasta"
   "Lausuntopyynt\u00f6 purkamisaikomuksesta"
   "Lausuntopyynt\u00f6 purkamislupahakemuksesta"
   "Lausuntopyynt\u00f6 rakennusj\u00e4rjestyksest\u00e4"
   "Lausuntopyynt\u00f6 suojeluesityksest\u00e4"])

(def p-statement-types
  "ELY statement types for P.
  07.01.05 Poikkeamisluvat tai poikkeamis-, suunnittelutarveratkaisu- ja rakennusj\u00e4rjestysp\u00e4\u00e4t\u00f6kset; rakennus-, toimenpide-, purkamis- ja maisematy\u00f6luvat.
  06.05.09  Teiden suoja- ja n\u00e4kem\u00e4alueelle rakentaminen (poikkeamisluvat).
  06.05.10 Teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentaminen (naapurin kuuleminen)"
  ["Lausuntopyynt\u00f6 poikkeamishakemuksesta"
   "Lausuntopyynt\u00f6 maisematy\u00f6luvasta"
   "Lausuntopyynt\u00f6 purkamisaikomuksesta"
   "Lausuntopyynt\u00f6 purkamislupahakemuksesta"
   "Lausuntopyynt\u00f6 suunnittelutarveratkaisusta"
   "Lausuntopyynt\u00f6 teiden suoja- ja n\u00e4kem\u00e4alueelle rakentamisen poikkeamishakemuksesta"
   "Lausuntopyynt\u00f6 naapurin kuulemisesta teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentamisesta"])

(def ya-statement-types
  "ELY statement types for YA"                              ; TODO check YA
  ["Lausuntopyynt\u00f6 maa-ainesten otosta ja k\u00e4sittelyst\u00e4"
   "Lausuntopyynt\u00f6 maisematy\u00f6luvasta"
   "Lausuntopyynt\u00f6 naapurin kuulemisesta teiden suoja- ja n\u00e4kem\u00e4alueen ulkopuolelle rakentamisesta"])

(def ymp-statement-types
  "ELY statement types for YM, YI, YL, VVVL, MAL.
  07.00.06 Ymp\u00e4rist\u00f6nsuojelulain valvontamenettely
  07.00.13 Pohjavesien suojelu
  07.00.32 Maa-ainesten otto ja k\u00e4sittely
  07.01.05 Poikkeamisluvat tai poikkeamis-, suunnittelutarveratkaisu- ja rakennusj\u00e4rjestysp\u00e4\u00e4t\u00f6kset; rakennus-, toimenpide-, purkamis- ja maisematy\u00f6luvat."
  ["Lausuntopyynt\u00f6 maisematy\u00f6luvasta"             ; from 07.01.05 Poikkeamisluvat... maisematy\u00f6luvat -->
   "Lausuntopyynt\u00f6 ymp\u00e4rist\u00f6nsuojelulain valvontamenettelyst\u00e4"
   "Lausuntopyynt\u00f6 pohjavesien suojelusuunnitelmasta"
   "Lausuntopyynt\u00f6 maa-ainesten otosta ja k\u00e4sittelyst\u00e4"])

(def mm-kt-statement-types
  "ELY statement types for MM and KT permit types
  07.01.01 Maakuntakaavoituksen ohjaus
  07.01.02 Yleiskaavamenettely
  07.01.03 Asemakaavoitusmenettely"
  ["Lausuntopyynt\u00f6 asemakaavasta"
   "Lausuntopyynt\u00f6 maakuntakaavasta"
   "Lausuntopyynt\u00f6 yleiskaavasta"])

(def all-statement-types
  (set (concat r-statement-types p-statement-types ya-statement-types ymp-statement-types mm-kt-statement-types)))

(def subtype-input-validator
  (fn [{{:keys [subtype]} :data}]
    (when-not (contains? all-statement-types subtype)
      (fail :error.illegal-key :source ::subtype-input-validator))))

(defn ely-uspa-enabled
  "Pre-checker that fails if Ely statements is not enabled in the application organization scope."
  [{:keys [organization]}]
  (when-not (:ely-uspa-enabled @organization)
    (fail :error.ely-statement-not-enabled)))
