(ns ru.abramium.tribe
  (:gen-class
    :implements [net.fabricmc.api.DedicatedServerModInitializer])
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.jdbc :as jdbc]
            [clojure.pprint :as p]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)
           (java.util.function Predicate)
           (net.fabricmc.fabric.api.networking.v1 ServerPlayConnectionEvents ServerPlayConnectionEvents$Join)
           (net.fabricmc.loader.api FabricLoader)
           (net.luckperms.api LuckPermsProvider)
           (net.luckperms.api.node Node NodeType)
           (net.luckperms.api.node.types PrefixNode)))

(def context (atom {:cache #{}}))

(def default-config
  {:resident-prefix "<aqua>[<white>Резидент<aqua>]"
   :kpfu-prefix     "<aqua>[<white>%s | %s<aqua>]"
   :postgres        {:dbtype   "postgres"
                     :dbname   "aleph"
                     :host     "localhost"
                     :user     "abramium"
                     :password "abramium"}})

(defn load-properties [path]
  (let [file (str (.resolve path "tribe.edn"))]
    (or (try (edn/read-string (slurp file))
             (catch Exception e (do (log/error e "Failed to read properties file") nil)))
        (do (spit file (with-out-str (p/pprint default-config)))
            default-config))))

(def ^Predicate pred
  (reify Predicate
    (test [_ v]
      (and (= NodeType/PREFIX (.getType (cast Node v)))
           (= 100 (.getPriority (cast PrefixNode v)))))))

(defn set-prefix [^UUID uuid prefix]
  (let [user-manager (.getUserManager (LuckPermsProvider/get))
        user (.getUser user-manager uuid)]
    (-> (.data user) (.clear pred))
    (-> (.data user) (.add prefix))
    (.saveUser user-manager user)))

(defn get-person [ctx username]
  (-> @ctx
      (:postgres) (jdbc/get-by-id :persons username :username)
      (:person) (.getValue) (json/read-str :key-fn keyword)))

(defmulti to-prefix (fn [_ json] (-> json keys first)))

(defmethod to-prefix :resident [ctx _]
  (-> (@ctx :resident-prefix)
      (PrefixNode/builder 100)
      (.build)))

(defmethod to-prefix :default [ctx _]
  (to-prefix ctx {:resident nil}))

(defmethod to-prefix :kpfu [ctx json]
  (let [{:keys [institute group]} (:kpfu json)]
    (-> (@ctx :kpfu-prefix)
        (format institute group)
        (PrefixNode/builder 100)
        (.build))))

(defn listener [ctx]
  (reify ServerPlayConnectionEvents$Join
    (onPlayReady [_ handler _ _]
      (try (let [player (.getPlayer handler)
                 name (.getScoreboardName player)
                 uuid (.getUUID player)]
             (when-not (contains? (@ctx :cache) uuid)
               (->> name
                    (get-person ctx)
                    (to-prefix ctx)
                    (set-prefix uuid))
               (swap! ctx #(update % :cache conj uuid))
               (log/info (str name " has been handled successfully"))))
           (catch Exception e (log/error e (.getMessage e)))))))

(defn -onInitializeServer [_]
  (let [config (-> (FabricLoader/getInstance)
                   (.getConfigDir) (load-properties))]
    (log/info "Abramium Tribe is Here!")
    (swap! context #(merge % config))
    (.register ServerPlayConnectionEvents/JOIN
               (listener context))))
