;(function() {
  "use strict";

  var usersModel = users.create(editUserModel, 'admin');

  hub.onPageChange("admin-users", usersModel.load);

  $(function() {
    $("#admin-users").applyBindings(usersModel);
  });

})();
