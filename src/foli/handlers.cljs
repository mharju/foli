(ns foli.handlers
  (:require
    [ajax.core :as a]
    [clojure.string :refer [join lower-case]]
    [cljs-time.core :as t]
    [cljs-time.coerce :as c]
    [cljs-time.format :as f]
    [re-frame.core :refer [register-handler dispatch debug]]))

(def foli-url "http://data.foli.fi/")
(def cache-timeout (t/minutes 5))

(defn- format-response [response]
  (map (fn [item]
     (let [estimated-time (t/to-default-time-zone (c/from-long (* 1000 (item "expectedarrivaltime"))))]
         {:display (item "destinationdisplay")
          :estimated-time estimated-time})) (response "result")))

(defn stale? [stamp] (or (nil? stamp) (t/before? (t/plus (t/now) cache-timeout) stamp)))

(register-handler :fetch-stop-data
    (fn [app-state [_ stop-id]]
      (let [stop-data (get-in app-state [:stops stop-id])]
          (when (or (nil? stop-data)
                    (stale? (:fetch-time (meta stop-data))))
            (a/GET (join "/" [ foli-url "siri/sm" stop-id]) {
                              :handler
                              (fn [result]
                                (dispatch [:set-stop-data stop-id result]))
                              :response-format :json})))
        app-state))

(register-handler :set-stop-data
    (fn [app-state [_ stop-id data]]
      (assoc-in app-state [:stops stop-id] (with-meta (format-response data) {:fetch-time (t/now)}))))

(register-handler :fetch-stops
    (fn [app-state _]
      (when (or (nil? (:stop-ids app-state))
                (stale? (:fetch-time (meta (:stop-ids app-state)))))
          (a/GET (join "/" [foli-url "siri/sm"]) {
                :handler
                (fn [result]
                  (dispatch [:set-stops result]))
                :response-format :json}))
      app-state))

(register-handler :set-stops
    (fn [app-state [_ result]]
        (let [new-state  (-> app-state
            (assoc :stop-ids
                   (into (with-meta {} {:fetch-time (t/now)})
                         (keep (fn [[k v]]
                                 (when-not (nil? (v "stop_name")) [k (v "stop_name")]))) result)))]
          new-state)))

(register-handler :set-selected-stop
    (fn [app-state [_ stop-id]]
      (dispatch [:fetch-stop-data stop-id])
      (-> app-state
          (dissoc :name-search-results)
          (assoc :selected-stop stop-id))))

(register-handler :main
    (fn [app-state [_ stop-id]]
      (dissoc app-state :selected-stop)))

(register-handler :search-stop
    (fn [app-state [_ stop-name]]
      (if-not (nil? ((app-state :stop-ids) stop-name))
          (dispatch [:set-selected-stop stop-name])
          (dispatch [:search-stop-with-name stop-name]))
      (assoc app-state :search-value stop-name)))

(register-handler :search-stop-with-name
    (fn [app-state [_ stop-name]]
      (-> app-state
          (dissoc :selected-stop)
          (assoc :name-search-results
                 (when (< 3 (count stop-name)) (keep
                   (fn [[k v]]
                     (when (.startsWith (lower-case v) (lower-case stop-name))
                       {:name v :id k}))
                      (:stop-ids app-state)))))))

