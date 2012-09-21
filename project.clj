(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0-beta10" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.1.2"]
                 [enlive "1.0.1"]
                 [org.clojure/tools.nrepl "0.2.0-beta8"]
                 [org.mindrot/jbcrypt "0.3m"]]
  :main lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "2.0.0-SNAPSHOT"]
                             [lein-buildid "0.1.0-SNAPSHOT"]]}}
  :min-lein-version "2.0.0")
