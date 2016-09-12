ArgusSDK
=====

ArgusSDK is the official JavaSDK for Argus.  Use it to integrate client code with Argus!

## Example

```
        String username = getUsername();
        String password = getPassword();
        List<String> expressions = getExpressions();
        int connections = 10;
        try (ArgusService svc = ArgusService.getInstance("https://argus.mycompany.com/argusws", connections)) {
            svc.getAuthService().login(username, password);
            List<Metric> metrics = svc.getMetricService().getMetrics(expressions);
            // Do some cool stuff with the services.
            svc.getAuthService().logout();
        }
```

## API Documentation
API documentation can be generated simply by building the project.  Do so, and code on.