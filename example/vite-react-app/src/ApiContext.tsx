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
