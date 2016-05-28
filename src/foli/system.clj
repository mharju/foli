(ns foli.system
  (:require [org.httpkit.server :refer [run-server]]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [defroutes GET]]
            [clojure.java.jdbc :as j]
            [clojure.data.json :as json]
            [ring.middleware.params :refer [wrap-params]]))

(def db {:subprotocol "sqlite" :subname "resources/public/foli.sqlite3"})

(defn build-stop-route-index []
  (let [routes-and-stops (j/query db ["select distinct s.stop_id, r.route_short_name
                           from stops s
                           left join stop_times st on st.stop_id = s.stop_id
                           left join trips t on t.trip_id = st.trip_id
                           left join routes r on r.route_id = t.route_id"])]
    (for [route-and-stop routes-and-stops] (j/insert! db :routes_stops route-and-stop))))

(defn find-routes-by-stop-ids [stop-ids]
    (let [result (j/query
                   db
                   (concat
                     [(str "select stop_id, route_short_name
                           from routes_stops
                           where stop_id IN ("
                                               (clojure.string/join ","
                                                                    (map (fn [_] "?")
                                                                         (range (count stop-ids))))
                                               ")")] stop-ids))]
      (->> result
          (group-by :stop_id)
          (map (fn [[k v]] [k (map :route_short_name v)]))
          (into {}))))

(defn routes [stops]
  {:status 200
    :headers { "Content-Type" "application/json; charset=utf-8" "Access-Control-Allow-Origin" "*" }
    :body (json/write-str (find-routes-by-stop-ids (clojure.string/split stops #",")))})

(defroutes app
   (GET "/routes/" {:keys [query-params]}
        (routes (get query-params "stops"))))

(defn start-server [handler port]
  (let [server (run-server (wrap-params app) {:port port})]
    (println (str "Up and running on port " port))
    server))

(defn stop-server [server]
  (when server (server)))

(defrecord FoliSystem []
  component/Lifecycle
  (start [this]
    (assoc this :server (start-server #'app 9009)))
  (stop [this]
    (stop-server (:server this))
    (dissoc this :server)))

(defn create-system []
  (FoliSystem.))

(defn -main [& args]
  (if (= (second args) "build-index")
    (do
      (println "Building route and stop index. Please wait.")
      (build-stop-route-index))
    (.start (create-system))))

