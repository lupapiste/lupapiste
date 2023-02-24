db.createUser({
  user: "lupapiste",
  pwd: "lupapiste",
  roles: [{role: "readWriteAnyDatabase", db: "admin"},
          {role: "dbAdminAnyDatabase", db: "admin"},
          {role: "readWrite", db: "lupapiste"}]});
