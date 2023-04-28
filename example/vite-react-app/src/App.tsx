/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { useCallback, useState } from "react";
import reactLogo from "./assets/react.svg";
import viteLogo from "/vite.svg";
import "./App.css";
import { useApi } from "./ApiContext";
import { pipe } from "fp-ts/lib/function";
import * as E from "fp-ts/lib/Either";

function App() {
  const { get: apiGet } = useApi();
  const [count, setCount] = useState(0);

  const [apiPath, setApiPath] = useState<string>("/services");
  const [apiResults, setApiResults] = useState<string>("Not yet called");

  const apiButtonClickHandler = useCallback(() => {
    setApiResults("Calling API...");

    const apiCallAsTaskEither = pipe(apiGet(apiPath));

    apiCallAsTaskEither()
      .then((result) => {
        if (E.isLeft(result)) {
          return "Error: " + result.left.message;
        } else {
          return result.right.text();
        }
      })
      .then((result) => setApiResults(result))
      .catch((err) => {
        setApiResults("Error: " + err.message);
      });
  }, [apiGet, apiPath, setApiResults]);

  return (
    <div className="App">
      <div>
        <a href="https://vitejs.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://reactjs.org" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>Vite + React</h1>

      <div className="card">
        <p>Enter path and click button to call API with GET</p>
        <input value={apiPath} onChange={(e) => setApiPath(e.target.value)} />
        <button onClick={() => apiButtonClickHandler()}>Call API</button>
        <h1>API results</h1>
        <pre>{apiResults}</pre>
        <h2>Example API paths</h2>
        <p>/services</p>
        <p>
          /services/findProductById?inParams=%7B%22idToFind%22:%22GZ-1001%22%7D
        </p>
      </div>

      <div className="card">
        <p>Click this button to see the counter increment</p>
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </div>
  );
}

export default App;
