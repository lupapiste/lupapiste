;(function() {
  "use strict";

  function NotificationsModel() {
    var self = this;

    self.applicants = ko.observable(false);
    self.authorities = ko.observable(false);
    self.title = ko.observable("");
    self.message = ko.observable("");


    self.updateNotifications = function() {
      ajax.command("notifications-update", {applicants: self.applicants(),
                                            authorities: self.authorities(),
                                            "title-fi": self.title(),
                                            "message-fi": self.message()})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    };
  }

  var notificationsModel = new NotificationsModel();

  $(function() {
    $("#notifications").applyBindings(notificationsModel);
  });

})();
