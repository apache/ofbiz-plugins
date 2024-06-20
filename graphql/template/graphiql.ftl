<html>
<head>
    <title>Simple GraphiQL Example</title>
    <link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet"/>
</head>
<body style="margin: 0;">
<div id="graphiql" style="height: 100vh;"></div>

<script
        crossorigin
        src="https://unpkg.com/react/umd/react.production.min.js"
></script>
<script
        crossorigin
        src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"
></script>
<script
        crossorigin
        src="https://unpkg.com/graphiql/graphiql.min.js"
></script>

<script>
    const graphQLFetcher = graphQLParams =>
        fetch('https://localhost:8443/graphql/api', {
            method: 'post',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'Authorization': 'Bearer '+ 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJBcGFjaGVPRkJpeiIsImlhdCI6MTU0NzczOTM0OCwiZXhwIjoxNjc5Mjc1MzQ4LCJhdWQiOiJ3d3cuZXhhbXBsZS5jb20iLCJzdWIiOiJqcm9ja2V0QGV4YW1wbGUuY29tIiwiR2l2ZW5OYW1lIjoiSm9obm55IiwiU3VybmFtZSI6IlJvY2tldCIsIkVtYWlsIjoianJvY2tldEBleGFtcGxlLmNvbSIsInVzZXJMb2dpbklkIjoiYWRtaW4iLCJSb2xlIjpbIk1hbmFnZXIiLCJQcm9qZWN0IEFkbWluaXN0cmF0b3IiXX0.fwafgrgpodBJcXxNTQdZknKeWKb3sDOsQrcR2vcRw97FznD6mkE79p10Tu7cqpUx7LiXuROUAnXEgqDice-BSg'
            },
            body: JSON.stringify(graphQLParams),
            credentials: 'include',
        })
            .then(response => response.json())
            .catch(() => response.text());
    ReactDOM.render(
        React.createElement(GraphiQL, {fetcher: graphQLFetcher}),
        document.getElementById('graphiql'),
    );
</script>
</body>
</html>