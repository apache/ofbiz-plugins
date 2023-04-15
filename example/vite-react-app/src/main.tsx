import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.css";
import {ApiContextProvider} from "./ApiContext";

ReactDOM.createRoot(
  document.getElementById("ReactExamplesRootContainer") as HTMLElement
).render(
  <React.StrictMode>
    <ApiContextProvider>
      <App />
    </ApiContextProvider>
  </React.StrictMode>
);
