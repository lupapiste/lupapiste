(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0-beta10" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.2.0"]
                 [enlive "1.0.1"]
                 [org.clojure/tools.nrepl "0.2.0-beta8"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [digest "1.3.0"]
                 [clj-http "0.5.3"]
                 [clj-time "0.4.4"]
                 [fi.sito.oskari/oskari "0.1"]]
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "2.0.0-SNAPSHOT"]
                             [lein-buildid "0.1.0-SNAPSHOT"]]}}
  :main lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
