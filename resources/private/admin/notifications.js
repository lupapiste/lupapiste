;(function() {
  "use strict";

  function NotificationsModel() {
    var self = this;

    self.applicants = ko.observable(false);
    self.authorities = ko.observable(false);
    self.message = ko.observable("");

    self.updateNotifications = function() {
      ajax.command("notifications-update", {applicants: self.applicants(),
                                            authorities: self.authorities(),
                                            message: self.message()})
        .success(function(res) {
          console.log("success", res);
        })
        .error(_.noop)
        .call();
    };
  }

  var notificationsModel = new NotificationsModel();

  $(function() {
    $("#notifications").applyBindings(notificationsModel);
  });

})();
