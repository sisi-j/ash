import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { BetaBuildProvider } from "./beta";
import "./styles.css";

const root = document.getElementById("root");
if (!root) throw new Error("#root is missing from index.html");

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <BetaBuildProvider>
      <App />
    </BetaBuildProvider>
  </React.StrictMode>,
);
