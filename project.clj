(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring "1.2.0"]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup]]
                 [compojure "1.1.5" :exclusions [org.clojure/tools.macro]]
                 [com.novemberain/monger "1.6.0"]
                 [com.taoensso/timbre "2.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [enlive "1.1.4"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.jasypt/jasypt "1.9.1"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [crypto-random "1.1.0" :exclusions [commons-codec]]
                 [clj-http "0.7.7" :exclusions [commons-codec]]
                 [camel-snake-kebab "0.1.2"]
                 [digest "1.4.3"]
                 [clj-time "0.6.0"]
                 [org.apache.commons/commons-lang3 "3.1"] ; Already a dependency but required explicitly
                 [commons-io/commons-io "2.4"]
                 [com.lowagie/itext "4.2.1"]
                 [org.clojure/data.zip "0.1.1"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [endophile "0.1.1" :exclusions [hiccup]]
                 [com.draines/postal "1.11.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [swiss-arrows "0.6.0"]
                 [me.raynes/fs "1.4.5"]
                 [ontodev/excel "0.2.0" :exclusions [[xml-apis]]]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [com.yahoo.platform.yui/yuicompressor "2.4.7" :exclusions [rhino/js]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [fi.sito/oskari "0.9.25"]
                 [slingshot "0.10.3"]
                 [com.google.zxing/javase "2.2"]
                 [digest "1.4.3"]
                 [org.clojure/tools.trace "0.7.6"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [ring-mock "0.1.5"]
                                  [clj-ssh "0.5.6"]]
                   :plugins [[lein-midje "3.1.1"]
                             [lein-buildid "0.2.0"]
                             [lein-nitpicker "0.4.0"]
                             [lein-hgnotes "0.2.0-SNAPSHOT"]]
                   :source-paths ["test-utils"]
                   :jvm-opts ["-Djava.awt.headless=true"
                              "-Xmx1G" "-XX:MaxPermSize=256M"]}
             :uberjar  {:source-paths ["main-src"]
                        :main lupapalvelu.main}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts ["-Xmx1G" "-XX:MaxPermSize=256M"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=http://lupadev.solita.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=http://lupatest.solita.fi" "-Djava.awt.headless=true"]}}
  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"hgnotes" #"terms\.html" #"\/email-templates\/"]}
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                            :checksum :ignore}]]
  :aliases {"integration" ["with-profile" "dev,itest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]}
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
