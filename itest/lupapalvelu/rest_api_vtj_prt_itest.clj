(ns lupapalvelu.rest-api-vtj-prt-itest
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(defn- api-update-national-building-id-call [application-id params]
  (http-post (format "%s/rest/application/%s/update-national-building-id" (server-address) application-id)
             (merge params {:throw-exceptions false})))

(def application (create-and-send-application sonja))

(def document (util/find-first (comp #{"uusiRakennus"} :name :schema-info) (:documents application)))

(def operation-id (get-in document [:schema-info :op :id]))

(fact "no nationalId set by default"
  (get-in document [:data :valtakunnallinenNumero :value]) => "")

(facts "sipoo backend user updates vtj-prt"
  (fact "updated succesfully"
    (api-update-national-building-id-call (:id application) {:form-params {:operationId operation-id :nationalBuildingId "1234567892"}
                                                             :as :json
                                                             :basic-auth ["sipoo-r-backend" "sipoo"]}) => http200?)

  (fact "vtj prt is updated"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))

(facts "sipoo backend user try to update vtj-prt on nonexesting document"
  (fact "Sipoo Backend can access"
    (api-update-national-building-id-call (:id application) {:form-params {:operationId (:id document) :nationalBuildingId "123456780S"}
                                                             :as :json
                                                             :basic-auth ["sipoo-r-backend" "sipoo"]}) => http404?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))

(fact "invalid vtj-prt"
  (fact "request fails with invalid parameter"
    (api-update-national-building-id-call (:id application) {:form-params {:operationId operation-id :nationalBuildingId "1234567891"}
                                                             :as :json
                                                             :basic-auth ["sipoo-r-backend" "sipoo"]})
    => http400?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))

(facts "application is not visible for jarvenpaa user"
  (fact "request fails for jarvenpaa"
    (api-update-national-building-id-call (:id application) {:form-params {:operationId operation-id :nationalBuildingId "123456780S"}
                                                             :as :json
                                                             :basic-auth ["jarvenpaa-backend" "jarvenpaa"]})
    => http404?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))
