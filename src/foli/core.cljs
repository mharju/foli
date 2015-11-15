(ns foli.core
  (:require [reagent.core :as re]
            [re-frame.core :refer [debug subscribe register-sub register-handler dispatch]]
            [secretary.core :as secretary :refer-macros  [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [cljs-time.format :as f]
            [foli.handlers])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))

(enable-console-print!)

(register-sub :stop-ids
    (fn [db _]
      (reaction (:stop-ids @db))))

(register-sub :stops
    (fn [db [_ stop-id]]
      (reaction (get-in @db [:stops stop-id]))))

(register-sub :selected-stop
    (fn [db _]
      (reaction (get-in @db [:selected-stop]))))

(register-sub :search-value
    (fn [db _]
      (reaction (get-in @db [:search-value]))))

(register-sub :name-search-results
    (fn [db _]
      (reaction (get-in @db [:name-search-results]))))

(defn schedule [data]
  (let [fmt (f/formatter "HH:mm")]
      [:tr
        [:td [:h2 (:display data)]]
        [:td [:p (f/unparse fmt (:estimated-time data))]]]))

(defn stop-schedule [stop-id]
  (let [stop (subscribe [:stops stop-id])
        stop-ids (subscribe [:stop-ids])]
    (fn [stop-id]
        [:div {:className "stop"}
              [:h1 (str stop-id " – " (@stop-ids stop-id))]
              [:table.table
                [:thead
                  [:tr
                    [:th "Kohde"]
                    [:th "Lähtö"]]]
                [:tbody
                  (map-indexed (fn [index s] ^{:key index} [schedule s]) @stop)]]])))

(declare stop-route)
(defn search-results []
  (let [search-results (subscribe [:name-search-results])]
      (fn []
        [:div.results
            (map-indexed (fn [index {:keys [name id]}] ^{:key index} [:a {:href (stop-route {:stop-id id})} (str name " " id)]) @search-results)])))

(defn application []
  (let [selected-stop (subscribe [:selected-stop])
        search-value (subscribe [:search-value])
        name-search-results (subscribe [:name-search-results])]
    (fn []
      [:div.container
          [:input.form-control {:placeholder "Syötä pysäkin osoite tai numero"
                                :value @search-value
                                :onChange #(dispatch [:search-stop (.-value (.-target %))])}]
          (when-not (nil? @selected-stop)
              [stop-schedule @selected-stop])
          (when-not (nil? @name-search-results)
               [search-results])])))

(re/render-component
  [application]
  (.getElementById js/document "app"))

(defroute stop-route "/stops/:stop-id" [stop-id]
    (dispatch [:set-selected-stop stop-id]))
(defroute default "*" []
    (dispatch [:main]))

(defn main []
    (secretary/set-config! :prefix  "#")
    (let [h  (History.)]
        (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch!  (.-token %)))
        (doto h (.setEnabled true)))
    (dispatch [:fetch-stops]))
(main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
