;(function() {
  "use strict";

  var usersList = null;
  
  hub.onPageChange("users", function() {
    if (!usersList) usersList = users.create($("#users .fancy-users-table"));
  });


})();
