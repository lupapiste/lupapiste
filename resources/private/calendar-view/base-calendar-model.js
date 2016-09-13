LUPAPISTE.BaseCalendarModel = function () {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.markSeen = function(r) {
    ajax
      .command("mark-reservation-update-seen", {id: lupapisteApp.models.application.id(), reservationId: r.id()})
      .success(function() {
        r.acknowledged("seen");
      })
      .call();
  };
};