FROM mongo:6.0
ADD ./init.js /docker-entrypoint-initdb.d/
# The replica set mode in lupis-mongo requires a key file for authentication, even though the development environment
# does not include additional replica set members. This is a dummy key file to be used in local development
ADD ./dev-mongo-rs-keyfile /etc/dev-mongo-rs-keyfile
RUN chown 999:999 /etc/dev-mongo-rs-keyfile && chmod 400 /etc/dev-mongo-rs-keyfile
