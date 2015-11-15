(ns foli.core
  (:require [reagent.core :as re]
            [re-frame.core :refer [subscribe register-sub register-handler dispatch]]
            [ajax.core :as a]
            [clojure.string :refer [join]]
            [secretary.core :as secretary :refer-macros  [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [cljs-time.format :as f])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(def foli-url "http://data.foli.fi/")

(register-handler :fetch-stop-data
    (fn [app-state [_ stop-id]]
      (let [result (a/GET (join "/" [ foli-url "siri/sm" stop-id]) {
                          :handler
                          (fn [result]
                            (dispatch [:set-stop-data stop-id result]))
                          :response-format :json})]
        app-state)))

(defn format-response [response]
  (map (fn [item]
     (let [estimated-time (t/to-default-time-zone (c/from-long (* 1000 (item "expectedarrivaltime"))))]
         {:display (item "destinationdisplay")
          :estimated-time estimated-time})) (response "result")))


(register-handler :set-stop-data
    (fn [app-state [_ stop-id data]]
      (assoc-in app-state [:stops stop-id]
            (format-response data))))

(register-sub :stops
    (fn [db [_ stop-id]]
      (reaction (get-in @db [:stops stop-id]))))

(defn schedule [data]
  (let [fmt (f/formatter "HH:mm")]
      [:tr
        [:td [:h2 (:display data)]]
        [:td [:p (f/unparse fmt (:estimated-time data))]]]))

(defn stop-schedule [stop-id]
  (let [stop (subscribe [:stops stop-id])]
      [:div {:className "stop"}
          [:h1 (str "Pysäkki " stop-id)]
          [:table.table
            [:thead
              [:tr
                [:th "Kohde"]
                [:th "Lähtö"]]]
            [:tbody
              (map-indexed (fn [index s] ^{:key index} [schedule s]) @stop)]]]))

(declare stop-route)
(register-handler :search-stop
    (fn [app-state [_ stop-name]]
      (secretary/dispatch! (stop-route {:stop-id stop-name}))
      app-state))

(defroute stop-route "/stops/:stop-id" [stop-id]
    (dispatch [:fetch-stop-data stop-id])
    (re/render-component
      [:div.container
          [:input.form-control {:placeholder "Syötä pysäkin nimi tai numero"
                               :onChange #(dispatch [:search-stop (.-value (.-target %))])}]
          [stop-schedule stop-id]]
      (.getElementById js/document "app")))

(defroute default-route "*" []
    (re/render-component
      [:div.container
          [:input.form-control {:placeholder "Syötä pysäkin nimi tai numero"
                               :onChange #(dispatch [:search-stop (.-value (.-target %))])}]]
      (.getElementById js/document "app")))

(defn main []
    (secretary/set-config! :prefix  "#")
    (let [h  (History.)]
        (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch!  (.-token %)))
        (doto h (.setEnabled true)))
    )
(main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
