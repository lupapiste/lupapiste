(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]))

(def ui-components
  {:jquery       {:css ["jquery.pnotify.default.css"]
                  :js ["jquery-1.8.0.min.js" "jquery.ba-hashchange.js"
                       "jquery.pnotify.min.js" "jquery.metadata-2.1.js" "jquery.tablesorter-2.0.5b.js"]}

   :oskari       {:depends [:jquery]
                  :js ["map.js"]
                  :css ["oskarimap.css"]
                  :html ["map.html"]}

   :knockout     {:js ["knockout-2.1.0.debug.js" "knockout.mapping-2.3.2.js" "knockout.validation.js"]}

   :underscore   {:js ["underscore.js"]}

   :common       {:depends [:oskari :jquery :knockout :underscore]
                  :js ["log.js" "notify.js" "hub.js" "loc.js" "ajax.js" "nav.js" "combobox.js" "ko.init.js"  "dialog.js"]
                  :css ["css/main.css"]
                  :html ["error.html"]}

   :buildinfo    {:depends [:jquery]
                  :js ["buildinfo.js"]}

   :invites      {:depends [:common]
                  :js ["invites.js"]}

   :repository   {:depends [:common]
                  :js ["repository.js"]}

   :application  {:depends [:common :repository]
                  :js ["application.js"]
                  :html ["application.html"]}

   :applications-common {:depends [:invites]
                         :js ["applications.js" "lupapiste.tablesorter.js"]
                         :css ["tablesorter.css"]}

   :applications {:depends [:common :repository :applications-common]
                  :js ["applications-config.js"]
                  :css ["applications.css"]
                  :html ["applications.html"]}

   :authority_applications {:depends [:common :repository :applications-common]
                            :js ["applications-config.js"]
                            :html ["applications.html"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js"]
                  :html ["attachment.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html"]}

   :wizard       {:js ["application-create-wizard.js"]
                  :html (map (partial format "application-create-wizard-%02d.html") (range 1 (inc 3)))}

   :applicant    {:depends [:common :application :applications :attachment :wizard :buildinfo]
                  :js ["applicant.js" ]
                  :html ["index.html"]}

   :authority    {:depends [:application :authority_applications :attachment :buildinfo]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :admin        {:depends [:oskari :application :applications :attachment :wizard :buildinfo]
                  :js ["admin.js"]
                  :html ["index.html" "admin.html"]}

   :welcome      {:depends [:register :jquery :buildinfo]
                  :js ["login.js"]
                  :html ["login.html" "index.html"]}})

; Make sure that all resources are available:

(doseq [c (keys ui-components)
        r (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
  (let [file      (c/path r)
        resource (.getResourceAsStream (clojure.lang.RT/baseLoader) file)]
    (if resource
      (.close resource)
      (throw (Exception. (str "Resource missing: " file))))))
