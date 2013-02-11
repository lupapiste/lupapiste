(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.4.2"]
                 [enlive "1.0.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.nrepl "0.2.1"]
                 [org.jasypt/jasypt "1.9.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [clj-http "0.6.4" :exclusions [commons-codec]]
                 [digest "1.4.2"]
                 [clj-time "0.4.4"]
                 [org.apache.commons/commons-lang3 "3.1"] ; Already a dependency but required explicitly
                 [commons-io/commons-io "2.4"]
                 [clj-pdf "1.0.6-SNAPSHOT"]
                 [org.clojure/data.zip "0.1.1"]
                 [com.draines/postal "1.9.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [ontodev/excel "0.2.0" :exclusions [[xml-apis]]]
                 [com.yahoo.platform.yui/yuicompressor "2.4.7" :exclusions [rhino/js]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [fi.sito/oskari "0.9.6"]]
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "2.0.1"]
                             [lein-buildid "0.1.0"]
                             [lein-nitpicker "0.3.0"]]}
             :itest {:test-paths ^:replace ["itest"]}
             :stest {:test-paths ^:replace ["stest"]}
             :lupadev {:jvm-opts ["-Dtarget_server=http://lupadev.solita.fi"]}
             :lupatest {:jvm-opts ["-Dtarget_server=http://lupatest.solita.fi"]}}
  :warn-on-reflection true
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"\/jquery" #"\/theme\/default\/" #"\/public\/lib\/" #"openlayers" #"underscore"]}
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                            :checksum :ignore}]]
  :main lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
