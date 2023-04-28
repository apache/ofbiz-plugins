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

import React, { useCallback, useContext, useState } from "react";
import { pipe } from "fp-ts/lib/function";
import * as E from "fp-ts/lib/Either";
import * as TE from "fp-ts/lib/TaskEither";

export type IApiContext = {
  get: (path: string) => TE.TaskEither<Error, Response>;
};

const invalidFunction = () => {
  throw new Error(
    "ApiContext consumer is not wrapped in a corresponding provider."
  );
};

const ApiContext = React.createContext<IApiContext>({
  get: invalidFunction,
});

const ApiContextProvider = ({ children }: { children: JSX.Element }) => {
  const [apiKey] = useState<string>(() =>
    document
      ? (document.getElementById("ofbizRestApiToken") as HTMLInputElement).value
      : "MISSING_API_KEY"
  );

  const get = useCallback(
    (path: string) => {
      return pipe(
        TE.tryCatch(
          () =>
            fetch(`/rest/${path}`, {
              method: "GET",
              headers: {
                Authorization: "Bearer " + apiKey,
              },
            }),
          E.toError
        )
      );
    },
    [apiKey]
  );

  return <ApiContext.Provider value={{ get }}>{children}</ApiContext.Provider>;
};

const useApi = () => useContext(ApiContext);

export { ApiContextProvider, useApi };
