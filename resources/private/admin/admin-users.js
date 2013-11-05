;(function() {
  "use strict";

  var usersList = null;
  var usersTableConfig = {
    ops: [{name: "enable",
           showFor: function(user) { return !user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: true})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "disable",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("set-user-enabled", {email: email, enabled: false})
               .success(function() { callback(true); })
               .call();
           }},
          {name: "resetPassword",
           showFor: function(user) { return user.enabled; },
           operation: function(email, callback) {
             ajax
               .command("reset-password", {email: email})
               .success(function() { callback(true); })
               .call();
           }}]
  };
  
  hub.onPageChange("users", function() {
    if (!usersList) usersList = users.create($("#users .fancy-users-table"), usersTableConfig);
  });

})();
