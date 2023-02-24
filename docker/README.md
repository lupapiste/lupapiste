# Development setup

Start docker containers:

```bash
docker-compose up -d
```

See the logs:

```bash
docker-compose logs -f
```

MongoDB console:

```bash
docker exec -it lupis-mongo mongo -u lupapiste -p lupapiste lupapiste
```

Check `user.properties` at project root, make sure you have:

```
mongodb.credentials.username  lupapiste
mongodb.credentials.password  lupapiste
mongodb.servers.0.host        localhost
mongodb.servers.0.port        27017
```

Start lupis repl and run minimal fixture:

```clj
(user/go)
; there is also button in UI which applies "minimal" fixture.
(lupapalvelu.fixture.core/apply-fixture "minimal")
```

# Cleanup

Stop containers:

```bash
docker-compose stop
```

Stopped containers can be started again with `docker-compose up`.

Destroy containers and their data:

```bash
docker-compose down
```

You can recreate containers again with `docker-compose up`.
