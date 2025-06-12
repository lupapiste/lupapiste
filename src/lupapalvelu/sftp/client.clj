(ns lupapalvelu.sftp.client
  (:require [clj-ssh.cli :as ssh-cli]
            [clj-ssh.ssh :as ssh]
            [lupapalvelu.logging :refer [error info infof]]
            [sade.core :refer [now]]
            [sade.files :as files]
            [sade.util :as util])
  (:import [com.jcraft.jsch ChannelSftp$LsEntry
                            JSch
                            JSchException
                            Logger
                            Session]
           [java.io File FileNotFoundException FileWriter]))

(set! *warn-on-reflection* true)

(defn write-to! [^File file ^String content]
  (let [writer (FileWriter. file)]
    (.write writer content)
    (.close writer)))

(defn- session->details
  [^Session session]
  (merge
    {:host           (.getHost session)
     :port           (.getPort session)
     :username       (.getUserName session)
     :client-version (.getClientVersion session)}
    (when (.isConnected session)
      {:server-version (.getServerVersion session)
       :server-algs    (.getType (.getHostKey session))})))

(defn- append-to-session
  [^Session session k v]
  (let [curr    (.getConfig session k)
        altered (str curr "," v)]
    (infof "Replacing %s: %s with %s" k curr altered)
    (.setConfig session k altered)))

(defn- with-ssh-channel [target-address {:keys [username password private-key-path port]} callback-fn-for-channel]
  {:pre [(fn? callback-fn-for-channel)]}
  (try
    (let [agent (ssh/ssh-agent {:use-system-ssh-agent false})] ;; TODO: Make :use-system-ssh-agent configurable?
      (when private-key-path
        (ssh/add-identity agent {:private-key-path private-key-path}))
      (let [session (ssh/session agent target-address (util/assoc-when {:strict-host-key-checking :no
                                                                        :username                 username}
                                                                       :password password
                                                                       :port (when (and (number? port)
                                                                                        (pos? port))
                                                                               port)))]
        ;; TODO: LPK-7184 / TT-24047: This should be temporary!
        ;; ssh-dss is not considered secure anymore!
        (append-to-session session "server_host_key", "ssh-dss")
        (ssh/with-connection session
          (let [channel (ssh/ssh-sftp session)]
            (ssh/with-channel-connection channel
              (callback-fn-for-channel {:channel channel :session session}))))))
    (catch JSchException e
      (error e (str "SSH connection " username "@" target-address))
      (throw e))))

(defn get-file [target-address {:keys [username password]} file-to-get target-file-name]
  (info "sftp" (str username "@" target-address ":" file-to-get) target-file-name)
  (ssh-cli/sftp target-address :get
                file-to-get
                target-file-name
                :username username
                :password password
                :strict-host-key-checking :no))

(defn get-file-with-pred [target-address auth path-to-file file-prefix-or-some-pred target-file-name]
  (info "Getting file from " target-address " in " path-to-file)
  (with-ssh-channel
    target-address
    auth
    (fn [{:keys [channel]}]
      (info "sftp: ls" path-to-file)
      (let [some-pred (if (fn? file-prefix-or-some-pred)
                        file-prefix-or-some-pred
                        (fn [^ChannelSftp$LsEntry entry]
                          (when (and (.startsWith (.getFilename entry) file-prefix-or-some-pred)
                                     (.endsWith   (.getFilename entry) ".xml"))
                            (.getFilename entry))))
            filename (->> (ssh/sftp channel {} :ls path-to-file)
                          (sort-by #(.getMTime (.getAttrs ^ChannelSftp$LsEntry %)) >)
                          (some some-pred))
            file-to-get (str path-to-file filename)]
        (if filename
          (do (info "sftp: get" file-to-get)
              (ssh/sftp channel {} :get file-to-get target-file-name)
              (info "sftp: done, saved file to" target-file-name)
              target-file-name)
          (throw (FileNotFoundException. (str "file not found from sftp, pred:" file-prefix-or-some-pred))))))))

(defn upload-file!
  "Upload string content as a file to a remote server
   Usage: (upload-file! <ip-address> <string-content-like-xml> <remote-path-to-file> {:username <sftp-user> :private-key-pah <path-to-local-private-key>})"
  [target-address string-content remote-path auth]
  (info "Uploading file to " target-address " in " remote-path)
  (with-ssh-channel
    target-address
    auth
    (fn [{:keys [channel]}]
      (files/with-temp-file temp-file
        (let [file-name (.getAbsolutePath temp-file)]
          (write-to! temp-file string-content)
          (ssh/sftp channel {} :put file-name remote-path))))))

(defn capture-logger
  [*storage]
  (reify Logger
    (isEnabled [_this _level]
      true)
    (log [_this level message]
      (let [level (case level
                    0 :debug
                    1 :info
                    2 :warn
                    3 :error
                    4 :fatal)]
        (swap! *storage conj [(now) level message])))))

(defn test-connection
  [{:keys [address auth]}]
  (let [old-logger (JSch/getLogger)
        *storage   (atom [])]
    (JSch/setLogger (capture-logger *storage))
    (try
      (with-ssh-channel
        address
        auth
        (fn [{:keys [session channel]}]
          ;; Run some arbitrary command
          (ssh/sftp channel {} :pwd)
          {:details (session->details session)
           :logs    (deref *storage)}))
      (catch Exception e
        {:exception (.getMessage e)
         :logs      (deref *storage)})
      (finally
        (JSch/setLogger old-logger)))))
