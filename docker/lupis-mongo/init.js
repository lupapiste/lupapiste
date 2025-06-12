db.createUser({
  user: "lupapiste",
  pwd: "lupapiste",
  roles: [{role: "readWriteAnyDatabase", db: "admin"},
          {role: "dbAdminAnyDatabase", db: "admin"},
          {role: "readWrite", db: "lupapiste"}]});

db = db.getSiblingDB("lupapiste");

db.createRole({
  role: "sessionReadOnly",
  privileges: [{
    resource: {db: "lupapiste", collection: "sessions"},
    actions: ["find"]
  }],
  roles: []
});

db.createUser({
  user: "sessions_ro",
  pwd: "sessions_ro",
  roles: [{role: "sessionReadOnly", db: "lupapiste"}]
});
