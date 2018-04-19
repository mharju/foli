(ns foli.handlers
  (:require
    [cljs.reader]
    [ajax.core :as a]
    [clojure.string :refer [join lower-case]]
    [clojure.set :as set]
    [cljs-time.core :as t]
    [cljs-time.coerce :as c]
    [cljs-time.format :as f]
    [re-frame.core :refer [register-handler dispatch debug]]))

(def development true)
(def foli-url "//data.foli.fi/")
(def server-url (if development "http://localhost:9009/" "//foli.taiste.fi:9009/"))
(def cache-timeout (t/minutes 5))

(defn- format-response [response]
  (map (fn [item]
     (let [estimated-time (t/to-default-time-zone (c/from-long (* 1000 (item "expecteddeparturetime"))))]
         {:display (item "destinationdisplay")
          :line (item "lineref")
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
      (let [stop-upper (when-not (nil? stop-name) (.toUpperCase stop-name))]
        (if-not (nil? ((app-state :stop-ids) stop-upper))
          (dispatch [:set-selected-stop stop-upper])
          (dispatch [:search-stop-with-name stop-upper])))
      (assoc app-state :search-value stop-name)))

(register-handler :search-stop-with-name
    (fn [app-state [_ stop-name]]
      (let [name-search-results (when (< 3 (count stop-name))
                                  (keep
                                    (fn [[k v]]
                                      (when (= (subs (lower-case v) 0 (count stop-name)) (lower-case stop-name))
                                        {:name v
                                        :id k}))
                                    (:stop-ids app-state)))]
        (dispatch [:get-routes (map :id name-search-results)])
        (-> app-state
            (dissoc :selected-stop)
            (assoc :name-search-results name-search-results)))))

(register-handler :get-routes
  (fn [app-state [_ stop-ids]]
    (if (> (count stop-ids) 0)
      (do
        (when-not (nil? (:request app-state))
          (.abort (:request app-state)))
        (let [request (a/GET (str server-url "routes/?stops=" (join "," stop-ids))
                             {:handler #(dispatch [:stop-routes %])
                             :response-format :json})]
          (assoc app-state :request request)))
         app-state)))

(register-handler :stop-routes
  (fn [app-state [_ results]]
    (-> app-state
        (dissoc :request)
        (update :name-search-results
                (fn [current]
                  (mapv #(assoc %
                           :lines (map (fn [x] (get x "route_short_name")) (get results (:id %)))
                           :stop-location
                             (-> (get-in results [(:id %) 0])
                                 (select-keys ["stop_lat" "stop_lon"])))
                    current))))))

(register-handler
  :remove-favorite
  (fn [app-state [_ stop-id]]
    (dispatch [:store-favorites])
    (update app-state :favorites set/difference #{stop-id})))

(register-handler
  :add-favorite
  (fn [app-state [_ stop-id]]
    (dispatch [:store-favorites])
    (update app-state :favorites set/union #{stop-id})))

(register-handler
  :load-favorites
  (fn [app-state _]
    (if-let [favorites (.getItem js/localStorage "favorites")]
      (assoc app-state :favorites (cljs.reader/read-string favorites))
      app-state)))

(register-handler
  :store-favorites
  (fn [app-state _]
    (.setItem js/localStorage "favorites" (get app-state :favorites))
    app-state))

(register-handler
  :show-location
  (fn [app-state [_ stop-id x y]]
    (let [{:strs [stop_lat stop_lon]}
          (->> app-state
               (:name-search-results)
               (filter #(= (:id %) stop-id))
               (first)
               (:stop-location))]
      (assoc
        app-state
        :location {:lat stop_lat :long stop_lon :x x :y y}))))

(register-handler
  :hide-location
  (fn [app-state _]
    (dissoc app-state :location)))
