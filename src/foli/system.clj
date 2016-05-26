(ns foli.system
  (:require [org.httpkit.server :refer [run-server]]
            [com.stuartsierra.component :as component]
            [korma.core :refer [defentity database fields table select modifier join where order sql-only]]
            [korma.db :refer [sqlite3 defdb]]
            [compojure.core :refer [defroutes GET]]))

(defdb db (sqlite3 {:db "resources/public/foli.sqlite3"}))

(defentity trips (database db))
(defentity stop-times (database db) (table :stop_times))
(defentity routes (database db))
(defentity stops (database db))

(defn find-stops-by-route [route-name stop-name]
  (map :stop_id
       (select
         stop-times
         (fields :stop_id)
         (modifier "distinct")
         (join trips (= :trips.trip_id :trip_id))
         (join routes (= :routes.route_id :trips.route_id))
         (join stops (= :stop_id :stops.stop_id))
         (where (= :routes.route_short_name route-name))
         (where (= :stops.stop_name stop-name))
         (order :arrival_time))))

(defn index [req]
  {:status 200
  :headers { "Content-Type" "text/html; charset=utf-8" }
  :body "<h1>Hei vaan</h1> <p>Tähän vois tulla <strong>Hauskaa</strong></p>"})

(defn stops [route stop]
  {:status 200
    :headers { "Content-Type" "text/plain; charset=utf-8" }
    :body (find-stops-by-route route stop)})

(defroutes app
   (GET "/" [] index)
   (GET "/stops/:route/:stop" [route stop] (stops route stop)))

(defn start-server [handler port]
  (let [server (run-server app {:port port})]
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
  (.start (create-system)))

