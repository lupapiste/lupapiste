(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.4.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clj-logging-config "1.9.10" :exclusions [log4j]]
                 [org.slf4j/slf4j-log4j12 "1.7.2"]
                 [enlive "1.0.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.nrepl "0.2.1"]
                 [org.jasypt/jasypt "1.9.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [crypto-random "1.1.0" :exclusions [commons-codec]]
                 [clj-http "0.6.4" :exclusions [commons-codec]]
                 [camel-snake-kebab "0.1.0"]
                 [digest "1.4.2"]
                 [clj-time "0.5.0"]
                 [org.apache.commons/commons-lang3 "3.1"] ; Already a dependency but required explicitly
                 [commons-io/commons-io "2.4"]
                 [com.lowagie/itext "2.1.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [com.draines/postal "1.9.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [swiss-arrows "0.5.1"]
                 [me.raynes/fs "1.4.0"]
                 [ontodev/excel "0.2.0" :exclusions [[xml-apis]]]
                 [com.yahoo.platform.yui/yuicompressor "2.4.7" :exclusions [rhino/js]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [fi.sito/oskari "0.9.6"]]
  :plugins [[org.timmc/lein-otf "2.0.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [ring-mock "0.1.1"]]
                   :plugins [[lein-midje "2.0.1"]
                             [lein-buildid "0.1.0"]
                             [lein-nitpicker "0.3.0"]]}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}
             :alltests {:source-paths ["itest" "stest"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=http://lupadev.solita.fi"]}
             :lupatest {:jvm-opts ["-Dtarget_server=http://lupatest.solita.fi"]}}
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"[\/\\]jquery" #"[\/\\]theme[\/\\]default"
                         #"[\/\\]public[\/\\]lib" #"openlayers" #"underscore" #"highcharts\.js"]}
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                            :checksum :ignore}]]
  :aliases {"integration" ["with-profile" "dev,itest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]}
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
