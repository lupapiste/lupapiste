(ns lupapalvelu.ddd-map-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]))

(apply-remote-minimal)

(facts "3D Map View"
       (let [good-map (contains {:ok true :location (contains "/dev/show-3dmap?lupapisteKey=")})
             r-id (create-app-id pena :propertyId sipoo-property-id :operation "pientalo")
             ya-id (create-app-id pena :propertyId sipoo-property-id :operation "ya-katulupa-vesi-ja-viemarityot")
             info-id (create-app-id pena :propertyId sipoo-property-id :operation "pientalo" :inforequest true)]
         (fact "In minimal, the 3D maps are supported for Sipoo-R"
               (command pena :redirect-to-3d-map :id r-id) => good-map)
         (fact "In minimal, the 3D maps are not supported for Sipoo-YA"
               (command pena :redirect-to-3d-map :id ya-id) => fail?)
         (fact "The 3D maps are supported for Sipoo-R inforequests too"
               (command pena :redirect-to-3d-map :id info-id) => good-map)
         (fact "Admin disables 3D maps for Sipoo-R"
               (command admin :set-3d-map-enabled :organizationId "753-R" :flag false) => ok?)
         (fact "Sipoo-R applications and inforequests no longer can access 3D maps"
               (command pena :redirect-to-3d-map :id r-id) => fail?
               (command pena :redirect-to-3d-map :id info-id) => fail?)
         (fact "3D map backend must be HTTPS"
               (command admin :update-3d-map-server-details
                        :organizationId "753-YA" :url "http://foo.bar.baz"
                        :username "foo" :password "bar")=> (partial expected-failure? :error.only-https-allowed)
               (command admin :update-3d-map-server-details
                        :organizationId "753-YA" :url "https://foo.bar.baz"
                        :username "foo" :password "bar") => ok?)))
